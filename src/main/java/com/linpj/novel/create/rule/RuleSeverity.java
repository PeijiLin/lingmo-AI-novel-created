package com.linpj.novel.create.rule;

/**
 * 规则严重级（对齐 Critic Issue 的 HIGH/MEDIUM/LOW 约定）
 *
 * 修订策略按 v2.0 设计 1.2：HIGH 触发重写；MEDIUM 局部修；LOW 交 Polisher
 *
 * @author HL
 */
public enum RuleSeverity {
    HIGH, MEDIUM, LOW
}
