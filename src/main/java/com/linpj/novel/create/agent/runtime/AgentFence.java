package com.linpj.novel.create.agent.runtime;

import java.util.Set;

/**
 * L2 有界 Agent 围栏（自主性改造 R1a：§12.2 契约）
 *
 * <p>不可变 record，入场构建一次；运行期剩余额度全部由 (已用步数, tokens 累计, 首步时间戳)
 * 纯函数推导——无独立 fence_state 表（R0 决策：崩溃后字段可任意重算，无需持久化）。
 *
 * <p>围栏五维（§12.4 判定表，首个命中即出）：
 * <ol>
 *   <li>maxSteps：步数上限（stepNo=模型调用序号，MALFORMED 纯文本步同样占位计步——
 *       修订案纪律 1 的 R1a 实施口径：调用即成本，占位保证 kill-resume 序列完整性）；</li>
 *   <li>tokenBudget：内循环 token 预算（主约束维度）；</li>
 *   <li>wallClockMs：墙钟预算（跨中断重起算——等待不计墙钟）；</li>
 *   <li>maxConsecutiveFailures：连续失败熔断（工具拒绝/执行失败/纯文本响应/调用异常同通道）；</li>
 *   <li>maxRepeatSignature：相邻步决策签名复读熔断。</li>
 * </ol>
 *
 * <p>allowAskHuman R1a 恒 false（协议就位、物理不注册——围栏先于自主性红线）；
 * R3 开放（withAskHuman 工厂）——跨天挂起/恢复待前端批，当下语义=转人审+安全网回 L1。
 *
 * @author HL
 */
public record AgentFence(int maxSteps, long tokenBudget, long wallClockMs,
                         Set<String> toolWhitelist,
                         int maxConsecutiveFailures, int maxRepeatSignature,
                         boolean allowAskHuman) {

    /** 常规围栏工厂：白名单外的围栏维度取设计默认（连败 2 / 复读 3 / ask_human 不开） */
    public static AgentFence of(int maxSteps, long tokenBudget, long wallClockMs, Set<String> toolWhitelist) {
        return new AgentFence(maxSteps, tokenBudget, wallClockMs,
                toolWhitelist == null ? Set.of() : Set.copyOf(toolWhitelist),
                2, 3, false);
    }

    /** 允许人工卡点的围栏（R3：ask_human 元工具注册开放） */
    public static AgentFence withAskHuman(int maxSteps, long tokenBudget, long wallClockMs,
                                          Set<String> toolWhitelist) {
        return new AgentFence(maxSteps, tokenBudget, wallClockMs,
                toolWhitelist == null ? Set.of() : Set.copyOf(toolWhitelist),
                2, 3, true);
    }
}
