package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.entity.NovelPlotThread;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.PlotThreadService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RC-05 知识边界违规（v2.0 设计 6.7：high 硬；闭世界判定——查询"谁知道"，不穷举"谁不知道"）
 *
 * 判定语义：秘密 S（whoKnows 非空的 OPEN 伏笔）的知情集合已记录；
 * 角色在【对白】中提及 S 的 secretKey，且该角色不在 whoKnows 中 → 边界违规。
 *
 * 必须诚实声明的局限（默认 OFF，影子态观测后再分级）：
 * - 仅校验对白：叙述层是作者声音（向读者交代是"提前剧透"，另一类问题且可属叙诡保护），不做知识边界判定
 * - 说话人归属是启发式：引号前窗口内最近角色名；归属失败跳过（宁可漏检不可误判）
 * - whoKnows 依赖 PLANT/RESOLVE/ADVANCE 声明维护：未声明的秘密不判（数据缺失≠违规）
 * - UNRELIABLE 叙诡场景若被作者声明，应通过豁免通道（exemptedRules 含 RC-05）关闭本书判检
 *
 * @author HL
 */
@Component
public class KnowledgeBoundaryRule implements ConsistencyRule {

    /** 对白引号（提取而非剥离——本规则消费对白本身） */
    private static final Pattern[] DIALOGUE_PATTERNS = {
            Pattern.compile("“([^”]*)”", Pattern.DOTALL),
            Pattern.compile("「([^」]*)」", Pattern.DOTALL),
            Pattern.compile("『([^』]*)』", Pattern.DOTALL),
    };

    /** 说话人归属窗口（引号向前找最近角色名） */
    private static final int SPEAKER_WINDOW = 30;

    /** 单次校验最多上报数 */
    private static final int MAX_VIOLATIONS = 3;

    private final PlotThreadService plotThreadService;
    private final CharacterCardsService characterCardsService;

    public KnowledgeBoundaryRule(PlotThreadService plotThreadService,
                                 CharacterCardsService characterCardsService) {
        this.plotThreadService = plotThreadService;
        this.characterCardsService = characterCardsService;
    }

    @Override
    public String id() {
        return "RC-05";
    }

    @Override
    public List<RuleStage> stages() {
        return List.of(RuleStage.DRAFT);
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public String description() {
        return "知情集合外角色在对白中提及秘密（闭世界判定：查询 whoKnows）";
    }

    @Override
    public List<RuleViolation> check(RuleCheckContext context) {
        if (context.getBookId() == null || context.getDraft() == null || context.getDraft().isBlank()) {
            return List.of();
        }
        List<NovelPlotThread> secrets;
        try {
            secrets = plotThreadService.listSecretThreads(context.getBookId());
        } catch (Exception e) {
            return List.of(); // 数据通道故障：规则静默（宁可漏检不可炸管道）
        }
        if (secrets == null || secrets.isEmpty()) {
            return List.of();
        }
        Map<String, Long> idByName = characterNames(context.getBookId());
        if (idByName.isEmpty()) {
            return List.of();
        }

        List<RuleViolation> violations = new ArrayList<>();
        String draft = context.getDraft();
        for (NovelPlotThread secret : secrets) {
            if (violations.size() >= MAX_VIOLATIONS) {
                break;
            }
            String key = secret.getSecretKey();
            if (key == null || key.isBlank()) {
                continue; // 无文本锚点：只维护集合不判文本（数据缺失≠违规）
            }
            java.util.Set<Long> knowers = PlotThreadService.whoKnowsOf(secret);
            if (knowers.isEmpty()) {
                continue;
            }
            for (Pattern pattern : DIALOGUE_PATTERNS) {
                Matcher matcher = pattern.matcher(draft);
                while (matcher.find() && violations.size() < MAX_VIOLATIONS) {
                    String dialogue = matcher.group(1);
                    if (dialogue == null || !dialogue.contains(key)) {
                        continue;
                    }
                    Long speaker = speakerOf(draft, matcher.start(), idByName);
                    if (speaker == null || knowers.contains(speaker)) {
                        continue; // 归属失败跳过；知情者提及合法
                    }
                    String speakerName = nameOf(idByName, speaker);
                    violations.add(RuleViolation.of(
                            id(), severity(), RuleStage.DRAFT,
                            String.format("「%s」不在伏笔#%d 知情集合内，却在对白中提及秘密关键词「%s」：%s",
                                    speakerName, secret.getId(), key,
                                    RuleTexts.snippet(dialogue, 0, Math.min(40, dialogue.length()))),
                            "该角色尚未获知此秘密；改由知情者揭示，或先安排获知情节（RESOLVE/ADVANCE 的 whoKnows 补声明）",
                            false));
                }
            }
        }
        return violations;
    }

    private Map<String, Long> characterNames(Long bookId) {
        Map<String, Long> idByName = new LinkedHashMap<>();
        try {
            List<CharacterCardsVo> cards = characterCardsService.getCharacterCardsByProjectId(bookId);
            if (cards != null) {
                cards.stream()
                        .filter(Objects::nonNull)
                        .filter(card -> card.getId() != null && card.getName() != null
                                && card.getName().length() >= 2)
                        .forEach(card -> idByName.put(card.getName(), card.getId()));
            }
        } catch (Exception ignored) {
            // 数据通道故障：空映射=全部跳过（静默）
        }
        return idByName;
    }

    /** 引号前窗口内最近出现的角色名（说话人归属启发式；失败返回 null） */
    private static Long speakerOf(String draft, int quoteStart, Map<String, Long> idByName) {
        int from = Math.max(0, quoteStart - SPEAKER_WINDOW);
        if (from >= quoteStart) {
            return null;
        }
        String window = draft.substring(from, quoteStart);
        Long speaker = null;
        int bestIndex = -1;
        for (Map.Entry<String, Long> entry : idByName.entrySet()) {
            int index = window.lastIndexOf(entry.getKey());
            if (index >= 0 && index > bestIndex) {
                bestIndex = index;
                speaker = entry.getValue();
            }
        }
        return speaker;
    }

    private static String nameOf(Map<String, Long> idByName, Long id) {
        return idByName.entrySet().stream()
                .filter(entry -> id.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(String.valueOf(id));
    }
}
