package com.linpj.novel.create.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linpj.novel.create.agent.CriticAgent;
import com.linpj.novel.create.agent.PlannerAgent;
import com.linpj.novel.create.agent.PolisherAgent;
import com.linpj.novel.create.agent.WriterAgent;
import com.linpj.novel.create.config.PipelineBudgetProperties;
import com.linpj.novel.create.config.PipelineFeatures;
import com.linpj.novel.create.mapper.novel.CreationStageArtifactMapper;
import com.linpj.novel.create.pojo.entity.CreationStageArtifact;
import com.linpj.novel.create.pojo.pipeline.AsyncCreationTaskMessage;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.CreationMode;
import com.linpj.novel.create.pojo.pipeline.CriticContext;
import com.linpj.novel.create.pojo.pipeline.Issue;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.PlannerContext;
import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import com.linpj.novel.create.pojo.pipeline.StageCode;
import com.linpj.novel.create.pojo.pipeline.StageEvent;
import com.linpj.novel.create.pojo.pipeline.StageUsage;
import com.linpj.novel.create.pojo.pipeline.WriterContext;
import com.linpj.novel.create.producer.CreationEventProducer;
import com.linpj.novel.create.rule.NarrativeZone;
import com.linpj.novel.create.rule.RuleCheckContext;
import com.linpj.novel.create.rule.RuleCheckResult;
import com.linpj.novel.create.rule.RuleChecker;
import com.linpj.novel.create.rule.RuleExemptionService;
import com.linpj.novel.create.rule.RuleSeverity;
import com.linpj.novel.create.rule.RuleStage;
import com.linpj.novel.create.rule.RuleViolation;
import com.linpj.novel.create.security.FinalDraftGate;
import com.linpj.novel.create.security.SensitiveTerms;
import com.linpj.novel.create.security.StreamGuard;
import com.linpj.novel.create.security.StreamSafetyGate;
import com.linpj.novel.create.security.StreamSensitiveHitException;
import com.linpj.novel.create.service.ChapterOutlineStoreService;
import com.linpj.novel.create.service.CreationTaskService;
import com.linpj.novel.create.service.PlotThreadService;
import com.linpj.novel.create.worldstate.ContextAssemblyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

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

    /** 规则校验结果的 artifact 轮次标记（N3/N5，区别于 Critic 评审轮次） */
    private static final int RULE_CHECK_ROUND = 97;

    /** 终稿采纳记录的 artifact 轮次标记（M3-D1：采纳轮次/回滚/未达标落账，区别于上述两类） */
    private static final int ADOPTED_ROUND = 96;

    /** 定稿前安全 gate 的 artifact 轮次标记（M4-E3：命中/修复/人审队列留档） */
    private static final int POST_GATE_ROUND = 95;

    /** 中置流式拦截的最大重生成次数（M4-E2：一次为限，避免"拦截-重生成"死循环烧钱） */
    private static final int MAX_STREAM_REGENERATIONS = 1;

    /** 修订循环预算比（M3-D3：修订累计 token 超首写 40% 强制出循环；S10 校准前的相对口径） */
    private static final double REVISION_BUDGET_RATIO = 0.40;

    /** RAG 默认召回数（Spring AI VectorStoreDocumentRetriever 默认值；M5-F1 L1 档减半的基数） */
    static final int DEFAULT_RAG_TOP_K = 4;

    // 节点标识（M5-F2 checkpoint：阶段:轮次命名空间；PRECISE 双稿/择优节点下一批扩展）
    private static final String NODE_PLANNING = "PLANNING";
    private static final String NODE_WRITING_R1 = "WRITING:1";
    private static final String NODE_CRITIC_R1 = "CRITIC:1";
    private static final String NODE_POLISHING = "POLISHING:1";

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
    private final PipelineFeatures pipelineFeatures;
    private final RuleChecker ruleChecker;
    private final RuleExemptionService ruleExemptionService;
    private final FinalDraftGate finalDraftGate;
    private final StreamSafetyGate streamSafetyGate;
    /** 预算守卫配置（M5-F1：不可关闭，仅数值可配——降级语义为代码常量） */
    private final PipelineBudgetProperties budgetProperties;
    /** 节点 checkpoint（M5-F2 断点续跑：可空——旁路能力，离线装配/单测无数据库环境时缺位） */
    private final com.linpj.novel.create.worldstate.NodeCheckpointService checkpointService;
    /** 成本账本（M5-F4：可空——观测能力，任务终态单点入账） */
    private final com.linpj.novel.create.worldstate.CostLedgerService costLedgerService;

    public CreationPipelineService(WriterAgent writerAgent,
                                    CriticAgent criticAgent,
                                    PlannerAgent plannerAgent,
                                    PolisherAgent polisherAgent,
                                    CreationTaskService creationTaskService,
                                    CreationStageArtifactMapper artifactMapper,
                                    ContextAssemblyService contextAssemblyService,
                                    PlotThreadService plotThreadService,
                                    ChapterOutlineStoreService outlineStoreService,
                                    CreationEventProducer creationEventProducer,
                                    PipelineFeatures pipelineFeatures,
                                    RuleChecker ruleChecker) {
        this(writerAgent, criticAgent, plannerAgent, polisherAgent, creationTaskService, artifactMapper,
                contextAssemblyService, plotThreadService, outlineStoreService, creationEventProducer,
                pipelineFeatures, ruleChecker, null);
    }

    /** 完整构造（含误报豁免服务——D6 误报闭环装配） */
    public CreationPipelineService(WriterAgent writerAgent,
                                    CriticAgent criticAgent,
                                    PlannerAgent plannerAgent,
                                    PolisherAgent polisherAgent,
                                    CreationTaskService creationTaskService,
                                    CreationStageArtifactMapper artifactMapper,
                                    ContextAssemblyService contextAssemblyService,
                                    PlotThreadService plotThreadService,
                                    ChapterOutlineStoreService outlineStoreService,
                                    CreationEventProducer creationEventProducer,
                                    PipelineFeatures pipelineFeatures,
                                    RuleChecker ruleChecker,
                                    RuleExemptionService ruleExemptionService) {
        this(writerAgent, criticAgent, plannerAgent, polisherAgent, creationTaskService, artifactMapper,
                contextAssemblyService, plotThreadService, outlineStoreService, creationEventProducer,
                pipelineFeatures, ruleChecker, ruleExemptionService, null, null, new PipelineBudgetProperties());
    }

    /** 含定稿前 gate、不含中置流式拦截的装配（单测/离线装配用：E2 前行为） */
    public CreationPipelineService(WriterAgent writerAgent,
                                    CriticAgent criticAgent,
                                    PlannerAgent plannerAgent,
                                    PolisherAgent polisherAgent,
                                    CreationTaskService creationTaskService,
                                    CreationStageArtifactMapper artifactMapper,
                                    ContextAssemblyService contextAssemblyService,
                                    PlotThreadService plotThreadService,
                                    ChapterOutlineStoreService outlineStoreService,
                                    CreationEventProducer creationEventProducer,
                                    PipelineFeatures pipelineFeatures,
                                    RuleChecker ruleChecker,
                                    RuleExemptionService ruleExemptionService,
                                    FinalDraftGate finalDraftGate) {
        this(writerAgent, criticAgent, plannerAgent, polisherAgent, creationTaskService, artifactMapper,
                contextAssemblyService, plotThreadService, outlineStoreService, creationEventProducer,
                pipelineFeatures, ruleChecker, ruleExemptionService, finalDraftGate, null,
                new PipelineBudgetProperties());
    }

    /**
     * 兼容构造（测试/离线装配）：预算守卫不可关闭，默认数值实例兑底；
     * checkpoint 为旁路能力可空（无数据库环境零依赖）。
     */
    CreationPipelineService(WriterAgent writerAgent,
                                     CriticAgent criticAgent,
                                     PlannerAgent plannerAgent,
                                     PolisherAgent polisherAgent,
                                     CreationTaskService creationTaskService,
                                     CreationStageArtifactMapper artifactMapper,
                                     ContextAssemblyService contextAssemblyService,
                                     PlotThreadService plotThreadService,
                                     ChapterOutlineStoreService outlineStoreService,
                                     CreationEventProducer creationEventProducer,
                                     PipelineFeatures pipelineFeatures,
                                     RuleChecker ruleChecker,
                                     RuleExemptionService ruleExemptionService,
                                     FinalDraftGate finalDraftGate,
                                     StreamSafetyGate streamSafetyGate,
                                     PipelineBudgetProperties budgetProperties) {
        this(writerAgent, criticAgent, plannerAgent, polisherAgent, creationTaskService, artifactMapper,
                contextAssemblyService, plotThreadService, outlineStoreService, creationEventProducer,
                pipelineFeatures, ruleChecker, ruleExemptionService, finalDraftGate, streamSafetyGate,
                budgetProperties, null);
    }

    /**
     * 兼容构造（测试/离线装配，含 checkpoint 无账本）：预算兑底、checkpoint/账本旁路可空。
     */
    CreationPipelineService(WriterAgent writerAgent,
                            CriticAgent criticAgent,
                            PlannerAgent plannerAgent,
                            PolisherAgent polisherAgent,
                            CreationTaskService creationTaskService,
                            CreationStageArtifactMapper artifactMapper,
                            ContextAssemblyService contextAssemblyService,
                            PlotThreadService plotThreadService,
                            ChapterOutlineStoreService outlineStoreService,
                            CreationEventProducer creationEventProducer,
                            PipelineFeatures pipelineFeatures,
                            RuleChecker ruleChecker,
                            RuleExemptionService ruleExemptionService,
                            FinalDraftGate finalDraftGate,
                            StreamSafetyGate streamSafetyGate,
                            PipelineBudgetProperties budgetProperties,
                            com.linpj.novel.create.worldstate.NodeCheckpointService checkpointService) {
        this(writerAgent, criticAgent, plannerAgent, polisherAgent, creationTaskService, artifactMapper,
                contextAssemblyService, plotThreadService, outlineStoreService, creationEventProducer,
                pipelineFeatures, ruleChecker, ruleExemptionService, finalDraftGate, streamSafetyGate,
                budgetProperties, checkpointService, null);
    }

    /**
     * 生产装配（Spring 唯一注入点）：安全 gate + 流式拦截 + 预算守卫 + checkpoint + 成本账本。
     */
    @Autowired
    public CreationPipelineService(WriterAgent writerAgent,
                                     CriticAgent criticAgent,
                                     PlannerAgent plannerAgent,
                                     PolisherAgent polisherAgent,
                                     CreationTaskService creationTaskService,
                                     CreationStageArtifactMapper artifactMapper,
                                     ContextAssemblyService contextAssemblyService,
                                     PlotThreadService plotThreadService,
                                     ChapterOutlineStoreService outlineStoreService,
                                     CreationEventProducer creationEventProducer,
                                     PipelineFeatures pipelineFeatures,
                                     RuleChecker ruleChecker,
                                     RuleExemptionService ruleExemptionService,
                                     FinalDraftGate finalDraftGate,
                                     StreamSafetyGate streamSafetyGate,
                                     PipelineBudgetProperties budgetProperties,
                                     com.linpj.novel.create.worldstate.NodeCheckpointService checkpointService,
                                     com.linpj.novel.create.worldstate.CostLedgerService costLedgerService) {
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
        this.pipelineFeatures = pipelineFeatures;
        this.ruleChecker = ruleChecker;
        this.ruleExemptionService = ruleExemptionService;
        this.finalDraftGate = finalDraftGate;
        this.streamSafetyGate = streamSafetyGate;
        this.budgetProperties = budgetProperties;
        this.checkpointService = checkpointService;
        this.costLedgerService = costLedgerService;
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
                request.getBookId(), request.getChapterId(), request.getMode().name(),
                featureSnapshot(request));
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
        boolean degraded = false;
        // 运行时开关一次性解析（与落库快照同源）：后续各节点按此执行（v2.0 设计 7.1）
        Map<String, Boolean> features = pipelineFeatures.merged(request.getFeatures());
        // 预算守卫（M5-F1 三级链之首）：书级硬上限校验（超限拒绝启动）+ 章级会话打开。
        // 守卫不可关闭（TC-30 契约）：预算数值可配，降级语义是代码常量；
        // 书级超限由状态机 catch 转 failTask（未完成报告随 error 转人工），与 L4 止损同一路径
        boolean failed = false;
        try {
            ChapterBudget budget = openChapterBudget(request, usage, startAt);
            switch (request.getMode()) {
                case FAST -> degraded = runFast(request, taskId, emitter, usage);
                case STANDARD -> degraded = runStandard(request, taskId, emitter, usage, features, budget);
                case REFINED -> degraded = runRefined(request, taskId, emitter, usage, features, budget);
                case PRECISE -> degraded = runPrecise(request, taskId, emitter, usage, features, budget);
                default -> throw new IllegalArgumentException("不支持的模式: " + request.getMode());
            }
        } catch (Exception e) {
            failed = true;
            log.error("管道执行失败: taskId={}, mode={}", taskId, request.getMode(), e);
            emitter.emitStage(StageEvent.failed(e.getMessage()));
            creationTaskService.failTask(taskId, e.getMessage());
        }
        if (!failed) {
            usage.setElapsedMs(System.currentTimeMillis() - startAt);
            emitter.emitStage(StageEvent.completed(degraded, usage));
            creationTaskService.completeTask(taskId, degraded,
                    Math.toIntExact(Math.min(Integer.MAX_VALUE, usage.getTotalTokens())), usage.getElapsedMs());
        }
        // M5-F4 成本入账（成功/失败均入账——已烧掉的 token 不因任务失败消失）：
        // 从阶段产物派生（stage→Agent→model 映射确定），幂等可重算，服务缺位/故障静默
        if (costLedgerService != null) {
            try {
                costLedgerService.ledgerRun(taskId, request.getMode().name(),
                        request.getBookId(), request.getChapterId());
            } catch (Exception e) {
                log.debug("成本入账异常（忽略，服务层已静默兜底）: taskId={}, {}", taskId, e.getMessage());
            }
        }
        emitter.complete();
    }

    // ------------------------------------------------------------------
    // 预算守卫（M5-F1：书级硬上限 -> 章级两维会话；轮级 40% 在重写回路）
    // ------------------------------------------------------------------

    /**
     * 书级预算校验 + 打开章级会话：
     * - 硬上限：该书累计 token（含 FAILED 任务）超上限 → 拒绝启动（executePipeline 兜底转 failTask，
     *   已完成的章节不受影响）；与安全层同属不可让渡域——烧穿预算前先停笔。
     * - 80% 水位：告警留痕（预算事件日志；结构化落账随 B3 checkpoint 节点留痕一起收口）。
     * - 查询容错：聚合失败按 0 处理——预算是守卫不是闸门，查询故障不拦死创作（失败率与误拦的取舍）。
     */
    private ChapterBudget openChapterBudget(PipelineRequest request, StageUsage usage, long startAt) {
        long bookTokens = 0;
        if (request.getBookId() != null) {
            try {
                bookTokens = creationTaskService.sumBookTokens(request.getBookId());
            } catch (Exception e) {
                log.warn("书级预算查询失败（按 0 处理）: bookId={}", request.getBookId());
            }
        }
        long bookMax = budgetProperties.getBook().getMaxTokens();
        if (bookMax > 0 && bookTokens > bookMax) {
            throw new BudgetExhaustedException(String.format(
                    "书级预算硬上限已耗尽: bookId=%s, 已用 %d / 上限 %d tokens——"
                            + "新章生成被拒绝（已完成章节不受影响）；续写请上调预算或重算统计",
                    request.getBookId(), bookTokens, bookMax));
        }
        if (bookMax > 0 && bookTokens > bookMax * BudgetLevel.ALERT_RATIO) {
            log.warn("书级预算接近上限（水位告警）: bookId={}, 已用 {} / {} tokens",
                    request.getBookId(), bookTokens, bookMax);
        }
        return new ChapterBudget(budgetProperties.getChapter(), usage, startAt);
    }

    /**
     * 生成节点前置检查：L4 止损——预算超限 20% 缓冲后停笔。
     * 已完成产物保留（artifact 实时落库），failTask.error 携带预算报告转人工。
     */
    private static void guardForGeneration(ChapterBudget budget, Long taskId, String node) {
        if (budget != null && budget.level().atLeast(BudgetLevel.L4)) {
            throw new BudgetExhaustedException(String.format(
                    "章级预算止损（L4）: taskId=%s, 节点=%s——停止后续生成，已完成产物已落库，转人工决策",
                    taskId, node));
        }
    }

    /**
     * L1+ 预警下 RAG 召回数减半（保底 1）。注入 WriterContext 字段而非装配器——
     * 预算是运行时状态，装配器保持账本职责单一。
     */
    private static void applyBudgetRagLimit(WriterContext context, ChapterBudget budget) {
        if (context == null || budget == null) {
            return;
        }
        int limit = budget.ragTopLimit(DEFAULT_RAG_TOP_K);
        if (limit < DEFAULT_RAG_TOP_K) {
            context.setRagTopLimit(limit);
        }
    }

    // ------------------------------------------------------------------
    // 节点 checkpoint（M5-F2：旁路落账 + 输入指纹；PLANNING 已恢复，Writer/Critic 恢复下一批）
    // ------------------------------------------------------------------

    /** Planner 节点输入指纹（复用判据：作者指令/章节未变才可恢复产物） */
    private static String planningInputHash(PipelineRequest request) {
        return sha256Of("book=" + request.getBookId()
                + "|chapter=" + request.getChapterId()
                + "|input=" + request.getUserInput()
                + "|type=" + request.getCreationType());
    }

    /** 内容指纹（节点输入哈希的通用口径：null 容忍拼接） */
    private static String sha256Of(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of()
                    .formatHex(digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // SHA-256 不可用时退化 hash：仍可区分输入变更（弱化但非失效）
            return Integer.toHexString(content.hashCode());
        }
    }

    /** 节点成功旁路落账（薄封装：服务缺位/故障静默——续跑资格损失绝不阻断管道） */
    private void markNodeSucceeded(Long taskId, String nodeId, String inputHash, long tokens, String note) {
        if (checkpointService == null) {
            return;
        }
        try {
            checkpointService.markSucceeded(taskId, nodeId, inputHash,
                    (int) Math.min(Integer.MAX_VALUE, Math.max(0, tokens)), note);
        } catch (Exception e) {
            // 自防：服务层静默之外的任何故障（mock/半初始化）也不得反向影响节点语义
            log.debug("checkpoint 落账异常（忽略）: runId={}, node={}, error={}", taskId, nodeId, e.getMessage());
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
        // 定稿前安全 gate（M4-E3）：命中则修复式改写，改写稿整体重置客户端缓冲后重发
        GatedDraft gated = gateBeforeFinalize(request, taskId, null, result.text());
        if (gated.repaired()) {
            emitter.emitStage(StageEvent.revision(StageCode.WRITING, 1));
            emitter.emitContent(gated.text());
        }
        saveArtifact(taskId, StageCode.WRITING, 1, Map.of("text", gated.text()), result.tokens());
        usage.addTokens(result.tokens());
        usage.setStages(usage.getStages() + 1);
        settlePlotLedger(request, gated.text());
        return false;
    }

    // ------------------------------------------------------------------
    // STANDARD：Writer -> Critic 闭环（含重写回路）
    // ------------------------------------------------------------------

    private boolean runStandard(PipelineRequest request, Long taskId, ProgressEmitter emitter,
                                 StageUsage usage, Map<String, Boolean> features, ChapterBudget budget) {
        DraftOutcome outcome = runWriterCriticLoop(request, taskId, emitter, usage, null, 1, 2, features, budget);
        // 定稿前安全 gate（M4-E3）
        GatedDraft gated = gateBeforeFinalize(request, taskId, null, outcome.draft());
        if (gated.repaired()) {
            emitter.emitStage(StageEvent.revision(StageCode.WRITING, Math.max(1, outcome.adoptedRound())));
            emitter.emitContent(gated.text());
        }
        settlePlotLedger(request, gated.text());
        return outcome.degraded() || (budget != null && budget.degraded());
    }

    // ------------------------------------------------------------------
    // REFINED：Planner -> Writer -> Critic(回路) -> Polisher（含复核）
    // ------------------------------------------------------------------

    private boolean runRefined(PipelineRequest request, Long taskId, ProgressEmitter emitter,
                               StageUsage usage, Map<String, Boolean> features, ChapterBudget budget) {
        final int total = 4; // PLANNING + WRITING + CRITIC + POLISHING
        // —— Planner（序号 1）——
        PlanOutcome plan = runPlanning(request, taskId, emitter, usage, total, features);
        return runRefinedWithPlan(request, taskId, emitter, usage, features, budget, plan, total);
    }

    /** REFINED 主干（已持有 Planner 产物；PRECISE 预算降级复用此路径且不重跑 Planner） */
    private boolean runRefinedWithPlan(PipelineRequest request, Long taskId, ProgressEmitter emitter,
                                       StageUsage usage, Map<String, Boolean> features, ChapterBudget budget,
                                       PlanOutcome plan, int total) {
        // —— Writer -> Critic 闭环（Writer 序号 2，Critic 序号 3）——
        DraftOutcome outcome = runWriterCriticLoop(request, taskId, emitter, usage, plan.outline(), 2, total, features, budget);

        // —— 伏笔记账 + Polisher（序号 4）——
        settleLedger(request, plan.outline(), outcome.draft());
        // 先求值再或：|| 短路会跳过 Polisher（Planner 降级时正文已写出，润色仍需执行）
        boolean polishDegraded = runPolishing(request, taskId, emitter, usage, plan.outline(), outcome.draft(), budget);
        // 预算降级（L3 跳 Critic/Polisher、L1 关 best-of 等）合并冒泡
        boolean budgetDegraded = budget != null && budget.degraded();
        return plan.degraded() || outcome.degraded() || polishDegraded || budgetDegraded;
    }

    // ------------------------------------------------------------------
    // PRECISE：REFINED + Writer 并行双稿择优（设计 11.2）
    // ------------------------------------------------------------------

    private boolean runPrecise(PipelineRequest request, Long taskId, ProgressEmitter emitter,
                               StageUsage usage, Map<String, Boolean> features, ChapterBudget budget) {
        final int total = 4;
        boolean degraded = false;

        // —— Planner（序号 1）——
        PlanOutcome plan = runPlanning(request, taskId, emitter, usage, total, features);
        degraded = degraded || plan.degraded();

        // L1+ 预警：关 best-of（设计 5.4）——PRECISE 降级为 REFINED 单稿链路。
        // 检查点在 Planner 之后：预算感知需要已发生的消耗（入口时 usage 恒 0 判不出档位）。
        // 双稿是成本最贵的档位，预算紧张时第一个让路；安全防线与规则校验不受影响（它们本就在两条链路上等价存在）
        if (budget != null && budget.level().atLeast(BudgetLevel.L1)) {
            log.warn("预算预警（L1+）：PRECISE 关闭 best-of，降级为 REFINED 单稿链路: taskId={}", taskId);
            budget.markDegraded();
            // 复用 REFINED 主干（已持有 Planner 产物，不再重复规划——降级路径不烧预算）
            return runRefinedWithPlan(request, taskId, emitter, usage, features, budget, plan, total);
        }

        // —— 并行双稿（N=2：标准稿流式转发，变体稿静默聚合）——
        // 预算止损（L4）：双稿是成本最贵的路径，开写前先拦（已完成 Planner 产物保留落库）
        guardForGeneration(budget, taskId, "PRECISE 双稿");
        emitter.emitStage(StageEvent.started(StageCode.WRITING, 2, total));
        creationTaskService.advanceStage(taskId, StageCode.WRITING.name());
        WriterContext writerContext = contextAssemblyService.assembleWriterContext(request, plan.outline());
        applyBudgetRagLimit(writerContext, budget);

        // 双稿都过 STREAM 层（M4-E2）：A 稿边写边发，B 稿静默聚合——
        // 若胜出的是 B 稿，其正文同样要"先拦后发"（否则择优回退的整稿重发就是绕过点）
        StreamGuard guardA = streamSafetyGate == null ? null : streamSafetyGate.newGuard();
        StreamGuard guardB = streamSafetyGate == null ? null : streamSafetyGate.newGuard();
        Consumer<String> sinkA = guardA == null ? emitter::emitContent : guardA.guarded(emitter::emitContent);
        Consumer<String> silent = chunk -> { };
        Consumer<String> sinkB = guardB == null ? silent : guardB.guarded(silent);
        Mono<WriterAgent.WriteResult> draftA = Mono.fromCallable(() -> {
                    WriterAgent.WriteResult result = writerAgent.writeVariant(writerContext, 1, sinkA);
                    if (guardA != null) {
                        guardA.flush(emitter::emitContent); // 跨包窗口的尾巴补齐
                    }
                    return result;
                })
                .subscribeOn(PIPELINE_SCHEDULER);
        Mono<WriterAgent.WriteResult> draftB = Mono.fromCallable(() -> {
                    WriterAgent.WriteResult result = writerAgent.writeVariant(writerContext, 2, sinkB);
                    if (guardB != null) {
                        guardB.flush(silent);
                    }
                    return result;
                })
                .subscribeOn(PIPELINE_SCHEDULER);
        WriterAgent.WriteResult resultA;
        WriterAgent.WriteResult resultB;
        try {
            var drafts = Objects.requireNonNull(
                    Mono.zip(draftA, draftB).block(DUAL_DRAFT_TIMEOUT), "并行双稿生成失败");
            resultA = drafts.getT1();
            resultB = drafts.getT2();
        } catch (Exception e) {
            // 双稿并行失败：降级为单稿（标准稿重试机制走 writeWithRetry）。
            // 失败前标准稿可能已流式外发部分内容：先 REVISION 重置客户端内容缓冲再走单稿，避免拼接污染
            // 命中类失败（M4-E2）同样走这条降级路：单稿链路自带"命中即断 + 截断重生成 + 失败即失败"，
            // 因此这里不重复落 STREAM 账（避免同一次拦截被计两次、污染拦截率）。
            if (e instanceof StreamSensitiveHitException hit) {
                log.warn("并行双稿流式命中，降级为单稿重生成: hits={}", FinalDraftGate.describe(hit.hits()));
            }
            log.warn("并行双稿失败，降级为单稿: {}", e.getMessage());
            degraded = true;
            emitter.emitStage(StageEvent.revision(StageCode.WRITING, 1));
            WriterAgent.WriteResult single = writeWithRetry(writerContext, emitter, null, null);
            resultA = single;
            resultB = null;
        }

        // —— 双 Critic 择优 ——
        // 预算止损（L4）：超限后连择优评审的钱也不花（A/B 稿正文均已在 artifact，转人工比对）；
        // 预算触顶（L3）：跳过择优评审（与单稿链路同语义：Critic 让路，规则与安全保留）
        guardForGeneration(budget, taskId, "PRECISE 择优评审");
        CriticAgent.ReviewResult reviewA;
        if (budget != null && budget.level().atLeast(BudgetLevel.L3)) {
            log.warn("预算触顶（L3）：PRECISE 跳过双 Critic 择优，保序取 A 稿: taskId={}", taskId);
            budget.markDegraded();
            reviewA = degradedPassReview("预算触顶 L3：跳过择优评审，采用 A 稿");
        } else {
            reviewA = reviewDraft(request, resultA.text(), plan.outline());
        }
        // ADR-6（v2.0 M3-D2，评审 N1 必改）：convergenceGuard 开启时选优用成对比较（同批评审消除跨调用绝对分漂移），
        // 关闭时保持现状绝对分比较（TIE 取 A 避免抖动）；绝对分仅继续作为通过的门槛判定
        emitter.emitStage(StageEvent.started(StageCode.CRITIC, 3, total));
        creationTaskService.advanceStage(taskId, StageCode.CRITIC.name());
        saveArtifact(taskId, StageCode.WRITING, 1, Map.of("text", resultA.text(), "variant", 1), resultA.tokens());
        usage.addTokens(resultA.tokens());
        CriticAgent.ReviewResult reviewB;
        if (resultB != null && !(budget != null && budget.level().atLeast(BudgetLevel.L3))) {
            reviewB = reviewDraft(request, resultB.text(), plan.outline());
            saveArtifact(taskId, StageCode.WRITING, PRECISE_REJECTED_ROUND,
                    Map.of("text", resultB.text(), "variant", 2, "note", "precise-rejected"), resultB.tokens());
            usage.addTokens(resultB.tokens());
        } else {
            reviewB = null; // 单稿降级 / 预算触顶（L3 评审降级）
        }
        usage.setStages(usage.getStages() + 1);

        boolean bWins = false;
        if (Boolean.TRUE.equals(features.get("convergenceGuard")) && resultB != null && reviewB != null) {
            CriticAgent.CompareResult verdict =
                    compareDrafts(request, usage, plan.outline(), resultA.text(), resultB.text());
            bWins = verdict.bWins();
            saveArtifact(taskId, StageCode.CRITIC, 1,
                    Map.of("pairwise", List.of("A", "B"), "winner", verdict.winner(), "reason", verdict.reason()),
                    verdict.tokens());
        } else {
            bWins = reviewB != null && scoreOf(reviewB.report()) > scoreOf(reviewA.report());
        }
        if (reviewB == null) {
            // B 稿未评审（单稿降级或预算触顶）：无比较基准，保守取 A 稿（与 TIE 保守语义一致）
            bWins = false;
        }
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
        // N5 规则校验并入胜稿评审报告（P6：硬校验不因择优而缺席）
        CriticAgent.ReviewResult selectedWithRules =
                reviewWithRules(request, taskId, selectedDraft, plan.outline(), selectedReview, features);
        long selectedWriteTokens = (bWins ? resultB : resultA).tokens();
        DraftOutcome outcome = rewriteLoop(request, taskId, emitter, usage, plan.outline(),
                writerContext, selectedDraft, selectedWithRules.report(), 2, total, features, selectedWriteTokens, budget);

        // —— 伏笔记账 + Polisher（序号 4）——
        settleLedger(request, plan.outline(), outcome.draft());
        // 先求值再或：|| 短路会跳过 Polisher（Planner/双稿降级时正文已写出，润色仍需执行）
        boolean polishDegraded = runPolishing(request, taskId, emitter, usage, plan.outline(), outcome.draft(), budget);
        boolean budgetDegraded = budget != null && budget.degraded();
        return degraded || outcome.degraded() || polishDegraded || budgetDegraded;
    }

    private static int scoreOf(ReviewReport report) {
        return report.getOverallScore() == null ? 0 : report.getOverallScore();
    }

    // ------------------------------------------------------------------
    // 可复用阶段
    // ------------------------------------------------------------------

    /** Planner 阶段（失败/空产物降级：跳过大纲，Writer 直接吃用户输入，管道不中断） */
    private PlanOutcome runPlanning(PipelineRequest request, Long taskId, ProgressEmitter emitter,
                                    StageUsage usage, int total, Map<String, Boolean> features) {
        emitter.emitStage(StageEvent.started(StageCode.PLANNING, 1, total));
        creationTaskService.advanceStage(taskId, StageCode.PLANNING.name());
        // F2 断点续跑（M5-F2）：同任务同输入的 PLANNING 已成功 → 恢复产物不重跑（零重复计费）。
        // 复用条件三重判定：checkpoint 存在 + 输入哈希一致（作者指令未变）+ 产物本体仍在（outline 账本可查）
        if (checkpointService != null && request.getChapterId() != null
                && checkpointService.isResumable(taskId, NODE_PLANNING, planningInputHash(request))) {
            ChapterOutline resumed = outlineStoreService.loadLatest(
                    request.getBookId(), request.getChapterId());
            if (resumed != null) {
                log.info("断点续跑：恢复 PLANNING 产物（不重跑 Planner，零重复计费）: taskId={}", taskId);
                return new PlanOutcome(resumed, false);
            }
            // 产物本体丢失（outline 账本被清理）：回退正常执行——宁可重做不可中途废
        }
        try {
            PlannerContext plannerContext = contextAssemblyService.assemblePlannerContext(request);
            PlannerAgent.PlanResult planResult = plannerAgent.plan(plannerContext);
            ChapterOutline outline = planResult.outline();
            usage.addTokens(planResult.tokens());
            usage.setStages(usage.getStages() + 1);
            if (outline == null) {
                // 结构化解析返回空（未抛异常）：等同 Planner 降级，Writer 直接消费用户输入
                log.warn("Planner 返回空大纲，降级为直接续写: bookId={}", request.getBookId());
                return new PlanOutcome(null, true);
            }
            outlineStoreService.saveOutline(request.getBookId(), request.getChapterId(), outline);
            saveArtifact(taskId, StageCode.PLANNING, 1, outline, planResult.tokens());
            // N3 细纲预审（v2.0 设计 5.1）：仅记录与落档，阻塞决策待人审卡点（B4/M3）
            runOutlineRuleCheck(request, taskId, outline, features);
            // F2：节点成功旁路落账（同输入续跑的复用凭证；失败静默不阻管道）
            markNodeSucceeded(taskId, NODE_PLANNING, planningInputHash(request), planResult.tokens(), null);
            return new PlanOutcome(outline, false);
        } catch (Exception e) {
            log.warn("Planner 失败，降级为直接续写: {}", e.getMessage());
            return new PlanOutcome(null, true);
        }
    }

    /**
     * Polisher 阶段（情节不动硬约束 + Critic 复核；复核不过则回退原稿）。
     *
     * 润色内容先缓冲不外发：复核通过才下发，复核不过以 REVISION(POLISHING) 事件重置客户端缓冲并补发原稿
     * ——保证客户端看到的终稿与服务端采纳的终稿一致（否则复核失败的润色稿会残留在前端缓冲里被保存）。
     */
    private boolean runPolishing(PipelineRequest request, Long taskId, ProgressEmitter emitter,
                                 StageUsage usage, ChapterOutline outline, String draft, ChapterBudget budget) {
        // 预算触顶（L3/L4）：Polisher 让路（纯语言层增益，不产生新内容）——
        // 与"生成节点 L4 抛异常"的分层原则：L4 只拦"未产生新正文"的生成（Writer/评审/重写）；
        // 此处正文已完成，跳润色直接出章是最优止损形态（比任务失败对作者更有利）
        if (budget != null && budget.level().atLeast(BudgetLevel.L3)) {
            log.warn("预算触顶（L3）：跳过 Polisher，直接采用原稿: taskId={}", taskId);
            budget.markDegraded();
            return true;
        }
        emitter.emitStage(StageEvent.started(StageCode.POLISHING, 4, 4));
        creationTaskService.advanceStage(taskId, StageCode.POLISHING.name());
        try {
            StringBuilder polishBuffer = new StringBuilder();
            PolisherAgent.PolishResult polishResult =
                    polisherAgent.polish(draft, request.getStyle(), polishBuffer::append);
            saveArtifact(taskId, StageCode.POLISHING, 1, Map.of("text", polishResult.text()), polishResult.tokens());
            usage.addTokens(polishResult.tokens());
            usage.setStages(usage.getStages() + 1);

            CriticContext verifyContext =
                    contextAssemblyService.assembleCriticContext(request, polishResult.text(), outline);
            CriticAgent.ReviewResult verify = criticAgent.verifyPolish(verifyContext, draft);
            saveArtifact(taskId, StageCode.CRITIC, POLISH_VERIFY_ROUND, verify.report(), verify.tokens());
            usage.addTokens(verify.tokens());
            usage.setStages(usage.getStages() + 1);
            // F2：润色节点旁路落账（输入指纹=润色前原稿；复核未过回退原稿时不上账——未采纳的事实不入账）
            if (verify.report().isApproved()) {
                markNodeSucceeded(taskId, NODE_POLISHING,
                        sha256Of("draft=" + draft), polishResult.tokens() + verify.tokens(), null);
            }
            if (!verify.report().isApproved()) {
                log.warn("Polisher 复核未通过，回退润色前原稿: {}", verify.report().getSummary());
                // 定稿前安全 gate（M4-E3）：回退稿同样要过 gate（润色被否不等于原稿干净）
                GatedDraft gated = gateBeforeFinalize(request, taskId, outline, draft);
                // 回退：重置客户端内容缓冲后补发原稿（与 REFINED/PRECISE 择优回退同一协议语义）
                emitter.emitStage(StageEvent.revision(StageCode.POLISHING, 1));
                emitter.emitContent(gated.text());
                return true;
            }
            // 定稿前安全 gate（M4-E3）：润色终稿定稿前过 gate，修复稿替换润色稿进入下方下发
            GatedDraft gated = gateBeforeFinalize(request, taskId, outline, polishResult.text());
            // 复核通过：REVISION(POLISHING) 重置客户端缓冲（抹去 Writer 原稿）后下发润色终稿，
            // 避免原稿与终稿在 content 流中拼接重复
            emitter.emitStage(StageEvent.revision(StageCode.POLISHING, 1));
            emitter.emitContent(gated.text());
            return false;
        } catch (Exception e) {
            log.warn("Polisher 失败，降级采用原稿: {}", e.getMessage());
            // 润色内容未外发过，客户端缓冲仍是 Writer 终稿，无需回退信号
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
                                             StageUsage usage, ChapterOutline outline, int writerIndex,
                                             int total, Map<String, Boolean> features, ChapterBudget budget) {
        // —— Writer 首轮 ——
        guardForGeneration(budget, taskId, "Writer 首写"); // 预算止损（L4）：Planner 产物已落库，转人工
        emitter.emitStage(StageEvent.started(StageCode.WRITING, writerIndex, total));
        creationTaskService.advanceStage(taskId, StageCode.WRITING.name());
        WriterContext writerContext = contextAssemblyService.assembleWriterContext(request, outline);
        applyBudgetRagLimit(writerContext, budget); // L1+ 预警：RAG 召回减半
        // F2 第二批：首写断点恢复——同任务同输入的首写已成功且产物仍在 artifact → 恢复成稿不重写。
        // 输入指纹与落账侧完全同构（outline/用户指令），hash 一致即输入未变；产物读回失败回退正常执行
        String writerInputHash = sha256Of("outline=" + outline + "|direct=" + request.getUserInput());
        String resumedDraft = null;
        if (checkpointService != null && checkpointService.isResumable(taskId, NODE_WRITING_R1, writerInputHash)) {
            resumedDraft = loadArtifactText(taskId, StageCode.WRITING, 1);
            if (resumedDraft != null) {
                log.info("断点续跑：恢复 WRITING:1 成稿（不重写，零重复计费）: taskId={}", taskId);
                // SSE 语义：恢复稿不发增量 content（不是正在生成），仅落一次 REVISION 事件供前端重置缓冲
                emitter.emitStage(StageEvent.revision(StageCode.WRITING, 1));
                emitter.emitContent(resumedDraft);
            }
        }
        WriterAgent.WriteResult writeResult = resumedDraft != null
                ? new WriterAgent.WriteResult(resumedDraft, 0L) // 恢复稿零计费（token 已在首跑入账）
                : writeWithRetry(writerContext, emitter, null, null);
        saveArtifact(taskId, StageCode.WRITING, 1, Map.of("text", writeResult.text()), writeResult.tokens());
        usage.addTokens(writeResult.tokens());
        usage.setStages(usage.getStages() + 1);
        // F2：首写节点旁路落账（输入指纹=大纲；恢复路径 markSucceeded 幂等跳过）
        markNodeSucceeded(taskId, NODE_WRITING_R1, writerInputHash, writeResult.tokens(), resumedDraft != null ? "resumed" : null);

        // —— Critic 首审（N5 规则校验并入：阻塞级 HIGH 违规与 Critic 问题同构参与重写触发）——
        // 预算止损（L4）：连评审的钱也不花（超限 20% 后停笔——正文已在 artifact，转人工）；
        // 预算触顶（L3）：Critic 让路（硬校验与安全防线继续；RuleChecker 在 reviewWithRules 内不受预算影响）
        guardForGeneration(budget, taskId, "Critic 首审");
        emitter.emitStage(StageEvent.started(StageCode.CRITIC, writerIndex + 1, total));
        creationTaskService.advanceStage(taskId, StageCode.CRITIC.name());
        CriticAgent.ReviewResult baseReview;
        if (budget != null && budget.level().atLeast(BudgetLevel.L3)) {
            log.warn("预算触顶（L3）：跳过 Critic 首审（降级），规则校验与安全防线保留: taskId={}", taskId);
            budget.markDegraded();
            baseReview = degradedPassReview("预算触顶 L3：跳过首审（评审降级保护）");
        } else {
            baseReview = reviewDraft(request, writeResult.text(), outline);
        }
        CriticAgent.ReviewResult firstReview =
                reviewWithRules(request, taskId, writeResult.text(), outline, baseReview, features);
        saveArtifact(taskId, StageCode.CRITIC, 1, firstReview.report(), firstReview.tokens());
        usage.addTokens(firstReview.tokens());
        usage.setStages(usage.getStages() + 1);
        // F2：首审节点旁路落账（输入指纹=待审正文；L3 预算跳审不落账——未执行的事实不入账）
        if (baseReview.tokens() > 0) {
            markNodeSucceeded(taskId, NODE_CRITIC_R1,
                    sha256Of("draft=" + writeResult.text()), firstReview.tokens(), null);
        }

        return rewriteLoop(request, taskId, emitter, usage, outline, writerContext,
                writeResult.text(), firstReview.report(), writerIndex, total, features, writeResult.tokens(), budget);
    }

    /**
     * 重写回路（止损：总轮数 <= maxRewriteRounds，硬上限 3）。
     *
     * convergenceGuard 开启（v2.0 M3-D1/D2/D3，收敛控制器）：
     * - best-of 追踪：历史最优稿/报告留档，新稿劣于最优时回滚最优版本并强制出循环（成对比较判定，
     *   绝对分只做门槛——评审 N1：跨调用漂移不可用于跨轮比较）
     * - 循环预算：修订累计 token（含判优调用）超首写 40% 强制出循环（S10 校准前的相对口径）
     * - 轮次落账：采纳轮次/回滚/未达标写入 artifact（ADOPTED_ROUND，断点续跑 F2 的轮次恢复数据源）
     * 关闭（默认）：现状语义（轮次上限 + HIGH/MEDIUM 触发规则，每轮最新即终稿）。
     *
     * @param firstReport     首审报告（择优稿场景为胜稿的评审报告，不再重复首审）
     * @param baseWriteTokens 首写（或择优胜稿）的 token——修订预算的基准
     */
    private DraftOutcome rewriteLoop(PipelineRequest request, Long taskId, ProgressEmitter emitter,
                                     StageUsage usage, ChapterOutline outline, WriterContext writerContext,
                                     String draft, ReviewReport firstReport, int writerIndex, int total,
                                     Map<String, Boolean> features, long baseWriteTokens, ChapterBudget budget) {
        boolean guard = Boolean.TRUE.equals(features.get("convergenceGuard"));
        ReviewReport report = firstReport;
        String bestDraft = draft;
        ReviewReport bestReport = firstReport;
        int adoptedRound = 1;
        long revisionTokens = 0;
        boolean rolledBack = false;
        boolean budgetExhausted = false;
        int round = 1;
        // 预算紧缩（L2）：K=1 且仅修 HIGH（设计 5.4）——预算档位实时评估（重写前重算）
        boolean tightenOnlyHigh = budget != null && budget.level().atLeast(BudgetLevel.L2);
        if (tightenOnlyHigh) {
            log.warn("预算紧缩（L2+）：重写轮数压至 K=1 且仅修 HIGH 问题: taskId={}", taskId);
        }
        int roundsLimit = tightenOnlyHigh ? 1 : request.getMaxRewriteRounds();
        while (!report.isApproved() && round <= roundsLimit && shouldRewrite(report, round, tightenOnlyHigh)) {
            // 章级预算档位（L2-L4，实时：每轮重写前重算）
            if (budget != null) {
                if (budget.level().atLeast(BudgetLevel.L4)) {
                    throw new BudgetExhaustedException(String.format(
                            "章级预算止损（L4）：修订循环第 %d 轮前停笔——已完成产物已落库，转人工决策", round));
                }
                if (budget.level().atLeast(BudgetLevel.L3)) {
                    // 预算触顶（L3）：终止修订循环（当前稿出章，unmet 落账待人审）；规则与安全不受影响
                    log.warn("预算触顶（L3）：终止修订循环（当前稿出章+unmet 落账）: taskId={}, round={}", taskId, round);
                    budget.markDegraded();
                    break;
                }
            }
            // 循环预算（M3-D3）：修订累计 token 超首写 40% -> 强制出循环（防无限烧钱，不可达 pass 时及时止损）
            if (guard && revisionTokens > REVISION_BUDGET_RATIO * baseWriteTokens) {
                budgetExhausted = true;
                log.warn("修订预算耗尽（累计 {} token / 首写 {} token），强制出循环: taskId={}",
                        revisionTokens, baseWriteTokens, taskId);
                break; // 客户端缓冲已持有当前稿全文（流式外发），无需重置信号
            }
            emitter.emitStage(StageEvent.revision(StageCode.WRITING, round));
            creationTaskService.advanceStage(taskId, StageCode.WRITING.name());
            WriterAgent.WriteResult rewriteResult = writeWithRetry(writerContext, emitter, report, draft);
            saveArtifact(taskId, StageCode.WRITING, round + 1,
                    Map.of("text", rewriteResult.text()), rewriteResult.tokens());
            usage.addTokens(rewriteResult.tokens());
            usage.setStages(usage.getStages() + 1);
            revisionTokens += rewriteResult.tokens();
            draft = rewriteResult.text();

            emitter.emitStage(StageEvent.started(StageCode.CRITIC, writerIndex + 1, total));
            creationTaskService.advanceStage(taskId, StageCode.CRITIC.name());
            CriticAgent.ReviewResult baseRecheck = reviewDraft(request, draft, outline);
            CriticAgent.ReviewResult recheck =
                    reviewWithRules(request, taskId, draft, outline, baseRecheck, features);
            saveArtifact(taskId, StageCode.CRITIC, round + 1, recheck.report(), recheck.tokens());
            usage.addTokens(recheck.tokens());
            usage.setStages(usage.getStages() + 1);
            revisionTokens += recheck.tokens();
            report = recheck.report();

            if (guard) {
                // best-of 判定（M3-D2）：新稿（挑战者 A）vs 历史最优（擂主 B）；TIE 保守保持擂主
                CriticAgent.CompareResult verdict =
                        compareDrafts(request, usage, outline, draft, bestDraft);
                revisionTokens += verdict.tokens();
                if (verdict.aWins()) {
                    bestDraft = draft;
                    bestReport = report;
                    adoptedRound = round + 1;
                } else {
                    // 新稿劣于或未胜过历史最优：回滚最优版本并终止循环（LLM 修订不是单调改进算子）
                    rolledBack = true;
                    log.info("修订回滚：新稿未胜过历史最优(采纳轮次 {}), 回滚: taskId={}", adoptedRound, taskId);
                    emitter.emitStage(StageEvent.revision(StageCode.WRITING, round));
                    emitter.emitContent(bestDraft);
                    draft = bestDraft;
                    report = bestReport;
                    break;
                }
            }
            round++;
        }
        if (guard) {
            // 轮次落账（M3-D1）：采纳轮次/回滚/预算/未达标留档；未达标待人审队列（M3-D6/P2 卡点）接管
            saveArtifact(taskId, StageCode.CRITIC, ADOPTED_ROUND, Map.of(
                    "adoptedRound", adoptedRound,
                    "rolledBack", rolledBack,
                    "budgetExhausted", budgetExhausted,
                    "unmet", !report.isApproved()), 0);
        }
        return new DraftOutcome(draft, !report.isApproved(), adoptedRound, rolledBack);
    }

    /**
     * 成对比较封装（M3-D2）：判优调用失败保守归 TIE（保持擂主/现状稿）。
     * 判优 token 计入修正预算（usage 与 revisionTokens 均计入）。 
     */
    private CriticAgent.CompareResult compareDrafts(PipelineRequest request, StageUsage usage,
                                                   ChapterOutline outline, String draftA, String draftB) {
        try {
            CriticContext context = contextAssemblyService.assembleCriticContext(request, draftA, outline);
            CriticAgent.CompareResult verdict = criticAgent.compare(context, draftA, draftB);
            usage.addTokens(verdict.tokens());
            return verdict;
        } catch (Exception e) {
            log.warn("成对比较调用失败，保守保持既有稿: {}", e.getMessage());
            return new CriticAgent.CompareResult("TIE", "判优调用失败，保守保持既有稿", 0);
        }
    }

    /**
     * 重写触发规则（设计 3.3）：
     * 存在 HIGH -> 强制重写；仅 MEDIUM -> 附建议重写 1 轮；仅 LOW -> 直接通过。
     * 预算紧缩（L2，M5-F1）：MEDIUM 触发通道关闭——只修致命问题，紧缩期不做打磨性重写。
     */
    private boolean shouldRewrite(ReviewReport report, int round, boolean onlyHigh) {
        if (report.hasHighIssue()) {
            return true;
        }
        return !onlyHigh && report.hasMediumIssue() && round == 1;
    }

    /** 预算触顶时的降级通过报告（approved=true + 降级标注，冒泡 degraded 由 budget.markDegraded 承担） */
    private static CriticAgent.ReviewResult degradedPassReview(String summary) {
        ReviewReport fallback = new ReviewReport();
        fallback.setApproved(true);
        fallback.setOverallScore(7);
        fallback.setSummary(summary);
        return new CriticAgent.ReviewResult(fallback, 0);
    }

    // ------------------------------------------------------------------
    // 确定性规则校验节点（N3/N5，ruleRouting 总闸门控——v2.0 M1-B3）
    // ------------------------------------------------------------------

    /**
     * N5 前置：在 Critic 评审结果上并入规则校验。
     *
     * - ruleRouting 关闭（默认）：原样返回，管道行为与主线完全一致（TC-32 关开关回归）
     * - 阻塞级违规以 Critic Issue 同构形式并入（severity/type/evidence/suggestion），
     *   复用既有 shouldRewrite 分流：HIGH 强制重写、MEDIUM 附建议一轮——不发明新控制流
     * - P6 不可让渡：Critic 故障降级（视为通过）时，阻塞级规则违规仍然强制重写
     *
     * @return 并入规则后的评审结果（token 计 Critic 原值，规则校验为代码组件无 token）
     */
    private CriticAgent.ReviewResult reviewWithRules(PipelineRequest request, Long taskId, String draft,
                                                     ChapterOutline outline, CriticAgent.ReviewResult base,
                                                     Map<String, Boolean> features) {
        if (!Boolean.TRUE.equals(features.get("ruleRouting"))) {
            return base;
        }
        RuleCheckResult rules = runRuleCheck(request, taskId, RuleStage.DRAFT, draft, outline);
        if (rules == null) {
            return base;
        }
        return new CriticAgent.ReviewResult(
                mergeRulesIntoReport(base.report(), rules.getBlocking()), base.tokens());
    }

    /** N3 细纲预审：仅记录与落档（阻塞决策待人审卡点 B4/M3；当前规则集均为 DRAFT 时机，OUTLINE 上下文为接线预留） */
    private void runOutlineRuleCheck(PipelineRequest request, Long taskId, ChapterOutline outline,
                                     Map<String, Boolean> features) {
        if (!Boolean.TRUE.equals(features.get("ruleRouting"))) {
            return;
        }
        runRuleCheck(request, taskId, RuleStage.OUTLINE, null, outline);
    }

    /**
     * 执行规则校验并落档（RULE_CHECK_ROUND=97 区别于 Critic 评审轮次）。
     *
     * @return 校验结果；无已启用规则执行（全 OFF / 时机无匹配）时返回 null——不产生空产物
     */
    private RuleCheckResult runRuleCheck(PipelineRequest request, Long taskId, RuleStage stage,
                                         String draft, ChapterOutline outline) {
        try {
            RuleCheckContext context = RuleCheckContext.of(stage);
            context.setBookId(request.getBookId());
            context.setChapterId(request.getChapterId());
            context.setDraft(draft);
            context.setOutline(outline);
            context.setTargetWordCount(request.getTargetWordCount());
            context.setPov(request.getPov());
            // 叙诡保护区（E5）：只认大纲里作者显式声明（评审 N2），模型自由发挥不算
            context.setUnreliableZone(NarrativeZone.declaresUnreliable(outline));
            // 误报豁免装配（M3-D6）：作者标记过的规则对本书静默；服务缺失/故障容忍为空集
            if (ruleExemptionService != null) {
                try {
                    context.setExemptedRules(ruleExemptionService.listExemptedRules(request.getBookId()));
                } catch (Exception e) {
                    log.warn("豁免清单加载失败（按无豁免继续）: bookId={}", request.getBookId());
                }
                try {
                    // 区间保护（E5）：区间内只告警不阻塞，避免为保叙诡章而整本关规则
                    context.setProtectedRules(ruleExemptionService.listProtectedRules(
                            request.getBookId(), request.getChapterId()));
                } catch (Exception e) {
                    log.warn("保护区清单加载失败（按无保护区继续）: bookId={}", request.getBookId());
                }
            }
            RuleCheckResult result = ruleChecker.check(context);
            if (result.getExecutedRules().isEmpty()) {
                return null;
            }
            if (!result.getShadow().isEmpty()) {
                // 影子态违规：只记录（分级上线观测数据，不参与阻塞）
                log.info("规则影子命中（不阻塞）: taskId={}, shadow={}", taskId, result.getShadow());
            }
            if (!result.getBlocking().isEmpty() || !result.getShadow().isEmpty()) {
                saveArtifact(taskId, StageCode.CRITIC, RULE_CHECK_ROUND,
                        Map.of("stage", stage.name(), "blocking", result.getBlocking(),
                                "shadow", result.getShadow()), 0);
            }
            return result;
        } catch (Exception e) {
            // 规则校验故障不阻断管道（RuleChecker 内已降级单规则，此处兜底整体故障）
            log.warn("规则校验失败（跳过，管道继续）: taskId={}", taskId, e);
            return null;
        }
    }

    /** 阻塞级违规并入 Critic 报告（同构 Issue；HIGH 违规强制 approved=false） */
    private static ReviewReport mergeRulesIntoReport(ReviewReport base, List<RuleViolation> blocking) {
        if (blocking == null || blocking.isEmpty()) {
            return base;
        }
        ReviewReport merged = new ReviewReport();
        merged.setApproved(base.isApproved());
        merged.setOverallScore(base.getOverallScore());
        merged.setSummary(base.getSummary() == null ? "" : base.getSummary());
        List<Issue> issues = base.getIssues() == null ? new ArrayList<>() : new ArrayList<>(base.getIssues());
        boolean anyHigh = false;
        for (RuleViolation violation : blocking) {
            Issue issue = new Issue();
            issue.setSeverity(violation.getSeverity().name());
            issue.setType("WORLD_RULE_" + violation.getRuleId());
            issue.setProblem(violation.getEvidence());
            issue.setEvidence(violation.getEvidence());
            issue.setSuggestion(violation.getSuggestion());
            issues.add(issue);
            anyHigh = anyHigh || violation.getSeverity() == RuleSeverity.HIGH;
        }
        merged.setIssues(issues);
        if (anyHigh) {
            merged.setApproved(false);
        }
        return merged;
    }

    // ------------------------------------------------------------------
    // 定稿前安全 gate（M4-E3，POST 层）
    // ------------------------------------------------------------------

    /**
     * 定稿前安全 gate（设计 4.6 安全三层 POST；P6 安全层不可让渡——不设开关）。
     *
     * 流程：词表扫描 → 无命中（落 PASS 记录，作为拦截率分母）→ 有命中则**修复式改写一次**
     * （命中转成定向修订清单，复用 Writer 修订链路：只改表达、不动情节）→ 复查：
     *   干净   → 采用修复稿（落 REWRITE）
     *   仍命中 → 采用修复稿并进人审队列（落 MANUAL_REVIEW），**不阻断交付**
     *
     * 为什么不硬拒绝：词表只覆盖显式字面命中，硬拒绝的误杀成本由作者承担（评审 P2/D6）；
     * 修复式改写 + 人审队列把"安全 vs 可用"的冲突交给人，而不是让词表替作者做决定。
     * 故障策略：gate 自身异常一律退回原文（宁可放过不可改错），与 Critic/规则校验的降级语义一致。
     *
     * @param outline REFINED/PRECISE 传大纲供修复改写用；STANDARD/FAST 传 null
     */
    private GatedDraft gateBeforeFinalize(PipelineRequest request, Long taskId,
                                          ChapterOutline outline, String draft) {
        if (finalDraftGate == null || draft == null || draft.isBlank()) {
            return new GatedDraft(draft, false);
        }
        try {
            List<SensitiveTerms.Hit> hits = finalDraftGate.scan(draft);
            if (hits.isEmpty()) {
                finalDraftGate.recordPass(request.getBookId(), request.getChapterId(), draft.length());
                return new GatedDraft(draft, false);
            }
            log.warn("定稿前词表命中，进入修复式改写: bookId={}, chapterId={}, hits={}",
                    request.getBookId(), request.getChapterId(), FinalDraftGate.describe(hits));
            WriterContext writerContext = contextAssemblyService.assembleWriterContext(request, outline);
            WriterAgent.WriteResult repaired = writerAgent.rewrite(
                    writerContext, draft, FinalDraftGate.repairReport(hits), 1, chunk -> { });
            List<SensitiveTerms.Hit> recheck = finalDraftGate.scan(repaired.text());
            if (recheck.isEmpty()) {
                finalDraftGate.recordRewrite(request.getBookId(), request.getChapterId(), hits,
                        repaired.text().length());
                saveArtifact(taskId, StageCode.WRITING, POST_GATE_ROUND,
                        Map.of("action", FinalDraftGate.ACTION_REWRITE,
                                "hits", FinalDraftGate.describe(hits),
                                "text", repaired.text()), repaired.tokens());
                return new GatedDraft(repaired.text(), true);
            }
            // 修复一次仍有命中：照常交付（不阻塞），同时进人审队列，由人决定删改/豁免/改词表
            log.warn("修复式改写后仍命中，转人审队列: bookId={}, chapterId={}, hits={}",
                    request.getBookId(), request.getChapterId(), FinalDraftGate.describe(recheck));
            finalDraftGate.recordManualReview(request.getBookId(), request.getChapterId(), recheck,
                    repaired.text().length());
            saveArtifact(taskId, StageCode.WRITING, POST_GATE_ROUND,
                    Map.of("action", FinalDraftGate.ACTION_MANUAL_REVIEW,
                            "hits", FinalDraftGate.describe(recheck),
                            "text", repaired.text()), repaired.tokens());
            return new GatedDraft(repaired.text(), true);
        } catch (Exception e) {
            log.warn("定稿前 gate 故障，按原文交付: bookId={}, chapterId={}, cause={}",
                    request.getBookId(), request.getChapterId(), e.getMessage());
            return new GatedDraft(draft, false);
        }
    }

    /** 定稿前 gate 结果：text=进入终稿的正文；repaired=正文是否被替换（调用方需 REVISION 重发） */
    private record GatedDraft(String text, boolean repaired) {
    }

    // ------------------------------------------------------------------
    // 阶段执行（含失败策略）
    // ------------------------------------------------------------------

    /**
     * Writer 执行：失败重试 1 次；再失败则管道失败（设计 4.4）。
     *
     * 首次尝试正常流式外发；重试尝试改为缓冲后一次性下发
     * ——失败尝试已外发的部分内容会污染客户端缓冲，重试成功后必须以 REVISION 事件重置再发完整产出。
     *
     * 中置流式拦截（M4-E2，设计 4.6 STREAM 层）：
     * - 每次尝试的下发都过 {@link StreamGuard}：命中词在**下发前**被拦下（客户端看不到），
     *   随后发 REVISION 作废已下发部分，用"截断点之前的正文 + 定向修订清单"重生成一次；
     * - 重生成后仍命中 / 无可重生成内容：落 STREAM/REJECT 并让任务失败（转人工，不硬塞）
     * —— 安全层的失败必须是显式失败，不能降级成"照发"。
     *
     * @param review 重写轮次携带的问题清单；null 表示首轮写作
     * @param draft  待修订原稿；首轮写作时为 null
     */
    private WriterAgent.WriteResult writeWithRetry(WriterContext context, ProgressEmitter emitter,
                                                    ReviewReport review, String draft) {
        int attempt = 0;
        int regenerations = 0;
        while (true) {
            final boolean isRetry = attempt > 0;
            StringBuilder retryBuffer = isRetry ? new StringBuilder() : null;
            Consumer<String> downstream = isRetry ? retryBuffer::append : emitter::emitContent;
            StreamGuard guard = streamSafetyGate == null ? null : streamSafetyGate.newGuard();
            Consumer<String> sink = guard == null ? downstream : guard.guarded(downstream);
            int round = Math.max(1, attempt + 1);
            try {
                WriterAgent.WriteResult result;
                if (review == null) {
                    result = writerAgent.write(context, sink);
                } else {
                    result = writerAgent.rewrite(context, draft, review, round, sink);
                }
                if (guard != null) {
                    guard.flush(downstream); // 尾巴补齐（跨包窗口留下的未下发字符）
                }
                if (isRetry) {
                    // 重试成功：REVISION 事件重置客户端缓冲（抹去失败尝试的部分内容）后下发完整产出
                    emitter.emitStage(StageEvent.revision(StageCode.WRITING, attempt + 1));
                    emitter.emitContent(result.text());
                }
                return result;
            } catch (StreamSensitiveHitException hit) {
                if (streamSafetyGate == null || regenerations >= MAX_STREAM_REGENERATIONS) {
                    if (streamSafetyGate != null) {
                        streamSafetyGate.recordReject(context.getBookId(), context.getChapterId(),
                                hit.hits(), hit.partialText().length());
                    }
                    log.warn("流式中置拦截无法恢复（已重生成 {} 次），任务失败: hits={}",
                            regenerations, FinalDraftGate.describe(hit.hits()));
                    throw hit;
                }
                regenerations++;
                streamSafetyGate.recordRewrite(context.getBookId(), context.getChapterId(),
                        hit.hits(), hit.partialText().length());
                log.warn("流式中置拦截：命中即断，截断重生成（第 {} 次）: hits={}",
                        regenerations, FinalDraftGate.describe(hit.hits()));
                // REVISION 作废客户端已收到的部分（与择优回退/重写轮次同一协议语义，前端无需改动）
                emitter.emitStage(StageEvent.revision(StageCode.WRITING, round));
                // 截断重生成：以已下发正文为原稿 + 与 POST 层同一份"只改表达"的修复指令
                draft = hit.partialText(); // 可能为空串：此时等价于"带避雷清单重写"，仍走同一修订链路
                review = FinalDraftGate.repairReport(hit.hits());
                attempt = 0; // 本次不计入普通失败重试次数
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
        if (artifact == null) {
            // artifact 列为 JSONB NOT NULL：空产物直接跳过，避免约束违反导致的无效落库
            log.debug("阶段产物为空，跳过落库: taskId={}, stage={}, round={}", taskId, stage, round);
            return;
        }
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

    /**
     * 读回最近一次阶段产物的文本字段（F2 第二批：Writer 成稿断点恢复）。
     * 兼容两种 artifact 形态：{"text": "..."}（写作产物）与纯字符串。
     * @return 无产物/结构不符/通道故障时返回 null（调用方回退正常执行——宁可重做）
     */
    private String loadArtifactText(Long taskId, StageCode stage, int round) {
        try {
            CreationStageArtifact record = artifactMapper.selectOne(
                    new LambdaQueryWrapper<CreationStageArtifact>()
                            .eq(CreationStageArtifact::getTaskId, taskId)
                            .eq(CreationStageArtifact::getStage, stage.name())
                            .eq(CreationStageArtifact::getRound, round)
                            .orderByDesc(CreationStageArtifact::getId)
                            .last("LIMIT 1"));
            if (record == null || record.getArtifact() == null) {
                return null;
            }
            Object artifact = record.getArtifact();
            if (artifact instanceof java.util.Map<?, ?> map && map.get("text") != null) {
                return String.valueOf(map.get("text"));
            }
            if (artifact instanceof String text && !text.isBlank()) {
                return text;
            }
            return null;
        } catch (Exception e) {
            log.warn("阶段产物读回失败（按无产物处理，回退重写）: taskId={}, stage={}, error={}",
                    taskId, stage, e.getMessage());
            return null;
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

    /**
     * 运行时机制开关快照（v2.0 设计 7.1：开关状态记进实验数据行）。
     * 部署默认 + 请求级覆盖的合并结果随任务落 creation_task.params——
     * 每条实验数据自带分组标签，避免后期分不清哪条数据是哪组。
     */
    private Map<String, Object> featureSnapshot(PipelineRequest request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mode", request.getMode().name());
        snapshot.put("maxRewriteRounds", request.getMaxRewriteRounds());
        snapshot.put("features", pipelineFeatures.merged(request.getFeatures()));
        return snapshot;
    }

    /** 闭环产出：终稿 + 是否降级 */
    private record DraftOutcome(String draft, boolean degraded, int adoptedRound, boolean rolledBack) {
    }

    /** Planner 阶段产出：大纲 + 是否降级 */
    private record PlanOutcome(ChapterOutline outline, boolean degraded) {
    }
}
