package com.linpj.novel.create.security;

import java.io.Serializable;

/**
 * 前置防线判定结果（v2.0 设计 7.1 / M4-E1）
 *
 * - blocked=true：拒绝（P1），必须携带可行替代（不可以说"不行"就完了）
 * - flagged=true：疑似标记（P2），放行但记入审计——拦截率/误杀率双指标的计数来源
 * - 其余：PASS（审计不记——正常流量全记会把账本刷成垃圾场）
 *
 * @author HL
 */
public record PrecheckVerdict(boolean blocked, boolean flagged, String category, String reason,
                              String suggestion) implements Serializable {

    public static PrecheckVerdict pass() {
        return new PrecheckVerdict(false, false, null, null, null);
    }

    /** 拒绝：给理由 + 可行替代（v2.0 边界纪律：拒绝必须给替代方案） */
    public static PrecheckVerdict reject(String category, String reason, String suggestion) {
        return new PrecheckVerdict(true, false, category, reason, suggestion);
    }

    /** 疑似：放行 + 审计标记（误杀率与漏检率权衡的观测数据） */
    public static PrecheckVerdict flag(String category, String reason) {
        return new PrecheckVerdict(false, true, category, reason, null);
    }
}
