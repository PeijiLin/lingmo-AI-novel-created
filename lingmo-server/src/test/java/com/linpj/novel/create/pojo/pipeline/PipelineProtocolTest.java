package com.linpj.novel.create.pojo.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linpj.novel.create.pojo.dto.AiChatRequest;
import com.linpj.novel.create.utils.JsonUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE 事件协议与枚举契约测试（设计 4.3）
 *
 * @author HL
 */
class PipelineProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------
    // ReviewReport 通过规则（设计 3.3）
    // ------------------------------------------------------------------

    @Test
    void reviewReportDetectsSeverityCaseInsensitively() {
        ReviewReport report = new ReviewReport();
        report.setIssues(List.of(issue("HIGH"), issue("high"), issue("Medium")));

        assertTrue(report.hasHighIssue());
        assertTrue(report.hasMediumIssue());
    }

    @Test
    void reviewReportHandlesNullIssues() {
        ReviewReport report = new ReviewReport();
        report.setIssues(null);

        assertFalse(report.hasHighIssue());
        assertFalse(report.hasMediumIssue());
    }

    private Issue issue(String severity) {
        Issue i = new Issue();
        i.setSeverity(severity);
        i.setType("PLOT_HOLE");
        return i;
    }

    // ------------------------------------------------------------------
    // 六维 rubric 契约（v2.0 设计 8.1 / M3-D4：weightedScore 权重计算与容错）
    // ------------------------------------------------------------------

    @Test
    void weightedScoreAppliesDimensionWeights() {
        ReviewReport report = new ReviewReport();
        report.setDimensionScores(java.util.Map.of(
                "PLOT_ADVANCE", 4, "CHARACTER", 4,
                "CONFLICT", 3, "PACING", 3,
                "PROSE", 3, "ENDING_HOOK", 3));
        // 加权：(4*.25 + 4*.20 + 3*.15 + 3*.15 + 3*.15 + 3*.10) = 1.0+0.8+0.45+0.45+0.45+0.3 = 3.45
        assertEquals(3.45, report.weightedScore(), 0.0001);
    }

    @Test
    void weightedScoreToleratesMissingDimensions() {
        ReviewReport report = new ReviewReport();
        report.setDimensionScores(java.util.Map.of("PLOT_ADVANCE", 5, "PROSE", 3));
        // 权重归一化：(5*.25 + 3*.15) / (.25+.15) = (1.25+0.45)/0.4 = 4.25
        assertEquals(4.25, report.weightedScore(), 0.0001);
    }

    @Test
    void weightedScoreEmptyIsZero() {
        ReviewReport report = new ReviewReport();
        assertEquals(0.0, report.weightedScore());
        report.setDimensionScores(null);
        assertEquals(0.0, report.weightedScore());
    }

    @Test
    void sixDimensionsRoundTripAndGateUnaffected() {
        ReviewReport report = new ReviewReport();
        report.setApproved(true);
        report.setOverallScore(8);
        report.setReadOnScore(6);
        report.setDimensionScores(new java.util.LinkedHashMap<>(java.util.Map.of(
                "PLOT_ADVANCE", 5, "CHARACTER", 4, "CONFLICT", 4,
                "PACING", 5, "PROSE", 4, "ENDING_HOOK", 5)));
        report.setIssues(java.util.List.of());

        ReviewReport parsed = JsonUtil.fromJson(JsonUtil.toJson(report), ReviewReport.class);
        // 序列化往返：六维与续读意愿保留
        assertEquals(Integer.valueOf(5), parsed.getDimensionScores().get("PLOT_ADVANCE"));
        assertEquals(Integer.valueOf(6), parsed.getReadOnScore());
        // 门槛判定不受新字段影响（旧模型缺维度时 gate 语义不变）
        assertTrue(parsed.isApproved() && !parsed.hasHighIssue());
    }

    // ------------------------------------------------------------------
    // Scene 叙事模式契约（v2.0 设计 2.1：JSON Schema 序列化往返）
    // ------------------------------------------------------------------

    @Test
    void sceneNarrativeModeRoundTripsThroughJson() {
        ChapterOutline outline = new ChapterOutline();
        outline.setChapterTitle("第二十一章");

        Scene flashback = new Scene();
        flashback.setLocation("落霞谷");
        flashback.setConflict("旧日重演");
        flashback.setNarrativeMode(NarrativeMode.FLASHBACK);
        flashback.setFlashbackTargetChapter(5L);

        Scene dream = new Scene();
        dream.setLocation("深渊");
        dream.setConflict("心魔");
        dream.setNarrativeMode(NarrativeMode.DREAM);

        outline.setScenes(List.of(flashback, dream));
        outline.setCharacterIds(List.of(1L));

        // Planner 输出 -> JSON -> 反序列化（BeanOutputConverter 同一路径的契约保障）
        String json = JsonUtil.toJson(outline);
        ChapterOutline parsed = JsonUtil.fromJson(json, ChapterOutline.class);

        assertEquals(NarrativeMode.FLASHBACK, parsed.getScenes().get(0).getNarrativeMode());
        assertEquals(5L, parsed.getScenes().get(0).getFlashbackTargetChapter());
        assertEquals(NarrativeMode.DREAM, parsed.getScenes().get(1).getNarrativeMode());
        // 未标注场景默认 null（渲染与规则侧按 NORMAL 处理）
        assertNull(parsed.getScenes().get(1).getFlashbackTargetChapter());
    }

    // ------------------------------------------------------------------
    // StageEvent 工厂与 SSE JSON 序列化
    // ------------------------------------------------------------------

    @Test
    void stageEventFactoriesProduceProtocolFields() {
        StageEvent started = StageEvent.started(StageCode.WRITING, 2, 4);
        assertEquals("WRITING", started.getCode());
        assertEquals("STARTED", started.getStatus());
        assertEquals(2, started.getIndex());
        assertEquals(4, started.getTotal());

        StageEvent revision = StageEvent.revision(StageCode.WRITING, 1);
        assertEquals("REVISION", revision.getStatus());
        assertEquals(1, revision.getRound());

        StageUsage usage = new StageUsage();
        usage.setStages(3);
        usage.setTotalTokens(8421L);
        StageEvent completed = StageEvent.completed(false, usage);
        assertEquals("COMPLETED", completed.getCode());
        assertFalse(completed.getDegraded());
        assertEquals(8421L, completed.getUsage().getTotalTokens());

        StageEvent failed = StageEvent.failed("模型无响应");
        assertEquals("FAILED", failed.getCode());
        assertEquals("模型无响应", failed.getError());
    }

    @Test
    void stageEventSurvivesJsonRoundTrip() {
        // ProgressEmitter 用 Gson 序列化 SSE 载荷，须与协议字段一一对应
        StageEvent original = StageEvent.started(StageCode.WRITING, 2, 4);
        String json = JsonUtil.toJson(original);
        StageEvent parsed = JsonUtil.fromJson(json, StageEvent.class);

        assertEquals(original.getCode(), parsed.getCode());
        assertEquals(original.getStatus(), parsed.getStatus());
        assertEquals(original.getIndex(), parsed.getIndex());
        assertEquals(original.getTotal(), parsed.getTotal());
        assertTrue(json.contains("\"code\":\"WRITING\""));
        assertTrue(json.contains("\"status\":\"STARTED\""));
    }

    // ------------------------------------------------------------------
    // CreationMode 大小写兼容反序列化（Jackson，@RequestBody 通道）
    // ------------------------------------------------------------------

    @Test
    void creationModeParsesCaseInsensitively() throws Exception {
        assertEquals(CreationMode.FAST, objectMapper.readValue("\"fast\"", CreationMode.class));
        assertEquals(CreationMode.STANDARD, objectMapper.readValue("\"Standard\"", CreationMode.class));
        assertEquals(CreationMode.REFINED, objectMapper.readValue("\"refined\"", CreationMode.class));
        assertEquals(CreationMode.PRECISE, objectMapper.readValue("\"PRECISE\"", CreationMode.class));
        assertNull(objectMapper.readValue("\"\"", CreationMode.class), "空串视为未传，走默认档");
    }

    @Test
    void creationModeRejectsUnknownValue() {
        // 未知值显式抛错（Spring MVC 转为 400），而非静默降级
        assertThrows(com.fasterxml.jackson.databind.exc.ValueInstantiationException.class,
                () -> objectMapper.readValue("\"UNKNOWN\"", CreationMode.class));
    }

    @Test
    void aiChatRequestBindsLowercaseMode() throws Exception {
        String json = """
                {"bookId": 1, "userInput": "写一段", "mode": "refined", "maxRewriteRounds": 3}
                """;
        AiChatRequest request = objectMapper.readValue(json, AiChatRequest.class);

        assertEquals(CreationMode.REFINED, request.getMode());
        assertEquals(3, request.getMaxRewriteRounds());

        PipelineRequest pipelineRequest = PipelineRequest.from(request);
        assertEquals(CreationMode.REFINED, pipelineRequest.getMode());
        assertEquals(3, pipelineRequest.getMaxRewriteRounds());
    }

    @Test
    void pipelineRequestDefaultsAndHardCaps() {
        AiChatRequest noMode = new AiChatRequest();
        noMode.setBookId(1L);
        noMode.setUserInput("写");
        PipelineRequest pipelineRequest = PipelineRequest.from(noMode);

        assertEquals(CreationMode.FAST, pipelineRequest.getMode(), "老前端不传 mode 默认 FAST");
        assertEquals(2, pipelineRequest.getMaxRewriteRounds());

        // 重写轮数硬上限 3：止损线不因入参放开
        AiChatRequest greedy = new AiChatRequest();
        greedy.setBookId(1L);
        greedy.setUserInput("写");
        greedy.setMaxRewriteRounds(99);
        assertEquals(3, PipelineRequest.from(greedy).getMaxRewriteRounds());

        AiChatRequest zero = new AiChatRequest();
        zero.setBookId(1L);
        zero.setUserInput("写");
        zero.setMaxRewriteRounds(0);
        assertEquals(1, PipelineRequest.from(zero).getMaxRewriteRounds(), "下限 1");
    }
}
