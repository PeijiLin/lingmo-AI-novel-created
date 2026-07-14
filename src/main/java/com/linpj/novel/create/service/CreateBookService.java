package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linpj.novel.create.pojo.dto.CreateBookAddRequest;
import com.linpj.novel.create.pojo.dto.CreateBookPageRequest;
import com.linpj.novel.create.pojo.entity.CreateBook;
import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.vo.CreateBookVo;

/**
* @author HL
* @description 针对表【create_book】的数据库操作Service
* @createDate 2025-09-14 16:43:21
*/
public interface CreateBookService extends IService<CreateBook> {

    Page<CreateBookVo> createBookPageByBookId(CreateBookPageRequest createBookPageRequest);

    /**
     * 添加章节
     * @param createBookAddRequest
     * @return
     */
    CreateBookVo addCreateBook(CreateBookAddRequest createBookAddRequest);

    void updateCreateBook(CreateBook createBook);
}
