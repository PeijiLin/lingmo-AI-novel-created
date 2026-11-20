package com.linpj.novel.create.rule;

import com.linpj.novel.create.config.PipelineRulesProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 确定性规则校验器（设计 6.7 规则集的执行入口；代码组件，非 LLM）
 *
 * 执行语义：
 * - OFF 规则不执行；SHADOW 规则违规进 result.shadow（永不阻塞）；
 *   ACTIVE 规则违规进 result.blocking（N5 由编排器决定局部重写）
 * - 豁免通道：context.exemptedRules 命中的规则完全跳过（作者误报标记，D6 闭环）
 * - 单规则异常降级为告警（failedRules 记录），不中断其余规则、不阻塞出章
 * - 时机过滤：规则按 stages() 与 context.stage 匹配后才执行
 *
 * @author HL
 */
@Service
public class RuleChecker {

    private static final Logger log = LoggerFactory.getLogger(RuleChecker.class);

    private final List<ConsistencyRule> rules;
    private final PipelineRulesProperties properties;

    public RuleChecker(List<ConsistencyRule> rules, PipelineRulesProperties properties) {
        this.rules = List.copyOf(rules);
        this.properties = properties;
    }

    @PostConstruct
    void validateConfig() {
        Set<String> registered = new LinkedHashSet<>();
        rules.forEach(rule -> registered.add(rule.id()));
        properties.validateAgainst(registered);
        log.info("RuleChecker 就绪: 注册规则 {} 条 {}", rules.size(), registered);
    }

    /** 批量执行（按规则状态分档） */
    public RuleCheckResult check(RuleCheckContext context) {
        RuleCheckResult result = new RuleCheckResult();
        for (ConsistencyRule rule : rules) {
            if (!applies(rule, context)) {
                continue;
            }
            String ruleId = rule.id();
            if (isExempted(ruleId, context)) {
                log.debug("规则豁免（作者误报标记）: ruleId={}", ruleId);
                continue;
            }
            RuleStatus status = properties.resolve(ruleId);
            if (status == RuleStatus.OFF) {
                continue;
            }
            result.markExecuted(ruleId);
            List<RuleViolation> violations;
            try {
                violations = rule.check(context);
            } catch (Exception e) {
                // 规则故障不阻塞出章：降级为告警（规则的可观测性优先于其判定结果）
                log.warn("规则执行异常（跳过该规则）: ruleId={}, error={}", ruleId, e.getMessage());
                result.markFailed(ruleId);
                continue;
            }
            boolean shadowOnly = status == RuleStatus.SHADOW;
            for (RuleViolation violation : violations) {
                violation.setShadow(shadowOnly);
                if (shadowOnly) {
                    result.addShadow(violation);
                } else {
                    result.addBlocking(violation);
                }
            }
        }
        return result;
    }

    private static boolean applies(ConsistencyRule rule, RuleCheckContext context) {
        return context.getStage() != null && rule.stages() != null
                && rule.stages().contains(context.getStage());
    }

    private static boolean isExempted(String ruleId, RuleCheckContext context) {
        return context.getExemptedRules() != null && context.getExemptedRules().contains(ruleId);
    }
}
