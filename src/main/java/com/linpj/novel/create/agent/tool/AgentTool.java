package com.linpj.novel.create.agent.tool;

import java.util.Map;

/**
 * Agent 工具接口（自主性改造 R1a：§12.8）
 *
 * <p>与 Spring AI 的 ToolCallback 分层：本接口是<b>节点语义层</b>（业务上下文寻址、
 * 结果语义），经 {@link ToolRegistry} 适配为 ToolCallback（描述注册）；执行<b>只走
 * Registry.execute</b>（唯一通道）——ChatClient 的 callback.call 永不被调用
 * （internalToolExecutionEnabled(false)，红线：未经 Registry 的工具调用零执行）。
 *
 * <p>工具契约（§2）：只读正典、超时/重试由回执账本 attempt 推导、结果一律视为
 * 不可信数据区（防注入：结果只作事本材料，消费侧不得当指令）。
 *
 * @author HL
 */
public interface AgentTool {

    /** 注册名（模型可见，如 canon.query） */
    String name();

    /** 描述（模型据此选择工具） */
    String description();

    /** 参数 JSON Schema（模型侧输入契约） */
    String inputSchema();

    /**
     * 执行（只经 Registry 调用）。
     *
     * @return 结果文本（喂回模型的 ToolResponse；null 视为空结果）
     */
    String execute(AgentToolContext context, String argumentsJson) throws Exception;

    /** 工具执行上下文（run/node 寻址 + 业务数据面 bookId/chapterId 等） */
    record AgentContext(Long runId, String nodeId, Map<String, Object> data) {

        public Object get(String key) {
            return data == null ? null : data.get(key);
        }

        public Long bookId() {
            Object v = get("bookId");
            return v instanceof Number n ? n.longValue() : null;
        }

        public Long chapterId() {
            Object v = get("chapterId");
            return v instanceof Number n ? n.longValue() : null;
        }
    }
}
