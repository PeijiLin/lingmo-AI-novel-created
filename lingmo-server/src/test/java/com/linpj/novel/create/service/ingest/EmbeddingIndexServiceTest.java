package com.linpj.novel.create.service.ingest;

import com.linpj.novel.create.pojo.entity.NovelRagChunk;
import com.linpj.novel.create.mapper.novel.NovelRagChunkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 统一索引入口单元测试：metadata 契约（数值类型是过滤命中的命门）+ 章节幂等语义
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingIndexServiceTest {

    @Mock private NovelRagChunkMapper chunkMapper;
    @Mock private PgVectorStore vectorStore;
    @Captor private org.mockito.ArgumentCaptor<List<Document>> documentsCaptor;

    private EmbeddingIndexService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingIndexService(chunkMapper, vectorStore, new ChunkingService());
        ReflectionTestUtils.setField(service, "embeddingModel", "test-embedding");
        ReflectionTestUtils.setField(service, "embeddingDim", 1024);
        // 模拟 BIGSERIAL 回填：insert 后分配递增 id（否则 metadata chunkId 为空，Document 构造会拒绝 null）
        org.mockito.Mockito.lenient().when(chunkMapper.insert(any(NovelRagChunk.class)))
                .thenAnswer(invocation -> {
                    NovelRagChunk row = invocation.getArgument(0);
                    row.setId(System.nanoTime());
                    return 1;
                });
    }

    // ------------------------------------------------------------------
    // metadata 契约（§3.3）
    // ------------------------------------------------------------------

    @Test
    void metadataContractNumbersNotStrings() {
        NovelRagChunk row = new NovelRagChunk();
        row.setId(345L);
        row.setBookId(12L);
        row.setChunkType("REFERENCE");
        row.setSourceId(7L);
        row.setChapterNum(null);

        Map<String, Object> metadata = service.buildMetadata(row);

        // bookId / chunkId / sourceId 必须是数值（Long）——写成字符串会让书级过滤恒空
        assertInstanceOf(Long.class, metadata.get("bookId"));
        assertInstanceOf(Long.class, metadata.get("chunkId"));
        assertInstanceOf(Long.class, metadata.get("sourceId"));
        assertEquals(12L, metadata.get("bookId"));
        assertEquals(345L, metadata.get("chunkId"));
        assertEquals("REFERENCE", metadata.get("kind"));
        assertEquals("test-embedding", metadata.get("embeddingModel"));
        assertEquals(1024, metadata.get("dim"));
    }

    @Test
    void chapterMetadataCarriesChapterNumAsNumber() {
        NovelRagChunk row = new NovelRagChunk();
        row.setId(1L);
        row.setBookId(2L);
        row.setChunkType("CHAPTER");
        row.setChapterNum(5);

        Map<String, Object> metadata = service.buildMetadata(row);

        assertEquals(5, metadata.get("chapterNum"));
        assertInstanceOf(Integer.class, metadata.get("chapterNum"));
    }

    // ------------------------------------------------------------------
    // 入库 / 幂等语义
    // ------------------------------------------------------------------

    @Test
    void indexChapterWritesVectorsWithFullMetadata() {
        String content = "章".repeat(600); // 单段 600 字：硬切 500 + 100，聚合后 2 块

        int chunks = service.indexChapter(1L, 3, content);

        assertEquals(2, chunks);
        verify(chunkMapper).delete(any());
        verify(vectorStore).delete("bookId == 1 && chapterNum == 3 && kind == 'CHAPTER'");
        verify(vectorStore).doAdd(anyList());
    }

    @Test
    void indexChapterSkipsWhenChapterNumMissing() {
        int chunks = service.indexChapter(1L, null, "内容");

        assertEquals(0, chunks);
        verify(vectorStore, never()).doAdd(anyList());
        verify(chunkMapper, never()).delete(any());
    }

    @Test
    void deleteSourceFiltersBySourceIdOnly() {
        service.deleteSource(9L);

        verify(vectorStore).delete("sourceId == 9");
        verify(chunkMapper).delete(any());
    }

    @Test
    void deleteBookFiltersByBookId() {
        service.deleteBook(4L);

        verify(vectorStore).delete("bookId == 4");
        verify(chunkMapper).delete(any());
    }

    @Test
    void emptyContentIndexesNothing() {
        int chunks = service.indexSourceContent(1L, 5L, "  ");

        assertEquals(0, chunks);
        verify(vectorStore, never()).doAdd(anyList());
    }

    @Test
    void indexSourceBuildsDocumentsWithContractMetadata() {
        service.indexSourceContent(1L, 5L, "林澈是三清山弟子，持青锋剑，性格沉静。他奉师命下山查访失踪的同门。");

        verify(vectorStore).doAdd(documentsCaptor.capture());
        List<Document> documents = documentsCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertFalse(documents.isEmpty());
        for (Document doc : documents) {
            Map<String, Object> metadata = doc.getMetadata();
            assertEquals(1L, metadata.get("bookId"));
            assertEquals(5L, metadata.get("sourceId"));
            assertEquals("REFERENCE", metadata.get("kind"));
            assertInstanceOf(Long.class, metadata.get("chunkId"));
        }
    }

    @Test
    void vectorDeleteFailureDoesNotBreakFlow() {
        org.mockito.Mockito.doThrow(new RuntimeException("filter parse error"))
                .when(vectorStore).delete(anyString());

        // 删除向量失败：吞异常，chunk 行删除照常执行
        service.deleteSource(9L);

        verify(chunkMapper).delete(any());
    }
}
