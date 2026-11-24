package com.linpj.novel.create.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.BookCreateRequest;
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

    /**
     * 新建作品（book_info 落库）
     * 作者缺省时自动关联当前登录用户（并在无作者档案时自动创建）。
     * 注：此接口需鉴权（见 WebMvcConfig 拆分），用于创作工作台「新建作品」。
     */
    @PostMapping("/create")
    public BaseResponse<BookInfoListVo> createBook(@RequestBody BookCreateRequest request) {
        ThrowsUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(bookInfoService.createBook(request));
    }

    /**
     * 发布作品：章节草稿（create_book）转展示侧章节，并回写作品统计。
     */
    @PutMapping("/publish")
    public BaseResponse<Void> publishBook(@RequestBody Map<String, Object> payload) {
        bookInfoService.publishBook(toLong(payload == null ? null : payload.get("bookId")));
        return ResultUtils.success(null, "发布成功");
    }

    /**
     * 阅读量 +1（阅读器打开章节时上报）
     */
    @PostMapping("/read/increment")
    public BaseResponse<Void> incrementReading(@RequestBody Map<String, Object> payload) {
        bookInfoService.incrementReadingCount(toLong(payload == null ? null : payload.get("bookId")));
        return ResultUtils.success();
    }

    /**
     * 轻量榜单：按阅读量倒序
     */
    @GetMapping("/ranking")
    public BaseResponse<List<BookInfoListVo>> queryRanking(@RequestParam(value = "limit", required = false) Integer limit) {
        int n = limit == null ? 20 : limit;
        return ResultUtils.success(bookInfoService.queryRanking(n));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
