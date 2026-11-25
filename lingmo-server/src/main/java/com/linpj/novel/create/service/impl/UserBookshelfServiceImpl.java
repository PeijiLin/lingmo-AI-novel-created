package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.context.UserStatusContext;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.BookShelfAddRequest;
import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.pojo.entity.UserBookshelf;
import com.linpj.novel.create.pojo.entity.UserInfo;
import com.linpj.novel.create.pojo.vo.BookInfoListVo;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.service.UserBookshelfService;
import com.linpj.novel.create.mapper.novel.UserBookshelfMapper;
import com.linpj.novel.create.service.UserInfoService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        // userId 未显式传时取当前登录用户（前端「加入书架」不传 userId）
        Long userId = bookShelfAddRequest.getUserId();
        if (userId == null || userId <= 0) {
            userId = UserStatusContext.getCurrentUserId();
        }
        if (bookId == null || userId == null || bookId <= 0 || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请先登录再加入书架");
        }
        BookInfo bookInfo = bookInfoService.getById(bookId);
        UserInfo userInfo = userInfoService.getById(userId);
        if (bookInfo == null || userInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图书或者用户不存在");
        }
        // 幂等：唯一约束 uk_bookshelf_user_book 下重复加入直接返回原记录
        UserBookshelf existing = this.lambdaQuery()
                .eq(UserBookshelf::getUserId, userId)
                .eq(UserBookshelf::getBookId, bookId)
                .one();
        if (existing != null) {
            return existing.getId();
        }
        UserBookshelf userBookshelf = new UserBookshelf();
        userBookshelf.setUserId(userId);
        userBookshelf.setBookId(bookId);
        boolean save = this.save(userBookshelf);
        ThrowsUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR);
        return userBookshelf.getId();
    }

    @Override
    public Page<BookInfoListVo> listShelfBooks(Long userId, long pageNum, long pageSize) {
        ThrowsUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "请先登录");
        long pNum = Math.max(pageNum, 1);
        long pSize = Math.min(Math.max(pageSize, 1), 100);

        List<UserBookshelf> shelf = this.lambdaQuery()
                .eq(UserBookshelf::getUserId, userId)
                .orderByDesc(UserBookshelf::getId)
                .list();
        List<Long> ids = shelf.stream()
                .map(UserBookshelf::getBookId)
                .filter(Objects::nonNull)
                .toList();

        Page<BookInfoListVo> page = new Page<>(pNum, pSize);
        page.setTotal(ids.size());
        if (ids.isEmpty()) {
            page.setRecords(List.of());
            return page;
        }

        Map<Long, BookInfoListVo> byId = bookInfoService.listVoByIds(ids).stream()
                .filter(vo -> vo.getId() != null)
                .collect(Collectors.toMap(BookInfoListVo::getId, vo -> vo, (a, b) -> a));
        // 按书架顺序（最近加入优先）输出，而不是按 book_info 的返回顺序
        List<BookInfoListVo> ordered = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        int from = (int) Math.min((pNum - 1) * pSize, ordered.size());
        int to = Math.min(from + (int) pSize, ordered.size());
        page.setRecords(ordered.subList(from, to));
        page.setPages((int) Math.ceil((double) ordered.size() / pSize));
        return page;
    }
}




