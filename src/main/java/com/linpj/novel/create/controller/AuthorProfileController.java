package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.AuthorInfoUpdateRequest;
import com.linpj.novel.create.pojo.vo.AuthorProfileVo;
import com.linpj.novel.create.service.AuthorInfoService;
import com.linpj.novel.create.utils.CommonHandle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作者档案（个人主页-创作身份）
 * @author HL
 */
@RestController
@RequestMapping("/author")
public class AuthorProfileController {

    private final AuthorInfoService authorInfoService;

    public AuthorProfileController(AuthorInfoService authorInfoService) {
        this.authorInfoService = authorInfoService;
    }

    /**
     * 当前登录用户的作者档案
     * @return 作者档案；尚未开档时 data 为 null
     */
    @GetMapping("/me")
    public BaseResponse<AuthorProfileVo> getMyAuthorProfile() {
        return ResultUtils.success(authorInfoService.getMyAuthorProfile());
    }

    /**
     * 更新（或首次创建）作者档案
     * @param request 笔名必填，其余字段可空
     * @return 保存后的档案
     */
    @PutMapping("/update")
    public BaseResponse<AuthorProfileVo> updateMyAuthorProfile(@RequestBody AuthorInfoUpdateRequest request) {
        ThrowsUtils.throwIf(CommonHandle.isNull(request), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(authorInfoService.updateMyAuthorProfile(request));
    }
}
