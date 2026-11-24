package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.constant.CacheConsts;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.mapper.novel.BookContentMapper;
import com.linpj.novel.create.pojo.entity.BookContent;
import com.linpj.novel.create.pojo.vo.BookContentVo;
import com.linpj.novel.create.service.BookChapterService;
import com.linpj.novel.create.service.BookContentService;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
* @author HL
* @description 针对表【book_content(小说章节内容)】的数据库操作Service实现
* @createDate 2025-09-09 19:57:42
*/
@Service
public class BookContentServiceImpl extends ServiceImpl<BookContentMapper, BookContent>
    implements BookContentService {

    private final RedisTemplate<String, Object> redisTemplate;

    public BookContentServiceImpl(RedisTemplate<String, Object> redisTemplate, BookChapterService bookChapterService) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public BookContentVo queryBookContentById(Long chapterId) {
        if (chapterId == null || chapterId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 构建缓存键
        String cacheKey = CacheConsts.REDIS_CACHE_PREFIX + "content::" + chapterId;
        // 尝试从缓存获取
        BookContentVo cachedContent = (BookContentVo) redisTemplate.opsForValue().get(cacheKey);
        if (cachedContent != null) {
            return cachedContent;
        }

        // 缓存未命中，查询数据库
        BookContent bookContent = this.getOne(new LambdaQueryWrapper<BookContent>()
                .eq(BookContent::getChapterId, chapterId));

        if (bookContent == null) {
            // 将空结果缓存，防止缓存穿透
            redisTemplate.opsForValue().set(cacheKey, "", Duration.ofMinutes(5));
            return null;
        }

        BookContentVo bookContentVo = new BookContentVo();
        BeanUtils.copyProperties(bookContent, bookContentVo);

        // 存入缓存
        redisTemplate.opsForValue().set(cacheKey, bookContentVo, Duration.ofHours(1));
        return bookContentVo;
    }
}




