package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.BookShelfAddRequest;
import com.linpj.novel.create.service.UserBookshelfService;
import com.linpj.novel.create.utils.CommonHandle;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

}
