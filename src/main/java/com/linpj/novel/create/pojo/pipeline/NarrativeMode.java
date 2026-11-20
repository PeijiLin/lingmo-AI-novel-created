package com.linpj.novel.create.pojo.pipeline;

/**
 * 场景叙事模式（v2.0 设计 2.1 ★：一个机制同时解决倒叙注入/梦境误报/戏中戏/叙诡四类缺陷）
 *
 * - NORMAL 正常推进：时间锚点=上一场景，全量规则适用
 * - FLASHBACK 回忆/倒叙：时间锚点=回忆目标章（状态按目标时刻求解，TC-08 调用链入口），全量规则但按目标时刻取快照
 * - DREAM 梦境/幻觉：不锚定时间轴，仅软规则（硬事实不入账、不推进 anchor）
 * - STORY_IN_STORY 戏中戏/转述：独立时间轴，仅软规则
 * - UNRELIABLE 叙诡保护区：必须作者显式声明（评审 N2），保护区内只告警不阻塞
 *
 * 错标风险（评审 N2）：Planner 错标为 NORMAL 时梦境内容会触发 RC-01 硬杀——
 * 作者可通过场景编辑覆盖模式，模式变更历史随大纲版本留档（outline 已有版本列）。
 *
 * @author HL
 */
public enum NarrativeMode {
    NORMAL,
    FLASHBACK,
    DREAM,
    STORY_IN_STORY,
    UNRELIABLE
}
