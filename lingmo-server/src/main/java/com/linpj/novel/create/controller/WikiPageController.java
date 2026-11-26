package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import com.linpj.novel.create.service.wiki.WikiBootstrapService;
import com.linpj.novel.create.service.wiki.WikiPageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Wiki 正典页接口（M3：页卡查询/编辑/状态 + 初始化引导）
 *
 * <p>路径风格对齐 knowledgeBase：context-path /api + /wiki/*。
 * 前端浏览器路径为 /api/wiki/*。
 *
 * @author HL
 */
@RestController
@RequestMapping("/wiki")
public class WikiPageController {

    private final WikiPageService wikiPageService;
    private final WikiBootstrapService wikiBootstrapService;

    public WikiPageController(WikiPageService wikiPageService,
                              WikiBootstrapService wikiBootstrapService) {
        this.wikiPageService = wikiPageService;
        this.wikiBootstrapService = wikiBootstrapService;
    }

    /** 本书正典页清单（可按 entityType 过滤，可选） */
    @GetMapping("/{bookId}/pages")
    public BaseResponse<List<NovelWikiPage>> listPages(
            @PathVariable Long bookId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String entityType) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR, "无效的书籍ID");
        return ResultUtils.success(entityType == null || entityType.isBlank()
                ? wikiPageService.listCanonPages(bookId)
                : wikiPageService.listCanonPages(bookId, entityType));
    }

    /** 单页详情（含别名） */
    @GetMapping("/page/{pageId}")
    public BaseResponse<Map<String, Object>> getPage(@PathVariable Long pageId) {
        NovelWikiPage page = wikiPageService.getPage(pageId);
        return ResultUtils.success(Map.of(
                "page", page,
                "aliases", wikiPageService.aliasesOf(pageId)));
    }

    /** 别名定位（实体定位入口：正文/查询里的名字 → 正典页） */
    @GetMapping("/{bookId}/locate")
    public BaseResponse<NovelWikiPage> locate(
            @PathVariable Long bookId,
            @org.springframework.web.bind.annotation.RequestParam String name) {
        ThrowsUtils.throwIf(name == null || name.isBlank(), ErrorCode.PARAMS_ERROR, "名称不能为空");
        return ResultUtils.success(wikiPageService.locateByAlias(bookId, name));
    }

    /** 编辑页卡（版本 +1；未变更字段传 null） */
    @PutMapping("/page/{pageId}")
    public BaseResponse<NovelWikiPage> editPage(
            @PathVariable Long pageId,
            @RequestBody Map<String, Object> body) {
        ThrowsUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(wikiPageService.editPage(
                pageId,
                str(body.get("title")),
                str(body.get("canonical")),
                body.get("attrs")));
    }

    /** 状态流转（CANON / DEPRECATED） */
    @PutMapping("/page/{pageId}/status")
    public BaseResponse<NovelWikiPage> changeStatus(
            @PathVariable Long pageId,
            @RequestBody Map<String, String> body) {
        String target = body == null ? null : body.get("status");
        return ResultUtils.success(wikiPageService.changeStatus(pageId, target));
    }

    /** 从角色卡/世界观初始化正典页（幂等，可重复执行；作者可再编辑校对） */
    @PostMapping("/{bookId}/bootstrap")
    public BaseResponse<WikiBootstrapService.BootstrapResult> bootstrap(@PathVariable Long bookId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR, "无效的书籍ID");
        return ResultUtils.success(wikiBootstrapService.bootstrap(bookId));
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
