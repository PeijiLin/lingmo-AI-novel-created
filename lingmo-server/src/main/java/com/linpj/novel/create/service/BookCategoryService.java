package com.linpj.novel.create.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.entity.BookCategory;
import com.linpj.novel.create.pojo.vo.BookCategoryVo;

import java.util.List;
import java.util.Map;

/**
* @author HL
* @description 针对表【book_category(小说类别)】的数据库操作Service
* @createDate 2025-09-09 19:56:18
*/
public interface BookCategoryService extends IService<BookCategory> {

    Map<String, List<BookCategoryVo>> queryBookCategories();
}
