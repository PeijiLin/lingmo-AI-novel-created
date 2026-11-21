package com.linpj.novel.create.pipeline;

import com.linpj.novel.create.agent.CriticAgent;
import com.linpj.novel.create.agent.PlannerAgent;
import com.linpj.novel.create.agent.PolisherAgent;
import com.linpj.novel.create.agent.WriterAgent;
import com.linpj.novel.create.mapper.novel.CreationStageArtifactMapper;
import com.linpj.novel.create.pojo.entity.CreationStageArtifact;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.CreationMode;
import com.linpj.novel.create.pojo.pipeline.CriticContext;
import com.linpj.novel.create.pojo.pipeline.Issue;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.PlannerContext;
import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import com.linpj.novel.create.pojo.pipeline.Scene;
import com.linpj.novel.create.pojo.pipeline.StageEvent;
import com.linpj.novel.create.pojo.pipeline.WriterContext;
import com.linpj.novel.create.producer.CreationEventProducer;
import com.linpj.novel.create.rule.RuleCheckContext;
import com.linpj.novel.create.rule.RuleCheckResult;
import com.linpj.novel.create.rule.RuleChecker;
import com.linpj.novel.create.rule.RuleSeverity;
import com.linpj.novel.create.rule.RuleStage;
import com.linpj.novel.create.rule.RuleViolation;
import com.linpj.novel.create.service.ChapterOutlineStoreService;
import com.linpj.novel.create.service.CreationTaskService;
import com.linpj.novel.create.service.PlotThreadService;
import com.google.gson.reflect.TypeToken;
import com.linpj.novel.create.utils.JsonUtil;
import com.linpj.novel.create.worldstate.ContextAssemblyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 编排器状态机单元测试（设计 4.4 失败策略 + 3.3 重写触发规则）
 *
 * runAsync 为同步执行路径（丢弃式发射器），用于验证调用拓扑与降级语义；
 * run 为 SSE 异步路径，用于验证事件协议（stage/content/REVISION/回退）。
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
class CreationPipelineServiceTest {

    @Mock private WriterAgent writerAgent;
    @Mock private CriticAgent criticAgent;
    @Mock private PlannerAgent plannerAgent;
    @Mock private PolisherAgent polisherAgent;
    @Mock private CreationTaskService creationTaskService;
    @Mock private CreationStageArtifactMapper artifactMapper;
    @Mock private ContextAssemblyService contextAssemblyService;
    @Mock private PlotThreadService plotThreadService;
    @Mock private ChapterOutlineStoreService outlineStoreService;
    @Mock private CreationEventProducer creationEventProducer;
    @Mock private RuleChecker ruleChecker;

    private CreationPipelineService service;
    private final com.linpj.novel.create.config.PipelineFeatures pipelineFeatures =
            new com.linpj.novel.create.config.PipelineFeatures();

    @BeforeEach
    void setUp() {
        service = new CreationPipelineService(writerAgent, criticAgent, plannerAgent,
                polisherAgent, creationTaskService, artifactMapper, contextAssemblyService,
                plotThreadService, outlineStoreService, creationEventProducer,
                pipelineFeatures, ruleChecker);
    }

    // ------------------------------------------------------------------
    // 构造工具
    // ------------------------------------------------------------------

    private PipelineRequest request(CreationMode mode) {
        PipelineRequest r = new PipelineRequest();
        r.setBookId(1L);
        r.setChapterId(10L);
        r.setUserInput("写一段夜色");
        r.setMode(mode);
        r.setMaxRewriteRounds(2);
        return r;
    }

    private ReviewReport report(boolean approved, int score, Issue... issues) {
        ReviewReport report = new ReviewReport();
        report.setApproved(approved);
        report.setOverallScore(score);
        report.setSummary("审查");
        report.setIssues(new ArrayList<>(List.of(issues)));
        return report;
    }

    private Issue issue(String severity, String type) {
        Issue i = new Issue();
        i.setSeverity(severity);
        i.setType(type);
        i.setProblem("问题");
        i.setEvidence("原文引用");
        i.setSuggestion("建议");
        return i;
    }

    private ChapterOutline outline() {
        ChapterOutline o = new ChapterOutline();
        o.setChapterTitle("第一章");
        Scene scene = new Scene();
        scene.setLocation("城墙");
        scene.setConflict("围城");
        scene.setOutcome("突围");
        o.setScenes(List.of(scene));
        o.setCharacterIds(List.of(1L, 2L));
        return o;
    }

    private void stubWriterContext() {
        WriterContext ctx = new WriterContext();
        ctx.setBookId(1L);
        when(contextAssemblyService.assembleWriterContext(any(), any())).thenReturn(ctx);
    }

    private void stubCriticContext() {
        when(contextAssemblyService.assembleCriticContext(any(), anyString(), any()))
                .thenReturn(new CriticContext());
    }

    private void stubPlannerContext() {
        when(contextAssemblyService.assemblePlannerContext(any())).thenReturn(new PlannerContext());
    }

    /** 订阅 run() 的 SSE 流并等待管道跑完，返回事件序列 */
    private List<ServerSentEvent<String>> runAndCollect(PipelineRequest req) throws Exception {
        CopyOnWriteArrayList<ServerSentEvent<String>> sink = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        service.run(req).subscribe(sink::add, t -> latch.countDown(), latch::countDown);
        assertTrue(latch.await(60, TimeUnit.SECONDS), "SSE 事件流未在限时内结束");
        return sink;
    }

    private static final Type STRING_MAP = new TypeToken<Map<String, String>>() {
    }.getType();

    private static List<StageEvent> stageEvents(List<ServerSentEvent<String>> events) {
        return events.stream()
                .filter(e -> "stage".equals(e.event()))
                .map(e -> JsonUtil.fromJson(e.data(), StageEvent.class))
                .toList();
    }

    private static List<String> contentTexts(List<ServerSentEvent<String>> events) {
        return events.stream()
                .filter(e -> "content".equals(e.event()))
                .map(e -> JsonUtil.<Map<String, String>>fromJson(e.data(), STRING_MAP).get("text"))
                .toList();
    }

    // ------------------------------------------------------------------
    // FAST：Writer 单链路
    // ------------------------------------------------------------------

    @Test
    void fastModeIsWriterOnly() {
        stubWriterContext();
        when(writerAgent.write(any(), any()))
                .thenReturn(new WriterAgent.WriteResult("正文", 100L));

        service.runAsync(1L, request(CreationMode.FAST));

        verify(writerAgent, times(1)).write(any(), any());
        verify(criticAgent, never()).review(any());
        verify(plannerAgent, never()).plan(any());
        verify(polisherAgent, never()).polish(anyString(), any(), any());
        verify(plotThreadService).extractInlinePlotMarks("正文");
        verify(creationTaskService).completeTask(eq(1L), eq(false), anyInt(), anyLong());
    }

    // ------------------------------------------------------------------
    // STANDARD：Writer + Critic 闭环
    // ------------------------------------------------------------------

    @Test
    void standardApprovedOnFirstReviewSkipsRewrite() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L));

        service.runAsync(2L, request(CreationMode.STANDARD));

        verify(writerAgent, times(1)).write(any(), any());
        verify(writerAgent, never()).rewrite(any(), anyString(), any(), anyInt(), any());
        verify(criticAgent, times(1)).review(any());
        verify(creationTaskService).completeTask(eq(2L), eq(false), anyInt(), anyLong());
    }

    @Test
    void highIssueForcesRewriteUntilApproved() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(writerAgent.rewrite(any(), eq("d1"), any(), eq(1), any()))
                .thenReturn(new WriterAgent.WriteResult("d2", 120L));
        when(criticAgent.review(any()))
                .thenReturn(new CriticAgent.ReviewResult(report(false, 6, issue("HIGH", "PLOT_HOLE")), 40L))
                .thenReturn(new CriticAgent.ReviewResult(report(true, 8), 45L));

        service.runAsync(3L, request(CreationMode.STANDARD));

        verify(writerAgent, times(1)).write(any(), any());
        verify(writerAgent, times(1)).rewrite(any(), eq("d1"), any(), eq(1), any());
        verify(criticAgent, times(2)).review(any());
        verify(creationTaskService).completeTask(eq(3L), eq(false), anyInt(), anyLong());
    }

    // ------------------------------------------------------------------
    // N5 规则校验接入（v2.0 M1-B3，ruleRouting 总闸门控）
    // ------------------------------------------------------------------

    /** TC-32：ruleRouting 默认关闭（请求不带覆盖）= 主线行为，规则校验器零调用 */
    @Test
    void ruleRoutingOffKeepsBaselineBehavior() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L));

        service.runAsync(21L, request(CreationMode.STANDARD));

        verify(ruleChecker, never()).check(any());
        verify(writerAgent, times(1)).write(any(), any());
        verify(creationTaskService).completeTask(eq(21L), eq(false), anyInt(), anyLong());
    }

    /** 开关开启 + ACTIVE 规则 HIGH 违规：即使 Critic 通过也强制重写（P6：硬校验不让位） */
    @Test
    void ruleRoutingOnBlockingHighForcesRewrite() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(writerAgent.rewrite(any(), eq("d1"), any(), eq(1), any()))
                .thenReturn(new WriterAgent.WriteResult("d2", 120L));
        // Critic 两次都直判通过：重写完全由规则驱动
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L));
        when(ruleChecker.check(any()))
                .thenReturn(ruleResult(withBlocking("RC-01", RuleSeverity.HIGH)))
                .thenReturn(ruleResult(null));

        PipelineRequest req = request(CreationMode.STANDARD);
        req.setFeatures(Map.of("ruleRouting", true));
        service.runAsync(22L, req);

        verify(ruleChecker, times(2)).check(any());
        verify(writerAgent, times(1)).rewrite(any(), eq("d1"), any(), eq(1), any());
        // 第二轮规则通过 + Critic 通过 -> 不降级出章
        verify(creationTaskService).completeTask(eq(22L), eq(false), anyInt(), anyLong());
    }

    /** 影子态规则违规：只记录不阻塞（分级上线观测语义） */
    @Test
    void ruleRoutingOnShadowViolationDoesNotBlock() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L));
        RuleCheckResult shadowResult = ruleResult(null);
        shadowResult.addShadow(RuleViolation.of("RC-12", RuleSeverity.MEDIUM, RuleStage.DRAFT,
                "叙述层出现第一人称标记", "检查叙述视角", true));
        when(ruleChecker.check(any())).thenReturn(shadowResult);

        PipelineRequest req = request(CreationMode.STANDARD);
        req.setFeatures(Map.of("ruleRouting", true));
        service.runAsync(23L, req);

        verify(ruleChecker, times(1)).check(any());
        verify(writerAgent, never()).rewrite(any(), anyString(), any(), anyInt(), any());
        verify(creationTaskService).completeTask(eq(23L), eq(false), anyInt(), anyLong());
    }

    /** 规则通道整体故障：降级跳过（管道照常出章） */
    @Test
    void ruleCheckerFailureDoesNotBreakPipeline() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L));
        when(ruleChecker.check(any())).thenThrow(new IllegalStateException("规则引擎故障"));

        PipelineRequest req = request(CreationMode.STANDARD);
        req.setFeatures(Map.of("ruleRouting", true));
        service.runAsync(24L, req);

        verify(creationTaskService).completeTask(eq(24L), eq(false), anyInt(), anyLong());
    }

    // ------------------------------------------------------------------
    // 收敛控制器（v2.0 M3-D1/D2/D3，convergenceGuard 开关门控）
    // ------------------------------------------------------------------

    /** TC-开关回归：guard 关闭（默认）时判优通道零调用——绝对分现状路径 */
    @Test
    void convergenceGuardOffNeverCallsCompare() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(false, 5, issue("MEDIUM", "TIMELINE")), 40L));
        when(writerAgent.rewrite(any(), anyString(), any(), anyInt(), any()))
                .thenReturn(new WriterAgent.WriteResult("d2", 120L));

        service.runAsync(31L, request(CreationMode.STANDARD));

        verify(criticAgent, never()).compare(any(), anyString(), anyString());
        verify(writerAgent, times(1)).rewrite(any(), anyString(), any(), anyInt(), any());
    }

    /** M3-D2：guard 开启时 PRECISE 择优走成对比较（ADR-6：绝对分只做门槛） */
    @Test
    void precisePairwiseSelectionUnderConvergenceGuard() throws Exception {
        stubWriterContext();
        stubCriticContext();
        stubPlannerContext();
        when(plannerAgent.plan(any())).thenReturn(new PlannerAgent.PlanResult(outline(), 80L));
        when(writerAgent.writeVariant(any(), eq(1), any()))
                .thenReturn(new WriterAgent.WriteResult("标准稿", 100L));
        when(writerAgent.writeVariant(any(), eq(2), any()))
                .thenReturn(new WriterAgent.WriteResult("变体稿", 110L));
        // 两稿绝对分相同（旧绝对分路径必取 A）：成对比较判 B 胜
        when(criticAgent.review(any()))
                .thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L))
                .thenReturn(new CriticAgent.ReviewResult(report(true, 8), 42L));
        when(criticAgent.compare(any(), eq("标准稿"), eq("变体稿")))
                .thenReturn(new CriticAgent.CompareResult("B", "变体稿因果更顺", 6L));
        when(polisherAgent.polish(anyString(), any(), any()))
                .thenReturn(new PolisherAgent.PolishResult("润色终稿", 60L));
        when(criticAgent.verifyPolish(any(), anyString()))
                .thenReturn(new CriticAgent.ReviewResult(report(true, 9), 30L));

        PipelineRequest req = request(CreationMode.PRECISE);
        req.setFeatures(Map.of("convergenceGuard", true));
        List<ServerSentEvent<String>> events = runAndCollect(req);

        // 择优由成对比较驱动（同分也能分出胜者）：变体稿胜出
        verify(criticAgent).compare(any(), eq("标准稿"), eq("变体稿"));
        assertTrue(contentTexts(events).stream().anyMatch(t -> t != null && t.contains("变体稿"))
                        || contentTexts(events).contains("变体稿"),
                "变体稿胜出应下发到内容流");
        // 采纳记录落档（D1）：首轮通过 adoptedRound=1，无回滚
        verify(artifactMapper).insert(argThat((CreationStageArtifact a) ->
                a.getRound() == 96 && a.getStage().equals("CRITIC")));
    }

    /** M3-D2：新稿劣于历史最优 -> 回滚最优版本并终止循环（LLM 修订非单调改进算子） */
    @Test
    void convergenceGuardOnRollsBackToBestDraftWhenRevisionWorse() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("首稿", 1000L));
        when(criticAgent.review(any()))
                .thenReturn(new CriticAgent.ReviewResult(report(false, 6, issue("HIGH", "PLOT_HOLE")), 40L))
                .thenReturn(new CriticAgent.ReviewResult(report(false, 6, issue("HIGH", "PLOT_HOLE")), 40L));
        when(writerAgent.rewrite(any(), eq("首稿"), any(), eq(1), any()))
                .thenReturn(new WriterAgent.WriteResult("劣化稿", 200L));
        // 新稿（挑战者 A=劣化稿）未胜过擂主（B=首稿）：TIE/B 均触发回滚
        when(criticAgent.compare(any(), eq("劣化稿"), eq("首稿")))
                .thenReturn(new CriticAgent.CompareResult("B", "首稿因果更完整", 6L));

        PipelineRequest req = request(CreationMode.STANDARD);
        req.setFeatures(Map.of("convergenceGuard", true));
        service.runAsync(32L, req);

        // 仅一轮重写即回滚出循环
        verify(writerAgent, times(1)).rewrite(any(), anyString(), any(), anyInt(), any());
        verify(artifactMapper).insert(argThat((CreationStageArtifact a) -> a.getRound() == 96
                && a.getStage().equals("CRITIC")
                && a.getArtifact() != null
                && a.getArtifact().toString().contains("rolledBack")));
        // 回滚后仍未达标：degraded 出章
        verify(creationTaskService).completeTask(eq(32L), eq(true), anyInt(), anyLong());
    }

    /** M3-D3：修订累计 token 超首写 40% -> 强制出循环（止损） */
    @Test
    void convergenceGuardOnBudgetExhaustionStopsLoop() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(criticAgent.review(any()))
                .thenReturn(new CriticAgent.ReviewResult(report(false, 6, issue("HIGH", "PLOT_HOLE")), 20L))
                .thenReturn(new CriticAgent.ReviewResult(report(false, 6, issue("HIGH", "PLOT_HOLE")), 20L));
        when(writerAgent.rewrite(any(), anyString(), any(), anyInt(), any()))
                .thenReturn(new WriterAgent.WriteResult("d2", 90L)); // 90+20 > 40%×100
        // 每轮新稿都胜过最优（走完整轮次路径，不触发回滚）
        when(criticAgent.compare(any(), anyString(), anyString()))
                .thenReturn(new CriticAgent.CompareResult("A", "新稿更优", 5L));

        PipelineRequest req = request(CreationMode.STANDARD);
        req.setFeatures(Map.of("convergenceGuard", true));
        req.setMaxRewriteRounds(3); // 轮次上限本可 3 轮
        service.runAsync(33L, req);

        // 预算在第 2 轮开始前耗尽：只有 1 次重写
        verify(writerAgent, times(1)).rewrite(any(), anyString(), any(), anyInt(), any());
        verify(artifactMapper).insert(argThat((CreationStageArtifact a) -> a.getRound() == 96
                && a.getArtifact() != null
                && a.getArtifact().toString().contains("budgetExhausted")));
        verify(creationTaskService).completeTask(eq(33L), eq(true), anyInt(), anyLong());
    }

    /** 构造规则校验结果（executedRules 非空代表规则确有执行） */
    private RuleCheckResult ruleResult(RuleViolation blocking) {
        RuleCheckResult result = new RuleCheckResult();
        result.markExecuted("RC-FAKE");
        if (blocking != null) {
            result.addBlocking(blocking);
        }
        return result;
    }

    private RuleViolation withBlocking(String ruleId, RuleSeverity severity) {
        return RuleViolation.of(ruleId, severity, RuleStage.DRAFT, "死人出场且无复活设定", "局部重写该场景", false);
    }

    @Test
    void rewriteStopsAtMaxRoundsAndMarksDegraded() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(writerAgent.rewrite(any(), anyString(), any(), anyInt(), any()))
                .thenReturn(new WriterAgent.WriteResult("d2", 120L))
                .thenReturn(new WriterAgent.WriteResult("d3", 130L));
        ReviewReport high = report(false, 5, issue("HIGH", "PLOT_HOLE"));
        when(criticAgent.review(any())).thenReturn(
                new CriticAgent.ReviewResult(high, 40L),
                new CriticAgent.ReviewResult(high, 41L),
                new CriticAgent.ReviewResult(high, 42L));

        service.runAsync(4L, request(CreationMode.STANDARD));

        // 1 首写 + 2 重写（maxRewriteRounds=2 止损），终稿取最后一稿
        verify(writerAgent, times(1)).write(any(), any());
        verify(writerAgent, times(2)).rewrite(any(), anyString(), any(), anyInt(), any());
        verify(criticAgent, times(3)).review(any());
        verify(creationTaskService).completeTask(eq(4L), eq(true), anyInt(), anyLong());
    }

    @Test
    void mediumOnlyRewritesExactlyOneRound() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(writerAgent.rewrite(any(), eq("d1"), any(), eq(1), any()))
                .thenReturn(new WriterAgent.WriteResult("d2", 120L));
        ReviewReport medium = report(false, 6, issue("MEDIUM", "TIMELINE"));
        when(criticAgent.review(any()))
                .thenReturn(new CriticAgent.ReviewResult(medium, 40L))
                .thenReturn(new CriticAgent.ReviewResult(medium, 41L));

        service.runAsync(5L, request(CreationMode.STANDARD));

        // 仅 MEDIUM 只重写 1 轮（第二轮 shouldRewrite 为 false），仍未通过则降级
        verify(writerAgent, times(1)).rewrite(any(), eq("d1"), any(), eq(1), any());
        verify(writerAgent, never()).rewrite(any(), eq("d2"), any(), eq(2), any());
        verify(creationTaskService).completeTask(eq(5L), eq(true), anyInt(), anyLong());
    }

    @Test
    void lowOnlyApprovedPassesDirectly() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(criticAgent.review(any()))
                .thenReturn(new CriticAgent.ReviewResult(report(true, 9, issue("LOW", "STYLE_DRIFT")), 40L));

        service.runAsync(6L, request(CreationMode.STANDARD));

        verify(writerAgent, never()).rewrite(any(), anyString(), any(), anyInt(), any());
        verify(creationTaskService).completeTask(eq(6L), eq(false), anyInt(), anyLong());
    }

    @Test
    void criticFailureDegradesToApproved() {
        stubWriterContext();
        stubCriticContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(criticAgent.review(any())).thenThrow(new RuntimeException("网关超时"));

        service.runAsync(7L, request(CreationMode.STANDARD));

        // 宁可放过不可改错：Critic 失败视为通过，不触发重写
        verify(writerAgent, never()).rewrite(any(), anyString(), any(), anyInt(), any());
        verify(creationTaskService).completeTask(eq(7L), eq(false), anyInt(), anyLong());
    }

    @Test
    void writerHardFailureFailsPipeline() {
        stubWriterContext();
        when(writerAgent.write(any(), any())).thenThrow(new RuntimeException("模型无响应"));

        service.runAsync(8L, request(CreationMode.FAST));

        // 重试 1 次仍失败 -> 管道失败
        verify(writerAgent, times(2)).write(any(), any());
        verify(creationTaskService).failTask(eq(8L), contains("模型无响应"));
        verify(creationTaskService, never()).completeTask(any(), anyBoolean(), any(), any());
    }

    // ------------------------------------------------------------------
    // REFINED：Planner + Writer + Critic + Polisher
    // ------------------------------------------------------------------

    @Test
    void plannerExceptionDegradesToDirectWrite() {
        stubWriterContext();
        stubCriticContext();
        stubPlannerContext();
        when(plannerAgent.plan(any())).thenThrow(new RuntimeException("解析失败"));
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L));
        when(polisherAgent.polish(anyString(), any(), any()))
                .thenReturn(new PolisherAgent.PolishResult("润色稿", 60L));
        when(criticAgent.verifyPolish(any(), anyString()))
                .thenReturn(new CriticAgent.ReviewResult(report(true, 9), 30L));

        service.runAsync(9L, request(CreationMode.REFINED));

        verify(contextAssemblyService).assembleWriterContext(any(), isNull());
        verify(outlineStoreService, never()).saveOutline(any(), any(), any());
        verify(creationTaskService).completeTask(eq(9L), eq(true), anyInt(), anyLong());
    }

    @Test
    void plannerNullOutlineMarksDegradedAndSkipsArtifact() {
        stubWriterContext();
        stubCriticContext();
        stubPlannerContext();
        when(plannerAgent.plan(any())).thenReturn(new PlannerAgent.PlanResult(null, 80L));
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L));
        when(polisherAgent.polish(anyString(), any(), any()))
                .thenReturn(new PolisherAgent.PolishResult("润色稿", 60L));
        when(criticAgent.verifyPolish(any(), anyString()))
                .thenReturn(new CriticAgent.ReviewResult(report(true, 9), 30L));

        service.runAsync(10L, request(CreationMode.REFINED));

        // 空大纲：不落 outline、不入库 PLANNING 产物（JSONB NOT NULL 保护）、标记降级
        verify(outlineStoreService, never()).saveOutline(any(), any(), any());
        verify(artifactMapper, never()).insert(argThat(
                (CreationStageArtifact a) -> "PLANNING".equals(a.getStage())));
        verify(creationTaskService).completeTask(eq(10L), eq(true), anyInt(), anyLong());
    }

    @Test
    void refinedHappyPathEmitsPolishedText() throws Exception {
        ChapterOutline outline = outline();
        stubWriterContext();
        stubCriticContext();
        stubPlannerContext();
        when(plannerAgent.plan(any())).thenReturn(new PlannerAgent.PlanResult(outline, 80L));
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("原稿", 100L));
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L));
        when(polisherAgent.polish(anyString(), any(), any()))
                .thenReturn(new PolisherAgent.PolishResult("润色终稿", 60L));
        when(criticAgent.verifyPolish(any(), anyString()))
                .thenReturn(new CriticAgent.ReviewResult(report(true, 9), 30L));

        List<ServerSentEvent<String>> events = runAndCollect(request(CreationMode.REFINED));

        verify(outlineStoreService).saveOutline(eq(1L), eq(10L), eq(outline));
        List<String> contents = contentTexts(events);
        assertTrue(contents.contains("润色终稿"), "复核通过后应下发润色终稿");
        List<StageEvent> stages = stageEvents(events);
        assertTrue(stages.stream().anyMatch(s -> "PLANNING".equals(s.getCode()) && "STARTED".equals(s.getStatus())));
        assertTrue(stages.stream().anyMatch(s -> "POLISHING".equals(s.getCode()) && "STARTED".equals(s.getStatus())));
        // 复核通过：下发终稿前也必须 REVISION(POLISHING) 重置缓冲，避免原稿与终稿在 content 流拼接重复
        assertTrue(stages.stream().anyMatch(s ->
                        "POLISHING".equals(s.getCode()) && "REVISION".equals(s.getStatus())),
                "润色通过下发终稿前应发送 POLISHING REVISION 重置信号");
        StageEvent completed = stages.stream()
                .filter(s -> "COMPLETED".equals(s.getCode())).findFirst().orElseThrow();
        assertFalse(Boolean.TRUE.equals(completed.getDegraded()), "全链路通过不应降级");
        assertNotNull(completed.getUsage());
        assertTrue(completed.getUsage().getTotalTokens() > 0);
    }

    @Test
    void polishVerifyFailedRollsBackToOriginalDraft() throws Exception {
        stubWriterContext();
        stubCriticContext();
        stubPlannerContext();
        when(plannerAgent.plan(any())).thenReturn(new PlannerAgent.PlanResult(outline(), 80L));
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("原稿", 100L));
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L));
        when(polisherAgent.polish(anyString(), any(), any()))
                .thenReturn(new PolisherAgent.PolishResult("被污染的润色稿", 60L));
        when(criticAgent.verifyPolish(any(), anyString()))
                .thenReturn(new CriticAgent.ReviewResult(report(false, 5, issue("HIGH", "PLOT_HOLE")), 30L));

        List<ServerSentEvent<String>> events = runAndCollect(request(CreationMode.REFINED));

        // 复核不过：REVISION(POLISHING) 重置缓冲 + 补发原稿；润色稿不得出现在内容流
        List<StageEvent> stages = stageEvents(events);
        assertTrue(stages.stream().anyMatch(s ->
                        "POLISHING".equals(s.getCode()) && "REVISION".equals(s.getStatus())),
                "复核失败应发送 POLISHING REVISION 回退信号");
        List<String> contents = contentTexts(events);
        assertTrue(contents.contains("原稿"), "回退后应补发原稿");
        assertFalse(contents.contains("被污染的润色稿"), "未通过复核的润色稿不得外发");
        StageEvent completed = stages.stream()
                .filter(s -> "COMPLETED".equals(s.getCode())).findFirst().orElseThrow();
        assertTrue(Boolean.TRUE.equals(completed.getDegraded()));
    }

    @Test
    void polisherExceptionKeepsOriginalAndMarksDegraded() {
        stubWriterContext();
        stubCriticContext();
        stubPlannerContext();
        when(plannerAgent.plan(any())).thenReturn(new PlannerAgent.PlanResult(outline(), 80L));
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L));
        when(polisherAgent.polish(anyString(), any(), any())).thenThrow(new RuntimeException("润色超时"));

        service.runAsync(11L, request(CreationMode.REFINED));

        verify(creationTaskService).completeTask(eq(11L), eq(true), anyInt(), anyLong());
    }

    // ------------------------------------------------------------------
    // PRECISE：并行双稿择优
    // ------------------------------------------------------------------

    @Test
    void preciseSelectsHigherScoredVariant() throws Exception {
        stubWriterContext();
        stubCriticContext();
        stubPlannerContext();
        when(plannerAgent.plan(any())).thenReturn(new PlannerAgent.PlanResult(outline(), 80L));
        when(writerAgent.writeVariant(any(), eq(1), any()))
                .thenReturn(new WriterAgent.WriteResult("标准稿", 100L));
        when(writerAgent.writeVariant(any(), eq(2), any()))
                .thenReturn(new WriterAgent.WriteResult("变体稿", 110L));
        when(criticAgent.review(any()))
                .thenReturn(new CriticAgent.ReviewResult(report(true, 6), 40L))   // 标准稿
                .thenReturn(new CriticAgent.ReviewResult(report(true, 9), 42L));  // 变体稿
        when(polisherAgent.polish(anyString(), any(), any()))
                .thenReturn(new PolisherAgent.PolishResult("润色终稿", 60L));
        when(criticAgent.verifyPolish(any(), anyString()))
                .thenReturn(new CriticAgent.ReviewResult(report(true, 9), 30L));

        List<ServerSentEvent<String>> events = runAndCollect(request(CreationMode.PRECISE));

        // 变体稿胜出：REVISION(WRITING) 重置缓冲后下发变体稿
        List<StageEvent> stages = stageEvents(events);
        assertTrue(stages.stream().anyMatch(s ->
                "WRITING".equals(s.getCode()) && "REVISION".equals(s.getStatus())));
        assertTrue(contentTexts(events).contains("变体稿"));
        // 落选稿以特殊轮次保留产物（供用户对比）
        verify(artifactMapper).insert(argThat((CreationStageArtifact a) ->
                a.getRound() == 98 && a.getStage().equals("WRITING")));
        verify(creationTaskService).completeTask(any(), eq(false), anyInt(), anyLong());
    }

    @Test
    void preciseDualDraftFailureFallsBackToSingleDraft() throws Exception {
        stubWriterContext();
        stubCriticContext();
        stubPlannerContext();
        when(plannerAgent.plan(any())).thenReturn(new PlannerAgent.PlanResult(outline(), 80L));
        when(writerAgent.writeVariant(any(), eq(1), any())).thenThrow(new RuntimeException("变体A崩溃"));
        when(writerAgent.writeVariant(any(), eq(2), any()))
                .thenReturn(new WriterAgent.WriteResult("变体稿", 110L));
        // 模拟真实 Agent 的流式回调：onChunk 消费端就是 SSE content 通道
        when(writerAgent.write(any(), any())).thenAnswer(invocation -> {
            java.util.function.Consumer<String> onChunk = invocation.getArgument(1);
            if (onChunk != null) {
                onChunk.accept("单稿降级");
            }
            return new WriterAgent.WriteResult("单稿降级", 100L);
        });
        when(criticAgent.review(any())).thenReturn(new CriticAgent.ReviewResult(report(true, 8), 40L));
        when(polisherAgent.polish(any(), any(), any()))
                .thenReturn(new PolisherAgent.PolishResult("润色终稿", 60L));
        when(criticAgent.verifyPolish(any(), anyString()))
                .thenReturn(new CriticAgent.ReviewResult(report(true, 9), 30L));

        List<ServerSentEvent<String>> events = runAndCollect(request(CreationMode.PRECISE));

        verify(writerAgent, times(1)).write(any(), any());
        List<String> contents = contentTexts(events);
        assertTrue(contents.contains("单稿降级"), "双稿失败后应下发单稿降级产出");
        verify(creationTaskService).completeTask(any(), eq(true), anyInt(), anyLong());
    }

    // ------------------------------------------------------------------
    // SSE 事件协议与 Writer 传输重试
    // ------------------------------------------------------------------

    @Test
    void writerTransientFailureRetriedWithBufferReset() throws Exception {
        stubWriterContext();
        // 首次失败（可能已外发部分内容），重试成功
        when(writerAgent.write(any(), any()))
                .thenThrow(new RuntimeException("连接中断"))
                .thenReturn(new WriterAgent.WriteResult("完整重试稿", 100L));

        List<ServerSentEvent<String>> events = runAndCollect(request(CreationMode.FAST));

        verify(writerAgent, times(2)).write(any(), any());
        // 重试成功后必须有 REVISION 重置信号，避免失败残片拼接污染前端缓冲
        assertTrue(stageEvents(events).stream().anyMatch(s ->
                        "WRITING".equals(s.getCode()) && "REVISION".equals(s.getStatus())),
                "传输重试成功后应发送 REVISION 重置信号");
        assertTrue(contentTexts(events).contains("完整重试稿"));
        verify(creationTaskService).completeTask(any(), eq(false), anyInt(), anyLong());
    }

    @Test
    void sseProtocolEmitsStartedAndCompletedStageEvents() throws Exception {
        stubWriterContext();
        when(writerAgent.write(any(), any())).thenReturn(new WriterAgent.WriteResult("d1", 100L));

        List<ServerSentEvent<String>> events = runAndCollect(request(CreationMode.FAST));

        List<StageEvent> stages = stageEvents(events);
        assertTrue(stages.stream().anyMatch(s ->
                "WRITING".equals(s.getCode()) && "STARTED".equals(s.getStatus())
                        && s.getIndex() == 1 && s.getTotal() == 1));
        assertTrue(stages.stream().anyMatch(s -> "COMPLETED".equals(s.getCode())));
        // COMPLETED 事件携带用量统计（成本面板埋点）
        StageEvent completed = stages.stream()
                .filter(s -> "COMPLETED".equals(s.getCode())).findFirst().orElseThrow();
        assertNotNull(completed.getUsage());
        assertEquals(1, completed.getUsage().getStages());
    }
}
