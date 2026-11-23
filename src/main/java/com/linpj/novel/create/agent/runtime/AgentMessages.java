package com.linpj.novel.create.agent.runtime;

import com.linpj.novel.create.pojo.entity.NovelAgentStep;
import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import com.linpj.novel.create.utils.JsonUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 消息序列纯函数构造 + 账本重放组装（自主性改造 R1a：§12.5——kill-resume 确定性核心）
 *
 * <p><b>build(task, history, receiptLoader) 是纯函数</b>：相同输入（任务静态文本 +
 * 账本行 + 回执）必产相同消息序列——重放与不中断运行的序列逐字节一致（E3 不变式 1，
 * 录制 JSONL diff 验证）。动态部分（剩余步数）只从 stepNo 与 fence.maxSteps 推导，
 * 落盘的账行数据即全部重放依据，禁止从运行时状态快照取值（纪律 3）。
 *
 * <p>账行 → 消息映射规则（与 AgentRuntime 落账约定一一对应）：
 * <ul>
 *   <li>kind=TOOL_CALL / status=DONE：assistant(toolCalls) + toolResponses
 *       （decision.calls 重建 ToolCall；decision.receipts 的 receiptId 经 receiptLoader
 *       读回执 result 作为 ToolResponse.responseData——不重调工具、不重复计费）；</li>
 *   <li>kind=TEXT / status!=DONE（纯文本失败步）：assistant(text) + user(retryHint)——
 *       重试指令固定模板，从账行存在性推导；</li>
 *   <li>kind=FINISH / status=FAILED（修复步历史）：assistant(finish toolCall) +
 *       user(repairHint(decision.violations))——同上纯函数推导；</li>
 *   <li>kind=FINISH / status=DONE：终态，不出现在历史（循环已收口）。</li>
 * </ul>
 * 调用异常步不落账（无响应可重放），天然缺席。
 *
 * <p>框架借鉴（§12.0）：重建 assistant 消息时 REPLACE 语义逐行重放，等价于
 * MessagesModelHook 的 UpdatePolicy.REPLACE 的确定性子集。
 *
 * @author HL
 */
public final class AgentMessages {

    private AgentMessages() {
    }

    /**
     * 组装第 k 步的完整消息序列。
     *
     * @param task          节点任务（静态文本 + 契约）
     * @param history       截至第 k-1 步的全部账行（stepNo 升序，含 MALFORMED/FAILED——序列完整性）
     * @param receiptLoader 回执读取函数（receiptId → 落盘回执；读不到时回执文本按缺省降级——
     *                      重放宁可给"回执缺失"提示也不中断，账行仍保持）
     * @param k             即将执行的步号
     */
    public static List<Message> build(AgentTask task, List<NovelAgentStep> history,
                                      Function<Long, NovelToolReceipt> receiptLoader, int k) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(task.systemPrompt()));
        messages.add(new UserMessage(task.taskPrompt() + AgentProtocol.protocolBlock(task)));

        if (history != null) {
            for (NovelAgentStep step : history) {
                if (step == null || step.getKind() == null || step.getStepNo() == null) {
                    continue;
                }
                appendHistory(messages, step, receiptLoader);
            }
        }

        messages.add(new UserMessage(AgentProtocol.stepHint(k, task.fence().maxSteps())));
        return messages;
    }

    /** 单账行 → 历史消息段（映射规则见类 javadoc） */
    private static void appendHistory(List<Message> messages, NovelAgentStep step,
                                      Function<Long, NovelToolReceipt> receiptLoader) {
        switch (step.getKind()) {
            case "TOOL_CALL" -> appendToolCallStep(messages, step, receiptLoader);
            case "TEXT" -> appendMalformedStep(messages, step);
            case "FINISH" -> appendFinishStep(messages, step);
            default -> {
                // 未知类型（ASK_HUMAN 等 R1a 未启用）：防御跳过，不破坏既有序列
            }
        }
    }

    /** 工具步：assistant(toolCalls) + toolResponses（从回执表重放，不重调工具） */
    private static void appendToolCallStep(List<Message> messages, NovelAgentStep step,
                                           Function<Long, NovelToolReceipt> receiptLoader) {
        StepDecision decision = StepDecision.parse(step.getDecision());
        if (decision == null || decision.calls().isEmpty()) {
            return; // 账行损坏：防御跳过（重放缺该步消息，宁可少喂不喂错）
        }
        List<AssistantMessage.ToolCall> toolCalls = decision.calls().stream()
                .map(c -> new AssistantMessage.ToolCall(c.get("id") == null ? "call_" + c.get("name") : String.valueOf(c.get("id")),
                        "function", String.valueOf(c.get("name")),
                        String.valueOf(c.getOrDefault("args", "{}"))))
                .toList();
        messages.add(AssistantMessage.builder().content("").toolCalls(toolCalls).build());

        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (Map<String, Object> receiptRef : decision.receipts()) {
            String callId = String.valueOf(receiptRef.getOrDefault("callId", "call_unknown"));
            String name = String.valueOf(receiptRef.getOrDefault("name", "unknown"));
            // 回执重建优先级：receiptId 落盘回执（幂等重放）→ 内联 result（deferred/拒绝场景）→ 缺省占位
            Object receiptIdObj = receiptRef.get("receiptId");
            String resultText;
            if (receiptIdObj instanceof Number number) {
                NovelToolReceipt receipt = receiptLoader.apply(number.longValue());
                resultText = receipt != null && receipt.getResult() != null
                        ? String.valueOf(receipt.getResult())
                        : "（回执缺失：receiptId=" + receiptIdObj + "）";
            } else if (receiptRef.get("result") != null) {
                resultText = String.valueOf(receiptRef.get("result"));
            } else {
                resultText = "（无回执记录）";
            }
            responses.add(new ToolResponseMessage.ToolResponse(callId, name, resultText));
        }
        if (!responses.isEmpty()) {
            messages.add(ToolResponseMessage.builder().responses(responses).build());
        }
    }

    /** 纯文本失败步：assistant(text) + 固定重试指令 */
    private static void appendMalformedStep(List<Message> messages, NovelAgentStep step) {
        StepDecision decision = StepDecision.parse(step.getDecision());
        String text = decision == null || decision.text() == null ? "" : decision.text();
        messages.add(new AssistantMessage(text));
        messages.add(new UserMessage(AgentProtocol.retryHint()));
    }

    /** finish 失败步（修复步历史）：assistant(finish 调用) + 修复指令（violations 从账行推导） */
    private static void appendFinishStep(List<Message> messages, NovelAgentStep step) {
        StepDecision decision = StepDecision.parse(step.getDecision());
        if (decision == null || decision.finishArguments() == null) {
            return;
        }
        messages.add(AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall(
                        decision.finishCallId() == null ? "call_finish" : decision.finishCallId(),
                        "function", AgentProtocol.FINISH_TOOL, decision.finishArguments())))
                .build());
        messages.add(new UserMessage(AgentProtocol.repairHint(
                decision.violations() == null ? "结构校验未通过" : decision.violations())));
    }

    /**
     * 账行 decision JSONB 的结构化读取（写读对称：AgentRuntime 落账时的约定结构）。
     * Gson Map 导航（与 JsonUtil 同栈），避免为账本内部结构建实体类。
     */
    static final class StepDecision {

        private final Map<String, Object> node;

        @SuppressWarnings("unchecked")
        private StepDecision(Map<String, Object> node) {
            this.node = node == null ? Map.of() : node;
        }

        static StepDecision parse(Object decision) {
            if (decision == null) {
                return null;
            }
            try {
                String json = decision instanceof String s ? s : JsonUtil.toJson(decision);
                return new StepDecision(JsonUtil.fromJson(json, Map.class));
            } catch (Exception e) {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> calls() {
            Object raw = node.get("calls");
            return raw instanceof List<?> list
                    ? (List<Map<String, Object>>) (List<?>) list : List.of();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> receipts() {
            Object raw = node.get("receipts");
            return raw instanceof List<?> list
                    ? (List<Map<String, Object>>) (List<?>) list : List.of();
        }

        String text() {
            Object raw = node.get("text");
            return raw == null ? null : String.valueOf(raw);
        }

        String finishArguments() {
            Object raw = node.get("args");
            return raw == null ? null : String.valueOf(raw);
        }

        String finishCallId() {
            Object raw = node.get("callId");
            return raw == null ? null : String.valueOf(raw);
        }

        String violations() {
            Object raw = node.get("violations");
            return raw == null ? null : String.valueOf(raw);
        }
    }
}
