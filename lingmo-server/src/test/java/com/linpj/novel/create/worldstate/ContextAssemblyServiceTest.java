package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.pojo.entity.CharacterRelationships;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.CriticContext;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.PlannerContext;
import com.linpj.novel.create.pojo.pipeline.WriterContext;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.BookChapterService;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CharacterRelationshipsService;
import com.linpj.novel.create.service.CharacterStateService;
import com.linpj.novel.create.service.wiki.WikiPageService;
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
    @Mock private CharacterRelationshipsService characterRelationshipsService;
    @Mock private BookChapterService bookChapterService;
    @Mock private WikiPageService wikiPageService;

    private ContextAssemblyService service;

    @BeforeEach
    void setUp() {
        service = new ContextAssemblyService(characterCardsService, bookInfoService,
                storyDigestService, plotThreadService, characterStateService,
                characterRelationshipsService, bookChapterService, wikiPageService);
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

    @Test
    void relationshipEdgesPrependToCharacterInfoWithChapterFilter() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(character(1L, "主角"), character(2L, "配角")));
        when(characterStateService.renderStates(eq(1L), any())).thenReturn(null);
        BookChapter chapter = new BookChapter();
        chapter.setChapterNum(5);
        when(bookChapterService.getById(10L)).thenReturn(chapter);
        CharacterRelationships edge = new CharacterRelationships();
        edge.setBookId(1L);
        edge.setFromCharacterId(1L);
        edge.setToCharacterId(2L);
        edge.setRelationshipType("师徒");
        edge.setDescription("引路人");
        edge.setIntensity(7);
        when(characterRelationshipsService.listActive(1L, 5)).thenReturn(List.of(edge));

        WriterContext context = service.assembleWriterContext(request(null), null);

        assertTrue(context.getCharacterInfo().contains("当前生效角色关系（第5章）"));
        assertTrue(context.getCharacterInfo().contains("主角 → 配角"));
        assertTrue(context.getCharacterInfo().contains("师徒"));
        assertTrue(context.getCharacterInfo().contains("[强度7]"));
    }

    // ------------------------------------------------------------------
    // Wiki 正典页注入（M3：黑板权威依据 + provenance 标记）
    // ------------------------------------------------------------------

    @Test
    void wikiPagesPrependToWorldSettingWithProvenanceMarks() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());
        when(characterStateService.renderStates(eq(1L), any())).thenReturn(null);
        com.linpj.novel.create.pojo.entity.NovelWikiPage page =
                new com.linpj.novel.create.pojo.entity.NovelWikiPage();
        page.setId(33L);
        page.setBookId(1L);
        page.setEntityType("WORLD_RULE");
        page.setEntityKey("rule:world_setting");
        page.setTitle("世界观总纲");
        page.setCanonical("灵潮衰退后的九州。");
        when(wikiPageService.listCanonPages(1L)).thenReturn(List.of(page));
        when(wikiPageService.renderPages(eq(1L), any()))
                .thenReturn("【正典设定页（以此为准，未列出的事实不得自行新增）】\n- [wiki:33] 世界观总纲\n  灵潮衰退后的九州。\n");

        WriterContext context = service.assembleWriterContext(request("原始设定文本"), null);

        assertNotNull(context.getWorldSetting());
        assertTrue(context.getWorldSetting().startsWith("【正典设定页"),
                "正典页块应前置");
        assertTrue(context.getWorldSetting().contains("[wiki:33]"), "provenance 标记应存在");
        assertTrue(context.getWorldSetting().contains("原始设定文本"), "原始设定保留在页块之后");
    }

    @Test
    void wikiChannelFailureDoesNotBlockAssembly() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());
        when(characterStateService.renderStates(eq(1L), any())).thenReturn(null);
        when(wikiPageService.listCanonPages(1L)).thenThrow(new RuntimeException("db down"));

        WriterContext context = service.assembleWriterContext(request("前端世界观"), null);

        assertEquals("前端世界观", context.getWorldSetting(), "Wiki 故障时回退原始设定");
    }

    @Test
    void wikiHardCapKeepsWikiBlockAndTrimsOriginal() {
        // 页块单行（与实现按首行拆分的结构一致）：provenance 在行内，之后接超长原始设定
        String wikiBlock = "【正典设定页】- [wiki:1] 页卡硬事实填充" + "硬".repeat(200);
        String original = "原".repeat(8000);
        String combined = wikiBlock + "\n" + original;

        String capped = ContextAssemblyService.wikiAwareHardCap(combined);

        assertTrue(capped.startsWith("【正典设定页"), "页块开头完整保留");
        assertTrue(capped.contains("[wiki:1]"), "provenance 不丢");
        assertTrue(capped.length() <= 8000 * 2 / 5 + 60,
                "整体应压在预算 2/5 附近，实际 " + capped.length());
        // 原始设定保尾部 2978 字：总长 3203 - 页块 225
        assertTrue(capped.endsWith("原".repeat(100)), "原始设定保尾部（被截断但保留）");
        assertTrue(capped.length() < original.length(), "原始设定确实被压缩");
    }

    @Test
    void nonWikiWorldSettingStillPlainHardCap() {
        String plain = "设".repeat(9000);
        String capped = ContextAssemblyService.wikiAwareHardCap(plain);
        assertTrue(capped.contains("已截断"), "非页块结构走普通硬截断");
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
        // 4. 极端兜底（TC-01 修复）：裁完前三步仍超预算时，世界观设定硬截断到 2/5 预算
        //    （旧行为 7000 字放行超预算 = 上下文膨胀缺陷，已修复）
        assertNotNull(context.getWorldSetting());
        assertTrue(context.getWorldSetting().startsWith("字"));
        assertTrue(context.getWorldSetting().contains("已截断"),
                "超限设定应带截断标记（完整版见书籍设定）");
        assertTrue(context.getWorldSetting().length() <= 8000 * 2 / 5 + 30, "设定应压到预算 2/5");
        // 用户输入永不裁
        assertEquals("继续写", context.getUserInput());
    }

    @Test
    void criticContextAlsoAppliesBudgetTrimming() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());

        PipelineRequest req = request("界".repeat(7000));
        req.setStyle("风".repeat(500));
        when(storyDigestService.getDigest(1L)).thenReturn("摘".repeat(3000));
        when(plotThreadService.renderPlannerThreads(1L, 10L)).thenReturn("伏".repeat(500));

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
        when(plotThreadService.renderPlannerThreads(1L, 10L)).thenReturn("伏".repeat(500));

        PlannerContext context = service.assemblePlannerContext(req);

        assertNull(context.getStyle());
        assertTrue(context.getStoryDigest().length() <= 700);
        assertNull(context.getActivePlotThreads());
        assertNotNull(context.getCharacterInfo());
        assertTrue(context.getCharacterInfo().contains("角色ID: 1"), "角色选择基准永不裁");
    }

    // ------------------------------------------------------------------
    // 伏笔渲染双口径（B1-8：H4 可见性隔离——排期 meta 只到 Planner/Critic，不到 Writer）
    // ------------------------------------------------------------------

    @Test
    void 伏笔排期仅下发Planner与Critic不进Writer() {
        when(bookInfoService.getById(1L)).thenReturn(null);
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());
        when(characterStateService.renderStates(eq(1L), any())).thenReturn(null);
        when(plotThreadService.renderActiveThreads(1L)).thenReturn("[伏笔#7] 断剑来历");
        when(plotThreadService.renderPlannerThreads(1L, 10L))
                .thenReturn("[伏笔#7] 断剑来历（须 5 章内回收）");

        WriterContext writer = service.assembleWriterContext(request(null), null);
        PlannerContext planner = service.assemblePlannerContext(request(null));
        CriticContext critic = service.assembleCriticContext(request(null), "正文", null);

        // Writer 只拿创作口径：知道"埋什么"但不知道"何时收"，防提前兑付；
        // Polisher 不消费伏笔（无该上下文槽位），矩阵的 ❌ 侧由装配器强制保证而非提示词自觉
        assertEquals("[伏笔#7] 断剑来历", writer.getActivePlotThreads(),
                "Writer 不见排期 meta（H4 隔离）");
        assertTrue(planner.getActivePlotThreads().contains("章内回收"),
                "Planner 拿排期（逾期回灌与回收规划）");
        assertTrue(critic.getActivePlotThreads().contains("章内回收"),
                "Critic 拿排期（审查是否按计划兑付）");
    }
}
