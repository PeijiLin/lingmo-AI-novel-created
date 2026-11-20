package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.NovelPendingAssertion;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 待证实断言服务测试（v2.0 M2-C6：隔离区生命周期——升格/过期/仲裁）
 *
 * @author HL
 */
class PendingAssertionServiceTest {

    private PendingAssertionService service;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelPendingAssertion.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new PendingAssertionService());
        doReturn(true).when(service).updateById(any(NovelPendingAssertion.class));
        doReturn(true).when(service).save(any(NovelPendingAssertion.class));
    }

    private NovelPendingAssertion pending(Long id, String status, Integer confirmations, Long expiresChapter) {
        NovelPendingAssertion assertion = new NovelPendingAssertion();
        assertion.setId(id);
        assertion.setBookId(1L);
        assertion.setStatus(status);
        assertion.setConfirmations(confirmations);
        assertion.setExpiresChapter(expiresChapter);
        return assertion;
    }

    @Test
    void record落待证实带过期边界() {
        service.record(1L, 10L, 7L, "HEARSAY", java.util.Map.of("location", "王都"));

        ArgumentCaptor<NovelPendingAssertion> captor = ArgumentCaptor.forClass(NovelPendingAssertion.class);
        verify(service).save(captor.capture());
        NovelPendingAssertion saved = captor.getValue();
        assertEquals("PENDING", saved.getStatus());
        assertEquals(0, saved.getConfirmations());
        assertEquals(Long.valueOf(40L), saved.getExpiresChapter(), "来源章 10 + 30 章 = 40");
        assertEquals("HEARSAY", saved.getAssertionType());
    }

    @Test
    void 两次独立锚点证实升格CONFIRMED() {
        // 顺序 stub：第一次读到计数 0，写入后第二次读到计数 1（模拟 DB 累计语义）
        doReturn(pending(5L, "PENDING", 0, 40L), pending(5L, "PENDING", 1, 40L))
                .when(service).getById(5L);

        service.addConfirmation(5L);
        service.addConfirmation(5L);

        ArgumentCaptor<NovelPendingAssertion> captor = ArgumentCaptor.forClass(NovelPendingAssertion.class);
        verify(service, org.mockito.Mockito.times(2)).updateById(captor.capture());
        // 第一次：计数 1，仍 PENDING（未置状态=保持原值）
        org.junit.jupiter.api.Assertions.assertEquals(1, captor.getAllValues().get(0).getConfirmations());
        org.junit.jupiter.api.Assertions.assertNull(captor.getAllValues().get(0).getStatus());
        // 第二次：计数 2 达升格门槛 -> CONFIRMED
        org.junit.jupiter.api.Assertions.assertEquals(2, captor.getAllValues().get(1).getConfirmations());
        org.junit.jupiter.api.Assertions.assertEquals("CONFIRMED", captor.getAllValues().get(1).getStatus());
    }

    @Test
    void 非PENDING断言不再累计证实() {
        doReturn(pending(5L, "CONFIRMED", 2, 40L)).when(service).getById(5L);

        service.addConfirmation(5L);

        verify(service, never()).updateById(any(NovelPendingAssertion.class));
    }

    @Test
    void 过期清扫只归档超窗PENDING() {
        // 5 号 expires=40 < 当前 41：过期；6 号 expires=50 未到；7 号已 CONFIRMED 不动
        doReturn(List.of(pending(5L, "PENDING", 0, 40L),
                pending(6L, "PENDING", 0, 50L),
                pending(7L, "CONFIRMED", 2, 40L)))
                .when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        int expired = service.expireOutdated(1L, 41L);

        assertEquals(1, expired);
        ArgumentCaptor<NovelPendingAssertion> captor = ArgumentCaptor.forClass(NovelPendingAssertion.class);
        verify(service).updateById(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        assertEquals("EXPIRED", captor.getValue().getStatus());
    }

    @Test
    void 作者仲裁驳回REJECTED() {
        service.resolve(9L, false);

        ArgumentCaptor<NovelPendingAssertion> captor = ArgumentCaptor.forClass(NovelPendingAssertion.class);
        verify(service).updateById(captor.capture());
        assertEquals("REJECTED", captor.getValue().getStatus());
    }

    @Test
    void 无效入参与故障静默() {
        service.record(null, 10L, 7L, "HEARSAY", "payload");
        service.record(1L, 10L, 7L, null, "payload");
        service.record(1L, 10L, 7L, "HEARSAY", null);
        verify(service, never()).save(any(NovelPendingAssertion.class));

        service.addConfirmation(null);
        verify(service, never()).getById(any());

        assertEquals(0, service.expireOutdated(null, 10L));
        assertEquals(0, service.expireOutdated(1L, null));
    }

    @Test
    void record写入故障不抛异常() {
        doThrow(new RuntimeException("库不可用")).when(service).save(any(NovelPendingAssertion.class));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.record(1L, 10L, 7L, "DREAM", "payload"));
    }
}
