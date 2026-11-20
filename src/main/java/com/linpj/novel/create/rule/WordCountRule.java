package com.linpj.novel.create.rule;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * RC-11 字数偏离（设计 6.7：字数偏离目标 >30%，low 硬）
 *
 * 判定：目标字数 > 0 且 |实际-目标|/目标 > 30% 时违规；
 * 内联伏笔标记 <!--plot:--> 与空白字符不计入正文字数。
 * 目标未配置（null/<=0）时规则静默——不算"偏离"，只能算"无基准"。
 *
 * @author HL
 */
@Component
public class WordCountRule implements ConsistencyRule {

    /** 偏差阈值 30%（设计 6.7 定值） */
    static final double DEVIATION_LIMIT = 0.30;

    private static final Pattern INLINE_PLOT_MARK = Pattern.compile("<!--plot:.*?-->", Pattern.DOTALL);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public String id() {
        return "RC-11";
    }

    @Override
    public List<RuleStage> stages() {
        return List.of(RuleStage.DRAFT);
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.LOW;
    }

    @Override
    public String description() {
        return "成稿字数偏离目标超过 30%";
    }

    @Override
    public List<RuleViolation> check(RuleCheckContext context) {
        String draft = context.getDraft();
        Integer target = context.getTargetWordCount();
        if (draft == null || draft.isBlank() || target == null || target <= 0) {
            return List.of();
        }
        String clean = WHITESPACE.matcher(
                INLINE_PLOT_MARK.matcher(draft).replaceAll("")).replaceAll("");
        int actual = clean.length();
        double deviation = Math.abs(actual - target) / (double) target;
        if (deviation <= DEVIATION_LIMIT) {
            return List.of();
        }
        String direction = actual < target ? "不足" : "超出";
        String suggestion = actual < target
                ? "目标不足：扩写场景细节、感官描写或人物反应，避免注水"
                : "超出目标：删减冗余段落与重复解释，保留核心冲突推进";
        return List.of(RuleViolation.of(
                id(), severity(), RuleStage.DRAFT,
                String.format("目标 %d 字，实际 %d 字，偏差 %.0f%%（%s）", target, actual, deviation * 100, direction),
                suggestion,
                false));
    }
}
