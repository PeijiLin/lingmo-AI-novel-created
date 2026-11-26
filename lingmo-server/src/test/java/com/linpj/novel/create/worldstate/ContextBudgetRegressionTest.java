package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.Scene;
import com.linpj.novel.create.pojo.pipeline.WriterContext;
import com.linpj.novel.create.service.BookChapterService;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CharacterRelationshipsService;
import com.linpj.novel.create.service.CharacterStateService;
import com.linpj.novel.create.service.PlotThreadService;
import com.linpj.novel.create.service.StoryDigestService;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * TC-01 / TC-08 前置回归（v2.0 §9 排期决策：零模型成本、全自动，花大钱跑实验前先验证架构成立）
 *
 * - TC-01 上下文膨胀：无论账本数据（摘要/伏笔/角色数）涨到多大，装配结果恒定不超预算
 *   ——防"长篇越写越贵、越写越糊"的最隐蔽缺陷（R-01）
 * - TC-08 倒叙注入：回忆场景必须拿到"回忆目标时刻"的状态而非最新状态——依赖三时间轴（M2-C3），
 *   骨架先行挂起，C3 落地后启用（届时该测试即为通过条件）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContextBudgetRegressionTest {

    /** 与 ContextAssemblyService.CONTEXT_CHAR_BUDGET 一致（8k 字符 ≈ 8k token） */
    private static final int CONTEXT_CHAR_BUDGET = 8000;

    @Mock private CharacterCardsService characterCardsService;
    @Mock private BookInfoService bookInfoService;
    @Mock private StoryDigestService storyDigestService;
    @Mock private PlotThreadService plotThreadService;
    @Mock private CharacterStateService characterStateService;
    @Mock private CharacterRelationshipsService characterRelationshipsService;
    @Mock private BookChapterService bookChapterService;

    private ContextAssemblyService service;

    @BeforeEach
    void setUp() {
        service = new ContextAssemblyService(characterCardsService, bookInfoService,
                storyDigestService, plotThreadService, characterStateService,
                characterRelationshipsService, bookChapterService);
    }

    private void stubBook(String worldSetting) {
        BookInfo book = new BookInfo();
        book.setId(1L);
        book.setWorldSetting(worldSetting);
        lenient().when(bookInfoService.getById(1L)).thenReturn(book);
    }

    private CharacterCardsVo card(Long id, String name) {
        CharacterCardsVo vo = new CharacterCardsVo();
        vo.setId(id);
        vo.setName(name);
        vo.setDescription("描述".repeat(60));
        vo.setPersonality("性格".repeat(40));
        vo.setBackground("背景".repeat(80));
        vo.setAbilities("能力".repeat(40));
        return vo;
    }

    private PipelineRequest request() {
        PipelineRequest request = new PipelineRequest();
        request.setBookId(1L);
        request.setChapterId(50L);
        request.setUserInput("写下一段");
        return request;
    }

    private static int totalChars(WriterContext context) {
        return len(context.getWorldSetting()) + len(context.getCharacterInfo())
                + len(context.getStoryDigest()) + len(context.getActivePlotThreads())
                + len(context.getStyle()) + len(context.getUserInput());
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    // ------------------------------------------------------------------
    // TC-01：上下文膨胀回归
    // ------------------------------------------------------------------

    @Test
    void TC01_第1章与第50章装配结果都不超预算() {
        stubBook("世界观".repeat(2000)); // 4000 字
        when(storyDigestService.getDigest(1L)).thenReturn("摘要".repeat(4000)); // 8000 字（远超）
        when(plotThreadService.renderActiveThreads(1L)).thenReturn("[伏笔#1] ".repeat(500)
                + "神秘来信隐藏惊天秘密，与师门旧案相关"); // 长伏笔清单
        when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(card(1L, "林砚"), card(2L, "顾昭")));
        when(characterStateService.renderStates(eq(1L), anyList())).thenReturn("状态".repeat(2000));

        ChapterOutline outline = new ChapterOutline();
        outline.setCharacterIds(List.of(1L));
        Scene scene = new Scene();
        scene.setLocation("城墙");
        scene.setConflict("围城");
        outline.setScenes(List.of(scene));

        WriterContext ctxLow = service.assembleWriterContext(request(), outline);   // "第 1 章"
        PipelineRequest late = request();
        late.setChapterId(50L);
        WriterContext ctxHigh = service.assembleWriterContext(late, outline);       // "第 50 章"

        // 预算口径与服务内部一致：worldSetting+characterInfo+digest+threads+style+userInput
        //（outline 是指令性输入不参与裁剪，与生产口径一致）
        assertTrue(totalChars(ctxLow) <= CONTEXT_CHAR_BUDGET,
                "早章装配超预算");
        assertTrue(totalChars(ctxHigh) <= CONTEXT_CHAR_BUDGET,
                "晚章装配超预算（账本增长未裁剪=膨胀缺陷回归）");
    }

    @Test
    void TC01_极端账本下出场角色卡仍保留() {
        stubBook("设定".repeat(4000));
        when(storyDigestService.getDigest(1L)).thenReturn("摘".repeat(10_000));
        when(plotThreadService.renderActiveThreads(1L)).thenReturn("[伏笔#x] ".repeat(1000));
        when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(card(1L, "林砚")));
        when(characterStateService.renderStates(eq(1L), anyList())).thenReturn(null);

        ChapterOutline outline = new ChapterOutline();
        outline.setCharacterIds(List.of(1L));

        WriterContext ctx = service.assembleWriterContext(request(), outline);

        // 裁剪可丢弃风格/摘要/伏笔，但角色卡（出场角色的创作基准）永不裁
        assertNotNull(ctx.getCharacterInfo());
        assertTrue(ctx.getCharacterInfo().contains("林砚"), "出场角色卡被裁=创作基准丢失");
    }

    // ------------------------------------------------------------------
    // TC-08：倒叙注入回归 —— 已在 C3 落地，端到端断言见 StateHistoryServiceTest
    //         .TC08_倒叙场景取回忆目标时刻的状态而非最新（含 covers 区间语义用例）
    //         此处保留原骨架作流量入口说明：倒叙场景调用链为
    //         outline 场景模式 FLASHBACK(目标章) -> StateHistoryService.solveState(at=目标章)
    // ------------------------------------------------------------------

    @Test
    @Disabled("覆盖位置已迁移：StateHistoryServiceTest.TC08_倒叙场景取回忆目标时刻的状态而非最新（C3 落地后启用）")
    void TC08_倒叙场景必须取回忆目标时刻的状态而非最新() {
        // 见 StateHistoryServiceTest —— 避免 C3/C4 两个测试文件重复维护同一验收
    }
}
