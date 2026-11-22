package com.linpj.novel.create.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.entity.CharacterRelationships;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.BookChapterService;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CharacterRelationshipsService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * RC-08 称谓与关系不符（设计 6.7：medium 硬，启发式）——B1-1 关系生效区间的消费端
 *
 * 判定（对白层，叙述层称谓无法判定说话者故不检）：
 * - 从 character_relationships 取"当前章生效"的关系边（chapter 区间 covers 语义，无区间 = 长期有效）
 * - 对白段中出现"某关系族的称谓词"，且称谓附近出现关系另一方角色名，
 *   而该关系族不在两角色的当前生效关系类型中 → 称谓与关系不符
 *
 * 关系族称谓表只收录强指向词（师父/爹爹/夫君/老贼等），泛化词（兄弟/朋友/你）不入表。
 *
 * 必须诚实声明的局限（默认 OFF，影子态观测后再分级）：
 * - relationshipType 为自由文本，未命中称谓表的关系类型整体跳过（如"同窗"）
 * - 关系演化若未维护生效区间（沿用旧行），已变化的关系会以旧称谓为合法 → 漏检
 * - 反讽/伪装称谓（明知是敌却客气）属于修辞，会误报——D6 豁免通道兜底
 *
 * @author HL
 */
@Component
public class AddressConsistencyRule implements ConsistencyRule {

    /** 关系族 → 称谓词（强指向词优先，弱词一律不收——误报控制优先于召回） */
    private static final Map<String, String[]> FAMILY_TERMS = new LinkedHashMap<>();

    static {
        FAMILY_TERMS.put("师徒", new String[]{"师父", "师尊", "师伯", "师叔", "师祖", "徒儿", "为师", "劣徒", "师兄", "师姐", "师弟", "师妹"});
        FAMILY_TERMS.put("父子", new String[]{"爹爹", "爹", "父亲", "娘亲", "娘", "母亲", "孩儿", "儿啊"});
        FAMILY_TERMS.put("兄弟", new String[]{"大哥", "二哥", "三哥", "兄长", "贤弟", "愚兄"});
        FAMILY_TERMS.put("姐妹", new String[]{"姐姐", "妹妹", "贤妹", "愚姐"});
        FAMILY_TERMS.put("夫妻", new String[]{"夫君", "相公", "娘子", "为夫", "为妻"});
        FAMILY_TERMS.put("主仆", new String[]{"主人", "属下", "奴婢", "老爷", "家主"});
        FAMILY_TERMS.put("敌对", new String[]{"老贼", "狗贼", "逆贼", "奸贼", "贼子", "恶贼"});
    }

    /** 称谓与角色名相邻窗口（双方名字出现在称谓前后该距离内即视为"对该角色称呼"） */
    private static final int TERM_NAME_WINDOW = 16;

    /** 最小角色名长度（单字名不做文本归属判定） */
    private static final int MIN_NAME_LENGTH = 2;

    /** 单次校验最多上报数 */
    private static final int MAX_VIOLATIONS = 3;

    private final CharacterRelationshipsService relationshipsService;
    private final CharacterCardsService characterCardsService;
    private final BookChapterService bookChapterService;

    public AddressConsistencyRule(CharacterRelationshipsService relationshipsService,
                                  CharacterCardsService characterCardsService,
                                  BookChapterService bookChapterService) {
        this.relationshipsService = relationshipsService;
        this.characterCardsService = characterCardsService;
        this.bookChapterService = bookChapterService;
    }

    @Override
    public String id() {
        return "RC-08";
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
        return "对白称谓与当前生效关系不符（关系生效区间 + 关系族称谓表，启发式）";
    }

    @Override
    public List<RuleViolation> check(RuleCheckContext context) {
        if (context.getBookId() == null || context.getDraft() == null || context.getDraft().isBlank()) {
            return List.of();
        }
        List<CharacterRelationships> edges;
        try {
            edges = relationshipsService.list(new LambdaQueryWrapper<CharacterRelationships>()
                    .eq(CharacterRelationships::getBookId, context.getBookId()));
        } catch (Exception e) {
            return List.of(); // 数据通道故障：规则静默
        }
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        List<CharacterCardsVo> cards;
        try {
            cards = characterCardsService.getCharacterCardsByProjectId(context.getBookId());
        } catch (Exception e) {
            return List.of();
        }
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        Map<Long, String> namesById = new LinkedHashMap<>();
        cards.stream()
                .filter(Objects::nonNull)
                .filter(card -> card.getId() != null
                        && card.getName() != null && card.getName().length() >= MIN_NAME_LENGTH)
                .forEach(card -> namesById.put(card.getId(), card.getName()));

        Integer currentNum = resolveChapterNum(context.getChapterId());

        // 生效边解析：无区间 = 长期有效；有区间但章号不可得 → 区间语义不可判，该规则整体静默
        boolean hasIntervalEdge = edges.stream().anyMatch(AddressConsistencyRule::hasInterval);
        if (hasIntervalEdge && currentNum == null) {
            return List.of();
        }
        // 配对 → 当前生效关系族集合（可空集 = 关系已按区间全部失效，旧称谓即违规——B1-1 的核心场景）
        Map<String, Set<String>> legalFamiliesByPair = new LinkedHashMap<>(); // key: minId-maxId
        Map<String, String> pairDesc = new LinkedHashMap<>();
        for (CharacterRelationships edge : edges) {
            if (edge == null || edge.getFromCharacterId() == null || edge.getToCharacterId() == null
                    || edge.getRelationshipType() == null) {
                continue;
            }
            String family = matchFamily(edge.getRelationshipType());
            if (family == null) {
                continue; // 未命中称谓表的关系类型：无法判定称谓合法性，跳过
            }
            String key = pairKey(edge.getFromCharacterId(), edge.getToCharacterId());
            boolean effective = !hasInterval(edge) || (currentNum != null && covers(edge, currentNum));
            if (effective) {
                legalFamiliesByPair.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(family);
            } else {
                // 失效边也要占位：配对进入校验域，且生效族集合保持为空集（如有）
                legalFamiliesByPair.computeIfAbsent(key, k -> new LinkedHashSet<>());
            }
            pairDesc.putIfAbsent(key, edge.getRelationshipType());
        }
        if (legalFamiliesByPair.isEmpty()) {
            return List.of();
        }

        List<RuleViolation> violations = new ArrayList<>();
        for (String segment : RuleTexts.dialogueSegments(context.getDraft())) {
            for (Map.Entry<String, Set<String>> entry : legalFamiliesByPair.entrySet()) {
                if (violations.size() >= MAX_VIOLATIONS) {
                    return violations;
                }
                String[] ids = entry.getKey().split("-");
                Long fromId = Long.parseLong(ids[0]);
                Long toId = Long.parseLong(ids[1]);
                boolean noActive = entry.getValue().isEmpty();
                for (Map.Entry<String, String[]> family : FAMILY_TERMS.entrySet()) {
                    if (entry.getValue().contains(family.getKey())) {
                        continue; // 当前生效关系族的称谓：合法
                    }
                    String term = firstTermNearName(segment, family.getValue(),
                            namesById.get(fromId), namesById.get(toId));
                    if (term != null) {
                        String relationNote = noActive
                                ? "当前无生效关系（原「" + pairDesc.get(entry.getKey()) + "」按章区间已失效）"
                                : "当前生效关系为「" + pairDesc.get(entry.getKey()) + "」";
                        violations.add(RuleViolation.of(id(), severity(), RuleStage.DRAFT,
                                String.format("对白以「%s」（%s系称谓）相称，但「%s」与「%s」%s：……%s……",
                                        term, family.getKey(), namesById.get(fromId), namesById.get(toId),
                                        relationNote,
                                        segment.length() > 40 ? segment.substring(0, 40) + "…" : segment),
                                "修正称谓，或确认关系已演化并在关系账本维护生效区间", false));
                    }
                }
            }
        }
        return violations;
    }

    /** 段内出现该族称谓词，且称谓附近出现任一方角色名（否则无法判定是对谁称呼） */
    private static String firstTermNearName(String segment, String[] terms, String nameA, String nameB) {
        for (String term : terms) {
            int index = segment.indexOf(term);
            while (index >= 0) {
                int termEnd = index + term.length();
                if (nearestName(segment, index, termEnd, nameA, nameB) != null) {
                    return term;
                }
                index = segment.indexOf(term, termEnd);
            }
        }
        return null;
    }

    /** 称谓前后窗口内出现任一方角色名 */
    private static String nearestName(String segment, int termStart, int termEnd, String nameA, String nameB) {
        for (String name : new String[]{nameA, nameB}) {
            if (name == null) {
                continue;
            }
            int from = Math.max(0, termStart - TERM_NAME_WINDOW);
            int to = Math.min(segment.length(), termEnd + TERM_NAME_WINDOW);
            if (from >= to) {
                continue;
            }
            if (segment.substring(from, to).contains(name)) {
                return name;
            }
        }
        return null;
    }

    /** 自由文本关系类型 → 关系族（包含匹配；"结拜兄弟"命中"兄弟"） */
    private static String matchFamily(String relationshipType) {
        String type = relationshipType.trim();
        for (String family : FAMILY_TERMS.keySet()) {
            if (type.contains(family)) {
                return family;
            }
        }
        return null;
    }

    private static boolean hasInterval(CharacterRelationships edge) {
        return edge.getChapterFrom() != null || edge.getChapterTo() != null;
    }

    /** 生效章区间判定（闭区间；from/to 为 null 视为开边界——语义与 novel_state_fact 一致） */
    private static boolean covers(CharacterRelationships edge, int chapterNum) {
        boolean afterFrom = edge.getChapterFrom() == null || chapterNum >= edge.getChapterFrom();
        boolean beforeTo = edge.getChapterTo() == null || chapterNum <= edge.getChapterTo();
        return afterFrom && beforeTo;
    }

    private static String pairKey(Long from, Long to) {
        return from < to ? from + "-" + to : to + "-" + from;
    }

    /** 记录 ID → 章号（解析失败返回 null：区间边存在时规则静默，宁漏勿误） */
    private Integer resolveChapterNum(Long chapterId) {
        if (chapterId == null) {
            return null;
        }
        try {
            BookChapter chapter = bookChapterService.getById(chapterId);
            return chapter == null ? null : chapter.getChapterNum();
        } catch (Exception e) {
            return null;
        }
    }
}
