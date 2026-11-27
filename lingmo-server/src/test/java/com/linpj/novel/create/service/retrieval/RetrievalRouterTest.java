package com.linpj.novel.create.service.retrieval;

import com.linpj.novel.create.pojo.entity.NovelRagChunk;
import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import com.linpj.novel.create.mapper.novel.NovelRagChunkMapper;
import com.linpj.novel.create.service.wiki.WikiPageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 检索路由单元测试：三段式主路径（实体定位→锚点下钻→相似度兜底）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetrievalRouterTest {

    @Mock private WikiPageService wikiPageService;
    @Mock private NovelRagChunkMapper chunkMapper;
    @Mock private org.springframework.ai.vectorstore.pgvector.PgVectorStore vectorStore;

    private RetrievalRouter router;

    @BeforeEach
    void setUp() {
        router = new RetrievalRouter(wikiPageService, chunkMapper, vectorStore);
        ReflectionTestUtils.setField(router, "ragEnabled", true);
    }

    private NovelWikiPage page(Long id, String type, String title) {
        NovelWikiPage page = new NovelWikiPage();
        page.setId(id);
        page.setBookId(1L);
        page.setEntityType(type);
        page.setEntityKey(type.toLowerCase() + ":" + id);
        page.setTitle(title);
        return page;
    }

    private NovelRagChunk settingChunk(Long id, Long pageId, String content) {
        NovelRagChunk chunk = new NovelRagChunk();
        chunk.setId(id);
        chunk.setBookId(1L);
        chunk.setPageId(pageId);
        chunk.setChunkType("SETTING");
        chunk.setSeq(0);
        chunk.setContent(content);
        return chunk;
    }

    // ------------------------------------------------------------------
    // ① 实体定位 + ② 锚点下钻（确定性路径，不走相似度）
    // ------------------------------------------------------------------

    @Test
    void aliasHitReturnsSettingChunksAndSimilarityOnlyFills() {
        NovelWikiPage linChe = page(1L, "CHARACTER", "林澈");
        when(wikiPageService.listCanonPages(1L)).thenReturn(List.of(linChe));
        when(wikiPageService.aliasesOf(1L)).thenReturn(List.of("林澈", "小林"));
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                settingChunk(10L, 1L, "林澈系青云宗外门弟子，性沉静。")));
        // ①② 已得 1 条（不足 topK=4）：③ 兜底被调用补齐，但返回空
        when(vectorStore.similaritySearch(
                org.mockito.ArgumentMatchers.<org.springframework.ai.vectorstore.SearchRequest>any()))
                .thenReturn(List.of());

        List<RetrievalRouter.Evidence> evidences =
                router.retrieve(1L, "写林澈在雾隐涧发现断刀", 4);

        assertEquals(1, evidences.size());
        assertEquals(10L, evidences.get(0).chunkId());
        assertEquals(1L, evidences.get(0).pageId());
        assertEquals("SETTING", evidences.get(0).kind());
        assertEquals(null, evidences.get(0).score(), "①② 确定性路径无相似度分");
        // ③ 只做补齐（quota 满即短路——见 aliasHitFillsQuotaSkipsSimilarity）
        verify(vectorStore).similaritySearch(
                org.mockito.ArgumentMatchers.<org.springframework.ai.vectorstore.SearchRequest>any());
    }

    @Test
    void aliasHitFillsQuotaSkipsSimilarity() {
        NovelWikiPage linChe = page(1L, "CHARACTER", "林澈");
        NovelWikiPage suWan = page(2L, "CHARACTER", "苏晚");
        when(wikiPageService.listCanonPages(1L)).thenReturn(List.of(linChe, suWan));
        when(wikiPageService.aliasesOf(1L)).thenReturn(List.of("林澈"));
        when(wikiPageService.aliasesOf(2L)).thenReturn(List.of("苏晚"));
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                settingChunk(10L, 1L, "林澈设定。"),
                settingChunk(11L, 1L, "林澈第二块。"),
                settingChunk(12L, 1L, "林澈第三块。"),
                settingChunk(13L, 1L, "林澈第四块。")));

        // ①② 命中 4 块 = topK：不触发相似度（省 embedding 成本）
        List<RetrievalRouter.Evidence> evidences = router.retrieve(1L, "林澈与苏晚同行", 4);

        assertEquals(4, evidences.size());
        verifyNoInteractions(vectorStore);
    }

    @Test
    void noAliasHitFallsBackToSimilarity() {
        when(wikiPageService.listCanonPages(1L)).thenReturn(List.of(page(1L, "CHARACTER", "林澈")));
        when(wikiPageService.aliasesOf(1L)).thenReturn(List.of("林澈"));
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        org.springframework.ai.document.Document doc =
                new org.springframework.ai.document.Document("青锋剑剑身刻有云纹。",
                        Map.of("chunkId", 77L, "kind", "REFERENCE", "bookId", 1L));
        org.springframework.ai.vectorstore.observation.VectorStoreObservationContext.Builder unused = null;
        when(vectorStore.similaritySearch(
                org.mockito.ArgumentMatchers.<org.springframework.ai.vectorstore.SearchRequest>any()))
                .thenReturn(List.of(doc));

        List<RetrievalRouter.Evidence> evidences = router.retrieve(1L, "青锋剑的来历", 4);

        assertEquals(1, evidences.size());
        assertEquals(77L, evidences.get(0).chunkId());
        assertEquals("REFERENCE", evidences.get(0).kind());
        verify(vectorStore).similaritySearch(
                org.mockito.ArgumentMatchers.<org.springframework.ai.vectorstore.SearchRequest>any());
    }

    @Test
    void similarityFailureKeepsCanonEvidence() {
        NovelWikiPage linChe = page(1L, "CHARACTER", "林澈");
        when(wikiPageService.listCanonPages(1L)).thenReturn(List.of(linChe));
        when(wikiPageService.aliasesOf(1L)).thenReturn(List.of("林澈"));
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                settingChunk(10L, 1L, "林澈设定。")));
        // 只给 1 条正典证据（不足 topK），兜底通道抛错
        when(vectorStore.similaritySearch(
                org.mockito.ArgumentMatchers.<org.springframework.ai.vectorstore.SearchRequest>any()))
                .thenThrow(new RuntimeException("embedding down"));

        List<RetrievalRouter.Evidence> evidences = router.retrieve(1L, "林澈出剑", 4);

        assertEquals(1, evidences.size(), "兜底故障时①②正典证据照常返回");
    }

    @Test
    void duplicateChunkIdsNotReturnedTwice() {
        NovelWikiPage page = page(1L, "CHARACTER", "林澈");
        when(wikiPageService.listCanonPages(1L)).thenReturn(List.of(page));
        when(wikiPageService.aliasesOf(1L)).thenReturn(List.of("林澈"));
        when(chunkMapper.selectList(any())).thenReturn(List.of(settingChunk(10L, 1L, "林澈设定。")));
        // 兜底返回同一 chunkId=10
        org.springframework.ai.document.Document dup =
                new org.springframework.ai.document.Document("林澈设定。",
                        Map.of("chunkId", 10L, "kind", "SETTING"));
        when(vectorStore.similaritySearch(
                org.mockito.ArgumentMatchers.<org.springframework.ai.vectorstore.SearchRequest>any()))
                .thenReturn(List.of(dup));

        List<RetrievalRouter.Evidence> evidences = router.retrieve(1L, "林澈出剑", 4);

        assertEquals(1, evidences.size(), "同 chunkId 证据去重（回指唯一）");
    }

    @Test
    void shortAliasIgnored() {
        NovelWikiPage page = page(1L, "CHARACTER", "甲");
        when(wikiPageService.listCanonPages(1L)).thenReturn(List.of(page));
        when(wikiPageService.aliasesOf(1L)).thenReturn(List.of("甲")); // 单字别名不做文本匹配
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        when(vectorStore.similaritySearch(
                org.mockito.ArgumentMatchers.<org.springframework.ai.vectorstore.SearchRequest>any()))
                .thenReturn(List.of());

        router.locatePages(1L, "写甲乙丙丁的故事");
        assertTrue(router.locatePages(1L, "写甲乙丙丁的故事").isEmpty(), "单字别名不参与定位（误报不可接受）");
    }

    @Test
    void blankInputReturnsEmpty() {
        assertTrue(router.retrieve(1L, null, 4).isEmpty());
        assertTrue(router.retrieve(1L, "  ", 4).isEmpty());
        assertTrue(router.retrieve(null, "查询", 4).isEmpty());
    }
}
