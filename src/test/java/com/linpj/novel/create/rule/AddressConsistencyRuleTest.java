package com.linpj.novel.create.rule;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.entity.CharacterRelationships;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.BookChapterService;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CharacterRelationshipsService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RC-08 称谓与关系规则测试（B1-1 生效区间 + 称谓表，含合法称谓/区间演化/失效配对陷阱用例）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressConsistencyRuleTest {

    @Mock private CharacterRelationshipsService relationshipsService;
    @Mock private CharacterCardsService characterCardsService;
    @Mock private BookChapterService bookChapterService;

    private AddressConsistencyRule rule;

    @BeforeAll
    static void initEntityMetadata() {
        // Lambda 条件构造需要实体 TableInfo 元数据（规则内构建 Wrapper，纯单测需手动初始化）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                CharacterRelationships.class);
    }

    @BeforeEach
    void setUp() {
        rule = new AddressConsistencyRule(relationshipsService, characterCardsService, bookChapterService);
        lenient().when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(card(1L, "林砚"), card(2L, "顾昭")));
        BookChapter chapter = new BookChapter();
        chapter.setId(500L);
        chapter.setChapterNum(14);
        lenient().when(bookChapterService.getById(500L)).thenReturn(chapter);
        lenient().when(relationshipsService.list(any(Wrapper.class))).thenReturn(List.<CharacterRelationships>of());
    }

    private CharacterCardsVo card(Long id, String name) {
        CharacterCardsVo vo = new CharacterCardsVo();
        vo.setId(id);
        vo.setName(name);
        return vo;
    }

    private CharacterRelationships edge(Long from, Long to, String type, Integer fromChap, Integer toChap) {
        CharacterRelationships edge = new CharacterRelationships();
        edge.setBookId(1L);
        edge.setFromCharacterId(from);
        edge.setToCharacterId(to);
        edge.setRelationshipType(type);
        edge.setChapterFrom(fromChap);
        edge.setChapterTo(toChap);
        return edge;
    }

    private RuleCheckContext draftContext(String draft) {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setBookId(1L);
        ctx.setChapterId(500L);
        ctx.setDraft(draft);
        return ctx;
    }

    @Test
    void 敌对关系用师门称谓_违规() {
        when(relationshipsService.list(any(Wrapper.class))).thenReturn(
                List.of(edge(1L, 2L, "敌对", null, null)));

        List<RuleViolation> violations = rule.check(draftContext("""
                顾昭横剑胸前。“林砚，你我同门二十年，师兄我今日别无选择。”
                风声骤起。
                """));

        assertEquals(1, violations.size());
        assertEquals(RuleSeverity.MEDIUM, violations.get(0).getSeverity());
        assertTrue(violations.get(0).getEvidence().contains("师兄"));
        assertTrue(violations.get(0).getEvidence().contains("敌对"));
    }

    @Test
    void 陷阱用例_生效关系族的称谓合法() {
        when(relationshipsService.list(any(Wrapper.class))).thenReturn(
                List.of(edge(1L, 2L, "师徒", null, null)));

        assertTrue(rule.check(draftContext("""
                顾昭垂首。“师父，弟子知错了。”
                """)).isEmpty(), "师徒关系用师门称谓：合法");
    }

    @Test
    void 陷阱用例_称谓附近无名不做归属判定() {
        when(relationshipsService.list(any(Wrapper.class))).thenReturn(
                List.of(edge(1L, 2L, "敌对", null, null)));

        // "师父"远离双方名字：无法判定对谁称呼，不作硬判定
        assertTrue(rule.check(draftContext("""
                顾昭望着碑林出神。“师父若在，又会如何抉择呢。”
                """)).isEmpty());
    }

    @Test
    void 陷阱用例_区间已演化的旧称谓违规() {
        // 师徒关系第 10 章后失效（区间 1-9），当前第 14 章仍称师父 → 违规（关系演化核心场景）
        when(relationshipsService.list(any(Wrapper.class))).thenReturn(
                List.of(edge(1L, 2L, "师徒", 1, 9)));

        List<RuleViolation> violations = rule.check(draftContext("""
                顾昭垂首。“林砚，师父不能看你一错到底。”
                """));

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).getEvidence().contains("已失效"), "失效关系仍用旧称谓要上报");
    }

    @Test
    void 区间内的新关系称谓合法() {
        // 师徒(1-9)失效 + 敌对(10-)生效：当前章称"逆贼"（敌对族）合法，称"师父"违规
        when(relationshipsService.list(any(Wrapper.class))).thenReturn(List.of(
                edge(1L, 2L, "师徒", 1, 9), edge(1L, 2L, "敌对", 10, null)));

        List<RuleViolation> violations = rule.check(draftContext("""
                “林砚，你这个逆贼！”顾昭咬牙切齿。
                """));

        assertTrue(violations.isEmpty(), "敌对族称谓与生效关系一致：合法");
    }

    @Test
    void 未命中称谓表的关系类型整体跳过() {
        when(relationshipsService.list(any(Wrapper.class))).thenReturn(
                List.of(edge(1L, 2L, "同窗", null, null)));

        assertTrue(rule.check(draftContext("“林砚师兄，请留步。”顾昭喊道。")).isEmpty(),
                "同窗不在称谓表：无法判定合法性，宁漏勿误");
    }

    @Test
    void 区间边存在但章号不可得_规则静默() {
        when(relationshipsService.list(any(Wrapper.class))).thenReturn(
                List.of(edge(1L, 2L, "师徒", 1, 9)));
        when(bookChapterService.getById(500L)).thenReturn(null);

        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setBookId(1L);
        ctx.setChapterId(500L);
        ctx.setDraft("“师父。”");

        assertTrue(rule.check(ctx).isEmpty(), "区间语义无法锚定当前章：整体静默");
    }

    @Test
    void 无关系账本_静默() {
        when(relationshipsService.list(any(Wrapper.class))).thenReturn(List.of());

        assertTrue(rule.check(draftContext("“林砚，师父不能让你胡来。”")).isEmpty());
    }

    @Test
    void 数据通道故障_静默() {
        when(relationshipsService.list(any(Wrapper.class))).thenThrow(new RuntimeException("库不可用"));

        assertTrue(rule.check(draftContext("“师父。”顾昭垂首。")).isEmpty());
        verify(characterCardsService, never()).getCharacterCardsByProjectId(anyLong());
    }
}
