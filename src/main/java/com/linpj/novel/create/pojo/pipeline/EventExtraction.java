package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 事件抽取结果（章节定稿抽取的伴生产物：时间线入账原料，C2/C6 合并决策——与人物快照同一次 LLM 调用产出）
 *
 * 时间字段语义（v2.0 设计 2.2，公理 P3 分工：LLM 只识别语义，代码做算术）：
 * - timeKind=DAY_OFFSET：相对表述（"三天后"），days=偏移天数
 * - timeKind=FUZZY：模糊表述（"半月已过"），days=估计天数
 * - timeKind=ABSOLUTE_LABEL：架空历法原文（"青云历314年春"），timeLabel=原文（不换算）
 * - 空/其他：无时间信息（绝不猜值）
 *
 * @author HL
 */
@Data
public class EventExtraction implements Serializable {

    /** 事件描述（必填） */
    private String eventDesc;

    private String location;

    /** 参与角色 ID 列表（从角色清单选择；可空数组） */
    private List<Long> participants;

    /** NARRATIVE_MODE：NORMAL / FLASHBACK / DREAM / STORY_IN_STORY（UNKNOWN 缺省按 NORMAL） */
    private String narrativeMode;

    /** DAY_OFFSET / FUZZY / ABSOLUTE_LABEL（空=无时间信息） */
    private String timeKind;

    /** 偏移天数（DAY_OFFSET/FUZZY 必填；其余可空） */
    private Integer days;

    /** 原文时间表述（ABSOLUTE_LABEL 必填；其余为审计原文可空） */
    private String timeLabel;

    private Double confidence;
}
