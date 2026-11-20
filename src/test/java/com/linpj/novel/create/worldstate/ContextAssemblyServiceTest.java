package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.CriticContext;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.PlannerContext;
import com.linpj.novel.create.pojo.pipeline.WriterContext;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CharacterStateService;
import com.linpj.novel.create.service.PlotThreadService;
import com.linpj.novel.create.service.StoryDigestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 上下文组装服务单元测试（设计 5.2：唯一事实源 + 出场过滤 + 8k 预算硬控）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
class ContextAssemblyServiceTest {

    @Mock private CharacterCardsService characterCardsService;
    @Mock private BookInfoService bookInfoService;
    @Mock private StoryDigestService storyDigestService;
    @Mock private PlotThreadService plotThreadService;
    @Mock private CharacterStateService characterStateService;

    private ContextAssemblyService service;

    @BeforeEach
    void setUp() {
        service = new ContextAssemblyService(characterCardsService, bookInfoService,
                storyDigestService, plotThreadService, characterStateService);
    }

    private PipelineRequest request(String frontendWorldSetting) {
        PipelineRequest r = new PipelineRequest();
        r.setBookId(1L);
        r.setChapterId(10L);
        r.setUserInput("继续写");
        r.setWorldSetting(frontendWorldSetting);
        return r;
    }

    private CharacterCardsVo character(Long id, String name) {
        CharacterCardsVo vo = new CharacterCardsVo();
        vo.setId(id);
        vo.setName(name);
        vo.setDescription(name + "的描述");
        vo.setPersonality("沉稳");
        return vo;
    }

    // ------------------------------------------------------------------
    // worldSetting 唯一事实源
    // ------------------------------------------------------------------

    @Test
    void serverWorldSettingTakesPriorityOverFrontend() {
        BookInfo book = new BookInfo();
        book.setWorldSetting("服务端世界观");
        when(bookInfoService.getById(1L)).thenReturn(book);
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());
        when(characterStateService.renderStates(eq(1L), any())).thenReturn(null);

        WriterContext context = service.assembleWriterContext(request("前端世界观"), null);

        assertEquals("服务端世界观", context.getWorldSetting());
    }

    @Test
    void frontendWorldSettingUsedAsFallback() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());
        when(characterStateService.renderStates(eq(1L), any())).thenReturn(null);

        WriterContext context = service.assembleWriterContext(request("前端世界观"), null);

        assertEquals("前端世界观", context.getWorldSetting());
    }

    @Test
    void frontendWorldSettingUsedWhenBookWorldSettingBlank() {
        BookInfo book = new BookInfo();
        book.setWorldSetting("   ");
        when(bookInfoService.getById(1L)).thenReturn(book);
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());
        when(characterStateService.renderStates(eq(1L), any())).thenReturn(null);

        WriterContext context = service.assembleWriterContext(request("前端世界观"), null);

        assertEquals("前端世界观", context.getWorldSetting());
    }

    // ------------------------------------------------------------------
    // 出场角色过滤
    // ------------------------------------------------------------------

    @Test
    void castFilterLoadsOnlyOutlinedCharacters() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(character(1L, "主角"), character(2L, "配角"), character(3L, "路人")));
        when(characterStateService.renderStates(eq(1L), any())).thenReturn(null);

        ChapterOutline outline = new ChapterOutline();
        outline.setCharacterIds(List.of(1L, 2L));
        WriterContext context = service.assembleWriterContext(request(null), outline);

        assertTrue(context.getCharacterInfo().contains("主角"));
        assertTrue(context.getCharacterInfo().contains("配角"));
        assertFalse(context.getCharacterInfo().contains("路人"), "非出场角色应被过滤");
    }

    @Test
    void missingCastFallsBackToAllCharacters() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(character(1L, "主角"), character(2L, "配角")));
        when(characterStateService.renderStates(eq(1L), any())).thenReturn(null);

        // 大纲引用的角色卡全部缺失：回退全量
        ChapterOutline outline = new ChapterOutline();
        outline.setCharacterIds(List.of(99L));
        WriterContext context = service.assembleWriterContext(request(null), outline);

        assertTrue(context.getCharacterInfo().contains("主角"));
        assertTrue(context.getCharacterInfo().contains("配角"));
    }

    // ------------------------------------------------------------------
    // 预算裁剪（设计 5.2：风格 < digest 截断 < 非出场角色 < 历史伏笔）
    // ------------------------------------------------------------------

    @Test
    void writerContextWithinBudgetKeepsEverything() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());
        when(characterStateService.renderStates(eq(1L), any())).thenReturn(null);
        PipelineRequest req = request("世界观");
        req.setStyle("古典武侠");
        when(storyDigestService.getDigest(1L)).thenReturn("前情摘要");
        when(plotThreadService.renderActiveThreads(1L)).thenReturn("[伏笔#1] 暗线");

        WriterContext context = service.assembleWriterContext(req, null);

        assertEquals("古典武侠", context.getStyle());
        assertEquals("前情摘要", context.getStoryDigest());
        assertEquals("[伏笔#1] 暗线", context.getActivePlotThreads());
    }

    @Test
    void overBudgetTrimsStyleThenDigestThenPlotThreads() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());
        when(characterStateService.renderStates(eq(1L), any())).thenReturn(null);

        PipelineRequest req = request("字".repeat(7000));
        req.setStyle("风".repeat(500));
        when(storyDigestService.getDigest(1L)).thenReturn("摘".repeat(3000));
        when(plotThreadService.renderActiveThreads(1L)).thenReturn("伏".repeat(500));

        WriterContext context = service.assembleWriterContext(req, null);

        // 1. 风格说明先裁
        assertNull(context.getStyle());
        // 2. digest 截断到保留尾部
        assertNotNull(context.getStoryDigest());
        assertTrue(context.getStoryDigest().startsWith("……"));
        assertTrue(context.getStoryDigest().length() <= 700);
        // 3. 超预算时历史伏笔最后裁掉
        assertNull(context.getActivePlotThreads());
        // 出场角色卡与用户输入永不裁
        assertEquals("字".repeat(7000), context.getWorldSetting());
        assertEquals("继续写", context.getUserInput());
    }

    @Test
    void criticContextAlsoAppliesBudgetTrimming() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());

        PipelineRequest req = request("界".repeat(7000));
        req.setStyle("风".repeat(500));
        when(storyDigestService.getDigest(1L)).thenReturn("摘".repeat(3000));
        when(plotThreadService.renderActiveThreads(1L)).thenReturn("伏".repeat(500));

        CriticContext context = service.assembleCriticContext(req, "待审正文", null);

        assertNull(context.getStyle(), "Critic 上下文超预算应先裁风格");
        assertTrue(context.getStoryDigest().length() <= 700, "digest 应截断");
        assertNull(context.getActivePlotThreads());
        // 待审正文是审查对象，永不裁
        assertEquals("待审正文", context.getDraft());
    }

    @Test
    void plannerContextTrimsButNeverCasts() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        // Planner 的角色列表是 characterIds 的选择基准，超预算也不裁
        when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(character(1L, "主角"), character(2L, "配角")));

        PipelineRequest req = request("界".repeat(7000));
        req.setStyle("风".repeat(500));
        when(storyDigestService.getDigest(1L)).thenReturn("摘".repeat(3000));
        when(plotThreadService.renderActiveThreads(1L)).thenReturn("伏".repeat(500));

        PlannerContext context = service.assemblePlannerContext(req);

        assertNull(context.getStyle());
        assertTrue(context.getStoryDigest().length() <= 700);
        assertNull(context.getActivePlotThreads());
        assertNotNull(context.getCharacterInfo());
        assertTrue(context.getCharacterInfo().contains("角色ID: 1"), "角色选择基准永不裁");
    }
}
