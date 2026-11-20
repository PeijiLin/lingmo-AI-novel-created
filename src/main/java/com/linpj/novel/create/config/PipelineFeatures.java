package com.linpj.novel.create.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管道机制开关（v2.0 设计 7.1：开关式双跑，feature flag 一等设计元素）
 *
 * - 每个机制独立开关，关闭 = 退回原行为（回归可证）；开关快照记入实验数据行
 * - 5 个消融级开关与假设对应（评审 N5 收敛）：
 *     summaryTree      定长摘要树+级联失效       <-> H2 记忆分层
 *     triTimeline      三时间轴+叙事模式标注      <-> H2 时序一致性
 *     credibility      叙述可信度分级            <-> H2/H4 抽取可信
 *     ruleRouting      确定性规则分流 RuleChecker <-> H3 校验分流
 *     convergenceGuard 收敛控制器升级            <-> H5 修订收敛
 * - 默认全 false（= 现状行为）；请求级可覆盖，实现同一部署下的开关式双跑
 * - 安全层与不可让渡降级（P6）不设开关，硬编码于编排层
 *
 * @author HL
 */
@Component
@ConfigurationProperties(prefix = "novel.pipeline.features")
public class PipelineFeatures {

    /** 定长摘要树 + 级联失效（关闭 = book 级滚动摘要现状） */
    private boolean summaryTree = false;

    /** 三时间轴 + 场景叙事模式标注（关闭 = 单一当前态快照现状） */
    private boolean triTimeline = false;

    /** 叙述可信度分级 FACT/LIE/HEARSAY/HYPOTHETICAL/DREAM（关闭 = 抽取结果按事实入账现状） */
    private boolean credibility = false;

    /** 确定性规则分流 RuleChecker（关闭 = 一致性全交 Critic 现状） */
    private boolean ruleRouting = false;

    /** 收敛控制器升级：成对比较回滚 + best-of 留档 + 循环预算（关闭 = 现有重写回路） */
    private boolean convergenceGuard = false;

    public boolean isSummaryTree() {
        return summaryTree;
    }

    public void setSummaryTree(boolean summaryTree) {
        this.summaryTree = summaryTree;
    }

    public boolean isTriTimeline() {
        return triTimeline;
    }

    public void setTriTimeline(boolean triTimeline) {
        this.triTimeline = triTimeline;
    }

    public boolean isCredibility() {
        return credibility;
    }

    public void setCredibility(boolean credibility) {
        this.credibility = credibility;
    }

    public boolean isRuleRouting() {
        return ruleRouting;
    }

    public void setRuleRouting(boolean ruleRouting) {
        this.ruleRouting = ruleRouting;
    }

    public boolean isConvergenceGuard() {
        return convergenceGuard;
    }

    public void setConvergenceGuard(boolean convergenceGuard) {
        this.convergenceGuard = convergenceGuard;
    }

    /** 全量开关快照（LinkedHashMap 保序：落库与实验数据行字段顺序稳定） */
    public Map<String, Boolean> asMap() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("summaryTree", summaryTree);
        map.put("triTimeline", triTimeline);
        map.put("credibility", credibility);
        map.put("ruleRouting", ruleRouting);
        map.put("convergenceGuard", convergenceGuard);
        return map;
    }

    /**
     * 请求级覆盖合并（开关式双跑：同一部署下按请求开/关单个机制，无需重启）。
     * 只接受已知键且值非空：未知键忽略，防止拼写错误悄悄改变实验分组（一次只开一个开关的纪律靠调用方遵守）。
     */
    public Map<String, Boolean> merged(Map<String, Boolean> overrides) {
        Map<String, Boolean> map = asMap();
        if (overrides != null) {
            overrides.forEach((key, value) -> {
                if (value != null && map.containsKey(key)) {
                    map.put(key, value);
                }
            });
        }
        return map;
    }
}
