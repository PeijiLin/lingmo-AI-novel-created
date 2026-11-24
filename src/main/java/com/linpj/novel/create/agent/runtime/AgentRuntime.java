package com.linpj.novel.create.agent.runtime;

import com.linpj.novel.create.agent.WriterAgent;
import com.linpj.novel.create.agent.tool.AgentToolContext;
import com.linpj.novel.create.agent.tool.ToolRegistry;
import com.linpj.novel.create.pojo.entity.NovelAgentStep;
import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import com.linpj.novel.create.pojo.pipeline.AgentEvent;
import com.linpj.novel.create.worldstate.AgentStepService;
import com.linpj.novel.create.worldstate.ToolReceiptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AgentRuntime 步骤循环引擎（自主性改造 R1a：§12.1/§12.4/§12.6）
 *
 * <p><b>节点无关</b>的通用循环：节点语义由 {@link AgentTask} 注入（材料/契约/降级回调），
 * R2 复用到 Writer/Planner 时本类零改动。
 *
 * <p>循环机制归代码、步骤决策归模型（§1）：每步一次 ChatClient 调用（框架自动工具执行
 * 禁用 internalToolExecutionEnabled(false)——工具执行仅经 ToolRegistry 唯一通道），
 * 模型输出的工具调用（含 finish 元工具出口）由代码执行、回执落盘、喂回下一步。
 *
 * <p>步骤循环（THINK→ACT→OBSERVE）出口：
 * <ul>
 *   <li><b>finish 单独调用</b>→契约校验→SUCCESS；校验败给<b>一次修复步</b>，仍败契约型
 *       DEGRADED（跳过 L1，11.1-M1b）；</li>
 *   <li><b>finish 混合调用</b>（同步骤含其他工具）→ finish 顺延（回执告知模型基于工具结果
 *       再确认收口——防未见证回执的假收口），本步骤按工具步正常落账；</li>
 *   <li><b>预算型耗尽</b>（步数/token/墙钟/连败/复读）→ L1Fallback 兜底；</li>
 *   <li>调用异常不落账不占预算只计连败；纯文本落 MALFORMED 占位（序列完整性）。</li>
 * </ul>
 *
 * <p>kill-resume：enter 时从步账重放 DONE 历史（AgentMessages 纯函数，回执不重调不计费），
 * stepNo 接续账本最大值；RUNNING 残留被同 stepNo 覆盖重做（R0 upsert 语义）。
 *
 * <p>步数口径（对修订案纪律 1 的 R1a 实施修订，§12.4 附注）：stepNo=模型调用序号，
 * MALFORMED 步同样占 maxSteps 预算——调用即成本，且占位保证序列重放完整性；
 * 调用异常不落账不占预算，仅计连败（连败熔断兜底）。
 *
 * @author HL
 */
@Service
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    static final String KIND_TOOL_CALL = "TOOL_CALL";
    static final String KIND_TEXT = "TEXT";
    static final String KIND_FINISH = "FINISH";

    private final AgentStepService stepService;
    private final ToolReceiptService receiptService;
    private final ToolRegistry toolRegistry;

    /**
     * 生产装配。ChatClient 不在此注入——按节点差异化路由（Critic L2 异源模型 /
     * Writer L2 主模型），由节点侧随 AgentTask 携带（task.chatClient()）。
     */
    @Autowired
    public AgentRuntime(AgentStepService stepService,
                        ToolReceiptService receiptService,
                        ToolRegistry toolRegistry) {
        this.stepService = stepService;
        this.receiptService = receiptService;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行一次 L2 有界 Agent 运行（同 run/node 幂等续跑——从账本接续）。
     * ChatClient 由节点侧随 task 携带（异源模型/录制 advisor 挂载点归节点）。
     */
    public AgentResult run(AgentTask task) {
        if (task == null || task.runId() == null || task.nodeId() == null
                || task.fence() == null || task.contract() == null || task.chatClient() == null) {
            return new AgentResult.Degraded("invalid-task", 0, 0);
        }
        try {
            return runLoop(task);
        } catch (Exception e) {
            // 未归档异常：转 DEGRADED（节点侧按各自语义消费）；L2 已烧 token 由账本如实记录
            log.warn("AgentRuntime 未归档异常（转 DEGRADED）: run={}, node={}, error={}",
                    task.runId(), task.nodeId(), e.getMessage());
            long spent = stepService.tokensUsed(task.runId(), task.nodeId());
            return new AgentResult.Degraded("runtime-error: " + e.getMessage(), spent, 0);
        }
    }

    private AgentResult runLoop(AgentTask task) {
        List<NovelAgentStep> history = stepService.stepsOf(task.runId(), task.nodeId());
        long tokensUsed = sumTokens(history);
        int nextStep = history.isEmpty() ? 1 : maxStepNo(history) + 1;
        long startAt = System.currentTimeMillis();
        int consecutiveFailures = 0;
        int signatureRepeat = 0;
        String lastSignature = null;
        boolean repairUsed = false;

        while (true) {
            int executedSteps = nextStep - 1;
            AgentFence fence = task.fence();

            // ---- 前置围栏检查（顺序即优先级，首个命中即出：预算型） ----
            String budgetReason = firstBudgetExhaustion(fence, executedSteps, tokensUsed,
                    System.currentTimeMillis() - startAt, consecutiveFailures, signatureRepeat);
            if (budgetReason != null) {
                return degradeByBudget(task, budgetReason, tokensUsed, executedSteps);
            }

            int stepNo = nextStep;
            List<Message> messages = AgentMessages.build(task, history, this::loadReceipt, stepNo);

            // ---- 模型调用（自动工具执行禁用——执行权归 Registry） ----
            emitStep(task, stepNo, "RUNNING");
            ChatResponse response;
            try {
                response = task.chatClient().prompt()
                        .messages(messages)
                        .toolCallbacks(callbacksFor(task))
                        .options(ToolCallingChatOptions.builder()
                                .internalToolExecutionEnabled(false)
                                .build())
                        .call()
                        .chatResponse();
                if (response == null) {
                    throw new IllegalStateException("chatResponse 为 null");
                }
            } catch (Exception e) {
                // 调用异常：不落账、不占步预算（重做语义），计连败
                log.warn("模型调用异常（计连败，不落账）: run={}, node={}, step={}, error={}",
                        task.runId(), task.nodeId(), stepNo, e.getMessage());
                consecutiveFailures++;
                continue;
            }

            long stepTokens = Math.max(0, tokensOf(response));
            tokensUsed += stepTokens;
            AssistantMessage output = response.getResult() == null ? null : response.getResult().getOutput();

            // ---- 纯文本响应（决策缺失）：MALFORMED 占位（序列完整性）+ 计连败 ----
            if (output == null || !output.hasToolCalls()) {
                String text = output == null || output.getText() == null ? "" : output.getText();
                NovelAgentStep malformed = recordStep(task, stepNo, KIND_TEXT,
                        Map.of("text", text), null, "MALFORMED", stepTokens, "no-tool-call");
                if (malformed != null) {
                    history.add(malformed);
                }
                consecutiveFailures++;
                signatureRepeat = 0; // 纯文本复读由连败熔断兜底（连续纯文本即连续失败）
                lastSignature = "<text:" + Math.abs(text.hashCode()) + ">";
                nextStep++;
                continue;
            }

            List<AssistantMessage.ToolCall> calls = output.getToolCalls();

            // ---- 决策签名（防复读：相邻步同签名计数） ----
            String signature = AgentProtocol.signatureOf(calls);
            signatureRepeat = signature.equals(lastSignature) ? signatureRepeat + 1 : 0;
            lastSignature = signature;

            // ---- 逐调用执行 ----
            Map<String, Object> ledgerDecision = new LinkedHashMap<>();
            List<Map<String, Object>> callMetas = new ArrayList<>();
            List<Map<String, Object>> receiptMetas = new ArrayList<>();
            boolean allRejected = true;
            AgentResult finishResult = null;
            boolean repairBreak = false;

            for (AssistantMessage.ToolCall call : calls) {
                if (AgentProtocol.FINISH_TOOL.equals(call.name()) && calls.size() == 1) {
                    // ---- 单独 finish：真收口（契约校验 + 修复步一次；返回非 null 即终态） ----
                    AgentResult outcome = handleFinish(task, stepNo, stepTokens, tokensUsed,
                            executedSteps, repairUsed, call);
                    if (outcome == null) {
                        repairUsed = true;
                        nextStep++;
                        repairBreak = true;
                    } else {
                        finishResult = outcome;
                    }
                    break;
                }
                if (AgentProtocol.FINISH_TOOL.equals(call.name())) {
                    // ---- 混合调用中的 finish：顺延（不基于未见证的回执收口） ----
                    // 仍回 tool 响应（协议要求逐调用应答），响文本为固定模板（重放由内联 result 重建）
                    String deferredCallId = nvl(call.id(), "call_agent_finish");
                    callMetas.add(Map.of(
                            "id", deferredCallId,
                            "name", AgentProtocol.FINISH_TOOL,
                            "args", nvl(call.arguments(), "{}")));
                    Map<String, Object> deferredMeta = new LinkedHashMap<>();
                    deferredMeta.put("callId", deferredCallId);
                    deferredMeta.put("name", AgentProtocol.FINISH_TOOL);
                    deferredMeta.put("result", AgentProtocol.finishDeferredHint());
                    deferredMeta.put("ok", Boolean.TRUE);
                    receiptMetas.add(deferredMeta);
                    continue;
                }
                // ---- 真实工具：Registry 唯一通道 ----
                emitToolCall(task, stepNo, call);
                ToolRegistry.ToolExecution execution = toolRegistry.execute(
                        call.name(), call.arguments(),
                        AgentToolContext.of(task.runId(), task.nodeId(), task.toolContext()),
                        receiptService);
                if (execution.ok()) {
                    allRejected = false;
                } else {
                    // 全拒步计连败（连续拒绝≈失效决策）
                }
                String callId = nvl(call.id(), "call_" + call.name());
                callMetas.add(Map.of(
                        "id", callId,
                        "name", call.name(),
                        "args", nvl(call.arguments(), "{}")));
                Map<String, Object> receiptMeta = new LinkedHashMap<>();
                receiptMeta.put("callId", callId);
                receiptMeta.put("name", call.name());
                receiptMeta.put("ok", execution.ok());
                if (execution.resultText() != null) {
                    receiptMeta.put("result", truncate(execution.resultText(), 400));
                }
                if (execution.receiptId() != null) {
                    receiptMeta.put("receiptId", execution.receiptId());
                }
                receiptMetas.add(receiptMeta);
                emitToolResult(task, stepNo, execution, call);
            }

            // ---- finish 终态 / 修复步分叉 ----
            if (finishResult != null) {
                return finishResult;
            }
            if (repairBreak) {
                continue; // 修复步：消息由下一轮 build 从 FAILED 账行推导 repairHint，本步不落工具账
            }

            // ---- 工具步落账（DONE；decision.calls+receipts=重放依据，receipt 优先回执表、
            //      无 receiptId（deferred/拒绝内联）时用内联 result） ----
            ledgerDecision.put("calls", callMetas);
            ledgerDecision.put("receipts", receiptMetas);
            Long firstReceiptId = receiptMetas.stream()
                    .map(m -> m.get("receiptId"))
                    .filter(v -> v instanceof Number)
                    .map(v -> ((Number) v).longValue())
                    .findFirst().orElse(null);
            NovelAgentStep done = recordStep(task, stepNo, KIND_TOOL_CALL,
                    ledgerDecision, firstReceiptId, "DONE", stepTokens, null);
            if (done != null) {
                history.add(done);
            }
            consecutiveFailures = allRejected ? consecutiveFailures + 1 : 0;
            nextStep++;
        }
    }

    /**
     * 单独 finish 的收口处理：结构校验 → 引文校验（R1b）→ 修复步一次/契约失败。
     *
     * @return SUCCESS/Degraded 终态；null = 进入修复步（重建消息由下一轮 build 从 FAILED 账行推导）
     */
    private AgentResult handleFinish(AgentTask task, int stepNo, long stepTokens, long tokensUsed,
                                     int executedSteps, boolean repairUsed, AssistantMessage.ToolCall call) {
        String deliverableJson = AgentProtocol.extractDeliverable(call.arguments());
        DeliverableContract.ContractCheck check = deliverableJson == null
                ? DeliverableContract.ContractCheck.fail("finish 参数缺失 deliverable")
                : task.contract().validate(deliverableJson);
        if (check.ok()) {
            // R1b 引文硬校验：结构过了还要引文可回验（evidenceId 存在性+界内+文本一致）
            DeliverableContract.CitationOutcome citations =
                    task.contract().verifyCitations(call.arguments(), stepNo);
            if (!citations.ok()) {
                check = DeliverableContract.ContractCheck.fail("引文校验未通过: " + citations.violations());
            }
        }
        if (check.ok()) {
            recordStep(task, stepNo, KIND_FINISH,
                    finishDecision(call.arguments(), call.id(), null), null,
                    "DONE", stepTokens, "finish-ok");
            emitFinish(task, stepNo, true, null);
            return new AgentResult.Success(deliverableJson, tokensUsed, executedSteps + 1);
        }
        if (!repairUsed) {
            recordStep(task, stepNo, KIND_FINISH,
                    finishDecision(call.arguments(), call.id(), check.violations()), null,
                    "FAILED", stepTokens, "contract-failed:" + truncate(check.violations(), 120));
            log.info("收口校验未通过，进入唯一修复步: run={}, node={}, violations={}",
                    task.runId(), task.nodeId(), truncate(check.violations(), 120));
            return null;
        }
        recordStep(task, stepNo, KIND_FINISH,
                finishDecision(call.arguments(), call.id(), check.violations()), null,
                "FAILED", stepTokens, "contract-failed-final");
        emitFinish(task, stepNo, false, check.violations());
        return new AgentResult.Degraded("contract-failed: " + check.violations(),
                tokensUsed, executedSteps + 1);
    }

    // ------------------------------------------------------------------
    // 围栏与降级
    // ------------------------------------------------------------------

    /** 预算型耗尽判定（顺序即优先级；返回 null=未耗尽） */
    private String firstBudgetExhaustion(AgentFence fence, int executedSteps, long tokensUsed,
                                         long elapsedMs, int consecutiveFailures, int signatureRepeat) {
        if (executedSteps >= fence.maxSteps()) {
            return "steps-exhausted:" + executedSteps + "/" + fence.maxSteps();
        }
        if (fence.tokenBudget() > 0 && tokensUsed >= fence.tokenBudget()) {
            return "token-budget:" + tokensUsed + "/" + fence.tokenBudget();
        }
        if (fence.wallClockMs() > 0 && elapsedMs > fence.wallClockMs()) {
            return "wall-clock:" + elapsedMs + "ms/" + fence.wallClockMs() + "ms";
        }
        if (consecutiveFailures >= fence.maxConsecutiveFailures()) {
            return "consecutive-failures:" + consecutiveFailures;
        }
        if (signatureRepeat >= fence.maxRepeatSignature()) {
            return "repeat-signature:" + signatureRepeat;
        }
        return null;
    }

    /** 预算型降级分叉：有 L1 回调走 FallbackL1（L2 成果不注入）；无回调直接 Degraded */
    private AgentResult degradeByBudget(AgentTask task, String reason, long l2Tokens, int steps) {
        log.info("预算型耗尽（{}）: run={}, node={}", reason, task.runId(), task.nodeId());
        emitStep(task, steps + 1, "BUDGET:" + reason);
        if (task.fallback() == null) {
            return new AgentResult.Degraded("budget-exhausted: " + reason, l2Tokens, steps);
        }
        try {
            Object l1Result = task.fallback().callOnce();
            return new AgentResult.FallbackL1(l1Result, reason, l2Tokens);
        } catch (Exception e) {
            log.warn("L1 兜底也失败（转 DEGRADED）: run={}, node={}, error={}",
                    task.runId(), task.nodeId(), e.getMessage());
            return new AgentResult.Degraded("budget-exhausted+l1-failed: " + reason
                    + "; l1-error=" + e.getMessage(), l2Tokens, steps);
        }
    }

    // ------------------------------------------------------------------
    // 账本与事件
    // ------------------------------------------------------------------

    /** 步账落盘（写通道故障返回带的内存行 id=null，调用方容忍——步账尽力而为，终态不因之翻转）。
     *  返回的内存行补齐 decision/receiptId/tokens/note——同 run 内存 history 直接可重放。 */
    private NovelAgentStep recordStep(AgentTask task, int stepNo, String kind, Object decision,
                                      Long receiptId, String status, long tokens, String note) {
        Integer cappedTokens = (int) Math.min(Integer.MAX_VALUE, Math.max(0, tokens));
        Long id = stepService.recordStep(task.runId(), task.nodeId(), stepNo, kind,
                decision, receiptId, status, cappedTokens == null ? 0 : cappedTokens, note);
        NovelAgentStep step = new NovelAgentStep();
        step.setId(id);
        step.setRunId(task.runId());
        step.setNodeId(task.nodeId());
        step.setStepNo(stepNo);
        step.setKind(kind);
        step.setDecision(decision);
        step.setReceiptId(receiptId);
        step.setStatus(status);
        step.setTokens(cappedTokens);
        step.setNote(note);
        return step;
    }

    /** 模型可见工具 = 白名单真实工具（Registry 描述注册）+ finish 元工具 */
    List<ToolCallback> callbacksFor(AgentTask task) {
        Set<String> names = new LinkedHashSet<>();
        if (task.fence().toolWhitelist() != null) {
            names.addAll(task.fence().toolWhitelist());
        }
        List<ToolCallback> callbacks = new ArrayList<>(toolRegistry.callbacksFor(names));
        callbacks.add(AgentProtocol.finishCallback(task.contract()));
        return callbacks;
    }

    private NovelToolReceipt loadReceipt(Long receiptId) {
        try {
            return receiptService.getById(receiptId);
        } catch (Exception e) {
            return null;
        }
    }

    private void emitStep(AgentTask task, int step, String status) {
        if (task.emitter() != null) {
            task.emitter().emitAgent(AgentEvent.step(task.runId(), task.nodeId(), step, status, null));
        }
    }

    private void emitToolCall(AgentTask task, int step, AssistantMessage.ToolCall call) {
        if (task.emitter() != null) {
            task.emitter().emitAgent(AgentEvent.toolCall(
                    task.runId(), task.nodeId(), step, call.name(),
                    Map.of("args", nvl(call.arguments(), "{}"))));
        }
    }

    private void emitToolResult(AgentTask task, int step, ToolRegistry.ToolExecution execution,
                                AssistantMessage.ToolCall call) {
        if (task.emitter() != null) {
            task.emitter().emitAgent(AgentEvent.toolResult(
                    task.runId(), task.nodeId(), step, execution.receiptId(), call.name(),
                    execution.ok(), Map.of("mode", execution.executedNow() ? "executed" : "replayed")));
        }
    }

    private void emitFinish(AgentTask task, int step, boolean ok, String violations) {
        if (task.emitter() != null) {
            task.emitter().emitAgent(AgentEvent.finishReport(
                    task.runId(), task.nodeId(), step,
                    ok ? Map.of("ok", true, "deliverable", "acked")
                            : Map.of("ok", false, "violations", nvl(violations, ""))));
        }
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static long sumTokens(List<NovelAgentStep> steps) {
        return steps.stream()
                .filter(s -> s != null && s.getTokens() != null)
                .mapToLong(NovelAgentStep::getTokens)
                .sum();
    }

    private static int maxStepNo(List<NovelAgentStep> steps) {
        return steps.stream()
                .filter(s -> s != null && s.getStepNo() != null)
                .mapToInt(NovelAgentStep::getStepNo)
                .max().orElse(0);
    }

    private static long tokensOf(ChatResponse response) {
        return WriterAgent.usageOf(response);
    }

    private static String nvl(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static Map<String, Object> finishDecision(String arguments, String callId, String violations) {
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("args", nvl(arguments, "{}"));
        if (callId != null) {
            decision.put("callId", callId);
        }
        if (violations != null) {
            decision.put("violations", truncate(violations, 120));
        }
        return decision;
    }
}
