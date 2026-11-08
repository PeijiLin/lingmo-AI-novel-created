package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * Planner 上下文（由 ContextAssemblyService 统一组装）
 *
 * @author HL
 */
@Data
public class PlannerContext implements Serializable {

    private Long bookId;

    private Long chapterId;

    private String userInput;

    private String title;

    private String worldSetting;

    /** 出场候选角色卡（含角色 ID，供大纲 characterIds 引用） */
    private String characterInfo;

    /** 滚动前情摘要 */
    private String storyDigest;

    /** 活跃伏笔清单（含伏笔 ID，供 plotOps 引用） */
    private String activePlotThreads;

    /** 创作类型 */
    private String creationType;

    /** 风格要求 */
    private String style;
}
