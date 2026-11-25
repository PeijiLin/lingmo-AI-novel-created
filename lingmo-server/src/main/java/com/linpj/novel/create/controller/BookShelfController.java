package com.linpj.novel.create.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.context.UserStatusContext;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.BookShelfAddRequest;
import com.linpj.novel.create.pojo.vo.BookInfoListVo;
import com.linpj.novel.create.service.UserBookshelfService;
import com.linpj.novel.create.utils.CommonHandle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author HL
 */
@RestController
@RequestMapping("/bookshelf")
public class BookShelfController {

    private final UserBookshelfService userBookshelfService;

    public BookShelfController(UserBookshelfService userBookshelfService) {
        this.userBookshelfService = userBookshelfService;
    }


    /**
     * 添加书架图书
     * @param bookShelfAddRequest
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addBook(@RequestBody BookShelfAddRequest bookShelfAddRequest) {
        ThrowsUtils.throwIf(CommonHandle.isNull(bookShelfAddRequest), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userBookshelfService.addBook(bookShelfAddRequest));
    }

    /**
     * 我的书架（需登录）：按最近加入优先分页返回书目。
     * userId 只取当前登录态，不信任前端传参。
     */
    @GetMapping("/list")
    public BaseResponse<Page<BookInfoListVo>> listShelf(
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "24") long pageSize) {
        Long userId = UserStatusContext.getCurrentUserId();
        ThrowsUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        return ResultUtils.success(userBookshelfService.listShelfBooks(userId, pageNum, pageSize));
    }

}
