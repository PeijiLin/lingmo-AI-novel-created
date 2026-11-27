package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import com.linpj.novel.create.service.wiki.WikiPageService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RC-W1 角色性别称谓与正典页冲突（M4：规则读 Wiki attrs 硬事实）
 *
 * 判定：CHARACTER 页 attrs.gender ∈ {男,女} 时，叙述层（对白已剥离）含该角色名
 * 的句子中出现相反性别代词（他/她/祂不算）→ 违规。
 *
 * 诚实声明的局限（启发式）：
 * - 同句出现其他角色时代词可能指向他人——句子含多个已知角色名时跳过（宁漏勿误）；
 * - "他/她"以外的称谓（那位姑娘/少年）不做判定（自由文本不可确定性归类）；
 * - 拟人/物称叙述（以动物/器物视角）会产生误报——按 per-book 豁免处理。
 *
 * @author HL
 */
@Component
public class WikiGenderRule implements ConsistencyRule {

    /** 单次校验最多上报数（防长稿泛规则风暴） */
    private static final int MAX_VIOLATIONS = 3;

    private final WikiPageService wikiPageService;

    public WikiGenderRule(WikiPageService wikiPageService) {
        this.wikiPageService = wikiPageService;
    }

    @Override
    public String id() {
        return "RC-W1";
    }

    @Override
    public List<RuleStage> stages() {
        return List.of(RuleStage.DRAFT);
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.MEDIUM;
    }

    @Override
    public String description() {
        return "角色性别称谓与正典页 attrs.gender 冲突（叙述层含名句的相反代词，启发式）";
    }

    @Override
    public List<RuleViolation> check(RuleCheckContext context) {
        if (context.getBookId() == null || context.getDraft() == null || context.getDraft().isBlank()) {
            return List.of();
        }
        List<NovelWikiPage> pages;
        try {
            pages = wikiPageService.listCanonPages(context.getBookId(), "CHARACTER");
        } catch (Exception e) {
            return List.of(); // Wiki 通道故障：规则静默（宁漏勿误）
        }
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }

        String narration = RuleTexts.stripDialogue(context.getDraft());
        List<String> violations = new ArrayList<>();
        List<String> knownNames = pages.stream()
                .map(page -> genderOf(page) == null ? null : page.getTitle())
                .filter(Objects::nonNull)
                .toList();

        for (NovelWikiPage page : pages) {
            String gender = genderOf(page);
            String name = page.getTitle();
            if (gender == null || name == null || name.length() < 2) {
                continue; // 单字名不做文本匹配（同 RC-01 口径）
            }
            String oppositePronoun = "男".equals(gender) ? "她" : "他";
            for (String window : windowsOf(narration, name)) {
                // 窗口内多角色：代词归属不可判定，跳过（宁漏勿误）
                if (countKnownNames(window, knownNames) > 1) {
                    continue;
                }
                if (window.contains(oppositePronoun)) {
                    violations.add("「" + name + "」正典性别为" + gender
                            + "，叙述层邻句出现相反代词「" + oppositePronoun + "」："
                            + clip(window, name));
                    break;
                }
                if (violations.size() >= MAX_VIOLATIONS) {
                    return build(violations, context);
                }
            }
        }
        return build(violations, context);
    }

    private static String genderOf(NovelWikiPage page) {
        Object attrs = page.getAttrs();
        if (attrs instanceof Map<?, ?> map) {
            Object gender = map.get("gender");
            if (gender != null) {
                String text = String.valueOf(gender).trim();
                if (text.contains("男")) {
                    return "男";
                }
                if (text.contains("女")) {
                    return "女";
                }
            }
        }
        return null;
    }

    /**
     * 含目标名的指代窗口：名字所在句 + 紧随句（中文主语省略常见——
     * "林澈收剑入鞘。她转身…"的反向代词在下一句，单句判定会漏检）。
     */
    static List<String> windowsOf(String narration, String name) {
        List<String> hits = new ArrayList<>();
        int idx = narration.indexOf(name);
        while (idx >= 0) {
            int start = sentenceStart(narration, idx);
            int end = sentenceEnd(narration, idx);
            // 并入紧随句（若其不含新句号后的句首名字，则扩展到下一句末）
            int nextEnd = sentenceEnd(narration, end);
            hits.add(narration.substring(start, Math.max(end, nextEnd)));
            idx = narration.indexOf(name, idx + name.length());
        }
        return hits;
    }

    private static int sentenceStart(String text, int idx) {
        for (int i = idx; i > 0; i--) {
            char c = text.charAt(i - 1);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n') {
                return i;
            }
        }
        return 0;
    }

    private static int sentenceEnd(String text, int idx) {
        for (int i = idx; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n') {
                return Math.min(i + 1, text.length());
            }
        }
        return text.length();
    }

    private static int countKnownNames(String sentence, List<String> names) {
        int count = 0;
        for (String name : names) {
            if (name != null && name.length() >= 2 && sentence.contains(name)) {
                count++;
            }
        }
        return count;
    }

    private static String clip(String sentence, String name) {
        int idx = sentence.indexOf(name);
        int start = Math.max(0, idx - 12);
        int end = Math.min(sentence.length(), idx + name.length() + 20);
        return sentence.substring(start, end);
    }

    private List<RuleViolation> build(List<String> violations, RuleCheckContext context) {
        return violations.stream()
                .map(text -> RuleViolation.of(id(), severity(), RuleStage.DRAFT,
                        text, "按正典页性别修改代词，或修正该角色正典页 attrs.gender", false))
                .toList();
    }
}
