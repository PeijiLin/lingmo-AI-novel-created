package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.UserLoginRequest;
import com.linpj.novel.create.pojo.dto.UserProfileUpdateRequest;
import com.linpj.novel.create.pojo.dto.UserRegisterRequest;
import com.linpj.novel.create.pojo.vo.UserProfileVo;
import com.linpj.novel.create.service.UserInfoService;
import com.linpj.novel.create.utils.CommonHandle;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @author HL
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserInfoService userInfoService;

    /**
     * 用户注册
     * @param userRegisterRequest 注册参数
     * @return 用户 id
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowsUtils.throwIf(CommonHandle.isNull(userRegisterRequest), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success( userInfoService.userRegister(userRegisterRequest));
    }

    /**
     * 用户登录
     * @param userLoginRequest 登录参数
     * @return jwt
     */
    @PostMapping("/login")
    public BaseResponse<Map<String,Object>> userLogin(@RequestBody UserLoginRequest userLoginRequest) {
        ThrowsUtils.throwIf(CommonHandle.isNull(userLoginRequest), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userInfoService.userLogin(userLoginRequest));
    }

    /**
     * 刷新token
     * @param payload
     * @return
     */
    @PostMapping("/refresh-token")
    public BaseResponse<Map<String, String>> refreshToken(@RequestBody Map<String, String> payload) {
        return ResultUtils.success(userInfoService.refreshToken(payload));
    }

    @PostMapping("/logout")
    public BaseResponse<?> logout(@RequestHeader("Authorization") String authHeader) {
        userInfoService.logout(authHeader);
        return ResultUtils.success(null, "登出成功");
    }

    /**
     * 当前登录用户资料（个人主页）
     * @return 用户资料（不含密码）
     */
    @GetMapping("/me")
    public BaseResponse<UserProfileVo> getCurrentUserProfile() {
        return ResultUtils.success(userInfoService.getCurrentUserProfile());
    }

    /**
     * 更新当前登录用户资料（昵称 / 性别）
     * @param request 可空字段表示不变更
     * @return 更新后资料
     */
    @PutMapping("/update")
    public BaseResponse<UserProfileVo> updateUserProfile(@RequestBody UserProfileUpdateRequest request) {
        ThrowsUtils.throwIf(CommonHandle.isNull(request), ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userInfoService.updateUserProfile(request));
    }


}
