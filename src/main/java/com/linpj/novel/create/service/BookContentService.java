package com.linpj.novel.create.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.entity.BookContent;
import com.linpj.novel.create.pojo.vo.BookContentVo;

/**
* @author HL
* @description 针对表【book_content(小说章节内容)】的数据库操作Service
* @createDate 2025-09-09 19:57:42
*/
public interface BookContentService extends IService<BookContent> {

    BookContentVo queryBookContentById(Long chapterId);
}
