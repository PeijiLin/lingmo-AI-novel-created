package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 场景（大纲的最小叙事单元，每个场景必须有冲突）
 *
 * @author HL
 */
@Data
public class Scene implements Serializable {

    /** 场景地点 */
    private String location;

    /** 参与人物角色 ID 列表 */
    private java.util.List<Long> participantIds;

    /** 本场景冲突 */
    private String conflict;

    /** 场景结果 */
    private String outcome;

    /**
     * 叙事模式（v2.0 设计 2.1，默认 NORMAL）：影响状态求解锚点与规则适用范围。
     * UNRELIABLE 必须由作者显式声明；FLASHBACK 应同时给出 flashbackTargetChapter。
     */
    private NarrativeMode narrativeMode;

    /** FLASHBACK 场景的回忆目标章号（状态按该章时刻求解；其余模式为空） */
    private Long flashbackTargetChapter;
}
