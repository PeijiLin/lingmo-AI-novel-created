package com.linpj.novel.create.service.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切块服务单元测试（确定性纯函数：边界 / 合并 / 硬切 / 重叠）
 *
 * @author HL
 */
class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService();

    @Test
    void nullOrBlankReturnsEmpty() {
        assertTrue(service.chunk(null).isEmpty());
        assertTrue(service.chunk("").isEmpty());
        assertTrue(service.chunk("   \n  \n").isEmpty());
    }

    @Test
    void shortTextSingleChunk() {
        List<ChunkingService.Chunk> chunks = service.chunk("林澈是三清山弟子，持青锋剑。");
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).seq());
        assertTrue(chunks.get(0).content().contains("林澈"));
        assertEquals(chunks.get(0).content().length(), chunks.get(0).tokenCount());
    }

    @Test
    void multiParagraphMergesToTargetSize() {
        String para = "段".repeat(200);
        String text = String.join("\n", para, para, para, para);
        List<ChunkingService.Chunk> chunks = service.chunk(text);
        assertTrue(chunks.size() >= 2, "四段 200 字应聚合为至少 2 块，实际 " + chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).seq(), "seq 应从 0 连续递增");
        }
    }

    @Test
    void adjacentChunksCarryOverlap() {
        String p1 = "一".repeat(400);
        String p2 = "二".repeat(400);
        List<ChunkingService.Chunk> chunks = service.chunk(p1 + "\n" + p2);
        assertTrue(chunks.size() >= 2);
        String second = chunks.get(1).content();
        assertTrue(second.endsWith("二".repeat(Math.min(ChunkingService.OVERLAP, 400))),
                "第二块应携带前块尾部重叠");
        assertFalse(second.startsWith("二二二二二二二二二二二二二"), "重叠之外不应再有前块内容");
    }

    @Test
    void oversizedParagraphHardSplitKeepsSentences() {
        // 单段 1500 字无换行：必须硬切，且块数 >= 3
        String text = "句。".repeat(750); // 1500 字符
        List<ChunkingService.Chunk> chunks = service.chunk(text);
        assertTrue(chunks.size() >= 3, "超长段应硬切成多块，实际 " + chunks.size());
        // 句末优先：首块应在句号处收块（长度为偶数）
        assertEquals(0, chunks.get(0).content().length() % 2);
    }

    @Test
    void tinyTailMergesIntoLastChunk() {
        String body = "甲".repeat(400);
        String tail = "尾";
        List<ChunkingService.Chunk> chunks = service.chunk(body + "\n" + tail);
        assertEquals(1, chunks.size(), "1 字尾块应并入前块");
        assertTrue(chunks.get(0).content().contains("尾"));
    }

    @Test
    void windowsLineEndingsNormalized() {
        List<ChunkingService.Chunk> chunks = service.chunk("甲\r\n乙\r\n");
        assertEquals(1, chunks.size());
        assertFalse(chunks.get(0).content().contains("\r"));
    }
}
