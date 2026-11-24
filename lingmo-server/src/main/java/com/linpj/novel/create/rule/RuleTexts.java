package com.linpj.novel.create.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则侧文本处理工具（对白剥离等启发式规则的公共依赖）
 *
 * 语义约定：对白内容是"角色说的话"（人物可提及任何事），叙述内容是"作者陈述的事实"——
 * 一致性规则只校验叙述层的确定性事实，对白层不做硬判定（角色说谎/谈及死者是合法叙事）。
 *
 * @author HL
 */
final class RuleTexts {

    /** 对白引号（中英文常用全部覆盖，成对剥除） */
    private static final Pattern[] DIALOGUE_PATTERNS = {
            Pattern.compile("“[^”]*”", Pattern.DOTALL),
            Pattern.compile("「[^」]*」", Pattern.DOTALL),
            Pattern.compile("『[^』]*』", Pattern.DOTALL),
            Pattern.compile("\"[^\"]*\"", Pattern.DOTALL),
    };

    private RuleTexts() {
    }

    /**
     * 剥离对白引号内容（未闭合引号整段保留——宁可多保留叙述文本也不误删叙述）
     */
    static String stripDialogue(String text) {
        String result = text;
        for (Pattern pattern : DIALOGUE_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }
        return result;
    }

    /**
     * 提取对白引号内文（与 stripDialogue 同一引号口径；未闭合引号段不提取）。
     * 称谓类规则（RC-08）只校验对白层——叙述层的称谓无法判定说话者与听话者。
     */
    static List<String> dialogueSegments(String text) {
        List<String> segments = new ArrayList<>();
        if (text == null) {
            return segments;
        }
        for (Pattern pattern : DIALOGUE_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String inner = matcher.group().substring(1, matcher.group().length() - 1);
                if (!inner.isBlank()) {
                    segments.add(inner);
                }
            }
        }
        return segments;
    }

    /**
     * 窗口内是否含任一关键词（大小写不敏感，用于"提及/回忆"上下文白名单判定）
     */
    static boolean windowContainsAny(String text, int start, int end, String[] keywords) {
        if (text == null || keywords == null) {
            return false;
        }
        int from = Math.max(0, start);
        int to = Math.min(text.length(), end);
        if (from >= to) {
            return false;
        }
        String window = text.substring(from, to);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && window.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 截取证据片段（去换行，利于 artifact 存储与人工复核） */
    static String snippet(String text, int start, int end) {
        if (text == null) {
            return "";
        }
        int from = Math.max(0, start);
        int to = Math.min(text.length(), end);
        return text.substring(from, to).replace("\n", " ");
    }
}
