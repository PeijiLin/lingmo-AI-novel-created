package com.linpj.novel.create.rule;

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
 * RC-02 位置跳变规则测试（B1-1：含"看似冲突实则合理"的转场与地名层次陷阱用例）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocationJumpRuleTest {

    @Mock private CharacterStateService characterStateService;
    @Mock private CharacterCardsService characterCardsService;

    private LocationJumpRule rule;

    @BeforeEach
    void setUp() {
        rule = new LocationJumpRule(characterStateService, characterCardsService);
        lenient().when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(card(1L, "林砚"), card(2L, "顾昭")));
        lenient().when(characterStateService.findLocations(eq(1L), any()))
                .thenReturn(Map.of(1L, new CharacterStateService.LocationFact("王都", 90L)));
    }

    private CharacterCardsVo card(Long id, String name) {
        CharacterCardsVo vo = new CharacterCardsVo();
        vo.setId(id);
        vo.setName(name);
        return vo;
    }

    private RuleCheckContext outlineContext(ChapterOutline outline) {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.OUTLINE);
        ctx.setBookId(1L);
        ctx.setChapterId(500L);
        ctx.setOutline(outline);
        return ctx;
    }

    private Scene scene(String location, Long... participants) {
        Scene scene = new Scene();
        scene.setLocation(location);
        scene.setParticipantIds(List.of(participants));
        return scene;
    }

    @Test
    void 首场景与账本位置不符且无移动叙述_违规() {
        ChapterOutline outline = new ChapterOutline();
        outline.setScenes(List.of(scene("剑池", 1L)));

        List<RuleViolation> violations = rule.check(outlineContext(outline));

        assertEquals(1, violations.size());
        assertEquals(RuleSeverity.HIGH, violations.get(0).getSeverity());
        assertTrue(violations.get(0).getEvidence().contains("林砚"));
        assertTrue(violations.get(0).getEvidence().contains("王都"));
    }

    @Test
    void 陷阱用例_地名层次差异视为同地() {
        ChapterOutline outline = new ChapterOutline();
        outline.setScenes(List.of(scene("王都城门", 1L)));

        List<RuleViolation> violations = rule.check(outlineContext(outline));

        assertTrue(violations.isEmpty(), "王都/王都城门为包含关系：视为同地不报");
    }

    @Test
    void 陷阱用例_细纲已声明移动_合法() {
        ChapterOutline outline = new ChapterOutline();
        Scene travel = scene("剑池", 1L);
        travel.setConflict("林砚连夜赶路，奔赴剑池");
        outline.setScenes(List.of(travel));

        List<RuleViolation> violations = rule.check(outlineContext(outline));

        assertTrue(violations.isEmpty(), "移动叙述已交代转场：合法");
    }

    @Test
    void 场景间跳变无移动叙述_违规() {
        // 账本基准与首场景一致（城墙），使本用例聚焦场景间转场判定
        when(characterStateService.findLocations(eq(1L), any()))
                .thenReturn(Map.of(1L, new CharacterStateService.LocationFact("城墙", 90L)));
        ChapterOutline outline = new ChapterOutline();
        outline.setScenes(List.of(scene("城墙", 1L), scene("王都", 1L)));

        List<RuleViolation> violations = rule.check(outlineContext(outline));

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).getEvidence().contains("场景 1"));
    }

    @Test
    void 场景间有中间转场场景_合法() {
        when(characterStateService.findLocations(eq(1L), any()))
                .thenReturn(Map.of(1L, new CharacterStateService.LocationFact("城墙", 90L)));
        ChapterOutline outline = new ChapterOutline();
        Scene first = scene("城墙", 1L);
        Scene travel = scene("官道", 1L);
        travel.setOutcome("两人启程赶往王都");
        outline.setScenes(List.of(first, travel, scene("王都", 1L)));

        List<RuleViolation> violations = rule.check(outlineContext(outline));

        assertTrue(violations.isEmpty(), "中间场景已交代移动：合法");
    }

    @Test
    void 同一角色首场景与场景间双跳变只报一条() {
        when(characterStateService.findLocations(eq(1L), any()))
                .thenReturn(Map.of(1L, new CharacterStateService.LocationFact("王都", 90L)));
        ChapterOutline outline = new ChapterOutline();
        outline.setScenes(List.of(scene("城墙", 1L), scene("剑池", 1L)));

        List<RuleViolation> violations = rule.check(outlineContext(outline));

        assertEquals(1, violations.size(), "同一角色去重：首个跳变事实优先，防噪声风暴");
    }

    @Test
    void 不同地但角色不重叠_合法() {
        ChapterOutline outline = new ChapterOutline();
        outline.setScenes(List.of(scene("城墙", 2L), scene("王都", 1L)));

        List<RuleViolation> violations = rule.check(outlineContext(outline));

        assertTrue(violations.isEmpty(), "分场景不同角色异地并行：合法叙事结构");
    }

    @Test
    void 账本无位置记录_静默() {
        when(characterStateService.findLocations(eq(1L), any())).thenReturn(Map.of());

        ChapterOutline outline = new ChapterOutline();
        outline.setScenes(List.of(scene("剑池", 1L)));

        assertTrue(rule.check(outlineContext(outline)).isEmpty(), "无已知位置基准不作比对");
    }

    @Test
    void 无书上下文_不触碰数据层() {
        RuleCheckContext noBook = RuleCheckContext.of(RuleStage.OUTLINE);
        noBook.setOutline(new ChapterOutline());

        assertTrue(rule.check(noBook).isEmpty());
        verify(characterStateService, never()).findLocations(anyLong(), any());
        verify(characterCardsService, never()).getCharacterCardsByProjectId(anyLong());
    }
}
