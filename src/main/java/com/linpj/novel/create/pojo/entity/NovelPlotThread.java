package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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

    private Date createdAt;

    private Date updatedAt;
}
