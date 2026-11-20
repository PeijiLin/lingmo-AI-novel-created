package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.NovelPlotThread;
import com.linpj.novel.create.pojo.pipeline.PlotOperation;
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
}
