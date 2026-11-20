package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelEventTimelineMapper;
import com.linpj.novel.create.pojo.entity.NovelEventTimeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 事件时间线服务（v2.0 设计 6.3 三时间轴之 T_story 账本）
 *
 * - 写入：recordEvent（归一化偏移 + 参照锚点 = 最近已锚定事件的天数）
 * - 读取：查书内时间线 / 最近锚点 / 按叙述序查询
 * - DREAM/STORY_IN_STORY 事件照常入库但不参与锚点推进（isAnchorable 判定，
 *   对应 C2→C4 联动：梦境不锚定时间轴）
 *
 * 单一写入者：仅定稿结算链（ChapterFinalizeService/C6 抽取节点）调用，管道内不写。
 *
 * @author HL
 */
@Service
public class EventTimelineService extends ServiceImpl<NovelEventTimelineMapper, NovelEventTimeline> {

    private static final Logger log = LoggerFactory.getLogger(EventTimelineService.class);

    private static final String MODE_NORMAL = "NORMAL";
    private static final String MODE_FLASHBACK = "FLASHBACK";
    private static final String MODE_DREAM = "DREAM";
    private static final String MODE_STORY_IN_STORY = "STORY_IN_STORY";

    /**
     * 事件入账：先归一化（offset + 参照锚点），再落库。
     *
     * @param offset     LLM 结构化偏移（null = 无时间信息 → UNKNOWN）
     * @param rawTimeText 原文时间表述（审计保留）
     */
    public void recordEvent(Long bookId, Long chapterId, Integer sceneNo, String narrativeMode,
                            TimeNormalization.Offset offset, String rawTimeText,
                            Long sourceChapter, String eventDesc, String location,
                            List<Long> participantIds) {
        if (bookId == null || eventDesc == null || eventDesc.isBlank()) {
            return;
        }
        String mode = normalizeMode(narrativeMode);
        // 参照锚点：取"时间轴推进场景"（NORMAL/FLASHBACK）中最近一个已锚定事件的天数；
        // DREAM/STORY_IN_STORY 不推进时间轴，也不以其为锚（梦境时间不可信，锚进去会污染整条轴）
        Integer referenceDay = isAnchorable(mode)
                ? lastAnchoredDayIndex(bookId, chapterId, sceneNo)
                : null;
        TimeNormalization.Resolved resolved = TimeNormalization.resolve(offset, referenceDay);
        if (resolved.kind() == StoryTimeKind.RELATIVE_RESOLVED
                || resolved.kind() == StoryTimeKind.FUZZY) {
            if (!isAnchorable(mode)) {
                // 防御：不可锚定场景即使解析出天数也不落 day_index（分型保留，天数置空）
                resolved = new TimeNormalization.Resolved(resolved.kind(), null);
            }
        }

        NovelEventTimeline event = new NovelEventTimeline();
        event.setBookId(bookId);
        event.setChapterId(chapterId);
        event.setSceneNo(sceneNo);
        event.setNarrativeMode(mode);
        event.setTimeKind(resolved.kind().name());
        event.setDayIndex(resolved.dayIndex());
        event.setRawTimeText(rawTimeText);
        event.setEventDesc(eventDesc);
        event.setLocation(location);
        event.setParticipants(participantIds);
        event.setSourceChapter(sourceChapter != null ? sourceChapter : chapterId);
        try {
            this.save(event);
        } catch (Exception e) {
            // 时间线是校验依据（审计/规则数据源）：单条失败告警不抛（写入方为定稿链，不能被拖死）
            log.error("事件入账失败（不阻塞结算）: bookId={}, event={}", bookId, eventDesc, e);
        }
    }

    /** 书内时间线（按叙述序：章 + 场景） */
    public List<NovelEventTimeline> timelineOf(Long bookId) {
        return this.list(new LambdaQueryWrapper<NovelEventTimeline>()
                .eq(NovelEventTimeline::getBookId, bookId)
                .orderByAsc(NovelEventTimeline::getChapterId)
                .orderByAsc(NovelEventTimeline::getSceneNo)
                .orderByAsc(NovelEventTimeline::getId));
    }

    /**
     * 最近锚点天数（供相对偏移解析的参照）。
     * 只看 NORMAL 模式且已锚定（day_index 非空）的事件，按叙述序取最后一条的下一天语义：
     * 参照即"当前进度天数"。
     */
    public Integer lastAnchoredDayIndex(Long bookId, Long currentChapterId, Integer currentSceneNo) {
        if (bookId == null) {
            return null;
        }
        LambdaQueryWrapper<NovelEventTimeline> wrapper = new LambdaQueryWrapper<NovelEventTimeline>()
                .eq(NovelEventTimeline::getBookId, bookId)
                .eq(NovelEventTimeline::getNarrativeMode, MODE_NORMAL)
                .isNotNull(NovelEventTimeline::getDayIndex);
        if (currentChapterId != null) {
            wrapper.lt(NovelEventTimeline::getChapterId, currentChapterId)
                    .or(w -> w.eq(NovelEventTimeline::getChapterId, currentChapterId)
                            .le(currentSceneNo != null,
                                    NovelEventTimeline::getSceneNo, currentSceneNo == null ? 0 : currentSceneNo - 1));
        }
        wrapper.orderByDesc(NovelEventTimeline::getDayIndex)
                .orderByDesc(NovelEventTimeline::getId)
                .last("LIMIT 1");
        NovelEventTimeline latest = this.getOne(wrapper, false);
        return latest == null ? null : latest.getDayIndex();
    }

    /** 叙述顺序上某章之前（含）的最后已知天数：倒叙场景回忆目标定位的查询入口 */
    public Integer anchoredDayAsOf(Long bookId, Long asOfChapterId) {
        if (bookId == null || asOfChapterId == null) {
            return null;
        }
        NovelEventTimeline latest = this.getOne(new LambdaQueryWrapper<NovelEventTimeline>()
                .eq(NovelEventTimeline::getBookId, bookId)
                .eq(NovelEventTimeline::getNarrativeMode, MODE_NORMAL)
                .isNotNull(NovelEventTimeline::getDayIndex)
                .le(NovelEventTimeline::getChapterId, asOfChapterId)
                .orderByDesc(NovelEventTimeline::getDayIndex)
                .last("LIMIT 1"), false);
        return latest == null ? null : latest.getDayIndex();
    }

    /** 场景是否锚定时间轴（DREAM/STORY_IN_STORY/UNRELIABLE 不锚定） */
    private static boolean isAnchorable(String mode) {
        return MODE_NORMAL.equals(mode) || MODE_FLASHBACK.equals(mode);
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_NORMAL;
        }
        String upper = mode.trim().toUpperCase();
        return switch (upper) {
            case MODE_FLASHBACK, MODE_DREAM, MODE_STORY_IN_STORY, "UNRELIABLE" -> upper;
            default -> MODE_NORMAL;
        };
    }
}
