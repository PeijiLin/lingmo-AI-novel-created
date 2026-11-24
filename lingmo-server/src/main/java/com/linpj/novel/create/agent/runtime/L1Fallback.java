package com.linpj.novel.create.agent.runtime;

/**
 * L1 降级回调（自主性改造 R1a：§12.6 预算型降级分叉）
 *
 * <p>预算型耗尽时由节点侧完成"L1 单次调用"兜底——回调在节点语境内执行
 * （如 Critic 试点 = CriticAgent.review(原 CriticContext)）。
 *
 * <p>契约：实现方自行处理一次内部重试语义（与现状 L1 节点一致）；
 * 抛异常 = L1 也失败，Runtime 包转 {@link AgentResult.Degraded}。
 * <b>L2 已执行的工具成果不得注入回调输入</b>（消融基线隔离，11.1-M1b）。
 *
 * @author HL
 */
@FunctionalInterface
public interface L1Fallback {

    /** 节点 L1 单次调用；返回值语义由节点定义（Runtime 透传进 FallbackL1.l1Result） */
    Object callOnce();
}
