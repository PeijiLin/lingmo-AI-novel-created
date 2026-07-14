package com.linpj.novel.create.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.reflect.TypeToken;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.CreateBookAddRequest;
import com.linpj.novel.create.pojo.dto.CreateBookPageRequest;
import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.pojo.entity.CreateBook;
import com.linpj.novel.create.pojo.vo.CreateBookVo;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.service.CreateBookService;
import com.linpj.novel.create.mapper.novel.CreateBookMapper;
import com.linpj.novel.create.utils.CommonHandle;
import com.linpj.novel.create.utils.JsonUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author HL
* @description 针对表【create_book】的数据库操作Service实现
* @createDate 2025-09-14 16:43:21
*/
@Service
public class CreateBookServiceImpl extends ServiceImpl<CreateBookMapper, CreateBook>
    implements CreateBookService{

    private final RedisTemplate<String, Object> redisTemplate;

    private final BookInfoService bookInfoService;

    public CreateBookServiceImpl(RedisTemplate<String, Object> redisTemplate, BookInfoService bookInfoService) {
        this.redisTemplate = redisTemplate;
        this.bookInfoService = bookInfoService;
    }

    /**
     * 根据图书id获取创作书籍列表
     * @param createBookPageRequest
     * @return
     */
    @Override
    public Page<CreateBookVo> createBookPageByBookId(CreateBookPageRequest createBookPageRequest) {
        ThrowsUtils.throwIf(CommonHandle.isReptile(createBookPageRequest.getPageSize()), ErrorCode.REPTILE_ERROR);
        Long bookId = createBookPageRequest.getBookId();
        if (bookId == null || bookId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Page<CreateBook> page = new Page<>(createBookPageRequest.getPageNum(), createBookPageRequest.getPageSize());
        LambdaQueryWrapper<CreateBook> wrapper = new LambdaQueryWrapper<CreateBook>().eq(CreateBook::getBookId, bookId).orderBy(true, createBookPageRequest.getSort(), CreateBook::getPublishedTime);
        Page<CreateBook> createBookPage = this.page(page, wrapper);
        Page<CreateBookVo> bookVoPage = new Page<>();
        bookVoPage.setCurrent(createBookPage.getCurrent());
        bookVoPage.setTotal(createBookPage.getTotal());
        bookVoPage.setSize(createBookPage.getSize());
        List<CreateBookVo> list = createBookPage.getRecords().stream().map(this::convertToVo).toList();
        bookVoPage.setRecords(list);
        return bookVoPage;
    }

    @Override
    public CreateBookVo addCreateBook(CreateBookAddRequest createBookAddRequest) {
        // 参数校验
        if (createBookAddRequest.getBookId() == null || createBookAddRequest.getBookId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        BookInfo bookInfo = bookInfoService.getById(createBookAddRequest.getBookId());
        if (bookInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "书籍信息不存在");
        }

        Long authorId = createBookAddRequest.getAuthorId();
        if (authorId == null || authorId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        CreateBook createBook = new CreateBook();

        BeanUtil.copyProperties(createBookAddRequest, createBook);
        // 保存章节信息
        boolean save = this.save(createBook);
        ThrowsUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR);
        return convertToVo(createBook);
    }

    @Override
    public void updateCreateBook(CreateBook createBook) {
        this.updateById(createBook);
    }

    public CreateBookVo convertToVo(CreateBook createBook) {
        CreateBookVo createBookVo = new CreateBookVo();
        createBookVo.setId(createBook.getId());
        createBookVo.setBookId(createBook.getBookId());
        createBookVo.setTitle(createBook.getTitle());
        createBookVo.setNumber(createBook.getNumber());
        createBookVo.setWordCount(createBook.getWordCount());
        createBookVo.setStatus(createBook.getStatus());
        createBookVo.setContent((String) createBook.getContent());
        createBookVo.setAuthorId(createBook.getAuthorId());
        if (createBook.getTags() != null && !"{}".equals(createBook.getTags())) {
            createBookVo.setTags(JsonUtil.fromJson(createBook.getTags(), new TypeToken<List<String>>(){}.getType()));
        }
        createBookVo.setNotes(createBook.getNotes());
        createBookVo.setPublishedTime(createBook.getPublishedTime());
        createBookVo.setUpdatedTime(createBook.getUpdatedTime());
        return createBookVo;
    }
}




