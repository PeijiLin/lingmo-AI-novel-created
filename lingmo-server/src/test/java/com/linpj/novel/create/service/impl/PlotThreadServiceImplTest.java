package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.entity.NovelPlotThread;
import com.linpj.novel.create.pojo.pipeline.PlotOperation;
import com.linpj.novel.create.service.BookChapterService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 伏笔账本服务单元测试（设计 5.3：意图内记账 + 幂等）
 *
 * MyBatis-Plus Lambda 条件构造需要实体 TableInfo 元数据（正常由 Mapper 注册时初始化），
 * 纯单测环境需手动 initTableInfo，之后对 ServiceImpl 做 spy 拦截数据访问。
 *
 * @author HL
 */
class PlotThreadServiceImplTest {

    private PlotThreadServiceImpl service;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelPlotThread.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BookChapter.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new PlotThreadServiceImpl());
    }

    // ------------------------------------------------------------------
    // 内联伏笔标记：提取与剥离
    // ------------------------------------------------------------------

    @Test
    void extractInlinePlotMarksFindsAllMarks() {
        String text = "夜色渐沉<!--plot: 城主暗线-->，他抬头望月。\n"
                + "过了一会儿<!--plot: 妹妹的信物-->藏在袖中。";
        List<String> marks = service.extractInlinePlotMarks(text);
        assertEquals(List.of("城主暗线", "妹妹的信物"), marks);
    }

    @Test
    void extractInlinePlotMarksHandlesEmptyAndNull() {
        assertTrue(service.extractInlinePlotMarks(null).isEmpty());
        assertTrue(service.extractInlinePlotMarks("没有任何标记").isEmpty());
        // 空描述标记不入账
        assertTrue(service.extractInlinePlotMarks("<!--plot:   -->正文").isEmpty());
    }

    @Test
    void stripInlinePlotMarksRemovesMarks() {
        String text = "夜色渐沉<!--plot: 城主暗线-->他抬头。";
        assertEquals("夜色渐沉他抬头。", service.stripInlinePlotMarks(text));
        assertNull(service.stripInlinePlotMarks(null));
        assertEquals("普通正文", service.stripInlinePlotMarks("普通正文"));
    }

    // ------------------------------------------------------------------
    // 内联标记入账（幂等：同描述跳过）
    // ------------------------------------------------------------------

    @Test
    void recordInlinePlotMarksSavesNewThreadsOnly() {
        doReturn(0L).when(service).count(any());
        doReturn(true).when(service).save(any(NovelPlotThread.class));

        service.recordInlinePlotMarks(1L, 10L, List.of("城主暗线", "妹妹的信物"));

        ArgumentCaptor<NovelPlotThread> captor = ArgumentCaptor.forClass(NovelPlotThread.class);
        verify(service, times(2)).save(captor.capture());
        List<NovelPlotThread> saved = captor.getAllValues();
        assertEquals("城主暗线", saved.get(0).getDescription());
        assertEquals("妹妹的信物", saved.get(1).getDescription());
        saved.forEach(t -> {
            assertEquals(1L, t.getBookId());
            assertEquals(10L, t.getPlantedChapterId());
            assertEquals("OPEN", t.getStatus());
        });
    }

    @Test
    void recordInlinePlotMarksSkipsExistingDescription() {
        // 幂等栅栏：同描述已存在（count>0）则不再入账
        doReturn(1L).when(service).count(any());

        service.recordInlinePlotMarks(1L, 10L, List.of("城主暗线"));

        verify(service, never()).save(any(NovelPlotThread.class));
    }

    // ------------------------------------------------------------------
    // Planner 声明的伏笔操作入账
    // ------------------------------------------------------------------

    @Test
    void plantOperationCreatesOpenThread() {
        doReturn(0L).when(service).count(any());
        doReturn(true).when(service).save(any(NovelPlotThread.class));

        PlotOperation op = new PlotOperation();
        op.setOp("PLANT");
        op.setDescription("主角获得了神秘钥匙");
        service.applyPlotOperations(1L, 10L, List.of(op));

        ArgumentCaptor<NovelPlotThread> captor = ArgumentCaptor.forClass(NovelPlotThread.class);
        verify(service).save(captor.capture());
        assertEquals("OPEN", captor.getValue().getStatus());
        assertEquals(10L, captor.getValue().getPlantedChapterId());
    }

    // ------------------------------------------------------------------
    // 知情集合维护（v2.0 M2-C5 闭世界：whoKnows 只增不减）
    // ------------------------------------------------------------------

    @Test
    void plantSecretPersistsWhoKnowsAndSecretKey() {
        doReturn(0L).when(service).count(any());
        doReturn(true).when(service).save(any(NovelPlotThread.class));

        PlotOperation op = new PlotOperation();
        op.setOp("PLANT");
        op.setDescription("林砚查知师父死因另有隐情");
        op.setWhoKnows(List.of(1L));
        op.setSecretKey("师父死因");
        service.applyPlotOperations(1L, 10L, List.of(op));

        ArgumentCaptor<NovelPlotThread> captor = ArgumentCaptor.forClass(NovelPlotThread.class);
        verify(service).save(captor.capture());
        assertEquals(List.of(1L), captor.getValue().getWhoKnows());
        assertEquals("师父死因", captor.getValue().getSecretKey());
    }

    @Test
    void advanceMergesWhoKnowsByUnion() {
        NovelPlotThread existing = new NovelPlotThread();
        existing.setId(7L);
        existing.setWhoKnows(List.of(1L, 3L));
        doReturn(existing).when(service).getById(7L);
        doReturn(true).when(service).updateById(any(NovelPlotThread.class));

        PlotOperation op = new PlotOperation();
        op.setOp("ADVANCE");
        op.setThreadId(7L);
        op.setWhoKnows(List.of(3L, 5L)); // 3 已知情：并集去重
        service.applyPlotOperations(1L, 10L, List.of(op));

        ArgumentCaptor<NovelPlotThread> captor = ArgumentCaptor.forClass(NovelPlotThread.class);
        verify(service).updateById(captor.capture());
        List<?> merged = (List<?>) captor.getValue().getWhoKnows();
        assertEquals(3, merged.size());
        assertTrue(merged.containsAll(List.of(1L, 3L, 5L)), "知情集合并集合并（只增不减）");
    }

    @Test
    void resolveMergesNewlyInformedKnowers() {
        NovelPlotThread existing = new NovelPlotThread();
        existing.setId(7L);
        existing.setWhoKnows(List.of(1L));
        doReturn(existing).when(service).getById(7L);
        doReturn(true).when(service).updateById(any(NovelPlotThread.class));

        PlotOperation op = new PlotOperation();
        op.setOp("RESOLVE");
        op.setThreadId(7L);
        op.setWhoKnows(List.of(2L));
        service.applyPlotOperations(1L, 10L, List.of(op));

        ArgumentCaptor<NovelPlotThread> captor = ArgumentCaptor.forClass(NovelPlotThread.class);
        verify(service).updateById(captor.capture());
        assertEquals("RESOLVED", captor.getValue().getStatus());
        List<?> merged = (List<?>) captor.getValue().getWhoKnows();
        assertEquals(2, merged.size());
        assertTrue(merged.containsAll(List.of(1L, 2L)), "收束=合法揭穿时刻：新增知情并入集合");
    }

    @Test
    void listSecretThreadsKeepsOnlyThoseWithKnowers() {
        NovelPlotThread secret = new NovelPlotThread();
        secret.setId(1L);
        secret.setStatus("OPEN");
        secret.setWhoKnows(List.of(1L));
        NovelPlotThread notSecret = new NovelPlotThread();
        notSecret.setId(2L);
        notSecret.setStatus("OPEN");
        notSecret.setWhoKnows(null);
        NovelPlotThread empty = new NovelPlotThread();
        empty.setId(3L);
        empty.setStatus("OPEN");
        empty.setWhoKnows(List.of());
        doReturn(List.of(secret, notSecret, empty)).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        List<NovelPlotThread> secrets = service.listSecretThreads(1L);

        assertEquals(1, secrets.size());
        assertEquals(1L, secrets.get(0).getId(), "无知情集合的线程不是秘密（数据缺失≠秘密）");
    }

    @Test
    void plantOperationIsIdempotentOnDuplicateDescription() {
        doReturn(1L).when(service).count(any());

        PlotOperation op = new PlotOperation();
        op.setOp("PLANT");
        op.setDescription("主角获得了神秘钥匙");
        service.applyPlotOperations(1L, 10L, List.of(op));

        verify(service, never()).save(any(NovelPlotThread.class));
    }

    @Test
    void plantWithExistingThreadIdAdvancesInsteadOfDuplicating() {
        // 引用既有伏笔再次声明 PLANT：视为 ADVANCE，不重复入账
        doReturn(true).when(service).updateById(any(NovelPlotThread.class));

        PlotOperation op = new PlotOperation();
        op.setOp("PLANT");
        op.setThreadId(7L);
        op.setDescription("推进神秘钥匙");
        service.applyPlotOperations(1L, 10L, List.of(op));

        verify(service, never()).save(any(NovelPlotThread.class));
        ArgumentCaptor<NovelPlotThread> captor = ArgumentCaptor.forClass(NovelPlotThread.class);
        verify(service).updateById(captor.capture());
        assertEquals(7L, captor.getValue().getId());
    }

    @Test
    void resolveOperationClosesThreadById() {
        doReturn(true).when(service).updateById(any(NovelPlotThread.class));

        PlotOperation op = new PlotOperation();
        op.setOp("RESOLVE");
        op.setThreadId(7L);
        service.applyPlotOperations(1L, 12L, List.of(op));

        ArgumentCaptor<NovelPlotThread> captor = ArgumentCaptor.forClass(NovelPlotThread.class);
        verify(service).updateById(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertEquals("RESOLVED", captor.getValue().getStatus());
        assertEquals(12L, captor.getValue().getResolvedChapterId());
    }

    @Test
    void resolveByDescriptionMatchesNearestOpenThread() {
        NovelPlotThread matched = new NovelPlotThread();
        matched.setId(9L);
        doReturn(matched).when(service).getOne(any());
        doReturn(true).when(service).updateById(any(NovelPlotThread.class));

        PlotOperation op = new PlotOperation();
        op.setOp("RESOLVE");
        op.setDescription("神秘钥匙");
        service.applyPlotOperations(1L, 12L, List.of(op));

        ArgumentCaptor<NovelPlotThread> captor = ArgumentCaptor.forClass(NovelPlotThread.class);
        verify(service).updateById(captor.capture());
        assertEquals(9L, captor.getValue().getId());
        assertEquals("RESOLVED", captor.getValue().getStatus());
    }

    @Test
    void resolveWithoutIdOrDescriptionIsIgnored() {
        PlotOperation op = new PlotOperation();
        op.setOp("RESOLVE");
        service.applyPlotOperations(1L, 12L, List.of(op));

        verify(service, never()).updateById(any(NovelPlotThread.class));
    }

    @Test
    void advanceOperationOnlyRefreshesTimestamp() {
        doReturn(true).when(service).updateById(any(NovelPlotThread.class));

        PlotOperation op = new PlotOperation();
        op.setOp("ADVANCE");
        op.setThreadId(7L);
        service.applyPlotOperations(1L, 10L, List.of(op));

        ArgumentCaptor<NovelPlotThread> captor = ArgumentCaptor.forClass(NovelPlotThread.class);
        verify(service).updateById(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        // ADVANCE 仅更新时间戳，不改状态
        assertNull(captor.getValue().getStatus());
    }

    @Test
    void unknownOperationTypeIsIgnored() {
        PlotOperation op = new PlotOperation();
        op.setOp("DELETE");
        op.setThreadId(7L);
        service.applyPlotOperations(1L, 10L, List.of(op));

        verify(service, never()).save(any(NovelPlotThread.class));
        verify(service, never()).updateById(any(NovelPlotThread.class));
    }

    // ------------------------------------------------------------------
    // 活跃伏笔渲染
    // ------------------------------------------------------------------

    @Test
    void renderActiveThreadsFormatsThreadsWithIds() {
        NovelPlotThread t1 = new NovelPlotThread();
        t1.setId(7L);
        t1.setDescription("城主暗线");
        NovelPlotThread t2 = new NovelPlotThread();
        t2.setId(8L);
        t2.setDescription("妹妹的信物");
        doReturn(List.of(t1, t2)).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        String rendered = service.renderActiveThreads(1L);

        assertTrue(rendered.contains("[伏笔#7] 城主暗线"));
        assertTrue(rendered.contains("[伏笔#8] 妹妹的信物"));
    }

    @Test
    void renderActiveThreadsReturnsNullWhenEmpty() {
        doReturn(List.of()).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertNull(service.renderActiveThreads(1L));
        assertNull(service.renderActiveThreads(null));
        assertTrue(service.listActiveThreads(null).isEmpty());
    }

    // ------------------------------------------------------------------
    // 伏笔排期（B1-3：plannedIn/deadlineIn 落库 + Planner 侧渲染 + 逾期回灌）
    // ------------------------------------------------------------------

    @Test
    void plant操作落库回收排期() {
        doReturn(0L).when(service).count(any());
        doReturn(true).when(service).save(any(NovelPlotThread.class));

        PlotOperation op = new PlotOperation();
        op.setOp("PLANT");
        op.setDescription("师妹身份之谜");
        op.setPlannedIn(3);
        op.setDeadlineIn(10);
        service.applyPlotOperations(1L, 200L, List.of(op));

        ArgumentCaptor<NovelPlotThread> captor = ArgumentCaptor.forClass(NovelPlotThread.class);
        verify(service).save(captor.capture());
        assertEquals(3, captor.getValue().getPlannedOffset());
        assertEquals(10, captor.getValue().getDeadlineOffset());
    }

    @Test
    void 创作侧渲染不含排期() {
        NovelPlotThread thread = new NovelPlotThread();
        thread.setId(7L);
        thread.setDescription("断剑来历");
        thread.setPlannedOffset(3);
        thread.setDeadlineOffset(10);
        doReturn(List.of(thread)).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        String rendered = service.renderActiveThreads(1L);

        assertTrue(rendered.contains("[伏笔#7] 断剑来历"));
        assertTrue(!rendered.contains("章内回收"), "Writer 侧不下发 meta 排期（H4 隔离）");
    }

    private PlotThreadServiceImpl plannerService(BookChapterService bookChapterService) {
        return spy(new PlotThreadServiceImpl(bookChapterService));
    }

    private BookChapter chapter(Long id, int chapterNum) {
        BookChapter chapter = new BookChapter();
        chapter.setId(id);
        chapter.setBookId(1L);
        chapter.setChapterNum(chapterNum);
        return chapter;
    }

    @Test
    void planner侧渲染带排期与逾期提醒() {
        BookChapterService bookChapters = mock(BookChapterService.class);
        PlotThreadServiceImpl planner = plannerService(bookChapters);
        NovelPlotThread scheduled = new NovelPlotThread();
        scheduled.setId(7L);
        scheduled.setDescription("断剑来历");
        scheduled.setPlannedOffset(3);
        scheduled.setDeadlineOffset(5);
        scheduled.setPlantedChapterId(200L);
        doReturn(List.of(scheduled)).when(planner).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doReturn(List.of(chapter(200L, 8), chapter(500L, 14))).when(bookChapters)
                .list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        String rendered = planner.renderPlannerThreads(1L, 500L);

        assertTrue(rendered.contains("计划约 3 章后回收"));
        assertTrue(rendered.contains("须 5 章内回收"));
        assertTrue(rendered.contains("已逾期"), "已过 6 章 > 截止 5 章：逾期回灌提醒");
    }

    @Test
    void planner侧渲染未逾期不带提醒() {
        BookChapterService bookChapters = mock(BookChapterService.class);
        PlotThreadServiceImpl planner = plannerService(bookChapters);
        NovelPlotThread scheduled = new NovelPlotThread();
        scheduled.setId(7L);
        scheduled.setDescription("断剑来历");
        scheduled.setDeadlineOffset(9);
        scheduled.setPlantedChapterId(200L);
        doReturn(List.of(scheduled)).when(planner).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doReturn(List.of(chapter(200L, 8), chapter(500L, 14))).when(bookChapters)
                .list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        String rendered = planner.renderPlannerThreads(1L, 500L);

        assertTrue(rendered.contains("须 9 章内回收"));
        assertTrue(!rendered.contains("已逾期"), "已过 6 章 <= 截止 9 章：不提醒");
    }

    @Test
    void planner侧渲染章号解析降级() {
        // 无章号服务（兼容构造）或章节查询失败：只渲染排期数字，不做逾期判定
        PlotThreadServiceImpl planner = spy(new PlotThreadServiceImpl());
        NovelPlotThread scheduled = new NovelPlotThread();
        scheduled.setId(7L);
        scheduled.setDescription("断剑来历");
        scheduled.setDeadlineOffset(5);
        scheduled.setPlantedChapterId(200L);
        doReturn(List.of(scheduled)).when(planner).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        String rendered = planner.renderPlannerThreads(1L, 500L);

        assertTrue(rendered.contains("须 5 章内回收"));
        assertTrue(!rendered.contains("已逾期"), "章号不可得：宁可漏报提醒不误报");
    }

    @Test
    void planner侧渲染无排期伏笔等于创作侧() {
        PlotThreadServiceImpl planner = spy(new PlotThreadServiceImpl());
        NovelPlotThread plain = new NovelPlotThread();
        plain.setId(8L);
        plain.setDescription("城主暗线");
        doReturn(List.of(plain)).when(planner).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        assertEquals("[伏笔#8] 城主暗线\n",
                planner.renderPlannerThreads(1L, 500L),
                "存量无排期伏笔：两侧渲染逐字节一致（现状回归）");
    }

    @Test
    void planner侧渲染空账本返回空() {
        BookChapterService bookChapters = mock(BookChapterService.class);
        PlotThreadServiceImpl planner = plannerService(bookChapters);
        doReturn(List.of()).when(planner).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        assertNull(planner.renderPlannerThreads(1L, 500L));
        assertNull(planner.renderPlannerThreads(null, null));
    }
}
