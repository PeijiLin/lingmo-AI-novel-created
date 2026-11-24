package com.linpj.novel.create.config;

import com.linpj.novel.create.rule.RuleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 规则分级配置（v2.0 设计 4.1：候选 → 影子 → 灰度 → 全量；B1 落 OFF/SHADOW/ACTIVE）
 *
 * - per-rule 状态：novel.pipeline.rules.status.RC-01=SHADOW
 * - 未配置的规则默认 OFF（= 现状行为，开关铁律 1）
 * - 全量默认值不写在代码里：上线状态是运营决策（陷阱集误报 ≤10% 才可 ACTIVE），
 *   代码只保留规则本体的"实现完备性"，状态永远来自配置/实验结论
 * - ruleRouting 为总闸（PipelineFeatures）：不接线不调用，规则状态仅在接线后生效
 *
 * @author HL
 */
@Component
@ConfigurationProperties(prefix = "novel.pipeline.rules")
public class PipelineRulesProperties {

    private static final Logger log = LoggerFactory.getLogger(PipelineRulesProperties.class);

    /** 规则状态表（key = 规则 ID；值不合法按 OFF 处理并告警——配置错误不得意外阻塞） */
    private Map<String, String> status = new LinkedHashMap<>();

    public Map<String, String> getStatus() {
        return status;
    }

    public void setStatus(Map<String, String> status) {
        this.status = status;
    }

    /** 解析规则状态：未配置 → OFF；非法值 → OFF + 告警 */
    public RuleStatus resolve(String ruleId) {
        String raw = status.get(ruleId);
        if (raw == null || raw.isBlank()) {
            return RuleStatus.OFF;
        }
        try {
            return RuleStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("规则状态配置不合法，按 OFF 处理: ruleId={}, value={}", ruleId, raw);
            return RuleStatus.OFF;
        }
    }

    /** 启动期校验：配置了但未注册的规则 ID 告警（拼写错误早发现，不悄悄失效） */
    public void validateAgainst(Set<String> registeredRuleIds) {
        status.keySet().forEach(configured -> {
            if (!registeredRuleIds.contains(configured)) {
                log.warn("规则状态配置指向未注册的规则（可能拼写错误，该配置无效）: {}", configured);
            }
        });
    }
}
