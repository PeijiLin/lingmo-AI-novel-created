package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.entity.NovelEventTimeline;
import com.linpj.novel.create.worldstate.EventTimelineService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RC-07 因果时序倒错（设计 6.7：high 硬）——B1-2 事件因果链（caused_by）的确定性消费端
 *
 * 判定（账本级，跨章累积检验——时间线在定稿结算时入账，本规则校验"截至上一章"的全链一致性）：
 * 对每条 caused_by 边（事件 E ← 起因 C）：
 * - E 与 C 的 T_story（day_index）均已知 且 day(E) &lt; day(C) → 后果早于原因，违规
 * - 任一端 day_index 未知（UNKNOWN/ABSOLUTE_LABEL） → 跳过该边（降级偏序，不猜值——红线 6）
 * - 任一端为 UNRELIABLE 叙事模式 → 跳过该边（不可靠叙述者的事件不可作为校验基准，M4-E5 同源语义）
 *
 * 数据依赖（B1-2 写入侧保证）：
 * - caused_by 仅接受同书已入账事件 → cause_id &lt; effect_id（入账序），因果图天然无环
 * - LLM 幻觉引用已在写入端降级为无因果，本规则无需重复防御
 *
 * 必须诚实声明的局限（默认 OFF，影子态观测后再分级）：
 * - 时点粒度为"天"（day_index 整数轴）：同日内的因果顺序不校验（无时辰轴，分辨不出）
 * - FLASHBACK 事件按回忆目标时刻锚定——倒叙章的因果仍应在故事时间上单调，若回忆内容
 *   与"正文宣称后的后续"边界模糊可能误报，影子数据观察
 *
 * @author HL
 */
@Component
public class CausalOrderRule implements ConsistencyRule {

    /** 单次校验最多上报数（长篇因果边多，防报告风暴） */
    private static final int MAX_VIOLATIONS = 5;

    private final EventTimelineService eventTimelineService;

    public CausalOrderRule(EventTimelineService eventTimelineService) {
        this.eventTimelineService = eventTimelineService;
    }

    @Override
    public String id() {
        return "RC-07";
    }

    @Override
    public List<RuleStage> stages() {
        return List.of(RuleStage.DRAFT);
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public String description() {
        return "后果事件早于起因事件（caused_by 边上 T_story 单调性校验，时间轴账本级）";
    }

    @Override
    public List<RuleViolation> check(RuleCheckContext context) {
        if (context.getBookId() == null) {
            return List.of();
        }
        List<NovelEventTimeline> timeline;
        try {
            timeline = eventTimelineService.timelineOf(context.getBookId());
        } catch (Exception e) {
            return List.of(); // 数据通道故障：规则静默
        }
        if (timeline == null || timeline.isEmpty()) {
            return List.of();
        }
        Map<Long, NovelEventTimeline> byId = timeline.stream()
                .filter(event -> event != null && event.getId() != null)
                .collect(Collectors.toMap(NovelEventTimeline::getId, event -> event,
                        (left, right) -> left));

        List<RuleViolation> violations = new ArrayList<>();
        for (NovelEventTimeline effect : timeline) {
            if (violations.size() >= MAX_VIOLATIONS) {
                break;
            }
            if (effect == null || effect.getCausedBy() == null) {
                continue;
            }
            NovelEventTimeline cause = byId.get(effect.getCausedBy());
            if (cause == null) {
                continue; // 引用悬空（脏数据/跨账本）：留给账本级守护，不在本规则重复上报
            }
            if (isSkippedMode(cause) || isSkippedMode(effect)) {
                continue; // UNRELIABLE：不可靠叙述不作为校验基准
            }
            if (cause.getDayIndex() == null || effect.getDayIndex() == null) {
                continue; // 任一端时间未知：降级偏序（UNKNOWN 不猜值，红线 6）
            }
            if (effect.getDayIndex() < cause.getDayIndex()) {
                violations.add(RuleViolation.of(id(), severity(), RuleStage.DRAFT,
                        String.format("事件「%s」(故事日 %d) 声明起因为「%s」(故事日 %d)：后果早于原因",
                                effect.getEventDesc(), effect.getDayIndex(),
                                cause.getEventDesc(), cause.getDayIndex()),
                        "核对时间抽取是否正确；若属刻意倒叙/预叙请修正事件的 day 归一或在细纲声明叙事模式", false));
            }
        }
        return violations;
    }

    /** 不可校验的叙事模式（叙诡保护区语义在规则侧的同源贯彻） */
    private static boolean isSkippedMode(NovelEventTimeline event) {
        return "UNRELIABLE".equalsIgnoreCase(event.getNarrativeMode());
    }
}
