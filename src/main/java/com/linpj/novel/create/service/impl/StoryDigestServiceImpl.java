package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelStoryDigestMapper;
import com.linpj.novel.create.pojo.entity.NovelStoryDigest;
import com.linpj.novel.create.service.StoryDigestService;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 滚动前情摘要服务实现
 *
 * @author HL
 */
@Service
public class StoryDigestServiceImpl extends ServiceImpl<NovelStoryDigestMapper, NovelStoryDigest>
        implements StoryDigestService {

    @Override
    public String getDigest(Long bookId) {
        if (bookId == null) {
            return null;
        }
        NovelStoryDigest digest = this.getById(bookId);
        return digest != null ? digest.getSummary() : null;
    }

    @Override
    public void saveDigest(Long bookId, Long lastChapterId, String summary) {
        if (bookId == null || summary == null || summary.isBlank()) {
            return;
        }
        NovelStoryDigest digest = new NovelStoryDigest();
        digest.setBookId(bookId);
        digest.setLastChapterId(lastChapterId);
        digest.setSummary(summary);
        digest.setUpdatedAt(new Date());
        this.saveOrUpdate(digest);
    }
}
