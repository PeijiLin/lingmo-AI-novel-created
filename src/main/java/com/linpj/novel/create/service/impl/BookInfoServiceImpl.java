package com.linpj.novel.create.service.impl;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.reflect.TypeToken;
import com.linpj.novel.create.constant.CacheConsts;
import com.linpj.novel.create.context.UserStatusContext;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.mapper.novel.BookInfoMapper;
import com.linpj.novel.create.mapper.novel.CreateBookMapper;
import com.linpj.novel.create.pojo.dto.BookCreateRequest;
import com.linpj.novel.create.pojo.dto.BookPageRequest;
import com.linpj.novel.create.pojo.entity.AuthorInfo;
import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.entity.BookContent;
import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.pojo.entity.CreateBook;
import com.linpj.novel.create.pojo.vo.BookInfoListVo;
import com.linpj.novel.create.service.AuthorInfoService;
import com.linpj.novel.create.service.BookChapterService;
import com.linpj.novel.create.service.BookContentService;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.utils.CommonHandle;
import com.linpj.novel.create.utils.JsonUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
* @author HL
* @description 针对表【book_info(小说信息)】的数据库操作Service实现
* @createDate 2025-09-09 16:27:08
*/
@Slf4j
@Service
public class BookInfoServiceImpl extends ServiceImpl<BookInfoMapper, BookInfo>
    implements BookInfoService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final AuthorInfoService authorInfoService;

    private final BookChapterService bookChapterService;

    private final BookContentService bookContentService;

    @Resource
    private CreateBookMapper createBookMapper;

    public BookInfoServiceImpl(RedisTemplate<String, Object> redisTemplate,
                               AuthorInfoService authorInfoService,
                               BookChapterService bookChapterService,
                               BookContentService bookContentService) {
        this.redisTemplate = redisTemplate;
        this.authorInfoService = authorInfoService;
        this.bookChapterService = bookChapterService;
        this.bookContentService = bookContentService;
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
        AuthorInfo authorInfo = resolveAuthorInfo(userId, false);
        if (authorInfo == null) {
            // 尚无作者档案：视为新用户，返回空列表（前端引导新建作品）
            return List.of();
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookInfoListVo createBook(BookCreateRequest request) {
        if (request == null || StringUtils.isBlank(request.getBookName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "书名不能为空");
        }
        // 确定作者：优先用请求显式 authorId，否则从当前登录用户解析/创建
        Long authorId = request.getAuthorId();
        String authorName = null;
        if (authorId == null || authorId <= 0) {
            Long currentUserId = UserStatusContext.getCurrentUserId();
            ThrowsUtils.throwIf(currentUserId == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录再创建作品");
            AuthorInfo authorInfo = resolveAuthorInfo(currentUserId, true);
            authorId = authorInfo.getId();
            authorName = authorInfo.getPenName();
        } else {
            AuthorInfo authorInfo = authorInfoService.getById(authorId);
            if (authorInfo != null) {
                authorName = authorInfo.getPenName();
            }
        }

        BookInfo bookInfo = new BookInfo();
        bookInfo.setBookName(request.getBookName().trim());
        bookInfo.setBookDesc(request.getBookDesc());
        bookInfo.setWorkDirection(request.getWorkDirection());
        bookInfo.setAuthorId(authorId);
        bookInfo.setAuthorName(StringUtils.isNotBlank(authorName) ? authorName : request.getBookName().trim());
        bookInfo.setBookStatus(0); // 连载中
        bookInfo.setVisitCount(0L);
        bookInfo.setScore(0);
        bookInfo.setWordCount(0);
        bookInfo.setIsVip(0);
        java.util.Date now = new java.util.Date();
        bookInfo.setCreateTime(now);
        bookInfo.setUpdateTime(now);
        boolean saved = this.save(bookInfo);
        ThrowsUtils.throwIf(!saved, ErrorCode.SYSTEM_ERROR, "新建作品失败");
        // 清理首页列表缓存，确保新作品即时可见
        clearBookListCache();
        return convertToVo(bookInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishBook(Long bookId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        BookInfo bookInfo = this.getById(bookId);
        ThrowsUtils.throwIf(bookInfo == null, ErrorCode.NOT_FOUND_ERROR, "作品不存在");

        List<CreateBook> drafts = createBookMapper.selectList(
                new LambdaQueryWrapper<CreateBook>().eq(CreateBook::getBookId, bookId));
        if (drafts == null || drafts.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "尚无章节草稿可发布");
        }
        // 按章节序号排序（未设置序号放最后）
        drafts.sort(Comparator.comparing(CreateBook::getNumber, Comparator.nullsLast(Integer::compareTo)));

        // 该书已发布的章节：number -> chapter（用于幂等更新）
        List<BookChapter> existingChapters = bookChapterService.list(
                new LambdaQueryWrapper<BookChapter>().eq(BookChapter::getBookId, bookId));
        Map<Integer, BookChapter> existingByNum = new java.util.HashMap<>();
        for (BookChapter c : existingChapters) {
            if (c.getChapterNum() != null) {
                existingByNum.put(c.getChapterNum(), c);
            }
        }

        int totalWords = 0;
        BookChapter lastChapter = null;
        for (CreateBook draft : drafts) {
            String plain = contentToPlainText(draft.getContent());
            int wordCount = draft.getWordCount() != null ? draft.getWordCount() : plain.length();
            totalWords += wordCount;

            BookChapter chapter = existingByNum.get(draft.getNumber());
            if (chapter == null) {
                chapter = new BookChapter();
                chapter.setBookId(bookId);
                chapter.setChapterNum(draft.getNumber());
                chapter.setIsVip(0);
                java.util.Date now = new java.util.Date();
                chapter.setCreateTime(now);
                chapter.setUpdateTime(now);
            }
            chapter.setChapterName(draft.getTitle());
            chapter.setWordCount(wordCount);
            chapter.setUpdateTime(new java.util.Date());
            if (chapter.getId() == null) {
                bookChapterService.save(chapter);
            } else {
                bookChapterService.updateById(chapter);
            }

            // 内容：以 chapterId 幂等写 book_content
            BookContent existingContent = bookContentService.getOne(
                    new LambdaQueryWrapper<BookContent>().eq(BookContent::getChapterId, chapter.getId()));
            if (existingContent == null) {
                BookContent content = new BookContent();
                content.setChapterId(chapter.getId());
                content.setContent(plain);
                java.util.Date now = new java.util.Date();
                content.setCreateTime(now);
                content.setUpdateTime(now);
                bookContentService.save(content);
            } else {
                existingContent.setContent(plain);
                existingContent.setUpdateTime(new java.util.Date());
                bookContentService.updateById(existingContent);
            }
            lastChapter = chapter;
        }

        // 回写 book_info：字数 / 最新章节 / 连载状态
        java.util.Date now = new java.util.Date();
        bookInfo.setWordCount(totalWords);
        if (lastChapter != null) {
            bookInfo.setLastChapterId(lastChapter.getId());
            bookInfo.setLastChapterName(lastChapter.getChapterName());
            bookInfo.setLastChapterUpdateTime(now);
        }
        bookInfo.setBookStatus(0); // 连载中
        bookInfo.setUpdateTime(now);
        this.updateById(bookInfo);
        // 刷新列表与榜单缓存
        clearBookListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementReadingCount(Long bookId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        BookInfo bookInfo = this.getById(bookId);
        if (bookInfo == null) {
            return;
        }
        long visit = bookInfo.getVisitCount() == null ? 0L : bookInfo.getVisitCount();
        bookInfo.setVisitCount(visit + 1);
        this.updateById(bookInfo);
    }

    @Override
    public List<BookInfoListVo> queryRanking(int limit) {
        if (limit <= 0 || limit > 100) {
            limit = 20;
        }
        List<BookInfo> list = this.list(new LambdaQueryWrapper<BookInfo>()
                .orderByDesc(BookInfo::getVisitCount)
                .last("LIMIT " + limit));
        return list.stream().map(this::convertToVo).toList();
    }

    // ---------- 私有辅助 ----------

    /**
     * 解析作者：委托 AuthorInfoService 统一实现（无档案时按需创建）。
     */
    private AuthorInfo resolveAuthorInfo(Long userId, boolean createIfAbsent) {
        return authorInfoService.resolveByUserId(userId, createIfAbsent);
    }

    /**
     * 章节内容转纯文本（兼容 Tiptap JSONB 结构与纯文本字符串）。
     */
    private String contentToPlainText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String) {
            // 可能是纯文本，也可能是 JSON 字符串；尝试解析
            String s = (String) content;
            if (s.startsWith("{") || s.startsWith("[")) {
                try {
                    Object parsed = JsonUtil.fromJson(s, Object.class);
                    if (parsed != null) {
                        return nodeToPlainText(parsed).trim();
                    }
                } catch (Exception ignore) {
                    return s;
                }
            }
            return s;
        }
        return nodeToPlainText(content).trim();
    }

    /**
     * 递归提取 Tiptap 文档节点文本。
     */
    private String nodeToPlainText(Object node) {
        if (node == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (node instanceof Map<?, ?> map) {
            String type = String.valueOf(map.get("type"));
            Object content = map.get("content");
            if (content instanceof List<?> list) {
                for (Object child : list) {
                    sb.append(nodeToPlainText(child));
                }
            } else if (content != null) {
                sb.append(nodeToPlainText(content));
            }
            Object text = map.get("text");
            if (text != null) {
                sb.append(text);
            }
            if (("paragraph".equals(type) || "heading".equals(type)) && sb.length() > 0) {
                sb.append("\n");
            }
        } else if (node instanceof List<?> list) {
            for (Object child : list) {
                sb.append(nodeToPlainText(child));
            }
        } else if (node != null) {
            sb.append(node);
        }
        return sb.toString();
    }

    private void clearBookListCache() {
        try {
            List<String> keys = new java.util.ArrayList<>();
            java.util.Set<String> all = redisTemplate.keys(CacheConsts.REDIS_CACHE_PREFIX + ":*");
            if (all != null) {
                keys.addAll(all);
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("清理作品列表缓存失败", e);
        }
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
        if (bookInfo == null) {
            return null;
        }
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