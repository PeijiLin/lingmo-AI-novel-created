package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.entity.NovelChapterOutline;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;

/**
 * 章节大纲账本服务（Planner 产物持久化，重写修订时版本递增）
 *
 * @author HL
 */
public interface ChapterOutlineStoreService extends IService<NovelChapterOutline> {

    /** 保存大纲（新版本递增） */
    void saveOutline(Long bookId, Long chapterId, ChapterOutline outline);

    /** 读取本书最新一版大纲（断点续跑 / 调试用） */
    ChapterOutline loadLatest(Long bookId);

    /**
     * 读取指定章节的最新一版大纲（F2 断点续跑：多章节书的精确恢复口径——
     * 按 bookId 取 latest 可能拿到其他章节的版本，恢复必须按 chapter 定位）。
     */
    ChapterOutline loadLatest(Long bookId, Long chapterId);
}
