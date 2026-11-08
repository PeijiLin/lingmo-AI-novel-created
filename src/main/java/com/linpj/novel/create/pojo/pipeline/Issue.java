package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * Critic 审查问题项（结构化产物，JSON 契约的一部分）
 *
 * @author HL
 */
@Data
public class Issue implements Serializable {

    /** 问题严重度：HIGH / MEDIUM / LOW */
    private String severity;

    /** 问题类型：CHARACTER_OOC / TIMELINE / PLOT_HOLE / WORLDVIEW_CONFLICT / STYLE_DRIFT */
    private String type;

    /** 引用正文片段（Critic 必须给出 evidence 原文引用，禁止凭主观偏好） */
    private String evidence;

    /** 问题描述 */
    private String problem;

    /** 修改建议 */
    private String suggestion;
}
