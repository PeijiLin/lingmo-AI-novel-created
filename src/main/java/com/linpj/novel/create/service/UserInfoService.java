package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.dto.UserLoginRequest;
import com.linpj.novel.create.pojo.dto.UserRegisterRequest;
import com.linpj.novel.create.pojo.entity.UserInfo;

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
}
