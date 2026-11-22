package com.linpj.novel.create.rule;

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
 * RC-02 位置跳变（设计 6.7：high 硬）——B1-1 落地，位置事实账本的确定性消费端
 *
 * 判定（两级，均在细纲层 ID/文本比对，不动成稿）：
 * - 首场景 vs 账本：出场角色的上一已知位置与本场景地点不一致，且细纲（冲突/结果）无移动叙述 → 疑似跳变
 * - 场景间：同一角色在前后场景地点不一致，且中间场景无移动叙述 → 疑似跳变
 *
 * 移动叙述判定：冲突/结果文本含移动类关键词（启程/抵达/赶往/连夜赶路…）。
 * 位置等价判定：trim 后相等，或互相包含（"青云宗"与"青云宗剑池"视为一致——LLM 位置表述存在层次差异）。
 *
 * 必须诚实声明的局限（默认 OFF，影子态观测后再分级）：
 * - 位置为抽取自由文本，同地异名（"帝都"/"王都"）不判等——影子数据决定豁免词表
 * - 移动关键词表无法穷举，含蓄转场（"夜色换了几重"）会漏判移动——宁漏勿误
 * - 移动如果是本场景内先行叙述（"他走出大殿，来到剑池"在 conflict 字段外）会误报——D6 豁免通道兜底
 *
 * @author HL
 */
@Component
public class LocationJumpRule implements ConsistencyRule {

    /** 移动类关键词：出现即视为细纲声明了转场过程 */
    static final String[] TRAVEL_WORDS = {
            "离开", "抵达", "出发", "赶往", "赶至", "赶赴", "奔赴", "启程", "动身", "起程",
            "连夜", "赶路", "来到", "走至", "行至", "移步", "返回", "折返", "转入", "来到", "踏入", "进入"
    };

    /** 最小位置名长度（单字地名不做比对） */
    private static final int MIN_LOCATION_LENGTH = 2;

    /** 单次校验最多上报数 */
    private static final int MAX_VIOLATIONS = 3;

    private final CharacterStateService characterStateService;
    private final CharacterCardsService characterCardsService;

    public LocationJumpRule(CharacterStateService characterStateService,
                            CharacterCardsService characterCardsService) {
        this.characterStateService = characterStateService;
        this.characterCardsService = characterCardsService;
    }

    @Override
    public String id() {
        return "RC-02";
    }

    @Override
    public List<RuleStage> stages() {
        return List.of(RuleStage.OUTLINE);
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public String description() {
        return "位置跳变无移动叙述（细纲场景地点 vs 位置事实账本，确定性比对）";
    }

    @Override
    public List<RuleViolation> check(RuleCheckContext context) {
        if (context.getBookId() == null || context.getOutline() == null
                || context.getOutline().getScenes() == null || context.getOutline().getScenes().isEmpty()) {
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
                .filter(card -> card.getId() != null)
                .forEach(card -> namesById.put(card.getId(), card.getName()));

        List<String> violations = new ArrayList<>();
        List<Scene> scenes = context.getOutline().getScenes();
        // 同一角色一次校验只报一条（首个跳变事实）：定位细节在证据里，去重防噪声
        java.util.Set<Long> flagged = new java.util.HashSet<>();

        // 1. 首场景 vs 位置事实账本（跳章基准：角色上一已知位置）
        checkFirstSceneAgainstLedger(context, scenes.get(0), namesById, violations, flagged);

        // 2. 场景间跳变（同一角色前后场景地点不一致且中间无移动叙述）
        checkSceneTransitions(scenes, namesById, violations, flagged);

        return violations.stream()
                .limit(MAX_VIOLATIONS)
                .map(text -> RuleViolation.of(id(), severity(), RuleStage.OUTLINE, text,
                        "补写赶路/启程叙述，或修正场景地点；隔章远途转场请在细纲中显式声明", false))
                .toList();
    }

    /** 首场景 vs 账本位置：不一致且场景自身无移动叙述 → 跳变疑似 */
    private void checkFirstSceneAgainstLedger(RuleCheckContext context, Scene first,
                                             Map<Long, String> namesById, List<String> violations,
                                             java.util.Set<Long> flagged) {
        if (first == null || first.getParticipantIds() == null || isBlank(first.getLocation())) {
            return;
        }
        boolean declaredTravel = sceneDeclaresTravel(first);
        Map<Long, CharacterStateService.LocationFact> locations;
        try {
            locations = characterStateService.findLocations(context.getBookId(),
                    first.getParticipantIds());
        } catch (Exception e) {
            return; // 数据通道故障：规则静默
        }
        for (Long participantId : first.getParticipantIds()) {
            if (violations.size() >= MAX_VIOLATIONS || flagged.contains(participantId)) {
                continue;
            }
            CharacterStateService.LocationFact fact = locations.get(participantId);
            if (fact == null || isBlank(fact.location()) || declaredTravel) {
                continue; // 无已知位置 / 场景已声明移动：跳过（不计入 flagged——后续场景间检查仍有效）
            }
            if (!locationsEquivalent(fact.location(), first.getLocation())) {
                flagged.add(participantId);
                String name = namesById.getOrDefault(participantId, "角色#" + participantId);
                violations.add(String.format("角色「%s」上一已知位置「%s」，首场景却在「%s」且无移动叙述",
                        name, fact.location(), first.getLocation()));
            }
        }
    }

    /** 场景间：同一角色出现在地点不同的相邻场景簇，且中间场景均无移动叙述 */
    private void checkSceneTransitions(List<Scene> scenes, Map<Long, String> namesById,
                                       List<String> violations, java.util.Set<Long> flagged) {
        for (int i = 0; i < scenes.size() && violations.size() < MAX_VIOLATIONS; i++) {
            Scene from = scenes.get(i);
            if (from == null || from.getParticipantIds() == null || isBlank(from.getLocation())) {
                continue;
            }
            for (int j = i + 1; j < scenes.size(); j++) {
                Scene to = scenes.get(j);
                if (to == null || to.getParticipantIds() == null || isBlank(to.getLocation())) {
                    continue;
                }
                if (locationsEquivalent(from.getLocation(), to.getLocation())) {
                    continue; // 同地：无需移动
                }
                // 中间段（含起点/目标场景自身）任何一个声明移动即视为已交代转场
                boolean declared = sceneDeclaresTravel(from) || sceneDeclaresTravel(to);
                for (int k = i + 1; k < j && !declared; k++) {
                    declared = sceneDeclaresTravel(scenes.get(k));
                }
                if (declared) {
                    continue;
                }
                for (Long participantId : from.getParticipantIds()) {
                    if (violations.size() >= MAX_VIOLATIONS) {
                        return;
                    }
                    if (to.getParticipantIds().contains(participantId) && !flagged.contains(participantId)) {
                        flagged.add(participantId);
                        String name = namesById.getOrDefault(participantId, "角色#" + participantId);
                        violations.add(String.format("角色「%s」场景 %d 在「%s」，场景 %d 转至「%s」，期间无移动叙述",
                                name, i + 1, from.getLocation(), j + 1, to.getLocation()));
                    }
                }
            }
        }
    }

    /** 场景是否声明了移动（冲突/结果文本含移动关键词） */
    private static boolean sceneDeclaresTravel(Scene scene) {
        if (scene == null) {
            return false;
        }
        String text = (scene.getConflict() == null ? "" : scene.getConflict())
                + (scene.getOutcome() == null ? "" : scene.getOutcome());
        for (String word : TRAVEL_WORDS) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** 位置等价：trim 相等或互相包含（"青云宗"与"青云宗剑池"视为一致） */
    private static boolean locationsEquivalent(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        if (left.length() < MIN_LOCATION_LENGTH || right.length() < MIN_LOCATION_LENGTH) {
            return true; // 太短不可比（单字地名不作位置判定——宁漏勿误）
        }
        return left.equals(right) || left.contains(right) || right.contains(left);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
