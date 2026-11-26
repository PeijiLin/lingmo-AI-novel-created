package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.entity.CharacterRelationships;
import com.linpj.novel.create.service.CharacterRelationshipsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色关系边接口（P0：关系进黑板的编辑入口）
 *
 * <p>路径与角色卡保持一致：控制器自带 /api 前缀，最终浏览器路径为 /api/api/character-relationship/*。
 *
 * @author HL
 */
@RestController
@RequestMapping("/api/character-relationship")
public class CharacterRelationshipController {

    private final CharacterRelationshipsService relationshipsService;

    public CharacterRelationshipController(CharacterRelationshipsService relationshipsService) {
        this.relationshipsService = relationshipsService;
    }

    /** 本书全部关系边（含历史生效区间） */
    @GetMapping("/list/{bookId}")
    public BaseResponse<List<CharacterRelationships>> list(@PathVariable Long bookId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR, "无效的书籍ID");
        return ResultUtils.success(relationshipsService.listForBook(bookId));
    }

    /** 当前章生效的关系边（chapterNum 可空：只返回长期有效边） */
    @GetMapping("/active/{bookId}")
    public BaseResponse<List<CharacterRelationships>> active(@PathVariable Long bookId,
                                                            @RequestParam(required = false) Integer chapterNum) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR, "无效的书籍ID");
        return ResultUtils.success(relationshipsService.listActive(bookId, chapterNum));
    }

    @PostMapping("/add")
    public BaseResponse<Long> add(@RequestBody CharacterRelationships edge) {
        validate(edge);
        edge.setId(null);
        boolean saved = relationshipsService.save(edge);
        ThrowsUtils.throwIf(!saved, ErrorCode.SYSTEM_ERROR);
        return ResultUtils.success(edge.getId());
    }

    @PutMapping("/modify")
    public BaseResponse<Boolean> modify(@RequestBody CharacterRelationships edge) {
        ThrowsUtils.throwIf(edge == null || edge.getId() == null || edge.getId() <= 0,
                ErrorCode.PARAMS_ERROR, "无效的关系ID");
        validate(edge);
        return ResultUtils.success(relationshipsService.updateById(edge));
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> delete(@PathVariable Long id) {
        ThrowsUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "无效的关系ID");
        return ResultUtils.success(relationshipsService.removeById(id));
    }

    private static void validate(CharacterRelationships edge) {
        ThrowsUtils.throwIf(edge == null, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(edge.getBookId() == null || edge.getBookId() <= 0, ErrorCode.PARAMS_ERROR, "书籍ID不能为空");
        ThrowsUtils.throwIf(edge.getFromCharacterId() == null, ErrorCode.PARAMS_ERROR, "起始角色不能为空");
        ThrowsUtils.throwIf(edge.getToCharacterId() == null, ErrorCode.PARAMS_ERROR, "目标角色不能为空");
        ThrowsUtils.throwIf(edge.getFromCharacterId().equals(edge.getToCharacterId()),
                ErrorCode.PARAMS_ERROR, "起始角色与目标角色不能相同");
    }
}
