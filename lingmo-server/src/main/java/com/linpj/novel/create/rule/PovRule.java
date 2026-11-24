package com.linpj.novel.create.rule;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RC-12 视角/人称越界（设计 6.7：POV 配置不符，medium 硬）
 *
 * 启发式判定（必须诚实声明的局限，规则默认 OFF，影子态观测误报后决定是否启用）：
 * - 仅"第三人称"配置生效：剥离对白后，叙述层出现第一人称标记（我/我们）即疑似越界
 * - 复合词白名单（自我/忘我/我行我素等）不计；低于阈值次数不计（残余书信/引文防误报）
 * - "第一人称"配置暂不校验：需主角名信息（M2 叙事模式标注后扩展）
 *
 * 误报高发：未加引号的内心独白、书信体、章节题记。豁免通道（RC-12 进 exemptedRules）
 * 与影子态观测是本规则的必要使用方式。
 *
 * @author HL
 */
@Component
public class PovRule implements ConsistencyRule {

    /** 叙述层第一人称标记次数阈值：低于此值视为噪声不计违规 */
    static final int OCCURRENCE_THRESHOLD = 2;

    /** 非人称"我"的白名单复合词/成语（叙述层合法出现） */
    private static final Pattern SAFE_COMPOUNDS = Pattern.compile(
            "自我|忘我|无我|本我|超我|真我|敌我|你我|我行我素|依然故我|时不我待|我武惟扬");

    /** 第一人称标记：任意"我"字（含"我们"），白名单复合词已先行剥离 */
    private static final Pattern FIRST_PERSON = Pattern.compile("我");

    @Override
    public String id() {
        return "RC-12";
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
        return "第三人称视角下叙述层出现第一人称标记（启发式，默认影子态观测）";
    }

    @Override
    public List<RuleViolation> check(RuleCheckContext context) {
        String draft = context.getDraft();
        String pov = context.getPov();
        if (draft == null || draft.isBlank() || pov == null || pov.isBlank()) {
            return List.of();
        }
        if (!pov.contains("第三人称")) {
            // 第一人称校验需主角名信息，M2 叙事模式标注后扩展
            return List.of();
        }
        String narration = RuleTexts.stripDialogue(draft);
        String neutralized = SAFE_COMPOUNDS.matcher(narration).replaceAll("");

        List<String> hits = new ArrayList<>();
        Matcher matcher = FIRST_PERSON.matcher(neutralized);
        while (matcher.find() && hits.size() < 5) {
            int start = Math.max(0, matcher.start() - 8);
            int end = Math.min(neutralized.length(), matcher.end() + 8);
            hits.add("……" + neutralized.substring(start, end).replace("\n", " ") + "……");
        }
        if (hits.size() < OCCURRENCE_THRESHOLD) {
            return List.of();
        }
        return List.of(RuleViolation.of(
                id(), severity(), RuleStage.DRAFT,
                String.format("叙述层（对白外）出现第一人称标记 %d 处：%s", hits.size(), String.join("；", hits)),
                "检查是否混入第一人称叙述，或未加引号的心声/书信片段",
                false));
    }
}
