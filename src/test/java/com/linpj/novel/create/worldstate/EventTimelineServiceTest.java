package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.NovelEventTimeline;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 时间归一化与事件时间线测试（v2.0 设计 2.2 + TC-09 相对时间解析）
 *
 * - TimeNormalization：LLM 出偏移 / 代码算术 / 解析失败 UNKNOWN 不猜值（红线 6）
 * - EventTimelineService：事件入账、锚点推进、DREAM 不锚定
 *
 * @author HL
 */
class EventTimelineServiceTest {

    private EventTimelineService service;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelEventTimeline.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new EventTimelineService());
    }

    // ------------------------------------------------------------------
    // TimeNormalization 纯函数（公理 P3 分工：代码做算术）
    // ------------------------------------------------------------------

    @Test
    void 相对偏移在参照锚点上做加法() {
        // "三天后" + 上事件 day=10 → day=13
        TimeNormalization.Resolved resolved =
                TimeNormalization.resolve(TimeNormalization.Offset.dayOffset(3), 10);
        assertEquals(StoryTimeKind.RELATIVE_RESOLVED, resolved.kind());
        assertEquals(13, resolved.dayIndex());
    }

    @Test
    void 模糊表述按中点落轴且分型保留() {
        // "半月已过" + day=10 → day=25，但 kind=FUZZY（消费端置信度降级）
        TimeNormalization.Resolved resolved =
                TimeNormalization.resolve(TimeNormalization.Offset.fuzzy(15), 10);
        assertEquals(StoryTimeKind.FUZZY, resolved.kind());
        assertEquals(25, resolved.dayIndex());
    }

    @Test
    void 架空历法保留标签偏序降级() {
        // "青云历 314 年春"：天数不可换算，day=null
        TimeNormalization.Resolved resolved =
                TimeNormalization.resolve(TimeNormalization.Offset.absoluteLabel("青云历314年春"), 10);
        assertEquals(StoryTimeKind.ABSOLUTE_LABEL, resolved.kind());
        assertNull(resolved.dayIndex());
    }

    @Test
    void 参照链断裂或无信息均为UNKNOWN绝不猜值() {
        assertEquals(StoryTimeKind.UNKNOWN,
                TimeNormalization.resolve(TimeNormalization.Offset.dayOffset(3), null).kind(),
                "参照断裂：相对偏移无处锚定，宁可不解析");
        assertEquals(StoryTimeKind.UNKNOWN,
                TimeNormalization.resolve(null, 10).kind(), "LLM 无时间输出");
        assertEquals(StoryTimeKind.UNKNOWN,
                TimeNormalization.resolve(new TimeNormalization.Offset("DAY_OFFSET", null, null), 10).kind(),
                "偏移缺数值：不猜天数");
        assertEquals(StoryTimeKind.UNKNOWN,
                TimeNormalization.resolve(new TimeNormalization.Offset("什么鬼", 3, null), 10).kind(),
                "非法偏移类型");
    }

    // ------------------------------------------------------------------
    // 事件入账（spy 拦截数据访问，验证归一化与模式语义）
    // ------------------------------------------------------------------

    /** 捕获 save 落库内容 */
    private NovelEventTimeline capturedEvent() {
        ArgumentCaptor<NovelEventTimeline> captor = ArgumentCaptor.forClass(NovelEventTimeline.class);
        verify(service).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 正常场景相对偏移挂最近锚点入账() {
        // 前序锚点 day=10
        doReturn(10).when(service).lastAnchoredDayIndex(1L, 20L, 3);

        service.recordEvent(1L, 20L, 3, "NORMAL",
                TimeNormalization.Offset.dayOffset(3), "三天后夜晚", 20L,
                "林砚抵达王都", "王都", List.of(1L));

        NovelEventTimeline event = capturedEvent();
        assertEquals("RELATIVE_RESOLVED", event.getTimeKind());
        assertEquals(13, event.getDayIndex());
        assertEquals("NORMAL", event.getNarrativeMode());
        assertEquals(20L, event.getSourceChapter());
    }

    @Test
    void 首个事件无锚点降级UNKNOWN() {
        doReturn(null).when(service).lastAnchoredDayIndex(1L, 1L, 1);

        service.recordEvent(1L, 1L, 1, "NORMAL",
                TimeNormalization.Offset.dayOffset(2), "两天后", 1L,
                "启程北行", "官道", List.of(1L));

        NovelEventTimeline event = capturedEvent();
        // 参照缺失：偏移不解析（不猜"第一天是 day 1"以外的任何锚点）
        assertEquals("UNKNOWN", event.getTimeKind());
        assertNull(event.getDayIndex());
    }

    @Test
    void 梦境场景不锚定时间轴天数置空() {
        // DREAM：即使误带偏移也不落 day_index（梦境时间不可信，防止污染时间轴）
        service.recordEvent(1L, 20L, 2, "DREAM",
                TimeNormalization.Offset.dayOffset(1), "梦里不知时日", 20L,
                "林砚梦见先父", "梦境", List.of(1L));

        NovelEventTimeline event = capturedEvent();
        assertEquals("DREAM", event.getNarrativeMode());
        assertNull(event.getDayIndex(), "DREAM 天数必须置空");
    }

    @Test
    void 枚举模式非法值归一化为NORMAL() {
        doReturn(null).when(service).lastAnchoredDayIndex(1L, 5L, 1);

        service.recordEvent(1L, 5L, 1, "随便写的",
                null, null, 5L, "事件A", "地点", null);

        assertEquals("NORMAL", capturedEvent().getNarrativeMode());
        assertEquals("UNKNOWN", capturedEvent().getTimeKind());
    }

    @Test
    void 事件写入失败不抛异常() {
        doReturn(null).when(service).lastAnchoredDayIndex(any(), any(), any());
        doAnswer(invocation -> {
            throw new RuntimeException("库不可用");
        }).when(service).save(any(NovelEventTimeline.class));

        // 时间线是校验数据源：写入失败只告警不阻塞定稿结算
        try {
            service.recordEvent(1L, 5L, 1, "NORMAL", null, null, 5L, "事件", "地点", null);
        } catch (Exception e) {
            throw new AssertionError("事件入账故障不得上抛", e);
        }
    }

    @Test
    void 无效入参静默不入账() {
        AtomicReference<String> failure = new AtomicReference<>();
        try {
            service.recordEvent(null, 5L, 1, "NORMAL", null, null, 5L, "事件", "地点", null);
            service.recordEvent(1L, 5L, 1, "NORMAL", null, null, 5L, "  ", "地点", null);
        } catch (Exception e) {
            failure.set(e.getMessage());
        }
        assertNull(failure.get());
        verify(service, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void 回忆目标章天数查询入口存在() {
        // C3/C4 倒叙装配的查询依赖（anchoredDayAsOf），此用例护住 API 契约不被误删
        NovelEventTimeline anchored = new NovelEventTimeline();
        anchored.setDayIndex(7);
        doReturn(anchored).when(service).getOne(any(), org.mockito.ArgumentMatchers.eq(false));
        Integer day = service.anchoredDayAsOf(1L, 5L);
        assertNotNull(day);
        assertEquals(7, day);
    }
}
