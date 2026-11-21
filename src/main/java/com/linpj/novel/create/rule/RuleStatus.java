package com.linpj.novel.create.rule;

/**
 * 规则生命周期（v2.0 设计 4.1 规则分级上线：候选 → 影子 → 灰度 → 全量）
 *
 * OFF     默认态：规则不执行（= 现状行为）
 * SHADOW  影子态：执行并记录，永不阻塞（误报观测期；上线门槛：陷阱集误报 ≤10%）
 * GRADUAL 灰度态：门槛达标后的观察期——按 bookId 分流（偶数书籍阻塞、奇数书籍影子），
 *         双跑数据在线验证误报率后再全量
 * ACTIVE  阻塞态：全部书籍违规参与阻塞决策
 *
 * @author HL
 */
public enum RuleStatus {
    OFF, SHADOW, GRADUAL, ACTIVE
}
