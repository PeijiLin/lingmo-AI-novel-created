package com.linpj.novel.create.service.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linpj.novel.create.pojo.entity.NovelRagChunk;
import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import com.linpj.novel.create.mapper.novel.NovelRagChunkMapper;
import com.linpj.novel.create.service.wiki.WikiPageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检索路由（v2.0 设计 §6.6 / WIKI_RAG_UNIFIED_PLAN §4：三段式主路径）
 *
 * <p>① 实体定位：书内别名精确匹配查询文本（纯 SQL，零 embedding 成本）；
 * <p>② 锚点下钻：命中页的 SETTING 块直接从 novel_rag_chunk 取（确定性取数，不走相似度）；
 * <p>③ 相似度兜底：bookId 过滤 + kNN 补充（永不跨书；已返回的 chunkId 不重复）。
 *
 * <p>权威分层（ADR-2）：SETTING（正典页）证据优先于 CHAPTER/REFERENCE（派生/素材）；
 * 全库相似度从"主路径"降级为"兜底"。
 *
 * @author HL
 */
@Service
public class RetrievalRouter {

    private static final Logger log = LoggerFactory.getLogger(RetrievalRouter.class);

    /** 检索证据（统一带回指字段：chunkId 必有，pageId/anchorId/chapterNum 按来源） */
    public record Evidence(Long chunkId, Long pageId, Long anchorId, Integer chapterNum,
                           String kind, String content, Double score) {

        Map<String, Object> metadata() {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("chunkId", chunkId);
            if (pageId != null) {
                meta.put("pageId", pageId);
            }
            if (anchorId != null) {
                meta.put("anchorId", anchorId);
            }
            if (chapterNum != null) {
                meta.put("chapterNum", chapterNum);
            }
            meta.put("kind", kind);
            return meta;
        }

        Document toDocument() {
            return new Document(content, metadata());
        }
    }

    /** 证据 → Document（供 RagAdvisorFactory 检索器适配；包外可用） */
    public static Document toDocument(Evidence evidence) {
        return evidence.toDocument();
    }

    private final WikiPageService wikiPageService;
    private final NovelRagChunkMapper chunkMapper;
    private final PgVectorStore vectorStore;

    @Value("${novel.pipeline.rag.enabled:true}")
    private boolean ragEnabled;

    public RetrievalRouter(WikiPageService wikiPageService,
                           NovelRagChunkMapper chunkMapper,
                           PgVectorStore vectorStore) {
        this.wikiPageService = wikiPageService;
        this.chunkMapper = chunkMapper;
        this.vectorStore = vectorStore;
    }

    /**
     * 三段式检索主入口。
     *
     * @param query 查询文本（大纲/正文/用户输入；含实体名即可命中步骤①）
     * @param topK  召回上限（含①②结果计入总额；null=默认 4）
     */
    public List<Evidence> retrieve(Long bookId, String query, Integer topK) {
        if (bookId == null || query == null || query.isBlank()) {
            return List.of();
        }
        int limit = topK == null || topK <= 0 ? 4 : topK;
        List<Evidence> result = new ArrayList<>();
        Set<Long> seenChunkIds = new LinkedHashSet<>();

        // ① 实体定位 + ② 锚点下钻（确定性：别名命中页 → 该页 SETTING 块全取）
        for (NovelWikiPage page : locatePages(bookId, query)) {
            chunkMapper.selectList(new LambdaQueryWrapper<NovelRagChunk>()
                            .eq(NovelRagChunk::getPageId, page.getId())
                            .eq(NovelRagChunk::getChunkType, "SETTING")
                            .orderByAsc(NovelRagChunk::getSeq))
                    .forEach(chunk -> {
                        if (seenChunkIds.add(chunk.getId()) && result.size() < limit) {
                            result.add(new Evidence(chunk.getId(), chunk.getPageId(), chunk.getAnchorId(),
                                    chunk.getChapterNum(), chunk.getChunkType(), chunk.getContent(), null));
                        }
                    });
        }
        if (result.size() >= limit) {
            return result;
        }

        // ③ 相似度兜底（bookId 过滤；同书 SETTING 块与①②同权重自然召回）
        if (ragEnabled) {
            try {
                SearchRequest request = SearchRequest.builder()
                        .query(query)
                        .topK(limit)
                        .filterExpression(new Filter.Expression(
                                Filter.ExpressionType.EQ,
                                new Filter.Key("metadata.bookId"),
                                new Filter.Value(bookId)))
                        .build();
                for (Document doc : vectorStore.similaritySearch(request)) {
                    Long chunkId = longOf(doc.getMetadata().get("chunkId"));
                    if (chunkId != null && seenChunkIds.add(chunkId) && result.size() < limit) {
                        result.add(new Evidence(chunkId,
                                longOf(doc.getMetadata().get("pageId")),
                                longOf(doc.getMetadata().get("anchorId")),
                                intOf(doc.getMetadata().get("chapterNum")),
                                strOf(doc.getMetadata().get("kind")),
                                doc.getText(),
                                doc.getScore()));
                    }
                }
            } catch (Exception e) {
                // 兜底通道故障：①②的确定性结果照常返回（正典证据不受影响）
                log.warn("相似度兜底检索失败（保留①②正典证据）: bookId={}, error={}", bookId, e.getMessage());
            }
        }
        return result;
    }

    /** ① 实体定位：书内别名在查询文本中的精确出现（子串匹配） */
    List<NovelWikiPage> locatePages(Long bookId, String query) {
        List<NovelWikiPage> pages = wikiPageService.listCanonPages(bookId);
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        String haystack = query.replace("\n", "");
        List<NovelWikiPage> located = new ArrayList<>();
        for (NovelWikiPage page : pages) {
            for (String alias : wikiPageService.aliasesOf(page.getId())) {
                if (alias != null && alias.length() >= 2
                        && haystack.contains(alias)) {
                    located.add(page);
                    break;
                }
            }
        }
        return located;
    }

    private static Long longOf(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static Integer intOf(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static String strOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
