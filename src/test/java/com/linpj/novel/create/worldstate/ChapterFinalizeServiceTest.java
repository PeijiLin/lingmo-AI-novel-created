package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.agent.ExtractionAgent;
import com.linpj.novel.create.pojo.pipeline.CharacterStateSnapshot;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CharacterStateService;
import com.linpj.novel.create.service.PlotThreadService;
import com.linpj.novel.create.service.StoryDigestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 章节定稿结算服务单元测试（幂等防重 + 全失败不影响章节保存）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChapterFinalizeServiceTest {

    @Mock private ExtractionAgent extractionAgent;
    @Mock private StoryDigestService storyDigestService;
    @Mock private CharacterStateService characterStateService;
    @Mock private PlotThreadService plotThreadService;
    @Mock private CharacterCardsService characterCardsService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private ChapterFinalizeService service;

    @BeforeEach
    void setUp() {
        service = new ChapterFinalizeService(extractionAgent, storyDigestService,
                characterStateService, plotThreadService, characterCardsService, redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private CharacterStateSnapshot snapshot(Long characterId) {
        CharacterStateSnapshot snapshot = new CharacterStateSnapshot();
        snapshot.setCharacterId(characterId);
        snapshot.setLocation("城墙");
        snapshot.setMindset("紧绷");
        return snapshot;
    }

    @Test
    void finalizeChapterUpdatesDigestAndCharacterStates() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(plotThreadService.extractInlinePlotMarks(anyString())).thenReturn(List.of("新伏笔"));
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("滚动摘要");
        when(extractionAgent.extractStates(any(), any()))
                .thenReturn(new ExtractionAgent.ExtractionResult(List.of(snapshot(1L), snapshot(2L)), 80L));
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());

        service.finalizeChapter(1L, 10L, "第一章", "章节正文");

        // 1. 内联伏笔补账
        verify(plotThreadService).recordInlinePlotMarks(1L, 10L, List.of("新伏笔"));
        // 2. 摘要滚动更新
        verify(storyDigestService).saveDigest(1L, 10L, "滚动摘要");
        // 3. 人物状态增量维护
        verify(characterStateService).updateState(eq(1L), eq(1L), eq(10L), any());
        verify(characterStateService).updateState(eq(1L), eq(2L), eq(10L), any());
        // 4. 防重标记写入
        verify(valueOperations).set(contains("10"), anyString(), any(Duration.class));
    }

    @Test
    void duplicateContentSkipsSettlement() throws Exception {
        String content = "章节正文";
        String hash = sha256(content);
        when(valueOperations.get(anyString())).thenReturn(hash);

        service.finalizeChapter(1L, 10L, "第一章", content);

        // 内容 hash 未变化：跳过全部结算
        verify(extractionAgent, never()).rollUpDigest(any(), any(), any());
        verify(storyDigestService, never()).saveDigest(anyLong(), anyLong(), anyString());
        verify(characterStateService, never()).updateState(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void redisFailureDoesNotBlockSettlement() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis 不可用"));
        when(plotThreadService.extractInlinePlotMarks(anyString())).thenReturn(List.of());
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("滚动摘要");
        when(extractionAgent.extractStates(any(), any()))
                .thenReturn(new ExtractionAgent.ExtractionResult(List.of(), 80L));
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.finalizeChapter(1L, 10L, "第一章", "章节正文"));

        verify(storyDigestService).saveDigest(eq(1L), eq(10L), eq("滚动摘要"));
    }

    @Test
    void extractionFailureDoesNotPropagate() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(plotThreadService.extractInlinePlotMarks(anyString())).thenReturn(List.of());
        when(extractionAgent.rollUpDigest(any(), any(), any()))
                .thenThrow(new RuntimeException("LLM 超时"));

        // 定稿结算全部失败不影响章节保存本身
        assertDoesNotThrow(() -> service.finalizeChapter(1L, 10L, "第一章", "章节正文"));
        verify(characterStateService, never()).updateState(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void blankOrNullContentIsIgnored() {
        service.finalizeChapter(1L, 10L, "第一章", null);
        service.finalizeChapter(1L, 10L, "第一章", "  ");
        service.finalizeChapter(null, 10L, "第一章", "正文");

        verify(extractionAgent, never()).rollUpDigest(any(), any(), any());
        verify(valueOperations, times(0)).set(anyString(), any(), any(Duration.class));
    }

    private static String sha256(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
