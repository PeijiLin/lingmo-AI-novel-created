package com.linpj.novel.create.agent.runtime;

/**
 * AgentRuntime 终态（自主性改造 R1a：§12.2 契约——降级分叉状态机）
 *
 * <ul>
 *   <li>{@link Success}：L2 收口成功（deliverableJson 已过收口契约校验）；</li>
 *   <li>{@link FallbackL1}：<b>预算型耗尽</b>（步数/token/墙钟/连败/复读熔断）→ 节点 L1
 *       单次调用兜底（11.1-M1b：L2 工具成果不进 L1 输入，消融基线隔离）；</li>
 *   <li>{@link Degraded}：<b>契约型失败</b>（finish 修复步一次仍不过）→ 直接降级 + 人审，
 *       不再烧 L1（同材料 L1 大概率也过不了）；预算型但节点未提供 L1 回调时同样落此态；</li>
 *   <li>{@link WaitingHuman}：ask_human 挂起（R1a allowAskHuman=false 物理不可达，占位）。</li>
 * </ul>
 *
 * tokens 口径：L2 已消耗如实入账（不因降级抹零——失败任务同样计费，B5 同款语义）。
 *
 * @author HL
 */
public sealed interface AgentResult permits
        AgentResult.Success, AgentResult.FallbackL1, AgentResult.Degraded, AgentResult.WaitingHuman {

    /** L2 收口成功：deliverableJson 已过 DeliverableContract 校验 */
    record Success(String deliverableJson, long tokensSpent, int stepsExecuted) implements AgentResult {
    }

    /** 预算型耗尽 → L1 单次调用兜底；l1Result = 节点 L1 调用产出 */
    record FallbackL1(Object l1Result, String reason, long l2TokensSpent) implements AgentResult {
    }

    /** 契约型失败（或无 L1 回调的预算型耗尽）：转人工 */
    record Degraded(String reason, long tokensSpent, int stepsExecuted) implements AgentResult {
    }

    /** 内层人工卡点（R3 落地；R1a allowAskHuman=false 不可达） */
    record WaitingHuman(String question, long tokensSpent) implements AgentResult {
    }
}
