package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 滚动前情摘要（book 级唯一，控制在 2000 字内；章节定稿后异步滚动更新）
 * @TableName novel_story_digest
 *
 * @author HL
 */
@TableName(value = "novel_story_digest")
@Data
public class NovelStoryDigest implements Serializable {

    /** 书籍 ID（主键） */
    @TableId(type = IdType.INPUT)
    private Long bookId;

    /** 滚动摘要（<=2000 字） */
    private String summary;

    /** 摘要覆盖到的最后章节 */
    private Long lastChapterId;

    private Date updatedAt;
}
