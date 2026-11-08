package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 章节大纲（Planner 结构化产物，`.call().entity(ChapterOutline.class)`）
 *
 * @author HL
 */
@Data
public class ChapterOutline implements Serializable {

    /** 章节标题 */
    private String chapterTitle;

    /** 场景序列（按叙事顺序） */
    private List<Scene> scenes;

    /** 本章出场人物角色 ID */
    private List<Long> characterIds;

    /** 伏笔操作：PLANT(埋) / RESOLVE(收) / ADVANCE(推进) */
    private List<PlotOperation> plotOps;
}
