package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.NovelStateFact;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 状态事实账本测试（v2.0 M2-C3：软失效 + 区间求解 + TC-08 倒叙注入回归）
 *
 * @author HL
 */
class StateHistoryServiceTest {

    private StateHistoryService service;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelStateFact.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new StateHistoryService());
    }

    private NovelStateFact fact(Long id, Long fromChapter, Long toChapter,
                                Long sourceChapter, Object state) {
        NovelStateFact fact = new NovelStateFact();
        fact.setId(id);
        fact.setBookId(1L);
        fact.setCharacterId(7L);
        fact.setValidFromChapter(fromChapter);
        fact.setValidToChapter(toChapter);
        fact.setSourceChapter(sourceChapter == null ? fromChapter : sourceChapter);
        fact.setState(state);
        return fact;
    }

    // ------------------------------------------------------------------
    // covers 纯函数（区间语义 = 倒叙正确性核心）
    // ------------------------------------------------------------------

    @Test
    void covers区间为左闭右开() {
        NovelStateFact fact = fact(1L, 5L, 19L, null, "落霞谷");
        assertFalse(StateHistoryService.covers(fact, 4L), "未生效");
        assertTrue(StateHistoryService.covers(fact, 5L), "起始章含");
        assertTrue(StateHistoryService.covers(fact, 18L), "区间内");
        assertFalse(StateHistoryService.covers(fact, 19L), "截止章不含（已被第 19 章新事实覆盖）");
    }

    @Test
    void covers开放区间覆盖一切后续() {
        NovelStateFact fact = fact(1L, 5L, null, null, "王都");
        assertTrue(StateHistoryService.covers(fact, 5L));
        assertTrue(StateHistoryService.covers(fact, 50L), "null validTo = 当前有效");
        assertFalse(StateHistoryService.covers(fact, 4L));
        assertFalse(StateHistoryService.covers(null, 5L), "空事实防御");
    }

    // ------------------------------------------------------------------
    // appendFact 闭环（旧事实软失效不物理删除）
    // ------------------------------------------------------------------

    @Test
    void appendFact将旧事实软失效到新区间前() {
        NovelStateFact old = fact(1L, 5L, null, null, "落霞谷");
        doReturn(List.of(old)).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        // stub 数据方法：spy 直接执行 IService 默认实现会因无 baseMapper 抛异常
        doReturn(true).when(service).updateById(any(NovelStateFact.class));
        doReturn(true).when(service).save(any(NovelStateFact.class));

        service.appendFact(1L, 7L, 19L, Map.of("location", "王都"));

        // 旧事实 validTo = 19（软失效，不删除）
        ArgumentCaptor<NovelStateFact> updateCaptor = ArgumentCaptor.forClass(NovelStateFact.class);
        verify(service).updateById(updateCaptor.capture());
        assertEquals(1L, updateCaptor.getValue().getId());
        assertEquals(19L, updateCaptor.getValue().getValidToChapter());

        // 新事实追加为当前有效
        ArgumentCaptor<NovelStateFact> saveCaptor = ArgumentCaptor.forClass(NovelStateFact.class);
        verify(service).save(saveCaptor.capture());
        assertEquals(19L, saveCaptor.getValue().getValidFromChapter());
        assertNull(saveCaptor.getValue().getValidToChapter());
        assertEquals(19L, saveCaptor.getValue().getSourceChapter());
    }

    @Test
    void appendFact无效入参静默() {
        assertNull(service.appendFact(null, 7L, 5L, "s"));
        assertNull(service.appendFact(1L, null, 5L, "s"));
        assertNull(service.appendFact(1L, 7L, null, "s"));
        assertNull(service.appendFact(1L, 7L, 5L, null));
        verify(service, org.mockito.Mockito.never()).save(any(NovelStateFact.class));
    }

    // ------------------------------------------------------------------
    // TC-08：倒叙注入回归（C3 落地后启用）
    // ------------------------------------------------------------------

    @Test
    void TC08_倒叙场景取回忆目标时刻的状态而非最新() {
        // 账本状态：第 5 章 落霞谷/重伤（后在第 19 章被覆盖）；第 19 章 王都/伤愈（当前有效）
        doReturn(List.of(
                fact(2L, 19L, null, 19L, Map.of("location", "王都", "condition", "伤愈")),
                fact(1L, 5L, 19L, 5L, Map.of("location", "落霞谷", "condition", "重伤"))))
                .when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        // 第 20 章为第 5 章的回忆篇（FLASHBACK）：倒叙场景按回忆目标章 5 求解
        NovelStateFact remembered = service.solveState(1L, 7L, 5L);

        // 必须拿到第 5 章事实（落霞谷/重伤），而不是最新（王都/伤愈）——R-03 修复验收
        assertEquals(1L, remembered.getId());
        assertEquals(5L, remembered.getSourceChapter());

        // 正常章节（第 21 章）继续推进：拿到最新有效事实
        assertEquals(2L, service.solveState(1L, 7L, 21L).getId());

        // 第 19 章当天：新区间已生效
        assertEquals(2L, service.solveState(1L, 7L, 19L).getId());
        // 第 4 章（首个事实之前）：无状态可取
        assertNull(service.solveState(1L, 7L, 4L));
    }

    @Test
    void solveLatest取当前有效事实() {
        doReturn(List.of(
                fact(2L, 19L, null, 19L, Map.of("location", "王都")),
                fact(1L, 5L, 19L, 5L, Map.of("location", "落霞谷"))))
                .when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        assertEquals(2L, service.solveLatest(1L, 7L).getId());
        assertNull(service.solveLatest(null, 7L));
        assertNull(service.solveLatest(1L, null));
    }
}
