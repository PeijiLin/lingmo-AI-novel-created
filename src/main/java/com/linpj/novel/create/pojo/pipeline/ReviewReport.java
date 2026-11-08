package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Critic 审查报告（结构化输出，`.call().entity(ReviewReport.class)`）
 *
 * 通过规则（设计 3.3）：approved = overallScore >= 7 且无 HIGH 问题；
 * 存在 >=1 个 HIGH 问题 -> 强制重写；仅 MEDIUM -> 附建议重写 1 轮；仅 LOW -> 直接通过。
 *
 * @author HL
 */
@Data
public class ReviewReport implements Serializable {

    /** 是否通过审查 */
    private boolean approved;

    /** 总评分 1-10（>=7 且无 HIGH 问题即 approved，7 分阈值阶段1实测后校准） */
    private Integer overallScore;

    /** 审查总结 */
    private String summary;

    /** 问题清单 */
    private List<Issue> issues = new ArrayList<>();

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
