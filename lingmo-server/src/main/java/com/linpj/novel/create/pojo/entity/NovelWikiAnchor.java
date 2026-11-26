package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Wiki 锚点（§3.1：每条硬事实/正文片段的依据）
 *
 * <p>field = attrs 键名或 CANON（页卡正文）；source_type 指向依据来源：
 * AUTHOR（作者录入）/ CHAPTER（章节原文）/ FACT（state_fact 账本）/ PROPOSAL（提案）。
 * 引文回指链：检索证据 → anchor → 原文/事实，即"这条设定的依据是什么"。
 *
 * @author HL
 */
@TableName(value = "novel_wiki_anchor")
@Data
public class NovelWikiAnchor implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long pageId;

    /** attrs 键名或 CANON（页卡正文） */
    private String field;

    /** AUTHOR / CHAPTER / FACT / PROPOSAL */
    private String sourceType;

    /** 章节 id / state_fact id / proposal id */
    private Long sourceId;

    private Integer chapterNum;

    /** 依据原文（截断存档） */
    private String quote;

    /** 置信度 0-1（抽取来源才有，作者录入恒 1.00） */
    private Double confidence;

    @TableField("created_at")
    private Date createdAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
