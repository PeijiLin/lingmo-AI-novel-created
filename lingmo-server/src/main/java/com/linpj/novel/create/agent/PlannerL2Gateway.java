package com.linpj.novel.create.agent;

import com.linpj.novel.create.agent.runtime.AgentFence;
import com.linpj.novel.create.agent.runtime.AgentResult;
import com.linpj.novel.create.agent.runtime.AgentRuntime;
import com.linpj.novel.create.agent.runtime.AgentTask;
import com.linpj.novel.create.agent.runtime.DeliverableContract;
import com.linpj.novel.create.agent.tool.CanonQueryTool;
import com.linpj.novel.create.agent.tool.RuleCheckTool;
import com.linpj.novel.create.agent.tool.SummaryGetTool;
import com.linpj.novel.create.config.AutonomyProperties;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.PlannerContext;
import com.linpj.novel.create.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Planner L2 网关（自主性改造 R2b：§3——大纲规划位试点，绞杀者单点包装）
 *
 * <p>把 PlannerAgent 的"单次大纲调用"升级为 AgentRuntime 有界循环：
 * 大纲草案 → rulecheck.run 验可行性（确定性零 LLM）→ 自主修订 → 再验 → 收口。
 * 产出与 L1 同构（PlannerAgent.PlanResult{outline, tokens}），编排器消费零改动。
 *
 * <p>结构差异（对齐 Writer 网关）：主模型 + PLANNER-L2 录制；不挂记忆/RAG
 * （序列确定性——kill-resume 重放约束）；<b>rulecheck.run 是唯一"验证工具"</b>
 * ——大纲的结构可行性不靠模型自评，靠确定性规则回执（判定确定化原则的节点内延伸）。
 *
 * @author HL
 */
@Service
public class PlannerL2Gateway {

    private static final Logger log = LoggerFactory.getLogger(PlannerL2Gateway.class);

    /** Planner L2 墙钟（多轮草案-验-修，对齐单次大纲 90s 放宽） */
    private static final long PLANNER_L2_WALL_CLOCK_MS = 240_000L;

    private final ChatClient chatClient;
    private final AgentRuntime agentRuntime;
    private final AutonomyProperties autonomyProperties;
    private final PlannerAgent plannerAgent;
    private final com.linpj.novel.create.worldstate.PendingAssertionService pendingAssertionService;

    @Autowired
    public PlannerL2Gateway(ChatModel chatModel,
                            ModelCallLog modelCallLog,
                            AgentRuntime agentRuntime,
                            AutonomyProperties autonomyProperties,
                            PlannerAgent plannerAgent,
                            com.linpj.novel.create.worldstate.PendingAssertionService pendingAssertionService) {
        this.agentRuntime = agentRuntime;
        this.autonomyProperties = autonomyProperties;
        this.plannerAgent = plannerAgent;
        this.pendingAssertionService = pendingAssertionService;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new ModelCallRecorder("PLANNER-L2", modelCallLog))
                .build();
    }

    /** 测试装配：直传预制 ChatClient（回放桩）与人审账依赖 */
    PlannerL2Gateway(ChatClient chatClient, AgentRuntime agentRuntime,
                     AutonomyProperties autonomyProperties, PlannerAgent plannerAgent,
                     com.linpj.novel.create.worldstate.PendingAssertionService pendingAssertionService) {
        this.chatClient = chatClient;
        this.agentRuntime = agentRuntime;
        this.autonomyProperties = autonomyProperties;
        this.plannerAgent = plannerAgent;
        this.pendingAssertionService = pendingAssertionService;
    }

    /**
     * L2 规划入口（plan 阶段的 L2 分支；降级链内建）。
     *
     * @return PlanResult 同构；降级/异常返 null（分发处安全网回 L1）
     */
    public PlannerAgent.PlanResult plan(PipelineRequest request, PlannerContext context,
                                        Long runId, String nodeId,
                                        com.linpj.novel.create.pipeline.ProgressEmitter emitter) {
        AutonomyProperties.NodeSetting setting =
                autonomyProperties.resolve(request.getMode(), AutonomyProperties.NODE_PLANNER);
        int maxSteps = Math.max(1, setting.getMaxSteps());
        long tokenBudget = Math.max(0, setting.getTokenBudget());

        AgentTask task = new AgentTask(runId, nodeId,
                AgentFence.of(maxSteps, tokenBudget, PLANNER_L2_WALL_CLOCK_MS,
                        Set.of(CanonQueryTool.NAME, SummaryGetTool.NAME, RuleCheckTool.NAME)),
                systemPrompt(), taskPrompt(context), chatClient,
                outlineContract(),
                // 预算型降级：L1 单次大纲兜底（L2 工具成果不注入——消融基线隔离）
                () -> plannerAgent.plan(context),
                toolContext(request),
                emitter);

        AgentResult result = agentRuntime.run(task);
        return consume(result);
    }

    /** L2 终态 → PlanResult 同构；非成功态返 null（分发处安全网回 L1） */
    private PlannerAgent.PlanResult consume(AgentResult result) {
        if (result instanceof AgentResult.Success success) {
            ChapterOutline outline = parseOutline(success.deliverableJson());
            if (outline != null && outline.getScenes() != null && !outline.getScenes().isEmpty()) {
                log.info("Planner L2 收口: steps={}, tokens={}", success.stepsExecuted(), success.tokensSpent());
                return new PlannerAgent.PlanResult(outline, success.tokensSpent());
            }
            log.warn("Planner L2 交付大纲缺失（返 null 走安全网）");
            return null;
        }
        if (result instanceof AgentResult.FallbackL1 fallback) {
            Object l1 = fallback.l1Result();
            if (l1 instanceof PlannerAgent.PlanResult planResult) {
                log.info("Planner L2 预算型耗尽（{}），L1 兜底产出", fallback.reason());
                return planResult;
            }
            log.warn("Planner L1 兜底返回异常（{}）", fallback.reason());
            return null;
        }
        if (result instanceof AgentResult.Degraded degraded) {
            log.warn("Planner L2 契约型失败（返 null 走安全网）: {}", degraded.reason());
            return null;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 契约与提示词
    // ------------------------------------------------------------------

    /** 大纲契约（方案 §3：大纲+场景任务[目标·阻力·选择·状态变化]）——ChapterOutline 同构 */
    DeliverableContract outlineContract() {
        return new DeliverableContract() {
            @Override
            public String finishSchema() {
                return """
                        {"type":"object","properties":{
                          "chapterTitle":{"type":"string"},
                          "scenes":{"type":"array","minItems":1,"items":{"type":"object","properties":{
                            "location":{"type":"string"},
                            "conflict":{"type":"string","description":"阻力"},
                            "outcome":{"type":"string","description":"状态变化（不可逆推进）"},
                            "narrativeMode":{"type":"string"}
                          },"required":["location","conflict","outcome"]}}
                        },"required":["chapterTitle","scenes"]}""";
            }

            @Override
            public String expectations() {
                return "交付本章大纲（chapterTitle + scenes）；每场景必填 location/conflict/outcome"
                        + "（目标·阻力·状态变化）；rulecheck.run 有 blocking 违规时必须修订后再收口";
            }

            @Override
            public ContractCheck validate(String deliverableJson) {
                Map<String, Object> deliverable = parseMap(deliverableJson);
                if (deliverable == null) {
                    return ContractCheck.fail("deliverable 不是合法 JSON");
                }
                if (!deliverable.containsKey("chapterTitle")) {
                    return ContractCheck.fail("缺少 chapterTitle");
                }
                Object title = deliverable.get("chapterTitle");
                if (title == null || String.valueOf(title).isBlank()) {
                    return ContractCheck.fail("chapterTitle 为空");
                }
                if (!(deliverable.get("scenes") instanceof List<?> scenes) || scenes.isEmpty()) {
                    return ContractCheck.fail("scenes 缺失或为空（至少一个场景）");
                }
                for (Object sceneObj : scenes) {
                    if (!(sceneObj instanceof Map<?, ?> scene)) {
                        return ContractCheck.fail("场景不是对象结构");
                    }
                    for (String key : new String[]{"location", "conflict", "outcome"}) {
                        Object value = scene.get(key);
                        if (value == null || String.valueOf(value).isBlank()) {
                            return ContractCheck.fail("场景缺 " + key + "（目标·阻力·状态变化三要素）");
                        }
                    }
                }
                return ContractCheck.passed();
            }
        };
    }

    static String systemPrompt() {
        return """
                你是小说大纲规划 Agent。产出本章大纲（chapterTitle + scenes），并主动验证其可行性。

                【规划约束】
                1. 每场景必填 location / conflict（阻力）/ outcome（状态变化，不可逆推进）。
                2. 产出大纲草案后调用 rulecheck.run 验证（确定性规则）：有 blocking 违规必须修订大纲再验，直至通过。
                3. 需要角色/前文/时间轴事实时调用 canon.query / summary.get 查证，不得凭空虚构。
                4. rulecheck 通过且场景完整后调用 agent_finish 收口。
                """;
    }

    /** 任务材料：与 L1 PlannerContext 同源（差异：验证靠工具——判定确定化） */
    static String taskPrompt(PlannerContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请规划本章大纲。\n\n");
        if (com.linpj.novel.create.agent.WriterAgent.notBlank(context.getWorldSetting())) {
            prompt.append("【世界观设定】\n").append(context.getWorldSetting()).append("\n\n");
        }
        if (com.linpj.novel.create.agent.WriterAgent.notBlank(context.getCharacterInfo())) {
            prompt.append("【人物设定与当前状态】\n").append(context.getCharacterInfo()).append("\n\n");
        }
        if (com.linpj.novel.create.agent.WriterAgent.notBlank(context.getStoryDigest())) {
            prompt.append("【前情摘要】\n").append(context.getStoryDigest()).append("\n\n");
        }
        if (com.linpj.novel.create.agent.WriterAgent.notBlank(context.getActivePlotThreads())) {
            prompt.append("【活跃伏笔账本】\n").append(context.getActivePlotThreads()).append("\n\n");
        }
        if (com.linpj.novel.create.agent.WriterAgent.notBlank(context.getUserInput())) {
            prompt.append("【用户创作意图】\n").append(context.getUserInput()).append("\n\n");
        }
        return prompt.toString();
    }

    private static Map<String, Object> toolContext(PipelineRequest request) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (request.getBookId() != null) {
            ctx.put("bookId", request.getBookId());
        }
        if (request.getChapterId() != null) {
            ctx.put("chapterId", request.getChapterId());
        }
        return ctx;
    }

    static ChapterOutline parseOutline(String deliverableJson) {
        if (deliverableJson == null || deliverableJson.isBlank()) {
            return null;
        }
        try {
            return JsonUtil.fromJson(deliverableJson, ChapterOutline.class);
        } catch (Exception e) {
            return null;
        }
    }

    static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonUtil.fromJson(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
