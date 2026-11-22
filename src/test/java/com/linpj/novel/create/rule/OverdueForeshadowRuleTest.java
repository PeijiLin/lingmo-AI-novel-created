package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.entity.NovelPlotThread;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.PlotOperation;
import com.linpj.novel.create.service.BookChapterService;
import com.linpj.novel.create.service.PlotThreadService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RC-09 伏笔逾期规则测试（B1-3 排期账本：截止章距告警 + 细纲 RESOLVE 豁免 + 未声明排期不适用）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverdueForeshadowRuleTest {

    @Mock private PlotThreadService plotThreadService;
    @Mock private BookChapterService bookChapterService;

    private OverdueForeshadowRule rule;

    @BeforeEach
    void setUp() {
        rule = new OverdueForeshadowRule(plotThreadService, bookChapterService);
        BookChapter current = chapter(500L, 14);
        BookChapter planted = chapter(200L, 8);
        lenient().when(bookChapterService.getById(500L)).thenReturn(current);
        lenient().when(bookChapterService.getById(200L)).thenReturn(planted);
    }

    private BookChapter chapter(Long id, int num) {
        BookChapter chapter = new BookChapter();
        chapter.setId(id);
        chapter.setBookId(1L);
        chapter.setChapterNum(num);
        return chapter;
    }

    private NovelPlotThread thread(long id, String desc, Long plantedChapterId, Integer deadlineOffset) {
        NovelPlotThread thread = new NovelPlotThread();
        thread.setId(id);
        thread.setBookId(1L);
        thread.setDescription(desc);
        thread.setStatus("OPEN");
        thread.setPlantedChapterId(plantedChapterId);
        thread.setDeadlineOffset(deadlineOffset);
        return thread;
    }

    private RuleCheckContext outlineContext(ChapterOutline outline) {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.OUTLINE);
        ctx.setBookId(1L);
        ctx.setChapterId(500L);
        ctx.setOutline(outline);
        return ctx;
    }

    @Test
    void 超截止章距未回收_告警() {
        // 埋于第 8 章，截止 5 章（应第 13 章内收），当前第 14 章 → 逾期
        when(plotThreadService.listActiveThreads(1L))
                .thenReturn(List.of(thread(7L, "断剑来历", 200L, 5)));

        List<RuleViolation> violations = rule.check(outlineContext(new ChapterOutline()));

        assertEquals(1, violations.size());
        assertEquals(RuleSeverity.LOW, violations.get(0).getSeverity(), "软告警不触发重写（LOW 不进重写门槛）");
        assertTrue(violations.get(0).getEvidence().contains("断剑来历"));
        assertTrue(violations.get(0).getEvidence().contains("6 章"));
    }

    @Test
    void 陷阱用例_窗口内未逾期_不告警() {
        when(plotThreadService.listActiveThreads(1L))
                .thenReturn(List.of(thread(7L, "断剑来历", 200L, 10)));

        assertTrue(rule.check(outlineContext(new ChapterOutline())).isEmpty(),
                "已过 6 章 <= 截止 10 章：仍在窗口内");
    }

    @Test
    void 本章细纲已声明回收_豁免() {
        when(plotThreadService.listActiveThreads(1L))
                .thenReturn(List.of(thread(7L, "断剑来历", 200L, 5)));
        ChapterOutline outline = new ChapterOutline();
        PlotOperation resolve = new PlotOperation();
        resolve.setOp("RESOLVE");
        resolve.setThreadId(7L);
        outline.setPlotOps(List.of(resolve));

        assertTrue(rule.check(outlineContext(outline)).isEmpty(),
                "细纲已声明 RESOLVE：写作意图在路上，不告警");
    }

    @Test
    void 未声明截止章距的存量伏笔_不适用() {
        when(plotThreadService.listActiveThreads(1L))
                .thenReturn(List.of(thread(7L, "城主暗线", 200L, null)));

        assertTrue(rule.check(outlineContext(new ChapterOutline())).isEmpty(),
                "排期属 Planner 新契约：存量无 deadline 伏笔无逾期概念（现状行为）");
    }

    @Test
    void 章号不可得_宁漏勿误() {
        when(plotThreadService.listActiveThreads(1L))
                .thenReturn(List.of(thread(7L, "断剑来历", 200L, 5)));
        when(bookChapterService.getById(500L)).thenReturn(null);

        assertTrue(rule.check(outlineContext(new ChapterOutline())).isEmpty());
    }

    @Test
    void 无章上下文或空账本_静默() {
        RuleCheckContext noChapter = RuleCheckContext.of(RuleStage.OUTLINE);
        noChapter.setBookId(1L);
        assertTrue(rule.check(noChapter).isEmpty());
        verify(plotThreadService, never()).listActiveThreads(anyLong());

        when(plotThreadService.listActiveThreads(1L)).thenReturn(List.of());
        assertTrue(rule.check(outlineContext(new ChapterOutline())).isEmpty());
    }
}
