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
 * - 保护区（E5）：context.unreliableZone 或 context.protectedRules 命中的规则只落 shadow（只告警不阻塞）
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
            // 分档与灰度分流（v2.0 M3-D5）：SHADOW 全影子；GRADUAL 按 bookId 分流
            // （偶数书籍阻塞/奇数书籍影子——同规则下双跑数据在线验证误报率）
            // 保护区（M4-E5）：章节区间保护或叙诡保护区内的违规一律降级为影子——只告警不阻塞，
            // 否则"作者为了保住叙诡章只能整本关规则"，一致性拦截会随之全局失效。
            boolean zoneProtected = protectedInZone(ruleId, context);
            boolean shadowOnly = zoneProtected
                    || status == RuleStatus.SHADOW
                    || (status == RuleStatus.GRADUAL && !blocksForBook(context));
            if (zoneProtected) {
                log.info("保护区降级（只告警）: ruleId={}, chapterId={}, unreliableZone={}",
                        ruleId, context.getChapterId(), context.isUnreliableZone());
            }
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

    /** 灰度分流：null bookId 保守进影子（数据缺失不引入阻塞） */
    private static boolean blocksForBook(RuleCheckContext context) {
        return context.getBookId() != null && context.getBookId() % 2 == 0;
    }

    private static boolean applies(ConsistencyRule rule, RuleCheckContext context) {
        return context.getStage() != null && rule.stages() != null
                && rule.stages().contains(context.getStage());
    }

    private static boolean isExempted(String ruleId, RuleCheckContext context) {
        return context.getExemptedRules() != null && context.getExemptedRules().contains(ruleId);
    }

    /** 保护区：本章显式声明了叙诡（UNRELIABLE），或该规则被章节区间豁免覆盖 */
    private static boolean protectedInZone(String ruleId, RuleCheckContext context) {
        if (context.isUnreliableZone()) {
            return true;
        }
        return context.getProtectedRules() != null && context.getProtectedRules().contains(ruleId);
    }
}
