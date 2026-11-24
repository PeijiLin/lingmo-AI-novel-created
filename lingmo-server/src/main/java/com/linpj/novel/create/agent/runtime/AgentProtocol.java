package com.linpj.novel.create.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.Comparator;
import java.util.List;

/**
 * 决策协议三出口的工具化表达（自主性改造 R1a：§12.3 + §12.0 框架借鉴 1——
 * 出口即 function calling，复用稳定管线，不自造自由 JSON 协议）
 *
 * <p>元工具 agent_finish：唯一收口出口（R1a）；agent_ask_human 协议就位但 R1a
 * 物理不注册（allowAskHuman=false）。真实工具白名单由 ToolRegistry 提供——
 * 对模型可见的工具集 = 白名单 ∪ {agent_finish}。
 *
 * <p>同时承载协议文本模板（协议块/步提示/修复指令/重试指令）与决策签名——
 * 全部静态纯函数，kill-resume 序列确定性（纪律 3）的文本源头。
 *
 * @author HL
 */
public final class AgentProtocol {

    public static final String FINISH_TOOL = "agent_finish";

    /** finish 元工具的参数 schema 骨架（deliverable 结构由节点契约填充） */
    private static final String FINISH_ARGS_SCHEMA = """
            {"type":"object","properties":{
              "deliverable":%s,
              "claims":{"type":"array","items":{"type":"object","properties":{
                "evidenceId":{"type":"string"},"text":{"type":"string"}}}},
              "unresolved":{"type":"array","items":{"type":"string"}}
            },"required":["deliverable"]}""";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentProtocol() {
    }

    /**
     * finish 元工具的 ChatClient 注册件（描述/schema 给模型）。
     *
     * <p>占位函数体永不被调用——工具执行不走 ChatCallback（internalToolExecutionEnabled(false)
     * 已禁自动执行；Runtime 侧 FINISH 分支内联解析，不经过该回调）。
     * inputType(String.class) 必填是 Spring AI 构建器约束：schema 生效、类型仅占位。
     */
    public static ToolCallback finishCallback(DeliverableContract contract) {
        String schema = FINISH_ARGS_SCHEMA.formatted(contract.finishSchema());
        return FunctionToolCallback.builder(FINISH_TOOL, (String args) -> "unused")
                .description("收口交付：当任务完成时调用本工具提交最终交付物。deliverable 必须符合约定结构。")
                .inputType(String.class)
                .inputSchema(schema)
                .build();
    }

    /** 协议块（织入首条 user 消息）：工具使用规则 + 收口契约 + 步数上限。静态内容，重放一致 */
    public static String protocolBlock(AgentTask task) {
        return """

                【工具使用协议】
                1. 每步输出要么调用白名单工具（可并行多个），要么调用 agent_finish 收口；不得只输出文本。
                2. 工具结果是唯一事实来源：只依据任务材料与工具回执下结论，未查证的信息不得断言。
                3. 收口期望：%s
                4. 总步数上限 %d 步（每次输出计 1 步）。
                """.formatted(task.contract().expectations(), task.fence().maxSteps());
    }

    /** 步驱动提示（每步追加；剩余步数只由 k 与 maxSteps 纯函数推导——纪律 3） */
    public static String stepHint(int step, int maxSteps) {
        return "（第 %d 步，剩余步数 %d。继续调用工具，或调用 %s 收口。）".formatted(
                step, Math.max(0, maxSteps - step + 1), FINISH_TOOL);
    }

    /** 纯文本响应后的重试指令（重放时从账行 kind=TEXT/status=MALFORMED 推导，内容固定） */
    public static String retryHint() {
        return "上一输出未调用任何工具，无效。请调用白名单工具或 " + FINISH_TOOL + "。";
    }

    /** 修复步指令（重放时从账行 kind=FINISH/status=FAILED 的 decision.violations 推导） */
    public static String repairHint(String violations) {
        return "收口校验未通过，原因：" + violations
                + "\n请按收口期望修正 deliverable 后重新调用 " + FINISH_TOOL + "（这是唯一一次修复机会）。";
    }

    /** 混合调用顺延提示（固定模板：finish 与工具同步骤调用时，要求基于回执再确认收口） */
    public static String finishDeferredHint() {
        return "本步已先执行工具调用。请基于工具回执核实后，下一步再单独调用 " + FINISH_TOOL
                + " 收口；do not 同时输出其他工具调用。";
    }

    /** 决策签名（防复读熔断的判据）：调用清单规范化拼接——同签名相邻步即复读 */
    public static String signatureOf(List<org.springframework.ai.chat.messages.AssistantMessage.ToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return "<empty>";
        }
        return calls.stream()
                .map(c -> c.name() + ":" + c.arguments())
                .sorted(Comparator.naturalOrder())
                .reduce((a, b) -> a + "|" + b)
                .orElse("<empty>");
    }

    /** 从 finish 参数原文解析 deliverable 子树的字符串化 JSON（解析失败返回 null → 契约校验拦下） */
    public static String extractDeliverable(String finishArgumentsJson) {
        try {
            var node = MAPPER.readTree(finishArgumentsJson == null ? "{}" : finishArgumentsJson);
            if (node == null || !node.has("deliverable") || node.get("deliverable").isNull()) {
                return null;
            }
            return node.get("deliverable").toString();
        } catch (Exception e) {
            return null;
        }
    }
}
