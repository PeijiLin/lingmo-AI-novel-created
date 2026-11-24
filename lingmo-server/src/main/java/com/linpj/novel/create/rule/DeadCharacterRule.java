package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.pipeline.AliveStatus;
import com.linpj.novel.create.pojo.pipeline.Scene;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CharacterStateService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RC-01 已死亡角色出现言行（设计 6.7：high 硬）
 *
 * 两级判定：
 * - OUTLINE（确定性）：细纲场景 participantIds 含生死状态为 DEAD 的角色 → 直接违规
 * - DRAFT（启发式）：叙述层（对白外）出现死亡角色名，且命中窗口不含"提及/回忆/身后事"
 *   类上下文词 → 疑似违规。对白内提及死者合法（角色谈论逝者是正常叙事）。
 *
 * 必须诚实声明的局限（默认 OFF，影子态观测后再分级）：
 * - "无复活设定"无法确定性获知（world_setting 是自由文本）：复活流题材请用
 *   per-book 豁免（exemptedRules 含 RC-01）
 * - 叙诡/回忆杀若未走对白引号包裹，叙述层出现死者名会被标记——M2 叙事模式标注
 *   （FLASHBACK/UNRELIABLE）落地后按场景模式豁免
 * - 单字名不做文本匹配（误报率不可接受），仅做 ID 级判定
 *
 * @author HL
 */
@Component
public class DeadCharacterRule implements ConsistencyRule {

    /** 提及/回忆/身后事类上下文白名单：命中窗口含这些词视为合法提及而非"言行出场" */
    static final String[] MENTION_CONTEXT = {
            "提到", "提起", "想到", "想起", "忆起", "回忆", "记得", "记起", "梦见", "梦见",
            "坟", "墓", "碑", "牌位", "灵位", "遗容", "遗物", "遗言", "遗体", "遗骸",
            "尸体", "尸首", "尸身", "祭", "悼", "追忆", "怀念", "思念",
            "死讯", "逝世", "阵亡", "殉", "亡故", "身后", "生前", "旧事", "往事", "音容"
    };

    /** 死者名匹配的最小长度（单字名不做文本匹配） */
    private static final int MIN_NAME_LENGTH = 2;

    /** 提及判定窗口：命中位置前后各 16 字符 */
    private static final int MENTION_WINDOW = 16;

    /** 单次校验最多上报数（防长稿泛规则风暴） */
    private static final int MAX_VIOLATIONS = 3;

    private final CharacterStateService characterStateService;
    private final CharacterCardsService characterCardsService;

    public DeadCharacterRule(CharacterStateService characterStateService,
                             CharacterCardsService characterCardsService) {
        this.characterStateService = characterStateService;
        this.characterCardsService = characterCardsService;
    }

    @Override
    public String id() {
        return "RC-01";
    }

    @Override
    public List<RuleStage> stages() {
        return List.of(RuleStage.OUTLINE, RuleStage.DRAFT);
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public String description() {
        return "已死亡角色出现言行（细纲 ID 级确定性判定 + 成稿叙述层启发式）";
    }

    @Override
    public List<RuleViolation> check(RuleCheckContext context) {
        if (context.getBookId() == null) {
            return List.of();
        }
        // 读取规则数据：角色卡（ID->名称）+ 生死状态（只读查询；数据缺失时规则静默）
        List<CharacterCardsVo> cards;
        try {
            cards = characterCardsService.getCharacterCardsByProjectId(context.getBookId());
        } catch (Exception e) {
            return List.of(); // 数据通道故障：规则静默（RuleChecker 有整体兜底，这里防止单规则数据故障炸管道）
        }
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        Map<Long, String> namesById = new LinkedHashMap<>();
        cards.stream()
                .filter(Objects::nonNull)
                .filter(card -> card.getId() != null)
                .forEach(card -> namesById.put(card.getId(), card.getName()));

        Map<Long, AliveStatus> statuses =
                characterStateService.findAliveStatuses(context.getBookId(), List.copyOf(namesById.keySet()));
        Map<Long, String> deadCharacters = new LinkedHashMap<>();
        namesById.forEach((id, name) -> {
            if (statuses.get(id) == AliveStatus.DEAD) {
                deadCharacters.put(id, name);
            }
        });
        if (deadCharacters.isEmpty()) {
            return List.of();
        }

        return context.getStage() == RuleStage.OUTLINE
                ? checkOutline(context, deadCharacters)
                : checkDraft(context, deadCharacters);
    }

    /** OUTLINE：participantIds 含死者 → 确定 HIGH（纯 ID 比对，零启发式） */
    private List<RuleViolation> checkOutline(RuleCheckContext context, Map<Long, String> deadCharacters) {
        List<RuleViolation> violations = new ArrayList<>();
        if (context.getOutline() == null || context.getOutline().getScenes() == null) {
            return violations;
        }
        List<Scene> scenes = context.getOutline().getScenes();
        for (int i = 0; i < scenes.size() && violations.size() < MAX_VIOLATIONS; i++) {
            Scene scene = scenes.get(i);
            if (scene == null || scene.getParticipantIds() == null) {
                continue;
            }
            for (Long participantId : scene.getParticipantIds()) {
                String name = deadCharacters.get(participantId);
                if (name != null) {
                    violations.add(RuleViolation.of(
                            id(), severity(), RuleStage.OUTLINE,
                            String.format("场景 %d 参与者含已死亡角色「%s」(ID=%d)", i + 1, name, participantId),
                            "已死亡角色不应参与场景言行；若属复活设定请通过作者豁免通道标注",
                            false));
                    break; // 每场景报一条即可
                }
            }
        }
        return violations;
    }

    /** DRAFT：叙述层死者名出现且非提及语境 → 疑似 HIGH（启发式） */
    private List<RuleViolation> checkDraft(RuleCheckContext context, Map<Long, String> deadCharacters) {
        String draft = context.getDraft();
        if (draft == null || draft.isBlank()) {
            return List.of();
        }
        String narration = RuleTexts.stripDialogue(draft);
        List<RuleViolation> violations = new ArrayList<>();
        for (Map.Entry<Long, String> dead : deadCharacters.entrySet()) {
            if (violations.size() >= MAX_VIOLATIONS) {
                break;
            }
            String name = dead.getValue();
            if (name == null || name.length() < MIN_NAME_LENGTH) {
                continue; // 单字名不做文本匹配
            }
            int index = narration.indexOf(name);
            while (index >= 0 && violations.size() < MAX_VIOLATIONS) {
                int end = index + name.length();
                boolean mentionOnly = RuleTexts.windowContainsAny(
                        narration, index - MENTION_WINDOW, end + MENTION_WINDOW, MENTION_CONTEXT);
                if (!mentionOnly) {
                    violations.add(RuleViolation.of(
                            id(), severity(), RuleStage.DRAFT,
                            String.format("叙述层出现已死亡角色「%s」言行疑似：……%s……",
                                    name, RuleTexts.snippet(narration, Math.max(0, index - 12), end + 12)),
                            "确认该角色是否复活/回忆/梦境场景；复活设定请走作者豁免，梦境回忆待 M2 叙事模式标注",
                            false));
                }
                index = narration.indexOf(name, index + name.length());
            }
        }
        return violations;
    }
}
