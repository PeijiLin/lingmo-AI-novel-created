package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import com.linpj.novel.create.service.wiki.WikiPageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * RC-W1 / RC-W2 单元测试：规则读 Wiki attrs 硬事实
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
class WikiAttrRulesTest {

    @Mock private WikiPageService wikiPageService;

    private WikiGenderRule genderRule;
    private WikiAgeRule ageRule;

    @BeforeEach
    void setUp() {
        genderRule = new WikiGenderRule(wikiPageService);
        ageRule = new WikiAgeRule(wikiPageService);
    }

    private NovelWikiPage character(Long id, String name, Map<String, Object> attrs) {
        NovelWikiPage page = new NovelWikiPage();
        page.setId(id);
        page.setBookId(1L);
        page.setEntityType("CHARACTER");
        page.setEntityKey("char:" + id);
        page.setTitle(name);
        page.setAttrs(attrs);
        return page;
    }

    private RuleCheckContext draft(String text) {
        RuleCheckContext context = RuleCheckContext.of(RuleStage.DRAFT);
        context.setBookId(1L);
        context.setDraft(text);
        return context;
    }

    // ------------------------------------------------------------------
    // RC-W1 性别称谓
    // ------------------------------------------------------------------

    @Test
    void genderPronounConflictReported() {
        when(wikiPageService.listCanonPages(1L, "CHARACTER"))
                .thenReturn(List.of(character(1L, "林澈", Map.of("gender", "男"))));

        List<RuleViolation> violations = genderRule.check(
                draft("林澈收剑入鞘。她转身望向山道尽头。"));

        assertEquals(1, violations.size());
        assertEquals("RC-W1", violations.get(0).getRuleId());
        assertTrue(violations.get(0).getEvidence().contains("她"));
    }

    @Test
    void matchingPronounNoViolation() {
        when(wikiPageService.listCanonPages(1L, "CHARACTER"))
                .thenReturn(List.of(character(1L, "林澈", Map.of("gender", "男"))));

        List<RuleViolation> violations = genderRule.check(
                draft("林澈收剑入鞘。他转身望向山道尽头。"));

        assertTrue(violations.isEmpty());
    }

    @Test
    void dialoguePronounNotChecked() {
        when(wikiPageService.listCanonPages(1L, "CHARACTER"))
                .thenReturn(List.of(character(1L, "林澈", Map.of("gender", "男"))));

        // 对白层"她"（角色转述他人）：叙述层剥离后不校验
        List<RuleViolation> violations = genderRule.check(
                draft("林澈摇头。“她不在意。”他说。"));

        assertTrue(violations.isEmpty(), "对白层代词不做硬判定");
    }

    @Test
    void multiCharacterSentenceSkipped() {
        when(wikiPageService.listCanonPages(1L, "CHARACTER"))
                .thenReturn(List.of(
                        character(1L, "林澈", Map.of("gender", "男")),
                        character(2L, "苏晚", Map.of("gender", "女"))));

        // 同句两角色：代词归属不可判定 → 跳过（宁漏勿误）
        List<RuleViolation> violations = genderRule.check(
                draft("林澈把信递给苏晚，她看完便折了起来。"));

        assertTrue(violations.isEmpty());
    }

    @Test
    void noAttrsOrNoWikiSilent() {
        when(wikiPageService.listCanonPages(1L, "CHARACTER"))
                .thenReturn(List.of(character(1L, "林澈", Map.of())));

        assertTrue(genderRule.check(draft("林澈收剑。她走了。")).isEmpty(),
                "无 gender attrs 时规则静默");
    }

    // ------------------------------------------------------------------
    // RC-W2 年龄
    // ------------------------------------------------------------------

    @Test
    void ageMismatchReported() {
        when(wikiPageService.listCanonPages(1L, "CHARACTER"))
                .thenReturn(List.of(character(1L, "林澈", Map.of("age", 19))));

        List<RuleViolation> violations = ageRule.check(
                draft("三十五岁的林澈站在山门前，神情复杂。"));

        assertEquals(1, violations.size());
        assertEquals("RC-W2", violations.get(0).getRuleId());
        assertTrue(violations.get(0).getEvidence().contains("35"));
    }

    @Test
    void ageWithinTolerancePasses() {
        when(wikiPageService.listCanonPages(1L, "CHARACTER"))
                .thenReturn(List.of(character(1L, "林澈", Map.of("age", 19))));

        // 剧情推进 ±2 岁合法
        assertTrue(ageRule.check(draft("二十岁的林澈站在山门前。")).isEmpty());
        assertTrue(ageRule.check(draft("十七岁的林澈站在山门前。")).isEmpty());
    }

    @Test
    void ageDialogueNotChecked() {
        when(wikiPageService.listCanonPages(1L, "CHARACTER"))
                .thenReturn(List.of(character(1L, "林澈", Map.of("age", 19))));

        // 对白中角色谎报年龄：不校验
        assertTrue(ageRule.check(draft("林澈答道：“我今年四十二了。”")).isEmpty());
    }

    @Test
    void ageOutOfWindowNotMatched() {
        when(wikiPageService.listCanonPages(1L, "CHARACTER"))
                .thenReturn(List.of(character(1L, "林澈", Map.of("age", 19))));

        // 「N岁」离名字远（>16 字符窗口）：不判定
        assertTrue(ageRule.check(
                draft("山道上走来一位白发老者，约莫七十岁，正是林澈的师祖。")).isEmpty());
    }
}
