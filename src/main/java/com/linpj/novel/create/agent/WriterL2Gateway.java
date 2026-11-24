package com.linpj.novel.create.agent;

import com.linpj.novel.create.agent.runtime.AgentFence;
import com.linpj.novel.create.agent.runtime.AgentRuntime;
import com.linpj.novel.create.agent.runtime.AgentResult;
import com.linpj.novel.create.agent.runtime.AgentTask;
import com.linpj.novel.create.agent.runtime.DeliverableContract;
import com.linpj.novel.create.agent.tool.CanonQueryTool;
import com.linpj.novel.create.agent.tool.EvidenceCodec;
import com.linpj.novel.create.agent.tool.ProposalEmitTool;
import com.linpj.novel.create.agent.tool.SummaryGetTool;
import com.linpj.novel.create.config.AutonomyProperties;
import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.WriterContext;
import com.linpj.novel.create.utils.JsonUtil;
import com.linpj.novel.create.worldstate.CitationLinkService;
import com.linpj.novel.create.worldstate.ToolReceiptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writer L2 网关（自主性改造 R2a：§3——首写位试点，绞杀者单点包装）
 *
 * <p>把 WriterAgent 的"单次生成调用"升级为 AgentRuntime 有界循环：
 * 按场景节拍起草 → 自主补查角色态/前文（canon.query/summary.get）→
 * 暂存正典提案（proposal.emit）→ 收口。产出与 L1 同构
 * （WriterAgent.WriteResult{text, tokens}），编排器消费零改动。
 *
 * <p>与 CriticL2Gateway 的结构差异：
 * <ul>
 *   <li><b>主模型非异源</b>：Writer 走主模型（ChatClient 自建 + WRITER-L2 录制）；
 *       <b>不挂 chatMemory 与 RAG advisor</b>——L2 循环的消息序列必须是纯函数可重放
 *       （记忆/检索 advisor 在消息前插入动态历史会破坏 kill-resume 确定性），
 *       上下文全由 taskPrompt 静态注入 + 工具动态取证（这正是 L1/L2 的消融变量差）；</li>
 *   <li><b>正文非流式</b>：finish 一次性交付（无 STREAM 层），终稿安全走既有
 *       POST gate（gateBeforeFinalize 消费点不变）；</li>
 *   <li><b>claims[] 校验</b>（R2a 落地）：finish 的 claims[].evidenceId 逐条
 *       确定性校验 + 引文账落账（空数组不强制——Writer 的引文对象是前文证据）；</li>
 *   <li><b>提案合规</b>：proposal.emit 只暂存（PENDING），提交门消费 R3 接入。</li>
 * </ul>
 *
 * @author HL
 */
@Service
public class WriterL2Gateway {

    private static final Logger log = LoggerFactory.getLogger(WriterL2Gateway.class);

    /** Writer L2 墙钟（多步起草+取证，对齐单稿 180s 放宽） */
    private static final long WRITER_L2_WALL_CLOCK_MS = 300_000L;

    /** 正文长度下限（结构校验用；S 系实验按 targetWordCount 比例校准） */
    static final int MIN_DRAFT_LENGTH = 200;

    private final ChatClient chatClient;
    private final AgentRuntime agentRuntime;
    private final AutonomyProperties autonomyProperties;
    private final WriterAgent writerAgent;
    private final ToolReceiptService receiptService;
    private final CitationLinkService citationLinkService;

    @Autowired
    public WriterL2Gateway(ChatModel chatModel,
                           ModelCallLog modelCallLog,
                           AgentRuntime agentRuntime,
                           AutonomyProperties autonomyProperties,
                           WriterAgent writerAgent,
                           ToolReceiptService receiptService,
                           CitationLinkService citationLinkService) {
        this.agentRuntime = agentRuntime;
        this.autonomyProperties = autonomyProperties;
        this.writerAgent = writerAgent;
        this.receiptService = receiptService;
        this.citationLinkService = citationLinkService;
        // 主模型 + WRITER-L2 录制；不挂记忆/RAG（确定性 javadoc 语义）
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new ModelCallRecorder("WRITER-L2", modelCallLog))
                .build();
    }

    /** 测试装配：直传预制 ChatClient（回放桩） */
    WriterL2Gateway(ChatClient chatClient, AgentRuntime agentRuntime,
                    AutonomyProperties autonomyProperties, WriterAgent writerAgent,
                    ToolReceiptService receiptService, CitationLinkService citationLinkService) {
        this.chatClient = chatClient;
        this.agentRuntime = agentRuntime;
        this.autonomyProperties = autonomyProperties;
        this.writerAgent = writerAgent;
        this.receiptService = receiptService;
        this.citationLinkService = citationLinkService;
    }

    /**
     * L2 首写入口（writeFirstDraft 的 L2 分支；降级链内建）。
     *
     * @param nodeId WRITING:1（首写位——重写轮次与 PRECISE 双稿保持 L1，绞杀者逐步）
     */
    public WriterAgent.WriteResult write(PipelineRequest request, WriterContext context,
                                         Long runId, String nodeId,
                                         com.linpj.novel.create.pipeline.ProgressEmitter emitter) {
        AutonomyProperties.NodeSetting setting =
                autonomyProperties.resolve(request.getMode(), AutonomyProperties.NODE_WRITER);
        int maxSteps = Math.max(1, setting.getMaxSteps());
        long tokenBudget = Math.max(0, setting.getTokenBudget());

        AgentTask task = new AgentTask(runId, nodeId,
                AgentFence.of(maxSteps, tokenBudget, WRITER_L2_WALL_CLOCK_MS,
                        Set.of(CanonQueryTool.NAME, SummaryGetTool.NAME, ProposalEmitTool.NAME)),
                systemPrompt(), taskPrompt(request, context), chatClient,
                writerContract(runId, nodeId),
                // 预算型降级：L1 单次生成兑底（L2 工具成果不注入——消融基线隔离；
                // 正文一次性下发，重试语义简化为单次——L2 已烧预算，兑底从简）
                () -> writerAgent.write(context, sinkOf(emitter)),
                toolContext(request),
                emitter);

        AgentResult result = agentRuntime.run(task);
        return consume(result, emitter);
    }

    /** L2 终态 → WriteResult 同构；Degraded/未知/交付缺失返回 null（分发处安全网回 L1） */
    private WriterAgent.WriteResult consume(AgentResult result,
                                            com.linpj.novel.create.pipeline.ProgressEmitter emitter) {
        if (result instanceof AgentResult.Success success) {
            String text = extractText(success.deliverableJson());
            if (text != null && !text.isBlank()) {
                if (emitter != null) {
                    // L1 流式语义对齐：终稿一次性下发（无 STREAM 层——POST gate 兕底安全）
                    emitter.emitContent(text);
                }
                log.info("Writer L2 收口: steps={}, tokens={}", success.stepsExecuted(), success.tokensSpent());
                return new WriterAgent.WriteResult(text, success.tokensSpent());
            }
            log.warn("Writer L2 交付正文缺失（返 null 走分发处安全网）");
            return null;
        }
        if (result instanceof AgentResult.FallbackL1 fallback) {
            Object l1 = fallback.l1Result();
            if (l1 instanceof WriterAgent.WriteResult writeResult) {
                log.info("Writer L2 预算型耗尽（{}），L1 兜底产出", fallback.reason());
                return writeResult;
            }
            log.warn("Writer L1 兜底返回异常（{}）——返 null 走分发处安全网", fallback.reason());
            return null;
        }
        if (result instanceof AgentResult.Degraded degraded) {
            // 契约型失败：不烧 L1 同材料（11.1-M1b）；返 null 由分发处安全网回 L1 重试链
            log.warn("Writer L2 契约型失败（返 null 走安全网）: {}", degraded.reason());
            return null;
        }
        log.warn("Writer L2 未知终态（返 null 走安全网）");
        return null;
    }

    private static java.util.function.Consumer<String> sinkOf(
            com.linpj.novel.create.pipeline.ProgressEmitter emitter) {
        return emitter == null ? s -> { } : emitter::emitContent;
    }

    // ------------------------------------------------------------------
    // 契约与提示词
    // ------------------------------------------------------------------
    // 契约与提示词
    // ------------------------------------------------------------------

    /** Writer 收口契约（实例：闭包捕获 run/node——线程安全，对齐 CriticL2Gateway 模式）：正文 + 交付汇报 */
    DeliverableContract writerContract(Long runId, String nodeId) {
        return new DeliverableContract() {
            @Override
            public String finishSchema() {
                return """
                        {"type":"object","properties":{
                          "text":{"type":"string","description":"本章正文全文（按大纲场景完整成稿）"},
                          "report":{"type":"object","properties":{
                            "written":{"type":"string","description":"写了什么（场景覆盖概述）"},
                            "proposalsStored":{"type":"array","items":{"type":"string"},"description":"已暂存提案的 proposalId 列表"},
                            "remaining":{"type":"string","description":"剩余未决事项（无则空串）"}
                          },"required":["written"]},
                          "claims":{"type":"array","items":{"type":"object","properties":{
                            "text":{"type":"string"},"evidenceId":{"type":"string"}}}}
                        },"required":["text","report"]}""";
            }

            @Override
            public String expectations() {
                return "交付正文全文与交付汇报；正文必须覆盖本章大纲场景；正文长度不少于 "
                        + MIN_DRAFT_LENGTH + " 字符；引用前文事实时在 claims 绑定 evidenceId";
            }

            @Override
            public ContractCheck validate(String deliverableJson) {
                Map<String, Object> deliverable = parseMap(deliverableJson);
                if (deliverable == null) {
                    return ContractCheck.fail("deliverable 不是合法 JSON");
                }
                String missingKey = missingKeys(deliverable, "text", "report");
                if (missingKey != null) {
                    return ContractCheck.fail("缺少必填字段 " + missingKey);
                }
                String text = String.valueOf(deliverable.get("text"));
                if (text.isBlank()) {
                    return ContractCheck.fail("正文为空");
                }
                if (text.strip().length() < MIN_DRAFT_LENGTH) {
                    return ContractCheck.fail("正文过短（" + text.strip().length() + " < "
                            + MIN_DRAFT_LENGTH + " 字符，S 系实验按 targetWordCount 校准）");
                }
                Object report = deliverable.get("report");
                if (!(report instanceof Map<?, ?> reportMap) || reportMap.get("written") == null) {
                    return ContractCheck.fail("交付汇报缺 written");
                }
                return ContractCheck.passed();
            }

            @Override
            public CitationOutcome verifyCitations(String finishArgumentsJson, int stepNo) {
                return verifyClaimsCitations(runId, nodeId, finishArgumentsJson, stepNo);
            }
        };
    }

    /** claims[] 引文校验（R2a）：逐条确定性校验 + 引文账落账；空数组不强制（Writer 引文对象是前文证据） */
    private DeliverableContract.CitationOutcome verifyClaimsCitations(Long runId, String nodeId,
                                                                      String finishArgumentsJson, int stepNo) {
        try {
            Map<String, Object> args = parseMap(finishArgumentsJson);
            if (args == null || !(args.get("claims") instanceof List<?> claims)) {
                return DeliverableContract.CitationOutcome.pass(); // claims 可选
            }
            int total = 0;
            int valid = 0;
            List<String> violations = new ArrayList<>();
            for (Object claimObj : claims) {
                if (!(claimObj instanceof Map<?, ?> claim)) {
                    continue;
                }
                String evidenceId = claim.get("evidenceId") == null
                        ? null : String.valueOf(claim.get("evidenceId"));
                if (evidenceId == null || evidenceId.isBlank()) {
                    continue; // 未绑定不强制（与 Critic HIGH 必绑的差异语义）
                }
                String claimText = claim.get("text") == null ? null : String.valueOf(claim.get("text"));
                total++;
                long[] parts = EvidenceCodec.parse(evidenceId);
                if (parts == null) {
                    violations.add("evidenceId 格式非法: " + evidenceId);
                    recordCitation(runId, nodeId, stepNo, claimText, evidenceId, -1, -1, false, "格式非法");
                    continue;
                }
                NovelToolReceipt receipt = loadReceiptQuietly(parts[0]);
                EvidenceCodec.EvidenceCheck check = EvidenceCodec.verify(evidenceId, claimText, receipt);
                recordCitation(runId, nodeId, stepNo, claimText, evidenceId, parts[0], (int) parts[1],
                        check.ok(), check.violations());
                if (check.ok()) {
                    valid++;
                } else {
                    violations.add(check.violations());
                }
            }
            if (violations.isEmpty()) {
                return new DeliverableContract.CitationOutcome(true, null, total, valid);
            }
            return new DeliverableContract.CitationOutcome(false, String.join("；", violations), total, valid);
        } catch (Exception e) {
            return new DeliverableContract.CitationOutcome(false, "引文校验通道故障: " + e.getMessage(), 0, 0);
        }
    }

    private NovelToolReceipt loadReceiptQuietly(long receiptId) {
        try {
            return receiptService.getById(receiptId);
        } catch (Exception e) {
            return null;
        }
    }

    private void recordCitation(Long runId, String nodeId, int stepNo, String claimText,
                                String evidenceId, long receiptId, int itemIndex,
                                boolean ok, String violations) {
        try {
            citationLinkService.record(runId, nodeId, stepNo, claimText,
                    evidenceId, receiptId, Math.max(1, itemIndex), ok, violations);
        } catch (Exception e) {
            // 服务内已静默容错
        }
    }

    // ------------------------------------------------------------------
    // 提示词
    // ------------------------------------------------------------------

    static String systemPrompt() {
        return """
                你是小说正文写作 Agent。按本章大纲的场景节拍完成正文，并在写作过程中主动查证需要的正典事实。

                【写作约束】
                1. 正文必须覆盖本章大纲的全部场景（目标·阻力·选择·状态变化）。
                2. 涉及角色状态/前文事实时，先调用 canon.query / summary.get 查证再落笔——不得凭空虚构正典事实。
                3. 写作中产生的新状态变更（伤势变化/位置转移等）用 proposal.emit 暂存提案（不会立即入正典）。
                4. 正文一次性完整交付（finish 的 text 字段），不要分段。
                5. 引用前文事实时在 claims 绑定 evidenceId（工具回执中的证据地址）。
                """;
    }

    /** 任务材料：与 L1 assembleWriterContext 同源材料（差异：检索交给工具——消融变量） */
    static String taskPrompt(PipelineRequest request, WriterContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请完成本章正文写作。\n\n");
        if (context.getOutline() != null) {
            prompt.append("【本章大纲】\n章节标题：")
                    .append(WriterAgent.nullSafe(context.getOutline().getChapterTitle(), "待定")).append("\n");
            if (context.getOutline().getScenes() != null) {
                context.getOutline().getScenes().forEach(scene ->
                        prompt.append("- 场景：").append(WriterAgent.nullSafe(scene.getLocation(), "待定"))
                                .append("；冲突：").append(WriterAgent.nullSafe(scene.getConflict(), "无"))
                                .append("；预期结果：").append(WriterAgent.nullSafe(scene.getOutcome(), "待定")).append("\n"));
            }
            prompt.append("\n");
        }
        if (WriterAgent.notBlank(context.getWorldSetting())) {
            prompt.append("【世界观设定】\n").append(context.getWorldSetting()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getCharacterInfo())) {
            prompt.append("【人物设定与当前状态】\n").append(context.getCharacterInfo()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getStoryDigest())) {
            prompt.append("【前情摘要】\n").append(context.getStoryDigest()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getActivePlotThreads())) {
            prompt.append("【活跃伏笔账本】\n").append(context.getActivePlotThreads()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getStyle())) {
            prompt.append("【文风要求】\n").append(context.getStyle()).append("\n\n");
        }
        if (WriterAgent.notBlank(request.getUserInput())) {
            prompt.append("【用户创作意图】\n").append(request.getUserInput()).append("\n\n");
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

    /** deliverable JSON → text 字段（Gson 树导航） */
    static String extractText(String deliverableJson) {
        Map<String, Object> deliverable = parseMap(deliverableJson);
        return deliverable == null ? null : String.valueOf(deliverable.getOrDefault("text", null));
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

    static String missingKeys(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (!map.containsKey(key)) {
                return key;
            }
        }
        return null;
    }
}
