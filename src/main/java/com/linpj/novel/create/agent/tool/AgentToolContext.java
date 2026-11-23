package com.linpj.novel.create.agent.tool;

import java.util.Map;

/**
 * 工具执行上下文载体（自主性改造 R1a：AgentTask.toolContext 的工具侧视图）
 *
 * <p>静态工厂 of(runId, nodeId, data)：Runtime 在每次工具执行前随调随建；
 * {@link AgentTool.AgentContext} 是其工具语义视图（bookId/chapterId 寻址）。
 *
 * @author HL
 */
public record AgentToolContext(Long runId, String nodeId, Map<String, Object> data) {

    public static AgentToolContext of(Long runId, String nodeId, Map<String, Object> data) {
        return new AgentToolContext(runId, nodeId, data == null ? Map.of() : data);
    }

    public AgentTool.AgentContext toAgentContext() {
        return new AgentTool.AgentContext(runId, nodeId, data);
    }
}
