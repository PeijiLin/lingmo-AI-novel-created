package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.dto.BookPageRequest;
import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.pojo.vo.BookInfoListVo;

import java.util.List;

/**
* @author HL
* @description 针对表【book_info(小说信息)】的数据库操作Service
* @createDate 2025-09-09 16:27:08
*/
public interface BookInfoService extends IService<BookInfo> {

    Page<BookInfoListVo> pageBookList(BookPageRequest bookPageRequest);

    List<BookInfoListVo> queryBookListByAuthorId(Long userId);

    BookInfoListVo queryBookInfoById(Long bookId);
}
