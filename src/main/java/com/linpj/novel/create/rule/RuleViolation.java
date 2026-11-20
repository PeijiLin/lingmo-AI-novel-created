package com.linpj.novel.create.rule;

import lombok.Data;

import java.io.Serializable;

/**
 * 规则违规项（RuleChecker 结构化产物，v2.0 设计 6.7）
 *
 * - evidence/word 语义同 Critic Issue 契约：确定性规则的证据是可复核的账本/统计事实，
 *   不再要求原文引用（区别于 Critic 的主观评审）
 * - shadow 标记：影子态规则产生的违规只记录、不参与阻塞（分级上线观测用）
 *
 * @author HL
 */
@Data
public class RuleViolation implements Serializable {

    /** 规则 ID（如 RC-11） */
    private String ruleId;

    /** 严重级 */
    private RuleSeverity severity;

    /** 校验时机 */
    private RuleStage stage;

    /** 证据（确定性事实：如"目标 3000 字，实际 950 字，偏差 68%"） */
    private String evidence;

    /** 修复建议 */
    private String suggestion;

    /** 是否影子态产物（不阻塞，仅统计） */
    private boolean shadow;

    public static RuleViolation of(String ruleId, RuleSeverity severity, RuleStage stage,
                                   String evidence, String suggestion, boolean shadow) {
        RuleViolation violation = new RuleViolation();
        violation.setRuleId(ruleId);
        violation.setSeverity(severity);
        violation.setStage(stage);
        violation.setEvidence(evidence);
        violation.setSuggestion(suggestion);
        violation.setShadow(shadow);
        return violation;
    }
}
