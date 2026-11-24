package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 工具回执账本服务测试（自主性改造 R0：同 attempt 幂等重放 / 竞态兜底 / 失败留痕 / 读故障宁可重做）
 *
 * @author HL
 */
class ToolReceiptServiceTest {

    private ToolReceiptService service;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelToolReceipt.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new ToolReceiptService());
    }

    @Test
    void 首次落账插入完整字段含失败回执() {
        // 失败回照样落盘（ok=false 留痕）；成功回执完整字段
        doReturn(List.of()).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doReturn(true).when(service).save(any(NovelToolReceipt.class));

        service.record(7L, "CRITIC:1", "canon.query", "hash-a", 0,
                Map.of("facts", List.of()), true, 0, 35L);
        service.record(7L, "CRITIC:1", "evidence.search", "hash-b", 0,
                null, false, 0, 5L);

        ArgumentCaptor<NovelToolReceipt> captor = ArgumentCaptor.forClass(NovelToolReceipt.class);
        verify(service, org.mockito.Mockito.times(2)).save(captor.capture());
        List<NovelToolReceipt> saved = captor.getAllValues();

        assertEquals("canon.query", saved.get(0).getTool());
        assertEquals("hash-a", saved.get(0).getArgsHash());
        assertEquals(0, saved.get(0).getAttempt());
        assertEquals(Boolean.TRUE, saved.get(0).getOk());
        assertEquals(35L, saved.get(0).getCostMs());

        assertEquals("evidence.search", saved.get(1).getTool());
        assertEquals(Boolean.FALSE, saved.get(1).getOk(), "失败回照样落盘（留痕不留成功）");
    }

    @Test
    void 同attempt幂等重放返回既有回执() {
        // kill-resume 同 attempt 再执行：返回既有回执，不重复执行/计费
        NovelToolReceipt existing = new NovelToolReceipt();
        existing.setId(50L);
        existing.setAttempt(0);
        existing.setOk(true);
        doReturn(List.of(existing)).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        NovelToolReceipt result = service.record(7L, "CRITIC:1", "canon.query", "hash-a", 0,
                Map.of("facts", List.of()), true, 0, 35L);

        assertSame(existing, result);
        verify(service, never()).save(any(NovelToolReceipt.class));
    }

    @Test
    void 竞态重复键冲突回读兜底() {
        // 双实例竞态：预检查为空 + save 撞 UNIQUE → 回读既有回执（幂等语义兜底）
        NovelToolReceipt existing = new NovelToolReceipt();
        existing.setId(50L);
        existing.setAttempt(0);
        existing.setOk(true);
        doReturn(List.of()).doReturn(List.of(existing)).when(service)
                .list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doThrow(new DuplicateKeyException("dup")).when(service).save(any(NovelToolReceipt.class));

        NovelToolReceipt result = service.record(7L, "CRITIC:1", "canon.query", "hash-a", 0,
                Map.of("facts", List.of()), true, 0, 35L);

        assertSame(existing, result, "竞态冲突回读既有回执，不把重复键当失败");
    }

    @Test
    void 竞态回读仍失败返回null交调用方决策() {
        doReturn(List.of()).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doThrow(new DuplicateKeyException("dup")).when(service).save(any(NovelToolReceipt.class));

        assertNull(service.record(7L, "CRITIC:1", "canon.query", "hash-a", 0,
                Map.of(), true, 0, 35L), "竞态回读失败返回 null");
    }

    @Test
    void 无效参数拒绝落账() {
        assertNull(service.record(null, "CRITIC:1", "canon.query", "h", 0, null, true, 0, 0));
        assertNull(service.record(7L, " ", "canon.query", "h", 0, null, true, 0, 0));
        assertNull(service.record(7L, "CRITIC:1", "  ", "h", 0, null, true, 0, 0));
        assertNull(service.record(7L, "CRITIC:1", "canon.query", null, 0, null, true, 0, 0));
        assertNull(service.record(7L, "CRITIC:1", "canon.query", "h", -1, null, true, 0, 0), "attempt 从 0 起");
        verify(service, never()).save(any(NovelToolReceipt.class));
    }

    @Test
    void 读故障预检查不盲写且读到侧宁可重做() {
        doThrow(new RuntimeException("库不可用")).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        assertNull(service.record(7L, "CRITIC:1", "canon.query", "h", 0, null, true, 0, 0));
        verify(service, never()).save(any(NovelToolReceipt.class));
        assertNull(service.latest(7L, "CRITIC:1", "canon.query", "h"), "读不到回执=重新执行工具");
        assertFalse(service.hasSucceeded(7L, "CRITIC:1", "canon.query", "h"),
                "绝不把读不到当已成功（防假成功另一面）");
    }

    @Test
    void 最新回执取最大attempt() {
        // 重试预算推导依据：最新 attempt 为 ok=false 才允许 attempt+1（工具契约重试<=1）
        NovelToolReceipt attempt0 = new NovelToolReceipt();
        attempt0.setAttempt(0);
        attempt0.setOk(true);
        NovelToolReceipt attempt1 = new NovelToolReceipt();
        attempt1.setAttempt(1);
        attempt1.setOk(false);
        doReturn(List.of(attempt0, attempt1)).when(service)
                .list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        assertEquals(1, service.latest(7L, "CRITIC:1", "canon.query", "h").getAttempt());
        assertFalse(attempt1.getOk(), "最新失败=允许 attempt+1（重试预算未耗尽）");
        assertTrue(service.hasSucceeded(7L, "CRITIC:1", "canon.query", "h"),
                "任一 attempt 成功即重放短路");
    }

    @Test
    void 全部失败无成功回执() {
        NovelToolReceipt attempt0 = new NovelToolReceipt();
        attempt0.setAttempt(0);
        attempt0.setOk(false);
        doReturn(List.of(attempt0)).when(service)
                .list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        assertFalse(service.hasSucceeded(7L, "CRITIC:1", "canon.query", "h"));
        assertEquals(0, service.latest(7L, "CRITIC:1", "canon.query", "h").getAttempt());
        assertNull(service.latest(null, null, null, null));
    }
}
