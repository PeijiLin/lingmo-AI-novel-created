package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.BookShelfAddRequest;
import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.pojo.entity.UserBookshelf;
import com.linpj.novel.create.pojo.entity.UserInfo;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.service.UserBookshelfService;
import com.linpj.novel.create.mapper.novel.UserBookshelfMapper;
import com.linpj.novel.create.service.UserInfoService;
import org.springframework.stereotype.Service;

/**
* @author HL
* @description 针对表【user_bookshelf(用户书架)】的数据库操作Service实现
* @createDate 2025-09-14 14:29:24
*/
@Service
public class UserBookshelfServiceImpl extends ServiceImpl<UserBookshelfMapper, UserBookshelf>
    implements UserBookshelfService{

    private final BookInfoService bookInfoService;

    private final UserInfoService userInfoService;

    public UserBookshelfServiceImpl(BookInfoService bookInfoService, UserInfoService userInfoService) {
        this.bookInfoService = bookInfoService;
        this.userInfoService = userInfoService;
    }

    @Override
    public Long addBook(BookShelfAddRequest bookShelfAddRequest) {
        // 参数校验
        Long bookId = bookShelfAddRequest.getBookId();
        Long userId = bookShelfAddRequest.getUserId();
        if (bookId == null || userId == null || bookId <= 0 || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        BookInfo bookInfo = bookInfoService.getById(bookId);
        UserInfo userInfo = userInfoService.getById(userId);
        if (bookInfo == null || userInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图书或者用户不存在");
        }
        UserBookshelf userBookshelf = new UserBookshelf();
        userBookshelf.setUserId(userId);
        userBookshelf.setBookId(bookId);
        boolean save = this.save(userBookshelf);
        ThrowsUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR);
        return userBookshelf.getId();
    }
}




