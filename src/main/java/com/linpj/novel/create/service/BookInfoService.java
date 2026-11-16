package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.dto.BookCreateRequest;
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

    /**
     * 新建作品（book_info 落库）
     * @param request 书名/简介/方向；authorId 缺省时从当前登录用户自动关联/创建作者
     * @return 新作品 VO
     */
    BookInfoListVo createBook(BookCreateRequest request);

    /**
     * 发布作品：把该书全部章节草稿（create_book）转写为展示侧章节（book_chapter + book_content），
     * 并回写 book_info 的字数/最新章节/连载状态。
     * @param bookId 作品 id
     */
    void publishBook(Long bookId);

    /**
     * 阅读量 +1（阅读器打开章节时上报）
     * @param bookId 作品 id
     */
    void incrementReadingCount(Long bookId);

    /**
     * 轻量榜单：按阅读量倒序
     * @param limit 返回条数
     */
    List<BookInfoListVo> queryRanking(int limit);
}
