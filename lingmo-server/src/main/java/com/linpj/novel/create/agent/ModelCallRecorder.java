package com.linpj.novel.create.agent;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;

/**
 * 模型调用录制 advisor（v2.0 设计 6.1：录制回放一等测试能力）
 *
 * - 每个阻塞型 Agent（Planner/Critic/Polisher/Extraction）各挂一个实例，agent 名在构造时固定
 * - order 取最低优先级：排在 advisor 链最内层（最贴近模型），记录的是其他 advisor
 *   （记忆注入/RAG 增强后）的最终 prompt——回放才有确定性
 * - 失败调用同样记录（error 字段），便于 S1 探针统计结构化输出失败样本
 * - 禁用录制时为直通透传，零开销；Writer 流式链路 M0 阶段未覆盖（见实施计划 A2 备注）
 *
 * @author HL
 */
public class ModelCallRecorder implements CallAdvisor {

    private final String agentName;
    private final ModelCallLog callLog;

    public ModelCallRecorder(String agentName, ModelCallLog callLog) {
        this.agentName = agentName;
        this.callLog = callLog;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.currentTimeMillis();
        try {
            ChatClientResponse response = chain.nextCall(request);
            callLog.record(agentName, request, response,
                    System.currentTimeMillis() - start, null);
            return response;
        } catch (RuntimeException e) {
            callLog.record(agentName, request, null,
                    System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    @Override
    public String getName() {
        return "ModelCallRecorder-" + agentName;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
