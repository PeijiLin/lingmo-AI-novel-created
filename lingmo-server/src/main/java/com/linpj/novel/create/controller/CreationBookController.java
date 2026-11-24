package com.linpj.novel.create.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.CreateBookAddRequest;
import com.linpj.novel.create.pojo.dto.CreateBookPageRequest;
import com.linpj.novel.create.pojo.entity.CreateBook;
import com.linpj.novel.create.pojo.vo.BookInfoListVo;
import com.linpj.novel.create.pojo.vo.CreateBookVo;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.service.CreateBookService;
import com.linpj.novel.create.utils.CommonHandle;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author HL
 */
@RestController
@RequestMapping("/creation")
public class CreationBookController {

    private final CreateBookService createBookService;
    private final BookInfoService bookInfoService;

    public CreationBookController(CreateBookService createBookService, BookInfoService bookInfoService) {
        this.createBookService = createBookService;
        this.bookInfoService = bookInfoService;
    }

    /**
     * 添加可编辑的章节信息
     * @param createBookAddRequest
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<CreateBookVo> addCreateBook(@RequestBody CreateBookAddRequest createBookAddRequest) {
        ThrowsUtils.throwIf(CommonHandle.isNull(createBookAddRequest), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(createBookService.addCreateBook(createBookAddRequest));
    }

    /**
     * 更新章节
     * @param createBook
     * @return
     */
    @PutMapping("/update")
    public BaseResponse updateCreateBook(@RequestBody CreateBook createBook) {
        ThrowsUtils.throwIf(CommonHandle.isNull(createBook), ErrorCode.PARAMS_ERROR);
        createBookService.updateCreateBook(createBook);
        return ResultUtils.success(null, "更新成功");
    }

    @GetMapping("/book/author")
    public BaseResponse<List<BookInfoListVo>> queryCreateBookListByAuthorId(Long userId) {
        ThrowsUtils.throwIf(CommonHandle.isNull(userId), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(bookInfoService.queryBookListByAuthorId(userId));
    }

    /**
     * 根据图书id查找编辑的书
     * @param createBookPageRequest
     * @return
     */
    @PostMapping("/page/id")
    public BaseResponse<Page<CreateBookVo>> createBookPageByBookId(@RequestBody CreateBookPageRequest createBookPageRequest) {
        ThrowsUtils.throwIf(CommonHandle.isNull(createBookPageRequest), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(createBookService.createBookPageByBookId(createBookPageRequest));
    }


    /**
     * 通过bookId查询
     * @param bookId
     * @return
     */
    @GetMapping("/bookInfo/id")
    public BaseResponse<BookInfoListVo> queryBookInfoById(Long bookId) {
        ThrowsUtils.throwIf(CommonHandle.isNull(bookId), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(bookInfoService.queryBookInfoById(bookId));
    }

}