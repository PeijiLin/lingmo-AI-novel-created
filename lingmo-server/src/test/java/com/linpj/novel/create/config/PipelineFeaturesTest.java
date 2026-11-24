package com.linpj.novel.create.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 机制开关单元测试（v2.0 设计 7.1：关闭 = 现状行为；请求级覆盖只认已知键）
 *
 * @author HL
 */
class PipelineFeaturesTest {

    @Test
    void 默认全关_等于现状行为() {
        PipelineFeatures features = new PipelineFeatures();
        Map<String, Boolean> map = features.asMap();
        assertEquals(5, map.size());
        map.values().forEach(v -> assertFalse(v, "默认全 false：关闭即现状"));
        // 快照键顺序稳定（落库 JSONB 字段顺序可预期）
        assertEquals("summaryTree, triTimeline, credibility, ruleRouting, convergenceGuard",
                String.join(", ", map.keySet()));
    }

    @Test
    void 请求级覆盖_已知键生效且不影响其他键() {
        PipelineFeatures features = new PipelineFeatures();
        Map<String, Boolean> merged = features.merged(Map.of("ruleRouting", true));
        assertEquals(Boolean.TRUE, merged.get("ruleRouting"));
        assertEquals(Boolean.FALSE, merged.get("summaryTree"));
        assertEquals(Boolean.FALSE, merged.get("triTimeline"));
        assertEquals(Boolean.FALSE, merged.get("credibility"));
        assertEquals(Boolean.FALSE, merged.get("convergenceGuard"));
        // 覆盖是副本语义：不回写部署默认
        assertFalse(features.isRuleRouting());
    }

    @Test
    void 未知键与空值忽略_防拼写错误改变实验分组() {
        PipelineFeatures features = new PipelineFeatures();
        Map<String, Boolean> overrides = new LinkedHashMap<>();
        overrides.put("ruleRoutng", true);   // 拼写错误：不是已知开关
        overrides.put("hacker", true);       // 未知键
        overrides.put("summaryTree", null);  // 空值视为未指定
        Map<String, Boolean> merged = features.merged(overrides);
        merged.values().forEach(v -> assertFalse(v, "未知键/空值不得悄悄开任何机制"));
    }

    @Test
    void 空覆盖返回默认快照() {
        PipelineFeatures features = new PipelineFeatures();
        assertEquals(features.asMap(), features.merged(null));
        assertEquals(features.asMap(), features.merged(new LinkedHashMap<>()));
    }

    @Test
    void 开关域只含五个消融机制_降级与安全不可配置关闭() {
        PipelineFeatures features = new PipelineFeatures();
        Map<String, Boolean> map = features.asMap();
        java.util.Set<String> ablation = java.util.Set.of(
                "summaryTree", "triTimeline", "credibility", "ruleRouting", "convergenceGuard");
        assertEquals(ablation, map.keySet(), "开关域必须恰好是 5 个消融机制");

        // TC-30：降级阶梯与安全层不在开关域内 —— 没有任何键能改它们
        for (String key : map.keySet()) {
            String k = key.toLowerCase(java.util.Locale.ROOT);
            assertFalse(k.contains("degrad") || k.contains("ladder") || k.contains("fallback")
                            || k.contains("safety"),
                    "降级/安全不得出现在可配置开关里: " + key);
        }

        // 逐个开机制不新增开关项（不存在"开启某机制 => 多出降级开关"的旁路）
        features.setSummaryTree(true);
        features.setTriTimeline(true);
        features.setCredibility(true);
        features.setRuleRouting(true);
        features.setConvergenceGuard(true);
        assertEquals(ablation, features.asMap().keySet(), "开机制不得扩展开关域");
        features.asMap().values().forEach(v -> assertTrue(v, "全开后快照如实反映"));
    }
}
