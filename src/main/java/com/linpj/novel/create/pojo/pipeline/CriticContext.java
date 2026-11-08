package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * Critic 上下文（由 ContextAssemblyService 统一组装）
 *
 * @author HL
 */
@Data
public class CriticContext implements Serializable {

    private Long bookId;

    /** 待审正文 */
    private String draft;

    private String title;

    private String worldSetting;

    /** 出场角色卡与当前状态 */
    private String characterInfo;

    /** 滚动前情摘要 */
    private String storyDigest;

    /** 活跃伏笔清单 */
    private String activePlotThreads;

    /** 本章大纲（有则审查剧情是否符合大纲） */
    private ChapterOutline outline;

    /** 用户输入（辅助判定创作意图是否达成） */
    private String userInput;

    /** 创作类型 */
    private String creationType;

    /** 风格要求（审查 STYLE_DRIFT 的基准） */
    private String style;
}
