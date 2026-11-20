package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.pipeline.AliveStatus;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.Scene;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RC-01 死亡出场规则测试（v2.0 M1-B2 回补：含"看似冲突实则合理"陷阱用例）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeadCharacterRuleTest {

    @Mock private CharacterStateService characterStateService;
    @Mock private CharacterCardsService characterCardsService;

    private DeadCharacterRule rule;

    @BeforeEach
    void setUp() {
        rule = new DeadCharacterRule(characterStateService, characterCardsService);
        lenient().when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(card(1L, "林砚"), card(2L, "顾昭")));
        lenient().when(characterStateService.findAliveStatuses(eq(1L), any()))
                .thenReturn(Map.of(1L, AliveStatus.DEAD, 2L, AliveStatus.ALIVE));
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
    void 细纲死者参与场景_确定性违规() {
        ChapterOutline outline = new ChapterOutline();
        Scene scene = new Scene();
        scene.setLocation("城墙");
        scene.setParticipantIds(List.of(2L, 1L)); // 1 号已死
        outline.setScenes(List.of(scene));

        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.OUTLINE);
        ctx.setBookId(1L);
        ctx.setOutline(outline);

        List<RuleViolation> violations = rule.check(ctx);

        assertEquals(1, violations.size());
        assertEquals(RuleSeverity.HIGH, violations.get(0).getSeverity());
        assertTrue(violations.get(0).getEvidence().contains("林砚"));
    }

    @Test
    void 细纲仅活人参与_无违规() {
        ChapterOutline outline = new ChapterOutline();
        Scene scene = new Scene();
        scene.setParticipantIds(List.of(2L));
        outline.setScenes(List.of(scene));

        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.OUTLINE);
        ctx.setBookId(1L);
        ctx.setOutline(outline);

        assertTrue(rule.check(ctx).isEmpty());
    }

    @Test
    void 成稿叙述层死人言行_违规() {
        List<RuleViolation> violations = rule.check(draftContext("""
                城头风紧。林砚按剑而立，目光扫过敌阵。
                顾昭领命下城。
                """));

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).getEvidence().contains("林砚按剑而立"));
    }

    @Test
    void 陷阱用例_对白内提及死者合法() {
        List<RuleViolation> violations = rule.check(draftContext("""
                顾昭望着空荡的城头。“若林砚还在，他一定会答应的。”她轻声说。
                风声呜咽。
                """));

        // "林砚"只出现在对白引号内，叙述层剥离后无残留
        assertTrue(violations.isEmpty(), "对白内谈论逝者是合法叙事");
    }

    @Test
    void 陷阱用例_回忆与身后事语境豁免() {
        List<RuleViolation> violations = rule.check(draftContext("""
                顾昭想起林砚生前最爱坐的位置，如今只剩一壶冷酒。
                她将林砚的遗物收入行囊，转身下城。
                """));

        assertTrue(violations.isEmpty(), "回忆/遗物语境为提及而非言行出场");
    }

    @Test
    void 状态未知_规则静默宁可漏检() {
        when(characterStateService.findAliveStatuses(eq(1L), any()))
                .thenReturn(Map.of(1L, AliveStatus.UNKNOWN));
        assertTrue(rule.check(draftContext("林砚拔剑出鞘，一剑破空。")).isEmpty());
    }

    @Test
    void 无书籍上下文_不触碰数据层() {
        RuleCheckContext noBook = RuleCheckContext.of(RuleStage.DRAFT);
        noBook.setDraft("林砚拔剑出鞘");

        assertTrue(rule.check(noBook).isEmpty());
        verify(characterStateService, never()).findAliveStatuses(anyLong(), any());
        verify(characterCardsService, never()).getCharacterCardsByProjectId(anyLong());
    }

    @Test
    void 单字名不做文本匹配() {
        when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(card(9L, "砚")));
        when(characterStateService.findAliveStatuses(eq(1L), any()))
                .thenReturn(Map.of(9L, AliveStatus.DEAD));

        assertTrue(rule.check(draftContext("砚立于城头，一言不发。")).isEmpty(),
                "单字名文本匹配误报率不可接受：仅保留 ID 级（细纲）判定");
    }
}
