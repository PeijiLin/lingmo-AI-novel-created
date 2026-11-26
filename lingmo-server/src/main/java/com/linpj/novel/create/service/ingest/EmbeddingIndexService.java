package com.linpj.novel.create.service.ingest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linpj.novel.create.pojo.entity.NovelRagChunk;
import com.linpj.novel.create.mapper.novel.NovelRagChunkMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一索引入口（WIKI_RAG_UNIFIED_PLAN §4.2：写 chunk + 写向量 + 删除向量的唯一通道）
 *
 * <p>设计约束：
 * <ul>
 *   <li>novel_rag_chunk 是权威记录；vector_store 只是索引投影（可按 chunk 表重建）；</li>
 *   <li>metadata 契约（§3.3）：bookId / chunkId / kind 必填且为数值——书级过滤与证据回指的命门；</li>
 *   <li>删除一律按 metadata 过滤（sourceId / bookId+chapterNum+kind），禁止按 content 模糊删。</li>
 * </ul>
 *
 * @author HL
 */
@Service
public class EmbeddingIndexService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIndexService.class);

    public static final String KIND_SETTING = "SETTING";
    public static final String KIND_CHAPTER = "CHAPTER";
    public static final String KIND_REFERENCE = "REFERENCE";

    private final NovelRagChunkMapper chunkMapper;
    private final PgVectorStore vectorStore;
    private final ChunkingService chunkingService;

    @Value("${spring.ai.openai.embedding.options.model:unknown}")
    private String embeddingModel;

    @Value("${spring.ai.openai.embedding.options.dimensions:1024}")
    private int embeddingDim;

    public EmbeddingIndexService(NovelRagChunkMapper chunkMapper,
                                 PgVectorStore vectorStore,
                                 ChunkingService chunkingService) {
        this.chunkMapper = chunkMapper;
        this.vectorStore = vectorStore;
        this.chunkingService = chunkingService;
    }

    // ------------------------------------------------------------------
    // 素材入库（REFERENCE）
    // ------------------------------------------------------------------

    /**
     * 素材文本入库：切块 → 写 chunk 行 → 写向量（metadata 全打标）。
     * 同一 source 已有块时先清旧（重传替换语义）。
     *
     * @return 本次入库块数（0 = 空文本，调用方应将 source 标记 FAILED）
     */
    public int indexSourceContent(Long bookId, Long sourceId, String content) {
        deleteSource(sourceId);
        List<ChunkingService.Chunk> chunks = chunkingService.chunk(content);
        if (chunks.isEmpty()) {
            return 0;
        }
        List<NovelRagChunk> rows = new ArrayList<>(chunks.size());
        for (ChunkingService.Chunk chunk : chunks) {
            NovelRagChunk row = new NovelRagChunk();
            row.setBookId(bookId);
            row.setSourceId(sourceId);
            row.setChunkType(KIND_REFERENCE);
            row.setSeq(chunk.seq());
            row.setContent(chunk.content());
            row.setTokenCount(chunk.tokenCount());
            row.setEmbeddingModel(embeddingModel);
            row.setStatus("READY");
            rows.add(row);
        }
        return index(rows);
    }

    // ------------------------------------------------------------------
    // 章节定稿回灌（CHAPTER，幂等：先删后插）
    // ------------------------------------------------------------------

    /**
     * 章节终稿入库：按 (bookId, chapterNum, CHAPTER) 幂等——删除旧块与旧向量后重建。
     *
     * @return 本次入库块数（0 = 空文本或章号不可得，跳过不报错）
     */
    public int indexChapter(Long bookId, Integer chapterNum, String content) {
        if (bookId == null || chapterNum == null || content == null || content.isBlank()) {
            return 0;
        }
        deleteChapter(bookId, chapterNum);
        List<ChunkingService.Chunk> chunks = chunkingService.chunk(content);
        if (chunks.isEmpty()) {
            return 0;
        }
        List<NovelRagChunk> rows = new ArrayList<>(chunks.size());
        for (ChunkingService.Chunk chunk : chunks) {
            NovelRagChunk row = new NovelRagChunk();
            row.setBookId(bookId);
            row.setChunkType(KIND_CHAPTER);
            row.setChapterNum(chapterNum);
            row.setSeq(chunk.seq());
            row.setContent(chunk.content());
            row.setTokenCount(chunk.tokenCount());
            row.setEmbeddingModel(embeddingModel);
            row.setStatus("READY");
            rows.add(row);
        }
        return index(rows);
    }

    // ------------------------------------------------------------------
    // 删除（级联清理 chunk 行 + 向量）
    // ------------------------------------------------------------------

    /** 删除某素材的全部块与向量 */
    public void deleteSource(Long sourceId) {
        if (sourceId == null) {
            return;
        }
        deleteVectors("sourceId == " + sourceId);
        chunkMapper.delete(new LambdaQueryWrapper<NovelRagChunk>()
                .eq(NovelRagChunk::getSourceId, sourceId));
    }

    /** 删除某章全部章节块与向量（幂等重灌前置） */
    public void deleteChapter(Long bookId, Integer chapterNum) {
        if (bookId == null || chapterNum == null) {
            return;
        }
        deleteVectors("bookId == " + bookId + " && chapterNum == " + chapterNum
                + " && kind == '" + KIND_CHAPTER + "'");
        chunkMapper.delete(new LambdaQueryWrapper<NovelRagChunk>()
                .eq(NovelRagChunk::getBookId, bookId)
                .eq(NovelRagChunk::getChapterNum, chapterNum)
                .eq(NovelRagChunk::getChunkType, KIND_CHAPTER));
    }

    /** 删除某书全部块与向量（知识库整库删除/重建用） */
    public void deleteBook(Long bookId) {
        if (bookId == null) {
            return;
        }
        deleteVectors("bookId == " + bookId);
        chunkMapper.delete(new LambdaQueryWrapper<NovelRagChunk>()
                .eq(NovelRagChunk::getBookId, bookId));
    }

    /** 本书块数（诊断/前端统计） */
    public long countChunks(Long bookId) {
        if (bookId == null) {
            return 0;
        }
        return chunkMapper.selectCount(new LambdaQueryWrapper<NovelRagChunk>()
                .eq(NovelRagChunk::getBookId, bookId));
    }

    // ------------------------------------------------------------------
    // 内部：写库 + 打标
    // ------------------------------------------------------------------

    /** 落 chunk 行（取回自增 id）→ 按契约打 metadata → 批量写向量 */
    private int index(List<NovelRagChunk> rows) {
        for (NovelRagChunk row : rows) {
            chunkMapper.insert(row);
        }
        List<Document> documents = new ArrayList<>(rows.size());
        for (NovelRagChunk row : rows) {
            documents.add(new Document(row.getContent(), buildMetadata(row)));
        }
        vectorStore.doAdd(documents);
        log.info("RAG 入库: bookId={}, kind={}, chunks={}", rows.get(0).getBookId(),
                rows.get(0).getChunkType(), rows.size());
        return rows.size();
    }

    /**
     * metadata 契约（§3.3）：bookId / chunkId / kind 必填；数值一律 Long（JSON number）。
     * 数值类型是过滤命中的命门——写字符串会导致书级过滤恒空（历史缺陷，勿回退）。
     */
    Map<String, Object> buildMetadata(NovelRagChunk row) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("bookId", row.getBookId());
        if (row.getId() != null) {
            metadata.put("chunkId", row.getId());
        }
        metadata.put("kind", row.getChunkType());
        if (row.getSourceId() != null) {
            metadata.put("sourceId", row.getSourceId());
        }
        if (row.getPageId() != null) {
            metadata.put("pageId", row.getPageId());
        }
        if (row.getAnchorId() != null) {
            metadata.put("anchorId", row.getAnchorId());
        }
        if (row.getChapterNum() != null) {
            metadata.put("chapterNum", row.getChapterNum());
        }
        metadata.put("embeddingModel", embeddingModel);
        metadata.put("dim", embeddingDim);
        return metadata;
    }

    private void deleteVectors(String filterExpression) {
        try {
            vectorStore.delete(filterExpression);
        } catch (Exception e) {
            // 向量删除失败只告警：chunk 行已删，残留向量会被过滤键天然隔离（bookId 必带）
            log.warn("向量删除失败（残留向量按 bookId 隔离，不影响检索隔离）: filter={}", filterExpression, e);
        }
    }

    /** 排序稳定输出（诊断用） */
    public Map<Long, List<NovelRagChunk>> groupByChapter(Long bookId) {
        Map<Long, List<NovelRagChunk>> result = new LinkedHashMap<>();
        chunkMapper.selectList(new LambdaQueryWrapper<NovelRagChunk>()
                        .eq(NovelRagChunk::getBookId, bookId)
                        .eq(NovelRagChunk::getChunkType, KIND_CHAPTER)
                        .orderByAsc(NovelRagChunk::getChapterNum))
                .forEach(chunk -> result.computeIfAbsent(
                        chunk.getChapterNum() == null ? -1L : chunk.getChapterNum().longValue(),
                        k -> new ArrayList<>()).add(chunk));
        return result;
    }
}
