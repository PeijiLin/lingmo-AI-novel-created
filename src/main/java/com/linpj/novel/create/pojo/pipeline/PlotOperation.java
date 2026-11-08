package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 伏笔操作（Planner 声明的写作意图；PLANT/RESOLVE 经 Critic 复核通过后入账）
 *
 * @author HL
 */
@Data
public class PlotOperation implements Serializable {

    /** 操作类型：PLANT（埋）/ RESOLVE（收）/ ADVANCE（推进） */
    private String op;

    /** 已存在伏笔的账本 ID；新埋伏笔时为空，以 description 为准入账 */
    private Long threadId;

    /** 伏笔描述（PLANT 新埋时必填；RESOLVE/ADVANCE 引用既有伏笔时可空） */
    private String description;

    /** 目标章节描述（如"下一章"），可选 */
    private String target;
}
