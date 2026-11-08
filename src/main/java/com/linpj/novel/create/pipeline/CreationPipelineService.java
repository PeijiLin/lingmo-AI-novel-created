package com.linpj.novel.create.pipeline;

import com.linpj.novel.create.agent.CriticAgent;
import com.linpj.novel.create.agent.PlannerAgent;
import com.linpj.novel.create.agent.PolisherAgent;
import com.linpj.novel.create.agent.WriterAgent;
import com.linpj.novel.create.mapper.novel.CreationStageArtifactMapper;
import com.linpj.novel.create.pojo.entity.CreationStageArtifact;
import com.linpj.novel.create.pojo.pipeline.AsyncCreationTaskMessage;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.CreationMode;
import com.linpj.novel.create.pojo.pipeline.CriticContext;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.PlannerContext;
import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import com.linpj.novel.create.pojo.pipeline.StageCode;
import com.linpj.novel.create.pojo.pipeline.StageEvent;
import com.linpj.novel.create.pojo.pipeline.StageUsage;
import com.linpj.novel.create.pojo.pipeline.WriterContext;
import com.linpj.novel.create.producer.CreationEventProducer;
import com.linpj.novel.create.service.ChapterOutlineStoreService;
import com.linpj.novel.create.service.CreationTaskService;
import com.linpj.novel.create.service.PlotThreadService;
import com.linpj.novel.create.worldstate.ContextAssemblyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * 创作管道编排器（设计第四节）
 *
 * 核心原则：
 * - 控制权在业务侧：拓扑、阶段顺序、重试轮数由代码决定，LLM 只负责生成
 * - 模式 → 管道定义（代码即拓扑，不搞 YAML/DB 配置化）：
 *     FAST      [Writer]
 *     STANDARD  [Writer -> Critic(+重写回路, max N 轮)]
 *     REFINED   [Planner -> Writer -> Critic(回路) -> Polisher(+复核)]
 *     PRECISE   REFINED + Writer 并行双稿择优（设计 11.2：Spring AI 原生并发，不引 graph-core）
 * - 同步模式：状态机在内存中跑完一次请求；阶段产物实时落库，异常中断后产物仍在
 * - 失败策略（设计 4.4）：
 *     Planner 失败  降级：跳过大纲，Writer 直接消费用户输入，事件标记 degraded
 *     Writer 失败   重试 1 次；再失败则管道失败（FAILED 事件，已产生产物保留）
 *     Critic 失败   降级：视为通过（宁可放过不可改错）并标记 degraded
 *     Polisher 失败 降级：采用润色前原稿
 * - 伏笔记账（设计 5.3）：Planner 声明的 PLANT/RESOLVE 在终稿采纳后入账；
 *   正文内联 <!--plot:--> 标记（大纲外新伏笔）一并入账——意图内记账，防"写了没人记"。
 *
 * @author HL
 */
@Service
public class CreationPipelineService {

    private static final Logger log = LoggerFactory.getLogger(CreationPipelineService.class);

    /** 管道执行调度器：命令式状态机跑在弹性线程池，不占用请求线程 */
    private static final Scheduler PIPELINE_SCHEDULER = Schedulers.boundedElastic();

    /** 润色复核产物的 artifact 轮次标记（区别于重写回路的 1..N） */
    private static final int POLISH_VERIFY_ROUND = 99;

    /** PRECISE 落选稿的 artifact 轮次标记 */
    private static final int PRECISE_REJECTED_ROUND = 98;

    /** PRECISE 双稿总超时（两稿并行，对齐单稿 Writer 180s） */
    private static final Duration DUAL_DRAFT_TIMEOUT = Duration.ofSeconds(180);

    private final WriterAgent writerAgent;
    private final CriticAgent criticAgent;
    private final PlannerAgent plannerAgent;
    private final PolisherAgent polisherAgent;
    private final CreationTaskService creationTaskService;
    private final CreationStageArtifactMapper artifactMapper;
    private final ContextAssemblyService contextAssemblyService;
    private final PlotThreadService plotThreadService;
    private final ChapterOutlineStoreService outlineStoreService;
    private final CreationEventProducer creationEventProducer;

    public CreationPipelineService(WriterAgent writerAgent,
                                    CriticAgent criticAgent,
                                    PlannerAgent plannerAgent,
                                    PolisherAgent polisherAgent,
                                    CreationTaskService creationTaskService,
                                    CreationStageArtifactMapper artifactMapper,
                                    ContextAssemblyService contextAssemblyService,
                                    PlotThreadService plotThreadService,
                                    ChapterOutlineStoreService outlineStoreService,
                                    CreationEventProducer creationEventProducer) {
        this.writerAgent = writerAgent;
        this.criticAgent = criticAgent;
        this.plannerAgent = plannerAgent;
        this.polisherAgent = polisherAgent;
        this.creationTaskService = creationTaskService;
        this.artifactMapper = artifactMapper;
        this.contextAssemblyService = contextAssemblyService;
        this.plotThreadService = plotThreadService;
        this.outlineStoreService = outlineStoreService;
        this.creationEventProducer = creationEventProducer;
    }

    // ------------------------------------------------------------------
    // 入口：同步 SSE 模式（阶段1-3 主路径）
    // ------------------------------------------------------------------

    /**
     * 同步模式入口：订阅返回 SSE 混合事件流（stage + content），管道在弹性线程上执行
     */
    public Flux<ServerSentEvent<String>> run(PipelineRequest request) {
        validate(request);
        Long taskId = creationTaskService.createRunningTask(
                request.getBookId(), request.getChapterId(), request.getMode().name());
        ProgressEmitter emitter = ProgressEmitter.create();
        return emitter.asFlux()
                .doOnSubscribe(subscription -> PIPELINE_SCHEDULER.schedule(() -> {
                    try {
                        executePipeline(request, taskId, emitter);
                    } catch (Exception e) {
                        // executePipeline 内部已兜底，这里防御状态机外的意外异常
                        log.error("管道执行意外异常: mode={}, bookId={}", request.getMode(), request.getBookId(), e);
                        emitter.error(e);
                    }
                }));
    }

    // ------------------------------------------------------------------
    // 入口：异步 Kafka 模式（阶段4，整章/批量生成）
    // ------------------------------------------------------------------

    /**
     * 提交异步创作任务：创建 PENDING 任务 + 投递 Kafka，立即返回任务 ID。
     * 消费者拿到锁 + 幂等栅栏（PENDING -> RUNNING CAS）后才跑管道；
     * 进度不走 SSE，前端轮询 GET /pipeline/task/{id}。
     */
    public Long submitAsyncTask(PipelineRequest request) {
        validate(request);
        Long taskId = creationTaskService.createPendingTask(
                request.getBookId(), request.getChapterId(), request.getMode().name(),
                AsyncCreationTaskMessage.of(null, request));
        creationEventProducer.sendCreationTask(AsyncCreationTaskMessage.of(taskId, request));
        return taskId;
    }

    /**
     * 异步执行管道（消费者专用）：丢弃事件流，进度靠 creation_task 状态机。
     * 幂等栅栏（PENDING -> RUNNING CAS）由消费者先行推进，此处直接执行状态机。
     */
    public void runAsync(Long taskId, PipelineRequest request) {
        validate(request);
        executePipeline(request, taskId, ProgressEmitter.discarding());
    }

    // ------------------------------------------------------------------
    // 状态机
    // ------------------------------------------------------------------

    private void executePipeline(PipelineRequest request, Long taskId, ProgressEmitter emitter) {
        long startAt = System.currentTimeMillis();
        StageUsage usage = new StageUsage();
        boolean degraded;
        try {
            switch (request.getMode()) {
                case FAST -> degraded = runFast(request, taskId, emitter, usage);
                case STANDARD -> degraded = runStandard(request, taskId, emitter, usage);
                case REFINED -> degraded = runRefined(request, taskId, emitter, usage);
                case PRECISE -> degraded = runPrecise(request, taskId, emitter, usage);
                default -> throw new IllegalArgumentException("不支持的模式: " + request.getMode());
            }
            usage.setElapsedMs(System.currentTimeMillis() - startAt);
            emitter.emitStage(StageEvent.completed(degraded, usage));
            creationTaskService.completeTask(taskId, degraded,
                    Math.toIntExact(Math.min(Integer.MAX_VALUE, usage.getTotalTokens())), usage.getElapsedMs());
        } catch (Exception e) {
            log.error("管道执行失败: taskId={}, mode={}", taskId, request.getMode(), e);
            emitter.emitStage(StageEvent.failed(e.getMessage()));
            creationTaskService.failTask(taskId, null, e.getMessage());
        } finally {
            emitter.complete();
        }
    }

    // ------------------------------------------------------------------
    // FAST：Writer 单链路
    // ------------------------------------------------------------------

    private boolean runFast(PipelineRequest request, Long taskId, ProgressEmitter emitter, StageUsage usage) {
        final int total = 1;
        emitter.emitStage(StageEvent.started(StageCode.WRITING, 1, total));
        creationTaskService.advanceStage(taskId, StageCode.WRITING.name());
        WriterContext writerContext = contextAssemblyService.assembleWriterContext(request, null);
        WriterAgent.WriteResult result = writeWithRetry(writerContext, emitter, null, null);
        saveArtifact(taskId, StageCode.WRITING, 1, Map.of("text", result.text()), result.tokens());
        usage.addTokens(result.tokens());
        usage.setStages(usage.getStages() + 1);
        settlePlotLedger(request, result.text());
        return false;
    }

    // ------------------------------------------------------------------
    // STANDARD：Writer -> Critic 闭环（含重写回路）
    // ------------------------------------------------------------------

    private boolean runStandard(PipelineRequest request, Long taskId, ProgressEmitter emitter, StageUsage usage) {
        DraftOutcome outcome = runWriterCriticLoop(request, taskId, emitter, usage, null, 1, 2);
        settlePlotLedger(request, outcome.draft());
        return outcome.degraded();
    }

    // ------------------------------------------------------------------
    // REFINED：Planner -> Writer -> Critic(回路) -> Polisher（含复核）
    // ------------------------------------------------------------------

    private boolean runRefined(PipelineRequest request, Long taskId, ProgressEmitter emitter, StageUsage usage) {
        final int total = 4; // PLANNING + WRITING + CRITIC + POLISHING
        boolean degraded = false;

        // —— Planner（序号 1）——
        PlanOutcome plan = runPlanning(request, taskId, emitter, usage, total);
        degraded = degraded || plan.degraded();

        // —— Writer -> Critic 闭环（Writer 序号 2，Critic 序号 3）——
        DraftOutcome outcome = runWriterCriticLoop(request, taskId, emitter, usage, plan.outline(), 2, total);
        degraded = degraded || outcome.degraded();

        // —— 伏笔记账 + Polisher（序号 4）——
        settleLedger(request, plan.outline(), outcome.draft());
        degraded = degraded || runPolishing(request, taskId, emitter, usage, plan.outline(), outcome.draft());
        return degraded;
    }

    // ------------------------------------------------------------------
    // PRECISE：REFINED + Writer 并行双稿择优（设计 11.2）
    // ------------------------------------------------------------------

    private boolean runPrecise(PipelineRequest request, Long taskId, ProgressEmitter emitter, StageUsage usage) {
        final int total = 4;
        boolean degraded = false;

        // —— Planner（序号 1）——
        PlanOutcome plan = runPlanning(request, taskId, emitter, usage, total);
        degraded = degraded || plan.degraded();

        // —— 并行双稿（N=2：标准稿流式转发，变体稿静默聚合）——
        emitter.emitStage(StageEvent.started(StageCode.WRITING, 2, total));
        creationTaskService.advanceStage(taskId, StageCode.WRITING.name());
        WriterContext writerContext = contextAssemblyService.assembleWriterContext(request, plan.outline());

        Mono<WriterAgent.WriteResult> draftA = Mono.fromCallable(
                        () -> writerAgent.writeVariant(writerContext, 1, emitter::emitContent))
                .subscribeOn(PIPELINE_SCHEDULER);
        Mono<WriterAgent.WriteResult> draftB = Mono.fromCallable(
                        () -> writerAgent.writeVariant(writerContext, 2, chunk -> { }))
                .subscribeOn(PIPELINE_SCHEDULER);
        WriterAgent.WriteResult resultA;
        WriterAgent.WriteResult resultB;
        try {
            var drafts = Objects.requireNonNull(
                    Mono.zip(draftA, draftB).block(DUAL_DRAFT_TIMEOUT), "并行双稿生成失败");
            resultA = drafts.getT1();
            resultB = drafts.getT2();
        } catch (Exception e) {
            // 双稿并行失败：降级为单稿（标准稿重试机制走 writeWithRetry）
            log.warn("并行双稿失败，降级为单稿: {}", e.getMessage());
            degraded = true;
            WriterAgent.WriteResult single = writeWithRetry(writerContext, emitter, null, null);
            resultA = single;
            resultB = null;
        }

        // —— 双 Critic 择优（overallScore 高者胜，平分取 A 避免抖动）——
        emitter.emitStage(StageEvent.started(StageCode.CRITIC, 3, total));
        creationTaskService.advanceStage(taskId, StageCode.CRITIC.name());
        CriticAgent.ReviewResult reviewA = reviewDraft(request, resultA.text(), plan.outline());
        saveArtifact(taskId, StageCode.WRITING, 1, Map.of("text", resultA.text(), "variant", 1), resultA.tokens());
        usage.addTokens(resultA.tokens());
        CriticAgent.ReviewResult reviewB;
        if (resultB != null) {
            reviewB = reviewDraft(request, resultB.text(), plan.outline());
            saveArtifact(taskId, StageCode.WRITING, PRECISE_REJECTED_ROUND,
                    Map.of("text", resultB.text(), "variant", 2, "note", "precise-rejected"), resultB.tokens());
            usage.addTokens(resultB.tokens());
        } else {
            reviewB = null; // 单稿降级
        }
        usage.setStages(usage.getStages() + 1);

        boolean bWins = reviewB != null && scoreOf(reviewB.report()) > scoreOf(reviewA.report());
        String selectedDraft = (bWins ? resultB : resultA).text();
        CriticAgent.ReviewResult selectedReview = bWins ? reviewB : reviewA;
        saveArtifact(taskId, StageCode.CRITIC, 1, selectedReview.report(), selectedReview.tokens());
        usage.addTokens(selectedReview.tokens());
        usage.setStages(usage.getStages() + 1);
        if (bWins) {
            // 变体稿胜出：前端按 REVISION 语义重置内容缓冲
            emitter.emitStage(StageEvent.revision(StageCode.WRITING, 1));
            emitter.emitContent(selectedDraft);
        }

        // —— 择优后照常进入重写回路（最优稿仍有 HIGH 问题时）——
        DraftOutcome outcome = rewriteLoop(request, taskId, emitter, usage, plan.outline(),
                writerContext, selectedDraft, selectedReview.report(), 2, total);
        degraded = degraded || outcome.degraded();

        // —— 伏笔记账 + Polisher（序号 4）——
        settleLedger(request, plan.outline(), outcome.draft());
        degraded = degraded || runPolishing(request, taskId, emitter, usage, plan.outline(), outcome.draft());
        return degraded;
    }

    private static int scoreOf(ReviewReport report) {
        return report.getOverallScore() == null ? 0 : report.getOverallScore();
    }

    // ------------------------------------------------------------------
    // 可复用阶段
    // ------------------------------------------------------------------

    /** Planner 阶段（失败降级：跳过大纲，Writer 直接吃用户输入，管道不中断） */
    private PlanOutcome runPlanning(PipelineRequest request, Long taskId, ProgressEmitter emitter,
                                    StageUsage usage, int total) {
        emitter.emitStage(StageEvent.started(StageCode.PLANNING, 1, total));
        creationTaskService.advanceStage(taskId, StageCode.PLANNING.name());
        try {
            PlannerContext plannerContext = contextAssemblyService.assemblePlannerContext(request);
            PlannerAgent.PlanResult planResult = plannerAgent.plan(plannerContext);
            ChapterOutline outline = planResult.outline();
            if (outline != null) {
                outlineStoreService.saveOutline(request.getBookId(), request.getChapterId(), outline);
            }
            saveArtifact(taskId, StageCode.PLANNING, 1, outline, planResult.tokens());
            usage.addTokens(planResult.tokens());
            usage.setStages(usage.getStages() + 1);
            return new PlanOutcome(outline, false);
        } catch (Exception e) {
            log.warn("Planner 失败，降级为直接续写: {}", e.getMessage());
            return new PlanOutcome(null, true);
        }
    }

    /** Polisher 阶段（情节不动硬约束 + Critic 复核；复核不过则回退原稿） */
    private boolean runPolishing(PipelineRequest request, Long taskId, ProgressEmitter emitter,
                                 StageUsage usage, ChapterOutline outline, String draft) {
        emitter.emitStage(StageEvent.started(StageCode.POLISHING, 4, 4));
        creationTaskService.advanceStage(taskId, StageCode.POLISHING.name());
        try {
            PolisherAgent.PolishResult polishResult =
                    polisherAgent.polish(draft, request.getStyle(), emitter::emitContent);
            saveArtifact(taskId, StageCode.POLISHING, 1, Map.of("text", polishResult.text()), polishResult.tokens());
            usage.addTokens(polishResult.tokens());
            usage.setStages(usage.getStages() + 1);

            CriticContext verifyContext =
                    contextAssemblyService.assembleCriticContext(request, polishResult.text(), outline);
            CriticAgent.ReviewResult verify = criticAgent.verifyPolish(verifyContext, draft);
            saveArtifact(taskId, StageCode.CRITIC, POLISH_VERIFY_ROUND, verify.report(), verify.tokens());
            usage.addTokens(verify.tokens());
            usage.setStages(usage.getStages() + 1);
            if (!verify.report().isApproved()) {
                log.warn("Polisher 复核未通过，回退润色前原稿: {}", verify.report().getSummary());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Polisher 失败，降级采用原稿: {}", e.getMessage());
            return true;
        }
    }

    /** 伏笔记账：Planner 声明的 PLANT/RESOLVE + 终稿内联标记（均在终稿采纳后入账） */
    private void settleLedger(PipelineRequest request, ChapterOutline outline, String draft) {
        if (outline != null && outline.getPlotOps() != null && !outline.getPlotOps().isEmpty()) {
            try {
                plotThreadService.applyPlotOperations(request.getBookId(), request.getChapterId(), outline.getPlotOps());
            } catch (Exception e) {
                log.error("伏笔意图入账失败: bookId={}", request.getBookId(), e);
            }
        }
        settlePlotLedger(request, draft);
    }

    /**
     * Writer -> Critic 闭环：单稿首写 + 首审 + 重写回路
     *
     * @param outline      章节大纲（REFINED/PRECISE 由 Planner 产出；STANDARD 为 null）
     * @param writerIndex  Writer 阶段序号（SSE 事件用）
     * @param total        管道阶段总数
     */
    private DraftOutcome runWriterCriticLoop(PipelineRequest request, Long taskId, ProgressEmitter emitter,
                                             StageUsage usage, ChapterOutline outline, int writerIndex, int total) {
        // —— Writer 首轮 ——
        emitter.emitStage(StageEvent.started(StageCode.WRITING, writerIndex, total));
        creationTaskService.advanceStage(taskId, StageCode.WRITING.name());
        WriterContext writerContext = contextAssemblyService.assembleWriterContext(request, outline);
        WriterAgent.WriteResult writeResult = writeWithRetry(writerContext, emitter, null, null);
        saveArtifact(taskId, StageCode.WRITING, 1, Map.of("text", writeResult.text()), writeResult.tokens());
        usage.addTokens(writeResult.tokens());
        usage.setStages(usage.getStages() + 1);

        // —— Critic 首审 ——
        emitter.emitStage(StageEvent.started(StageCode.CRITIC, writerIndex + 1, total));
        creationTaskService.advanceStage(taskId, StageCode.CRITIC.name());
        CriticAgent.ReviewResult firstReview = reviewDraft(request, writeResult.text(), outline);
        saveArtifact(taskId, StageCode.CRITIC, 1, firstReview.report(), firstReview.tokens());
        usage.addTokens(firstReview.tokens());
        usage.setStages(usage.getStages() + 1);

        return rewriteLoop(request, taskId, emitter, usage, outline, writerContext,
                writeResult.text(), firstReview.report(), writerIndex, total);
    }

    /**
     * 重写回路（止损：总轮数 <= maxRewriteRounds，硬上限 3）
     *
     * @param firstReport 首审报告（择优稿场景为胜稿的评审报告，不再重复首审）
     * @return 终稿与降级标记（轮数耗尽仍未通过 -> 取最后一稿 + degraded）
     */
    private DraftOutcome rewriteLoop(PipelineRequest request, Long taskId, ProgressEmitter emitter,
                                     StageUsage usage, ChapterOutline outline, WriterContext writerContext,
                                     String draft, ReviewReport firstReport, int writerIndex, int total) {
        ReviewReport report = firstReport;
        int round = 1;
        while (!report.isApproved() && round <= request.getMaxRewriteRounds() && shouldRewrite(report, round)) {
            emitter.emitStage(StageEvent.revision(StageCode.WRITING, round));
            creationTaskService.advanceStage(taskId, StageCode.WRITING.name());
            WriterAgent.WriteResult rewriteResult = writeWithRetry(writerContext, emitter, report, draft);
            saveArtifact(taskId, StageCode.WRITING, round + 1,
                    Map.of("text", rewriteResult.text()), rewriteResult.tokens());
            usage.addTokens(rewriteResult.tokens());
            usage.setStages(usage.getStages() + 1);
            draft = rewriteResult.text();

            emitter.emitStage(StageEvent.started(StageCode.CRITIC, writerIndex + 1, total));
            creationTaskService.advanceStage(taskId, StageCode.CRITIC.name());
            CriticAgent.ReviewResult recheck = reviewDraft(request, draft, outline);
            saveArtifact(taskId, StageCode.CRITIC, round + 1, recheck.report(), recheck.tokens());
            usage.addTokens(recheck.tokens());
            usage.setStages(usage.getStages() + 1);
            report = recheck.report();
            round++;
        }
        return new DraftOutcome(draft, !report.isApproved());
    }

    /**
     * 重写触发规则（设计 3.3）：
     * 存在 HIGH -> 强制重写；仅 MEDIUM -> 附建议重写 1 轮；仅 LOW -> 直接通过
     */
    private boolean shouldRewrite(ReviewReport report, int round) {
        if (report.hasHighIssue()) {
            return true;
        }
        return report.hasMediumIssue() && round == 1;
    }

    // ------------------------------------------------------------------
    // 阶段执行（含失败策略）
    // ------------------------------------------------------------------

    /**
     * Writer 执行：失败重试 1 次；再失败则管道失败（设计 4.4）
     *
     * @param review 重写轮次携带的问题清单；null 表示首轮写作
     * @param draft  待修订原稿；首轮写作时为 null
     */
    private WriterAgent.WriteResult writeWithRetry(WriterContext context, ProgressEmitter emitter,
                                                    ReviewReport review, String draft) {
        int attempt = 0;
        while (true) {
            try {
                if (review == null) {
                    return writerAgent.write(context, emitter::emitContent);
                }
                return writerAgent.rewrite(context, draft, review, attempt + 1, emitter::emitContent);
            } catch (Exception e) {
                attempt++;
                if (attempt > 1) {
                    throw e; // 重试 1 次仍失败：管道失败
                }
                log.warn("Writer 调用失败，重试第 {} 次: {}", attempt, e.getMessage());
            }
        }
    }

    /**
     * Critic 执行：失败降级视为通过（Critic 假阳性会把对的改坏，宁可放过不可改错）
     */
    private CriticAgent.ReviewResult reviewDraft(PipelineRequest request, String draft, ChapterOutline outline) {
        CriticContext criticContext = contextAssemblyService.assembleCriticContext(request, draft, outline);
        try {
            return criticAgent.review(criticContext);
        } catch (Exception e) {
            log.warn("Critic 调用失败，降级视为通过: {}", e.getMessage());
            ReviewReport fallback = new ReviewReport();
            fallback.setApproved(true);
            fallback.setOverallScore(7);
            fallback.setSummary("Critic 故障降级：视为通过（" + e.getMessage() + "）");
            return new CriticAgent.ReviewResult(fallback, 0);
        }
    }

    // ------------------------------------------------------------------
    // 伏笔记账与产物落库
    // ------------------------------------------------------------------

    /**
     * 终稿采纳后结算伏笔账本：正文内联 <!--plot:--> 标记（大纲外新伏笔）入账。
     * 幂等（同描述已存在则跳过），失败不中断管道。
     */
    private void settlePlotLedger(PipelineRequest request, String draft) {
        if (draft == null || request.getBookId() == null) {
            return;
        }
        try {
            var marks = plotThreadService.extractInlinePlotMarks(draft);
            if (!marks.isEmpty()) {
                plotThreadService.recordInlinePlotMarks(request.getBookId(), request.getChapterId(), marks);
            }
        } catch (Exception e) {
            log.error("内联伏笔入账失败: bookId={}", request.getBookId(), e);
        }
    }

    /** 阶段产物实时落库（异常中断后产物仍在，支持基于 last artifact 续跑——阶段三接口预留） */
    private void saveArtifact(Long taskId, StageCode stage, int round, Object artifact, long tokens) {
        try {
            CreationStageArtifact record = new CreationStageArtifact();
            record.setTaskId(taskId);
            record.setStage(stage.name());
            record.setRound(round);
            record.setArtifact(artifact);
            record.setTokens((int) Math.min(Integer.MAX_VALUE, tokens));
            record.setElapsedMs(0L);
            artifactMapper.insert(record);
        } catch (Exception e) {
            // 埋点落库失败不中断管道，仅记录日志
            log.error("阶段产物落库失败: taskId={}, stage={}", taskId, stage, e);
        }
    }

    private void validate(PipelineRequest request) {
        if (request.getMode() == null) {
            request.setMode(CreationMode.FAST);
        }
        if (request.getMaxRewriteRounds() < 1) {
            request.setMaxRewriteRounds(1);
        }
        // 重写轮数硬上限 3：止损线不因入参放开
        request.setMaxRewriteRounds(Math.min(3, request.getMaxRewriteRounds()));
    }

    /** 闭环产出：终稿 + 是否降级 */
    private record DraftOutcome(String draft, boolean degraded) {
    }

    /** Planner 阶段产出：大纲 + 是否降级 */
    private record PlanOutcome(ChapterOutline outline, boolean degraded) {
    }
}
