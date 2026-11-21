package com.linpj.novel.create.security;

import com.linpj.novel.create.pojo.pipeline.Issue;
import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定稿前安全 gate（v2.0 M4-E3，设计 4.6 安全三层：PRE / STREAM / POST 的 POST 层）
 *
 * 职责边界（刻意收窄，避免与规则引擎/评审重复）：
 * 1. 判定：词表命中扫描（{@link SensitiveTerms}，确定性字面匹配）
 * 2. 修复：把命中转成"定向修订"问题清单（与 Critic Issue 契约同构），由管道复用 Writer
 *    修订链路做**修复式改写**——不硬拒绝、不静默删改，保住情节与文风（对应 P2"拒绝给可行替代"）
 * 3. 兜底：修复一次后仍命中 → 进人审队列（落 POST/MANUAL_REVIEW），**不阻断交付**
 * 4. 落账（双指标）：每章一条 POST 记录；拦截率分母 = POST 记录总数，
 *    误杀率分母 = 作者申诉/豁免（D6 豁免清单 + P2 标记），二者口径都在库内可查
 *
 * 为什么没有开关（与 M4-E6 同一原则）：安全层不让渡，配置只能收紧词表不能关闭 gate。
 * 词表为空时命中恒为空，等价于现状行为——"默认不打扰"靠数据而非开关实现。
 *
 * @author HL
 */
@Service
public class FinalDraftGate {

    /** 后置层标识（novel_safety_log.layer） */
    public static final String LAYER = "POST";

    public static final String ACTION_PASS = "PASS";
    public static final String ACTION_REWRITE = "REWRITE";
    public static final String ACTION_MANUAL_REVIEW = "MANUAL_REVIEW";

    /** 无命中（正常章）的分类，保证拦截率有分母 */
    static final String CATEGORY_CLEAN = "CLEAN";
    static final String CATEGORY_PREFIX = "WORDLIST_";

    /** 修复式改写的问题类型（并入 Critic Issue 契约，供 Writer 定向修订） */
    static final String ISSUE_TYPE = "SAFETY_TERM";

    private final SensitiveTerms terms;
    private final SafetyAuditService audit;

    /** audit 允许为 null（单测/轻量装配：只判定不落库） */
    public FinalDraftGate(SensitiveTerms terms, SafetyAuditService audit) {
        this.terms = terms;
        this.audit = audit;
    }

    /** 扫描定稿前正文；无词表/无命中返回空清单 */
    public List<SensitiveTerms.Hit> scan(String text) {
        return terms == null ? List.of() : terms.scan(text);
    }

    /**
     * 命中 → 定向修订问题清单。
     * 只给"改写命中表达"的指令，明确禁止改情节/改结构/压缩篇幅（否则 gate 变成整章重写，
     * 误杀成本不可接受）。
     */
    public ReviewReport repairReport(List<SensitiveTerms.Hit> hits) {
        ReviewReport report = new ReviewReport();
        report.setApproved(false);
        report.setOverallScore(5);
        report.setSummary("定稿前安全 gate：正文命中内容词表，需原地改写命中表达");
        List<Issue> issues = new ArrayList<>();
        for (SensitiveTerms.Hit hit : hits) {
            Issue issue = new Issue();
            issue.setSeverity("HIGH");
            issue.setType(ISSUE_TYPE);
            issue.setProblem("正文出现需规避表达「" + hit.term() + "」（风险分类 " + hit.category() + "）");
            issue.setEvidence("「" + hit.term() + "」@第" + (hit.offset() + 1) + "字");
            issue.setSuggestion("仅改写该处表达（换喻/侧写/留白皆可），保持人物、情节、场景顺序与篇幅不变；"
                    + "不得删除该段情节，不得输出任何解释性文字");
            issues.add(issue);
        }
        report.setIssues(issues);
        return report;
    }

    /** 无命中：落流程通过记录（拦截率分母） */
    public void recordPass(Long bookId, Long chapterId, int length) {
        record(bookId, CATEGORY_CLEAN, ACTION_PASS, chapterId, List.of(), length);
    }

    /** 修复式改写成功：落改写记录（拦截率分子） */
    public void recordRewrite(Long bookId, Long chapterId, List<SensitiveTerms.Hit> hits, int length) {
        record(bookId, categoryOf(hits), ACTION_REWRITE, chapterId, hits, length);
    }

    /** 修复后仍命中：进人审队列（拦截率分子 + 待人工裁定） */
    public void recordManualReview(Long bookId, Long chapterId, List<SensitiveTerms.Hit> hits, int length) {
        record(bookId, categoryOf(hits), ACTION_MANUAL_REVIEW, chapterId, hits, length);
    }

    /** 命中清单可读摘要（日志/artifact 用，不含正文） */
    public static String describe(List<SensitiveTerms.Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "[]";
        }
        return hits.stream()
                .map(hit -> hit.term() + "(" + hit.category() + ")@" + hit.offset())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String categoryOf(List<SensitiveTerms.Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            return CATEGORY_CLEAN;
        }
        return CATEGORY_PREFIX + hits.get(0).category();
    }

    private void record(Long bookId, String category, String action, Long chapterId,
                        List<SensitiveTerms.Hit> hits, int length) {
        if (audit == null) {
            return;
        }
        try {
            // 落库只写定位信息与命中摘要，不写正文（双指标统计不需要正文，避免日志二次泄露）
            String detail = "chapterId=" + chapterId + ";len=" + length + ";hits=" + describe(hits);
            audit.record(bookId, LAYER, category, action, detail);
        } catch (Exception ignored) {
            // SafetyAuditService 本身不抛；此处仅防御装配缺失，gate 不因落账失败而改变交付结果
        }
    }
}
