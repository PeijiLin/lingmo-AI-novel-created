package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.dto.UserLoginRequest;
import com.linpj.novel.create.pojo.dto.UserProfileUpdateRequest;
import com.linpj.novel.create.pojo.dto.UserRegisterRequest;
import com.linpj.novel.create.pojo.entity.UserInfo;
import com.linpj.novel.create.pojo.vo.UserProfileVo;

import java.util.Map;

/**
* @author HL
* @description 针对表【user_info(用户信息)】的数据库操作Service
* @createDate 2025-08-28 23:28:05
*/
public interface UserInfoService extends IService<UserInfo> {

    Map<String, Object> userLogin(UserLoginRequest userLoginRequest);

    /**
     * 用户注册
     * @param userRegisterRequest 注册参数
     * @return 用户 id
     */
    Long userRegister(UserRegisterRequest userRegisterRequest);

    Map<String, String> refreshToken(Map<String, String> payload);

    void logout(String authHeader);

    /**
     * 查询当前登录用户资料（个人主页）
     * @return 用户资料（不含密码）
     */
    UserProfileVo getCurrentUserProfile();

    /**
     * 更新当前登录用户资料（昵称 / 性别）
     * @param request 可空字段表示不变更
     * @return 更新后资料
     */
    UserProfileVo updateUserProfile(UserProfileUpdateRequest request);
}
