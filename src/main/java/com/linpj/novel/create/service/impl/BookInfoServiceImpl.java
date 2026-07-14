package com.linpj.novel.create.service.impl;
import java.time.Duration;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.reflect.TypeToken;
import com.linpj.novel.create.constant.CacheConsts;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.mapper.novel.BookInfoMapper;
import com.linpj.novel.create.pojo.dto.BookPageRequest;
import com.linpj.novel.create.pojo.entity.AuthorInfo;
import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.pojo.vo.BookInfoListVo;
import com.linpj.novel.create.service.AuthorInfoService;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.utils.CommonHandle;
import com.linpj.novel.create.utils.JsonUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
* @author HL
* @description 针对表【book_info(小说信息)】的数据库操作Service实现
* @createDate 2025-09-09 16:27:08
*/
@Service
public class BookInfoServiceImpl extends ServiceImpl<BookInfoMapper, BookInfo>
    implements BookInfoService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final AuthorInfoService authorInfoService;

    public BookInfoServiceImpl(RedisTemplate<String, Object> redisTemplate, AuthorInfoService authorInfoService) {
        this.redisTemplate = redisTemplate;
        this.authorInfoService = authorInfoService;
    }


    @Override
    public Page<BookInfoListVo> pageBookList(BookPageRequest bookPageRequest) {
        ThrowsUtils.throwIf(CommonHandle.isReptile(bookPageRequest.getPageSize()), ErrorCode.REPTILE_ERROR);
        String cacheKey = buildCacheKey(bookPageRequest);

        // 尝试从缓存获取
        Page<BookInfoListVo> cachedPage = (Page<BookInfoListVo>) redisTemplate.opsForValue().get(cacheKey);
        if (cachedPage != null) {
            return cachedPage;
        }
        // 创建分页对象
        Page<BookInfo> page = new Page<>(bookPageRequest.getPageNum(), bookPageRequest.getPageSize());

        // 创建查询条件
        LambdaQueryWrapper<BookInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BookInfo::getBookStatus, 0); // 连载中的书籍
        wrapper.orderByDesc(BookInfo::getCreateTime); // 按创建时间倒序
        if (bookPageRequest.getCategoryId() != null) {
            wrapper.eq(BookInfo::getCategoryId, bookPageRequest.getCategoryId());
        }

        if (bookPageRequest.getWorkDirection() != null) {
            wrapper.eq(BookInfo::getWorkDirection, bookPageRequest.getWorkDirection());
        }

        // 执行分页查询
        Page<BookInfo> result = this.page(page, wrapper);
        // 信息过滤
        Page<BookInfoListVo> voPage = new Page<>();
        voPage.setTotal(result.getTotal());
        voPage.setCurrent(result.getCurrent());
        voPage.setSize(result.getSize());
        voPage.setRecords(result.getRecords().stream().map(this::convertToVo).toList());
        redisTemplate.opsForValue().set(cacheKey, voPage, Duration.ofMinutes(30));
        return voPage;
    }

    @Override
    public List<BookInfoListVo> queryBookListByAuthorId(Long userId) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        AuthorInfo authorInfo = authorInfoService.getOne(new LambdaQueryWrapper<AuthorInfo>().eq(AuthorInfo::getUserId, userId));
        if (authorInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该作家不存在");
        }
        Long authorId = authorInfo.getId();
        List<BookInfo> list = this.list(new LambdaQueryWrapper<BookInfo>().eq(BookInfo::getAuthorId, authorId));
        return list.stream().map(this::convertToVo).toList();
    }

    @Override
    public BookInfoListVo queryBookInfoById(Long bookId) {
        BookInfo bookInfo = this.getById(bookId);
        return convertToVo(bookInfo);
    }

    // 提取缓存 key 构建逻辑
    private String buildCacheKey(BookPageRequest request) {
        return String.format("%s:%s:%s:%s:%s",
                CacheConsts.REDIS_CACHE_PREFIX,
                request.getCategoryId() != null ? request.getCategoryId() : "all",
                request.getWorkDirection() != null ? request.getWorkDirection() : "all",
                request.getPageNum(),
                request.getPageSize());
    }

    // 提取 VO 转换逻辑
    private BookInfoListVo convertToVo(BookInfo bookInfo) {
        BookInfoListVo vo = new BookInfoListVo();
        vo.setId(bookInfo.getId());
        vo.setWorkDirection(bookInfo.getWorkDirection());
        vo.setCategoryId(bookInfo.getCategoryId());
        vo.setCategoryName(bookInfo.getCategoryName());
        vo.setPicUrl(bookInfo.getPicUrl());
        vo.setBookName(bookInfo.getBookName());
        vo.setAuthorId(bookInfo.getAuthorId());
        vo.setAuthorName(bookInfo.getAuthorName());
        vo.setBookDesc(bookInfo.getBookDesc());
        vo.setScore(bookInfo.getScore());
        vo.setBookStatus(bookInfo.getBookStatus());
        vo.setVisitCount(bookInfo.getVisitCount());
        vo.setWordCount(bookInfo.getWordCount());
        vo.setCommentCount(bookInfo.getCommentCount());
        vo.setLastChapterId(bookInfo.getLastChapterId());
        vo.setLastChapterName(bookInfo.getLastChapterName());
        vo.setLastChapterUpdateTime(bookInfo.getLastChapterUpdateTime());
        vo.setIsVip(bookInfo.getIsVip());
        vo.setCreateTime(bookInfo.getCreateTime());
        List<String> tagsList = JsonUtil.fromJson(bookInfo.getTags(), new TypeToken<List<String>>() {
        }.getType());
        vo.setTags(tagsList);
        return vo;
    }
}




