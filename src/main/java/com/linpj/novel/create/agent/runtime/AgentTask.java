package com.linpj.novel.create.agent.runtime;

import com.linpj.novel.create.pipeline.ProgressEmitter;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

/**
 * 节点任务输入（自主性改造 R1a：§12.2——节点语义注入点）
 *
 * <p>AgentRuntime 节点无关（不知 Critic 为何物）：材料/契约/降级回调/工具上下文/
 * <b>ChatClient</b>（节点差异化模型路由——Critic L2 走异源 Critic 模型客户端、
 * Writer L2 走主模型客户端；录制 advisor 由节点侧构建时挂载）全部由此 record 携带，
 * R2 复用到 Writer/Planner 时运行时零改动。
 *
 * <p>emitter 为 null 时按丢弃处理（异步任务/回放测试）。
 * toolContext 携带业务寻址参数（bookId/chapterId 等）——工具执行上下文的数据面。
 *
 * @author HL
 */
public record AgentTask(Long runId, String nodeId, AgentFence fence,
                        String systemPrompt, String taskPrompt,
                        ChatClient chatClient,
                        DeliverableContract contract, L1Fallback fallback,
                        Map<String, Object> toolContext, ProgressEmitter emitter) {

    /** 便捷构造：无降级回调（预算型耗尽也落 Degraded）+ 丢弃式发射器 */
    public static AgentTask of(Long runId, String nodeId, AgentFence fence,
                               ChatClient chatClient, String systemPrompt, String taskPrompt,
                               DeliverableContract contract) {
        return new AgentTask(runId, nodeId, fence, systemPrompt, taskPrompt, chatClient,
                contract, null, Map.of(), null);
    }
}
