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
 * 伏笔账本（核心资产）
 * 状态机：OPEN（已埋未收）-> RESOLVED（已收）/ ABANDONED（弃置）
 * 准入：Planner 声明的 PLANT/RESOLVE 经 Critic 复核通过后入账；正文内联 <!--plot:--> 标记补充入账
 *
 * @author HL
 */
@TableName(value = "novel_plot_threads")
@Data
public class NovelPlotThread implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    /** 伏笔描述 */
    private String description;

    /** 埋设章节 ID */
    private Long plantedChapterId;

    /** OPEN / RESOLVED / ABANDONED */
    private String status;

    /** 收束章节 ID */
    private Long resolvedChapterId;

    /**
     * 知情集合（v2.0 M2-C5 闭世界：JSONB 角色ID数组，非空即"带知情边界的事实"）。
     * 闭世界判定：角色 A 是否知道 S = 查询 A ∈ whoKnows，不穷举不知道。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object whoKnows;

    /** 正文可检索短关键词（如"师父死因"）——RC-05 对成稿做知识边界文本定位用（null=不做文本定位） */
    private String secretKey;

    /**
     * 计划回收章距（B1-3 伏笔排期：距埋设章 N 章后回收，Planner 声明"约 N 章后收"）。
     * 存相对章距而非绝对章号，避免与 book_chapter 章号域耦合；null = 未声明（现状行为）。
     */
    private Integer plannedOffset;

    /**
     * 截止回收章距（埋设章起 N 章内必须回收；超过触发 RC-09 逾期告警并回灌 Planner）。
     * 排期属 meta 信息：Writer 侧上下文不下发（H4 可见性隔离），仅 Planner/Critic 渲染。
     */
    private Integer deadlineOffset;

    private Date createdAt;

    private Date updatedAt;
}
