package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

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

    /**
     * 知情角色 ID 列表（v2.0 M2-C5）：
     * PLANT = 埋设时刻的初始知情者；RESOLVE/ADVANCE = 本次新增知情者（并入集合，收束即合法揭穿时刻）。
     * 与 secretKey 同时提供时该伏笔按"带知情边界的秘密"维护。
     */
    private List<Long> whoKnows;

    /** 秘密文本定位关键词（如"师父死因"）；PLANT 声明秘密时与 whoKnows 同时提供 */
    private String secretKey;

    /**
     * 计划回收章距（B1-3 伏笔排期："约 N 章后回收"，Planner 声明，可空）——落库 planned_offset。
     */
    private Integer plannedIn;

    /**
     * 截止回收章距（"须 N 章内回收"，超期触发 RC-09 逾期告警回灌 Planner，可空）——落库 deadline_offset。
     */
    private Integer deadlineIn;
}
