package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.entity.NovelEventTimeline;
import com.linpj.novel.create.worldstate.EventTimelineService;
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
 * RC-07 因果时序规则测试（B1-2：因果边 T_story 单调性 + UNKNOWN 降级 + 叙诡豁免）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CausalOrderRuleTest {

    @Mock private EventTimelineService eventTimelineService;

    private CausalOrderRule rule;

    @BeforeEach
    void setUp() {
        rule = new CausalOrderRule(eventTimelineService);
    }

    private static NovelEventTimeline event(long id, Integer dayIndex, Long causedBy) {
        NovelEventTimeline event = new NovelEventTimeline();
        event.setId(id);
        event.setBookId(1L);
        event.setEventDesc("事件" + id);
        event.setDayIndex(dayIndex);
        event.setCausedBy(causedBy);
        return event;
    }

    private RuleCheckContext draftContext() {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setBookId(1L);
        ctx.setChapterId(500L);
        return ctx;
    }

    @Test
    void 后果早于起因_违规() {
        // 事件3(day=8) 起因是 事件2(day=10)：结果早于原因
        when(eventTimelineService.timelineOf(1L)).thenReturn(List.of(
                event(1L, 5, null), event(2L, 10, 1L), event(3L, 8, 2L)));

        List<RuleViolation> violations = rule.check(draftContext());

        assertEquals(1, violations.size());
        assertEquals(RuleSeverity.HIGH, violations.get(0).getSeverity());
        assertTrue(violations.get(0).getEvidence().contains("事件3"));
        assertTrue(violations.get(0).getEvidence().contains("事件2"));
    }

    @Test
    void 因果单调_无违规() {
        when(eventTimelineService.timelineOf(1L)).thenReturn(List.of(
                event(1L, 5, null), event(2L, 10, 1L), event(3L, 12, 2L)));

        assertTrue(rule.check(draftContext()).isEmpty());
    }

    @Test
    void 同日因果不校验() {
        // 时点粒度为天：同日内的因果顺序无从判定
        when(eventTimelineService.timelineOf(1L)).thenReturn(List.of(
                event(1L, 10, null), event(2L, 10, 1L)));

        assertTrue(rule.check(draftContext()).isEmpty());
    }

    @Test
    void 任一端时间未知降级偏序() {
        // UNKNOWN 不猜值（红线 6）：起因 day 缺失的边不参与校验
        when(eventTimelineService.timelineOf(1L)).thenReturn(List.of(
                event(1L, 5, null), event(2L, null, 1L), event(3L, 8, 2L)));

        assertTrue(rule.check(draftContext()).isEmpty(), "时间未知的因果边降级为偏序：不判倒错");
    }

    @Test
    void 陷阱用例_叙诡事件不作为校验基准() {
        NovelEventTimeline unreliable = event(2L, 10, 1L);
        unreliable.setNarrativeMode("UNRELIABLE");
        when(eventTimelineService.timelineOf(1L)).thenReturn(List.of(
                event(1L, 5, null), unreliable, event(3L, 8, 2L)));

        assertTrue(rule.check(draftContext()).isEmpty(), "不可靠叙述的事件不进因果时序判定");
    }

    @Test
    void 悬空引用跳过_数据通道故障静默() {
        // caused_by 指向不存在的事件（理论不会发生：写入端已校验）与账本异常均静默
        when(eventTimelineService.timelineOf(1L)).thenReturn(List.of(event(3L, 8, 999L)));
        assertTrue(rule.check(draftContext()).isEmpty());

        when(eventTimelineService.timelineOf(1L)).thenThrow(new RuntimeException("库不可用"));
        assertTrue(rule.check(draftContext()).isEmpty());
    }

    @Test
    void 空账本与无书上下文_不触碰数据层() {
        RuleCheckContext noBook = RuleCheckContext.of(RuleStage.DRAFT);
        assertTrue(rule.check(noBook).isEmpty());
        verify(eventTimelineService, never()).timelineOf(anyLong());

        when(eventTimelineService.timelineOf(1L)).thenReturn(List.of());
        assertTrue(rule.check(draftContext()).isEmpty());
    }
}
