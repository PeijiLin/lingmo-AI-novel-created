package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CharacterStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * RC-06 道具连续性规则测试（v2.0 M1-B2 回补：移交白名单陷阱用例）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemContinuityRuleTest {

    @Mock private CharacterStateService characterStateService;
    @Mock private CharacterCardsService characterCardsService;

    private ItemContinuityRule rule;

    @BeforeEach
    void setUp() {
        rule = new ItemContinuityRule(characterStateService, characterCardsService);
        lenient().when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(card(1L, "林砚"), card(2L, "顾昭")));
        // 林砚独占持有青铜剑；传家玉佩两人共持（归属歧义应跳过）
        lenient().when(characterStateService.findHeldItemNames(eq(1L), any()))
                .thenReturn(Map.of(
                        1L, Set.of("青铜剑", "传家玉佩"),
                        2L, Set.of("传家玉佩")));
    }

    private CharacterCardsVo card(Long id, String name) {
        CharacterCardsVo vo = new CharacterCardsVo();
        vo.setId(id);
        vo.setName(name);
        return vo;
    }

    private RuleCheckContext draftContext(String draft) {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setBookId(1L);
        ctx.setDraft(draft);
        return ctx;
    }

    @Test
    void 他人取出独占持有道具_违规() {
        List<RuleViolation> violations = rule.check(draftContext("""
                顾昭疾步上前，从袖中取出青铜剑，横剑当胸。
                城下喊杀声震天。
                """));

        assertEquals(1, violations.size());
        assertEquals(RuleSeverity.MEDIUM, violations.get(0).getSeverity());
        assertTrue(violations.get(0).getEvidence().contains("顾昭"));
        assertTrue(violations.get(0).getEvidence().contains("林砚"));
    }

    @Test
    void 持有人自己取出_无违规() {
        assertTrue(rule.check(draftContext("""
                林砚退后半步，抽出青铜剑，剑锋映着月色。
                """)).isEmpty());
    }

    @Test
    void 陷阱用例_移交叙述豁免() {
        assertTrue(rule.check(draftContext("""
                顾昭接过林砚递来的青铜剑，郑重收入剑鞘。
                """)).isEmpty(), "接过/递来为合法移交，账本定稿时更新");

        assertTrue(rule.check(draftContext("""
                顾昭取出青铜剑——那是林砚还给她的旧物。
                """)).isEmpty(), "还给语境豁免");
    }

    @Test
    void 陷阱用例_对白内取出道具不校验() {
        assertTrue(rule.check(draftContext("""
                顾昭笑道：“待我取出青铜剑，胜你三分。”
                """)).isEmpty());
    }

    @Test
    void 共持道具归属歧义_跳过() {
        assertTrue(rule.check(draftContext("""
                顾昭从怀中取出传家玉佩，端详良久。
                """)).isEmpty(), "双持有人不做归属判定（宁漏勿误）");
    }

    @Test
    void 无取出动作或无账本数据_静默() {
        // 无动作动词：道具出现不等于取出使用
        assertTrue(rule.check(draftContext("墙上的青铜剑早已锈迹斑斑。顾昭叹了口气。")).isEmpty());

        // 未追踪的道具：跳过
        assertTrue(rule.check(draftContext("顾昭抽出短匕，抵在掌心。")).isEmpty());

        // 无账本数据
        when(characterStateService.findHeldItemNames(eq(1L), any())).thenReturn(Map.of());
        assertTrue(rule.check(draftContext("顾昭取出青铜剑。")).isEmpty());
    }
}
