package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * Writer 上下文（由 ContextAssemblyService 统一组装；阶段1 先由编排器用请求直填，
 * 阶段2 起从 World State 账本组装：world_setting + story_digest + 出场角色卡 + 伏笔 + 大纲）
 *
 * @author HL
 */
@Data
public class WriterContext implements Serializable {

    private Long bookId;

    private Long chapterId;

    /** 用户输入（续写指令/草稿/关键词） */
    private String userInput;

    /** 创作类型：续写 / 对话 / 情节 / 描写 / 润色 / 灵感 */
    private String creationType;

    private String title;

    /** 世界观设定（阶段2起：服务端 book_info.world_setting 为唯一事实源，前端值仅覆盖提示） */
    private String worldSetting;

    /** 风格要求 */
    private String style;

    /** 出场角色卡（完整）+ 当前动态状态（阶段2起由账本组装） */
    private String characterInfo;

    /** 滚动前情摘要（<=2000 字，阶段2起） */
    private String storyDigest;

    /** 活跃伏笔清单（阶段2起） */
    private String activePlotThreads;

    /** 章节大纲（REFINED/PRECISE；可空=Planner 降级直写） */
    private ChapterOutline outline;
}
