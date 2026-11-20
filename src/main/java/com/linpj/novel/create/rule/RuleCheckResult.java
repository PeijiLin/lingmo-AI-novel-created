package com.linpj.novel.create.rule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 规则批量校验结果
 *
 * blocking：ACTIVE 规则的违规（参与阻塞决策）
 * shadow：SHADOW 规则的违规（只记录不阻塞，分级上线观测数据）
 * 豁免规则（ctx.exemptedRules 命中）不产生任何违规——作者豁免即视为该规则对本书静默
 *
 * @author HL
 */
public class RuleCheckResult {

    private final List<RuleViolation> blocking = new ArrayList<>();
    private final List<RuleViolation> shadow = new ArrayList<>();
    private final Set<String> executedRules = new LinkedHashSet<>();
    private final Set<String> failedRules = new LinkedHashSet<>();

    public void addBlocking(RuleViolation violation) {
        blocking.add(violation);
    }

    public void addShadow(RuleViolation violation) {
        shadow.add(violation);
    }

    public void markExecuted(String ruleId) {
        executedRules.add(ruleId);
    }

    public void markFailed(String ruleId) {
        failedRules.add(ruleId);
    }

    public List<RuleViolation> getBlocking() {
        return List.copyOf(blocking);
    }

    public List<RuleViolation> getShadow() {
        return List.copyOf(shadow);
    }

    public Set<String> getExecutedRules() {
        return Set.copyOf(executedRules);
    }

    public Set<String> getFailedRules() {
        return Set.copyOf(failedRules);
    }

    /** 是否存在阻塞级 HIGH 违规（触发局部重写，设计 5.1 N5 分流） */
    public boolean hasBlockingHigh() {
        return blocking.stream().anyMatch(v -> v.getSeverity() == RuleSeverity.HIGH);
    }

    /** 是否存在任意阻塞违规 */
    public boolean hasBlocking() {
        return !blocking.isEmpty();
    }
}
