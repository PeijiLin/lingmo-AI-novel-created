package com.linpj.novel.create.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.pojo.dto.BookPageRequest;
import com.linpj.novel.create.pojo.dto.ChapterPageRequest;
import com.linpj.novel.create.pojo.vo.BookCategoryVo;
import com.linpj.novel.create.pojo.vo.BookChapterVo;
import com.linpj.novel.create.pojo.vo.BookContentVo;
import com.linpj.novel.create.pojo.vo.BookInfoListVo;
import com.linpj.novel.create.service.BookCategoryService;
import com.linpj.novel.create.service.BookChapterService;
import com.linpj.novel.create.service.BookContentService;
import com.linpj.novel.create.service.BookInfoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/book")
public class ReadingController {

    private final BookCategoryService bookCategoryService;

    private final BookInfoService bookInfoService;

    private final BookContentService bookContentService;
    private final BookChapterService bookChapterService;

    public ReadingController(BookCategoryService bookCategoryService, BookInfoService bookInfoService, BookContentService bookContentService, BookChapterService bookChapterService) {
        this.bookCategoryService = bookCategoryService;
        this.bookInfoService = bookInfoService;
        this.bookContentService = bookContentService;
        this.bookChapterService = bookChapterService;
    }

    /**
     * 查询小说类型
     * @return
     */
    @GetMapping("/category/list")
    public BaseResponse<Map<String, List<BookCategoryVo>>> queryBookCategories() {
        return ResultUtils.success(bookCategoryService.queryBookCategories());
    }

    /**
     * 根据类型分页查询小说列表
     * @param bookPageRequest
     * @return
     */
    @PostMapping("/page")
    public BaseResponse<Page<BookInfoListVo>> pageBookList(@RequestBody BookPageRequest bookPageRequest) {
        return ResultUtils.success(bookInfoService.pageBookList(bookPageRequest));
    }

    /**
     * 根据章节 id 查询小说内容
     */
    @GetMapping("/content")
    public BaseResponse<BookContentVo> queryBookContentById(Long chapterId) {
        return ResultUtils.success(bookContentService.queryBookContentById(chapterId));
    }

    /**
     * 根据小说id查询小说分页章节信息
     * @param chapterPageRequest
     * @return
     */
    @PostMapping("/chapter/page")
    public BaseResponse<Page<BookChapterVo>> queryBookChapterById(@RequestBody ChapterPageRequest chapterPageRequest) {
        return ResultUtils.success(bookChapterService.queryBookChapterById(chapterPageRequest));
    }
}
