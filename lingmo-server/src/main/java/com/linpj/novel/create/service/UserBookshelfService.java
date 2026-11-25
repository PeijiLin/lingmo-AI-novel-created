package com.linpj.novel.create.service;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.pojo.dto.BookShelfAddRequest;
import com.linpj.novel.create.pojo.entity.UserBookshelf;
import com.linpj.novel.create.pojo.vo.BookInfoListVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author HL
* @description 针对表【user_bookshelf(用户书架)】的数据库操作Service
* @createDate 2025-09-14 14:29:24
*/
public interface UserBookshelfService extends IService<UserBookshelf> {

    Long addBook(BookShelfAddRequest bookShelfAddRequest);

    /** 当前用户书架分页列表（按最近加入优先） */
    Page<BookInfoListVo> listShelfBooks(Long userId, long pageNum, long pageSize);
}
