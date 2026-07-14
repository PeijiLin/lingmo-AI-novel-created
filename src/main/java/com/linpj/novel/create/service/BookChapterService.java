package com.linpj.novel.create.service;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.dto.ChapterPageRequest;
import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.vo.BookChapterVo;

/**
* @author HL
* @description 针对表【book_chapter(小说章节)】的数据库操作Service
* @createDate 2025-09-09 19:57:42
*/
public interface BookChapterService extends IService<BookChapter> {

    Page<BookChapterVo> queryBookChapterById(ChapterPageRequest chapterPageRequest);
}
