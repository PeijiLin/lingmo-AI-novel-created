package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CharacterStateService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RC-06 道具凭空出现（设计 6.7：medium 硬）
 *
 * 判定（对账本持有物做确定性比对，方向：单一持有人的道具被他人"取出/使用"）：
 * - 道具名与 character_state.itemNames 精确匹配（长名字段不做子串模糊——宁漏勿误）
 * - 仅校验"独占持有"道具（两个及以上角色同时持有 → 归属歧义，跳过）
 * - 动作检测：叙述层出现"取出/掏出/亮出/拔出/抽出/拿出(+了)+道具名"
 * - 归属检测：动作位置向前 24 字符窗口内出现的角色名不是持有人 → 疑似凭空出现
 * - 移交白名单：窗口含 递/送/交/赠/接过/归还/丢给 等移交词 → 合法转移（账本在定稿时更新）
 *
 * 必须诚实声明的局限（默认 OFF，影子态观测后再分级）：
 * - 本章新获得的道具（叙事内首次引入）会被误报——影子数据决定豁免词表是否扩展
 * - 代词指代（"他取出剑"）无法判定归属，跳过（该场景属 RC-13 交 Critic 的主观判定）
 * - 对白内的道具提及不校验（角色谈论道具有任何合法性）
 *
 * @author HL
 */
@Component
public class ItemContinuityRule implements ConsistencyRule {

    /** 取出道具动作模式（可选"了"连接） */
    private static final Pattern DRAW_PATTERN =
            Pattern.compile("(取出|掏出|亮出|拔出|抽出|拿出)了?");

    /** 归属回看窗口（动作向前找操作者角色名） */
    private static final int OWNER_WINDOW = 24;

    /** 移交词白名单：窗口内出现视为合法转移 */
    static final String[] HANDOFF_WORDS = {
            "递给", "送给", "交给", "赠给", "递来", "送来", "交来", "丢给", "拋给", "掷给",
            "接过", "接过来", "归还", "还给", "交还", "买下", "拾得", "捡起", "斩获", "缴获", "战利品",
            "借用", "暂借", "代为保管"
    };

    /** 最小角色名长度（单字名不参与文本归属判定） */
    private static final int MIN_NAME_LENGTH = 2;

    /** 单次校验最多上报数 */
    private static final int MAX_VIOLATIONS = 3;

    private final CharacterStateService characterStateService;
    private final CharacterCardsService characterCardsService;

    public ItemContinuityRule(CharacterStateService characterStateService,
                              CharacterCardsService characterCardsService) {
        this.characterStateService = characterStateService;
        this.characterCardsService = characterCardsService;
    }

    @Override
    public String id() {
        return "RC-06";
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
        return "单一持有人道具被他人取出使用（对账本 itemNames 精确比对）";
    }

    @Override
    public List<RuleViolation> check(RuleCheckContext context) {
        if (context.getBookId() == null || context.getDraft() == null || context.getDraft().isBlank()) {
            return List.of();
        }
        List<CharacterCardsVo> cards;
        try {
            cards = characterCardsService.getCharacterCardsByProjectId(context.getBookId());
        } catch (Exception e) {
            return List.of(); // 数据通道故障：规则静默
        }
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        Map<Long, String> namesById = new LinkedHashMap<>();
        cards.stream()
                .filter(Objects::nonNull)
                .filter(card -> card.getId() != null && card.getName() != null && card.getName().length() >= MIN_NAME_LENGTH)
                .forEach(card -> namesById.put(card.getId(), card.getName()));

        Map<Long, Set<String>> heldItems =
                characterStateService.findHeldItemNames(context.getBookId(), List.copyOf(namesById.keySet()));

        // 独占持有道具 -> 持有人（多持有人跳过）
        Map<String, Long> exclusiveItems = new LinkedHashMap<>();
        Map<String, Integer> itemCount = new LinkedHashMap<>();
        heldItems.forEach((characterId, items) -> items.forEach(item -> itemCount.merge(item, 1, Integer::sum)));
        heldItems.forEach((characterId, items) -> items.forEach(item -> {
            if (itemCount.get(item) == 1) {
                exclusiveItems.put(item, characterId);
            }
        }));
        if (exclusiveItems.isEmpty()) {
            return List.of();
        }

        String narration = RuleTexts.stripDialogue(context.getDraft());
        List<RuleViolation> violations = new ArrayList<>();

        Matcher action = DRAW_PATTERN.matcher(narration);
        while (action.find() && violations.size() < MAX_VIOLATIONS) {
            int itemStart = action.end();
            for (Map.Entry<String, Long> entry : exclusiveItems.entrySet()) {
                String item = entry.getKey();
                if (!narration.startsWith(item, itemStart)) {
                    continue;
                }
                String ownerName = namesById.get(entry.getValue());
                if (ownerName == null) {
                    continue;
                }
                // 移交白名单：动作前后窗口（前 24 后 16）内出现移交词 → 合法转移（账本定稿时更新）
                boolean handoff = RuleTexts.windowContainsAny(
                        narration, action.start() - OWNER_WINDOW,
                        itemStart + item.length() + 16, HANDOFF_WORDS);
                if (handoff) {
                    continue;
                }
                // 归属判定：动作前窗口内的最近角色名是否为持有人
                String actor = nearestCharacterBefore(narration, action.start(), namesById.values());
                if (actor != null && !actor.equals(ownerName)) {
                    violations.add(RuleViolation.of(
                            id(), severity(), RuleStage.DRAFT,
                            String.format("道具「%s」由「%s」持有，但叙述中由「%s」取出：……%s……",
                                    item, ownerName, actor,
                                    RuleTexts.snippet(narration, Math.max(0, action.start() - 10), itemStart + item.length() + 10)),
                            "补写道具移交过程（拾取/赐予/缴获），或修正操作者；若为合法转移请补充移交叙述",
                            false));
                }
                break; // 动作位置只对应首个命中道具
            }
        }
        return violations;
    }

    /** 动作位置向前窗口内最近出现的角色名（含动作位置自身起点的重叠文本；无则 null） */
    private static String nearestCharacterBefore(String narration, int position, java.util.Collection<String> names) {
        int from = Math.max(0, position - OWNER_WINDOW);
        if (from >= position) {
            return null;
        }
        String window = narration.substring(from, position);
        String nearest = null;
        int nearestIndex = -1;
        for (String name : names) {
            if (name == null || name.length() < MIN_NAME_LENGTH) {
                continue;
            }
            int index = window.lastIndexOf(name);
            if (index >= 0 && index > nearestIndex) {
                nearestIndex = index;
                nearest = name;
            }
        }
        return nearest;
    }
}
