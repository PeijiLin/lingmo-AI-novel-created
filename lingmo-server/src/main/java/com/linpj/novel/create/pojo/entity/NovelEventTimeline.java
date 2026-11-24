package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linpj.novel.create.utils.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 事件时间线条目（v2.0 设计 6.3：三时间轴中的 T_story 载体）
 *
 * 三时间轴映射：
 * - T_story：dayIndex（书内天数轴，代码归一化产物）
 * - T_narr：chapterId + sceneNo（叙述顺序）
 * - T_sys：createdAt（系统写入时间，审计用）
 *
 * narrative_mode=C4 场景叙事模式；FLASHBACK/DREAM/STORY_IN_STORY 场景的事件
 * 是否推进时间轴由装配/校验侧按模式决定（DREAM 不锚定时间、FLASHBACK 锚回忆目标章）。
 *
 * @TableName novel_event_timeline
 *
 * @author HL
 */
@TableName(value = "novel_event_timeline", autoResultMap = true)
@Data
public class NovelEventTimeline implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    /** T_narr：章节 */
    private Long chapterId;

    /** T_narr：场景号（可空 = 整章事件） */
    private Integer sceneNo;

    /** C4 叙事模式：NORMAL/FLASHBACK/DREAM/STORY_IN_STORY/UNRELIABLE */
    private String narrativeMode;

    /** 时间归一化分型 */
    private String timeKind;

    /** T_story：书内天数轴（null = 不定，时序校验按偏序处理） */
    private Integer dayIndex;

    /** 原文时间表述（"三天后夜晚"——审计追溯与人工核对） */
    private String rawTimeText;

    /** 事件描述（"林砚与顾昭王都论剑"） */
    private String eventDesc;

    private String location;

    /** 参与角色 ID 列表（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object participants;

    /** FLASHBACK 场景的回忆目标章（T_narr 溯源；NORMAL 为自身章） */
    private Long sourceChapter;

    /**
     * 直接起因事件 ID（B1-2 因果链，RC-07 数据源）。
     * 仅允许引用已入账事件（写入端校验存在性），故必有 cause_id &lt; effect_id，图天然无环。
     */
    private Long causedBy;

    private Date createdAt;
}
