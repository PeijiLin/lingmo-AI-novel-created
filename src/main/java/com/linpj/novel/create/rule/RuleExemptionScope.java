package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.entity.NovelRuleExemption;

import java.util.Date;

/**
 * 规则豁免的作用范围（v2.0 M4-E5 叙诡保护区）
 *
 * 为什么要区分范围（设计评审 N2）：豁免原本只有"整本静默"一档，作者为了让叙诡章不被
 * 一致性规则误杀，只能把整本书的规则关掉——代价是真正的一致性冲突从此无人拦截。
 * 这里把豁免拆成三种语义：
 * <ul>
 *   <li>整本（无区间，M3-D6 原语义）：规则<b>完全跳过</b>，用于"这条规则对本书不适用"；</li>
 *   <li>区间（章节闭区间）：区间内该规则<b>只告警不阻塞</b>（落 shadow），区间外照常拦截；</li>
 *   <li>到期复核（reviewAt）：过期即失效，规则恢复拦截；过期项进人审队列等作者续期/撤回。</li>
 * </ul>
 * 纯值对象，判定逻辑不碰 DB/时间源，便于单测覆盖边界（无界区间、空章节号、过期边界）。
 *
 * @author HL
 */
public record RuleExemptionScope(String ruleId, Long chapterFrom, Long chapterTo, Date reviewAt) {

    /** 从库表行还原（行可为 null，返回 null 交由调用方跳过） */
    public static RuleExemptionScope from(NovelRuleExemption exemption) {
        if (exemption == null) {
            return null;
        }
        return new RuleExemptionScope(exemption.getRuleId(), exemption.getChapterFrom(),
                exemption.getChapterTo(), exemption.getReviewAt());
    }

    /** 整本豁免（两端皆空）：沿用 D6 语义——规则对该书静默 */
    public boolean coversWholeBook() {
        return chapterFrom == null && chapterTo == null;
    }

    /** 是否覆盖该章（闭区间；缺省一端表示该方向无界）。章节号缺失时不能断言覆盖 */
    public boolean coversChapter(Long chapterId) {
        if (chapterId == null) {
            return false;
        }
        if (chapterFrom != null && chapterId < chapterFrom) {
            return false;
        }
        return chapterTo == null || chapterId <= chapterTo;
    }

    /** 复核期已过（reviewAt 为空 = 不设到期，长期有效） */
    public boolean isExpired(Date now) {
        return reviewAt != null && now != null && now.after(reviewAt);
    }

    /**
     * 保护区对该章是否生效：未过期 且（整本 或 覆盖该章）。
     * 注意整本豁免在 RuleChecker 里走"完全跳过"，此方法主要服务区间声明；
     * 二者共用"过期即失效"这一条语义，避免出现过期豁免仍静默规则的僵化状态。
     */
    public boolean protects(Long chapterId, Date now) {
        if (isExpired(now)) {
            return false;
        }
        return coversWholeBook() || coversChapter(chapterId);
    }

    /** 是否声明了章节区间（区别于整本豁免） */
    public boolean isChapterRange() {
        return chapterFrom != null || chapterTo != null;
    }
}
