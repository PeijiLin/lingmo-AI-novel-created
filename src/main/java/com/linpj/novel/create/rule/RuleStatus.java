package com.linpj.novel.create.rule;

/**
 * 规则生命周期（v2.0 设计 4.1 规则分级上线）
 *
 * OFF    默认态：规则不执行（= 现状行为）
 * SHADOW 影子态：执行并记录，但永不阻塞（误报观测期；上线门槛：陷阱集误报 ≤10%）
 * ACTIVE 阻塞态：违规参与阻塞决策
 *
 * 灰度（部分请求阻塞）在 M3-D5 随 MLOps 闭环扩展，B1 阶段先落 SHADOW/ACTIVE 两级。
 *
 * @author HL
 */
public enum RuleStatus {
    OFF, SHADOW, ACTIVE
}
