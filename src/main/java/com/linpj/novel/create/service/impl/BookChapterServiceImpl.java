package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.constant.CacheConsts;
import com.linpj.novel.create.mapper.novel.BookChapterMapper;
import com.linpj.novel.create.pojo.dto.ChapterPageRequest;
import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.vo.BookChapterVo;
import com.linpj.novel.create.service.BookChapterService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author HL
* @description 针对表【book_chapter(小说章节)】的数据库操作Service实现
* @createDate 2025-09-09 19:57:42
*/
@Service
public class BookChapterServiceImpl extends ServiceImpl<BookChapterMapper, BookChapter>
    implements BookChapterService {

    private final RedisTemplate<String, Object> redisTemplate;

    public BookChapterServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Page<BookChapterVo> queryBookChapterById(ChapterPageRequest chapterPageRequest) {
        // 构建缓存键
        String cacheKey = String.format("%s:%s:%s:%s",
                CacheConsts.BOOK_CHAPTER_LIST_CACHE_KEY,
                chapterPageRequest.getBookInfoId(),
                chapterPageRequest.getPageNum(),
                chapterPageRequest.getPageSize());
        try {
            // 尝试从缓存获取
            Object cachedObject = redisTemplate.opsForValue().get(cacheKey);
            if (cachedObject instanceof Page) {
                // 从Redis获取的Page对象可能需要特殊处理
                Page<BookChapterVo> cachedPage = convertToBookChapterVoPage((Page<?>) cachedObject);
                if (cachedPage != null) {
                    return cachedPage;
                }
            }
        } catch (Exception e) {
            log.warn("从Redis获取章节列表缓存失败，将从数据库查询");
        }
        Page<BookChapter> page = new Page<>(chapterPageRequest.getPageNum(), chapterPageRequest.getPageSize());
        LambdaUpdateWrapper<BookChapter> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BookChapter::getBookId, chapterPageRequest.getBookInfoId());
        Page<BookChapter> chapterPage = this.page(page, wrapper);
        Page<BookChapterVo> chapterVoPage = new Page<>();
        chapterVoPage.setSize(chapterPage.getSize());
        chapterVoPage.setCurrent(chapterPage.getCurrent());
        chapterVoPage.setTotal(chapterPage.getTotal());
        chapterVoPage.setRecords(chapterPage.getRecords().stream().map(bookChapter -> {
            BookChapterVo bookChapterVo = new BookChapterVo();
            bookChapterVo.setId(bookChapter.getId());
            bookChapterVo.setBookId(bookChapter.getBookId());
            bookChapterVo.setChapterNum(bookChapter.getChapterNum());
            bookChapterVo.setChapterName(bookChapter.getChapterName());
            bookChapterVo.setWordCount(bookChapter.getWordCount());
            bookChapterVo.setIsVip(bookChapter.getIsVip());
            bookChapterVo.setCreateTime(bookChapter.getCreateTime());
            return bookChapterVo;
        }).toList());
        // 存入缓存
        try {
            redisTemplate.opsForValue().set(cacheKey, chapterVoPage, Duration.ofMinutes(30));
        } catch (Exception e) {
            log.warn("存储章节列表到Redis缓存失败");
        }
        return chapterVoPage;
    }

    /**
     * 将从Redis获取的Page对象转换为BookChapterVo Page对象
     * @param sourcePage 从Redis获取的Page对象
     * @return 转换后的Page对象
     */
    private Page<BookChapterVo> convertToBookChapterVoPage(Page<?> sourcePage) {
        try {
            Page<BookChapterVo> targetPage = new Page<>();
            targetPage.setSize(sourcePage.getSize());
            targetPage.setCurrent(sourcePage.getCurrent());
            targetPage.setTotal(sourcePage.getTotal());

            List<BookChapterVo> records = sourcePage.getRecords().stream()
                    .filter(obj -> obj instanceof BookChapterVo)
                    .map(obj -> (BookChapterVo) obj)
                    .collect(Collectors.toList());

            targetPage.setRecords(records);
            return targetPage;
        } catch (Exception e) {
            log.warn("转换Page对象失败");
            return null;
        }
    }
}




