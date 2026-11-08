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
 * 章节大纲（Planner 产物，含修订版本）
 * @TableName novel_chapter_outline
 *
 * @author HL
 */
@TableName(value = "novel_chapter_outline", autoResultMap = true)
@Data
public class NovelChapterOutline implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long chapterId;

    /** ChapterOutline 序列化（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object outline;

    /** 修订版本（同一章节重跑递增） */
    private Integer version;

    private Date createdAt;
}
