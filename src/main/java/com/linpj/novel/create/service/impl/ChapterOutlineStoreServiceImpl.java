package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelChapterOutlineMapper;
import com.linpj.novel.create.pojo.entity.NovelChapterOutline;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.service.ChapterOutlineStoreService;
import com.linpj.novel.create.utils.JsonUtil;
import org.springframework.stereotype.Service;

/**
 * 章节大纲账本服务实现
 *
 * @author HL
 */
@Service
public class ChapterOutlineStoreServiceImpl extends ServiceImpl<NovelChapterOutlineMapper, NovelChapterOutline>
        implements ChapterOutlineStoreService {

    @Override
    public void saveOutline(Long bookId, Long chapterId, ChapterOutline outline) {
        if (bookId == null || outline == null) {
            return;
        }
        Integer currentVersion = this.list(new LambdaQueryWrapper<NovelChapterOutline>()
                        .eq(NovelChapterOutline::getBookId, bookId)
                        .orderByDesc(NovelChapterOutline::getVersion)
                        .last("LIMIT 1"))
                .stream().findFirst().map(NovelChapterOutline::getVersion).orElse(0);

        NovelChapterOutline record = new NovelChapterOutline();
        record.setBookId(bookId);
        record.setChapterId(chapterId);
        record.setOutline(outline);
        record.setVersion(currentVersion.intValue() + 1);
        this.save(record);
    }

    @Override
    public ChapterOutline loadLatest(Long bookId) {
        if (bookId == null) {
            return null;
        }
        return this.list(new LambdaQueryWrapper<NovelChapterOutline>()
                        .eq(NovelChapterOutline::getBookId, bookId)
                        .orderByDesc(NovelChapterOutline::getId)
                        .last("LIMIT 1"))
                .stream().findFirst()
                .map(record -> JsonUtil.fromJson(JsonUtil.toJson(record.getOutline()), ChapterOutline.class))
                .orElse(null);
    }
}
