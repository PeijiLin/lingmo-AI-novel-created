package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import com.linpj.novel.create.service.wiki.WikiPageService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RC-W2 角色年龄与正典页冲突（M4：规则读 Wiki attrs 硬事实）
 *
 * 判定：CHARACTER 页 attrs.age 为数值时，叙述层（对白已剥离）出现
 * 「{名}…{N}岁」/「{N}岁的{名}」模式且 N ≠ 设定年龄 → 违规。
 *
 * 诚实声明的局限（启发式）：
 * - 年龄随剧情推进自然增长（"过了三年，他十六岁了"）合法但会误报——
 *   允许 ± 小偏差（AGE_TOLERANCE）之外的才报；跨年叙事请用 per-book 豁免；
 * - 对白中的年龄自述不校验（角色可谎报年龄，同 RuleTexts 叙述层口径）；
 * - 仅匹配「N岁」紧邻名字模式（±16 字符窗口），泛指年龄不判定。
 *
 * @author HL
 */
@Component
public class WikiAgeRule implements ConsistencyRule {

    /** 单次校验最多上报数（防长稿泛规则风暴） */
    private static final int MAX_VIOLATIONS = 3;

    /** 剧情推进容忍（岁）：设定 15 岁，正文 16/17 岁视为合法推进 */
    static final int AGE_TOLERANCE = 2;

    /** 名字前直连窗口（"三十五岁的林澈"——含"的"直连判定） */
    private static final int BEFORE_WINDOW = 8;

    private static final Pattern AGE_PATTERN = Pattern.compile("([0-9一二三四五六七八九十百零两]{1,7})岁");

    private final WikiPageService wikiPageService;

    public WikiAgeRule(WikiPageService wikiPageService) {
        this.wikiPageService = wikiPageService;
    }

    @Override
    public String id() {
        return "RC-W2";
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
        return "角色年龄与正典页 attrs.age 冲突（叙述层「N岁」邻名窗口比对，启发式）";
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
            return List.of(); // Wiki 通道故障：规则静默
        }
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }

        String narration = RuleTexts.stripDialogue(context.getDraft());
        List<RuleViolation> violations = new ArrayList<>();
        for (NovelWikiPage page : pages) {
            Integer age = ageOf(page);
            String name = page.getTitle();
            if (age == null || name == null || name.length() < 2) {
                continue;
            }
            int idx = narration.indexOf(name);
            while (idx >= 0 && violations.size() < MAX_VIOLATIONS) {
                // 紧邻模式一：名字后（到句末标点）——"林澈今年三十五岁"
                int afterStart = idx + name.length();
                int afterEnd = afterStart;
                while (afterEnd < narration.length() && !isTerminator(narration.charAt(afterEnd))) {
                    afterEnd++;
                }
                Integer stated = firstMismatch(narration.substring(afterStart, afterEnd), age);
                // 紧邻模式二：名字前 8 字符内且以"的"直连——"三十五岁的林澈"
                // （"约莫七十岁，正是林澈"逗号隔断：年龄属他人，不判定）
                if (stated == null) {
                    int beforeStart = Math.max(0, idx - BEFORE_WINDOW);
                    String before = narration.substring(beforeStart, idx);
                    int link = before.lastIndexOf('的');
                    if (link >= 0) {
                        stated = firstMismatch(before.substring(0, link), age);
                    }
                }
                if (stated != null) {
                    violations.add(RuleViolation.of(id(), severity(), RuleStage.DRAFT,
                            "「" + name + "」正典年龄 " + age + " 岁，叙述层出现「"
                                    + stated + "岁」（窗口：…"
                                    + clip(narration, idx, name) + "…）",
                            "按正典页年龄修正叙述，或剧情确有推进时更新该角色正典页 attrs.age",
                            false));
                }
                idx = narration.indexOf(name, idx + name.length());
            }
            if (violations.size() >= MAX_VIOLATIONS) {
                break;
            }
        }
        return violations;
    }

    /** 片段内首个与设定年龄冲突的「N岁」（中文/阿拉伯数字；容忍 ±2 剧情推进） */
    private static Integer firstMismatch(String segment, Integer canonAge) {
        if (segment == null || segment.isBlank()) {
            return null;
        }
        Matcher matcher = AGE_PATTERN.matcher(segment);
        while (matcher.find()) {
            Integer stated = parseAge(matcher.group(1));
            if (stated != null && Math.abs(stated - canonAge) > AGE_TOLERANCE) {
                return stated;
            }
        }
        return null;
    }

    private static boolean isTerminator(char c) {
        return c == '。' || c == '！' || c == '？' || c == '；' || c == '\n' || c == '，';
    }

    private static Integer ageOf(NovelWikiPage page) {
        Object attrs = page.getAttrs();
        if (attrs instanceof Map<?, ?> map) {
            Object age = map.get("age");
            if (age instanceof Number n) {
                return n.intValue();
            }
            if (age instanceof String s && s.matches("\\d{1,4}")) {
                return Integer.parseInt(s.trim());
            }
        }
        return null;
    }

    /** 中文/阿拉伯数字年龄解析（三十五=35、二十=20、廿不做、九十九=99；解析失败返回 null） */
    static Integer parseAge(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            // 落入中文数字解析
        }
        int result = 0;
        int current = 0;
        for (char c : raw.toCharArray()) {
            int digit = switch (c) {
                case '零' -> 0;
                case '一' -> 1;
                case '二', '两' -> 2;
                case '三' -> 3;
                case '四' -> 4;
                case '五' -> 5;
                case '六' -> 6;
                case '七' -> 7;
                case '八' -> 8;
                case '九' -> 9;
                default -> -1;
            };
            if (digit >= 0) {
                current = current * 10 + digit;
                continue;
            }
            switch (c) {
                case '十' -> {
                    result += (current == 0 ? 1 : current) * 10;
                    current = 0;
                }
                case '百' -> {
                    result += (current == 0 ? 1 : current) * 100;
                    current = 0;
                }
                default -> {
                    return null; // 非法字符（如"廿"）：不判定
                }
            }
        }
        return result + current;
    }

    private static String clip(String text, int idx, String name) {
        int start = Math.max(0, idx - 10);
        int end = Math.min(text.length(), idx + name.length() + 18);
        return text.substring(start, end);
    }
}
