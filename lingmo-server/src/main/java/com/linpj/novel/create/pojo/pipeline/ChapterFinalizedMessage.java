package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 章节定稿消息（novel.chapter.finalized topic 载荷）
 *
 * 消费者执行：滚动摘要更新 story_digest + 人物动态状态抽取 + 内联伏笔补账。
 *
 * @author HL
 */
@Data
public class ChapterFinalizedMessage implements Serializable {

    private Long bookId;

    /** 章节记录 ID（create_book.id） */
    private Long chapterId;

    private String title;

    /** 定稿正文 */
    private String content;
}
