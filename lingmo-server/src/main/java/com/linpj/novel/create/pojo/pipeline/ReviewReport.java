package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Critic 审查报告（结构化输出，`.call().entity(ReviewReport.class)`）
 *
 * 通过规则（设计 3.3）：approved = overallScore >= 7 且无 HIGH 问题；
 * 存在 >=1 个 HIGH 问题 -> 强制重写；仅 MEDIUM -> 附建议重写 1 轮；仅 LOW -> 直接通过。
 *
 * 六维 rubric（v2.0 设计 8.1 / M3-D4）：dimensionScores 各维 0-5 分，续读意愿 readOnScore 1-7 单列不
 * 计入总分（模拟目标读者）。绝对分仅作门槛（ADR-6）：跨轮/跨稿比较一律走成对比较；
 * weightedScore 供 S4 实验与人工 Spearman ρ 校准使用，不参与通过判定。
 *
 * @author HL
 */
@Data
public class ReviewReport implements Serializable {

    /** 六维维度标识（与 weights 一一对应，实验数据列名稳定） */
    public static final String[] DIMENSIONS = {
            "PLOT_ADVANCE", "CHARACTER", "CONFLICT", "PACING", "PROSE", "ENDING_HOOK"
    };

    /** 各维权重（设计 8.1 通用权重：情节 0.25 / 人物 0.20 / 张力 0.15 / 节奏 0.15 / 语言 0.15 / 钩子 0.10） */
    private static final double[] WEIGHTS = {0.25, 0.20, 0.15, 0.15, 0.15, 0.10};

    /** 是否通过审查 */
    private boolean approved;

    /** 总评分 1-10（>=7 且无 HIGH 问题即 approved，7 分阈值阶段1实测后校准） */
    private Integer overallScore;

    /** 六维分（0-5）：PLOT_ADVANCE / CHARACTER / CONFLICT / PACING / PROSE / ENDING_HOOK；
     * 旧模型输出缺失该字段时为 null/空，门槛判定不受影响 */
    private Map<String, Integer> dimensionScores = new LinkedHashMap<>();

    /** 续读意愿 1-7（单列，不计入总分；模拟目标读者翻页意愿） */
    private Integer readOnScore;

    /** 审查总结 */
    private String summary;

    /** 问题清单 */
    private List<Issue> issues = new ArrayList<>();

    /**
     * 加权总分（0-5 尺度）：六维按权重加权；缺失维度跳过并重新归一化（旧模型/降级容错）。
     * 仅作 S4 实验数据与归因分析，不参与批准门槛。
     */
    public double weightedScore() {
        if (dimensionScores == null || dimensionScores.isEmpty()) {
            return 0.0;
        }
        double weighted = 0;
        double weightSum = 0;
        for (int i = 0; i < DIMENSIONS.length; i++) {
            Integer score = dimensionScores.get(DIMENSIONS[i]);
            if (score != null) {
                weighted += score * WEIGHTS[i];
                weightSum += WEIGHTS[i];
            }
        }
        return weightSum == 0 ? 0.0 : weighted / weightSum;
    }

    public boolean hasHighIssue() {
        return hasSeverity("HIGH");
    }

    public boolean hasMediumIssue() {
        return hasSeverity("MEDIUM");
    }

    private boolean hasSeverity(String severity) {
        if (issues == null) {
            return false;
        }
        return issues.stream().anyMatch(i -> severity.equalsIgnoreCase(i.getSeverity()));
    }
}
