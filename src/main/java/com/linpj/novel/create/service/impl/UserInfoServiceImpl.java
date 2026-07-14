package com.linpj.novel.create.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.mapper.novel.UserInfoMapper;
import com.linpj.novel.create.pojo.dto.UserLoginRequest;
import com.linpj.novel.create.pojo.dto.UserRegisterRequest;
import com.linpj.novel.create.pojo.entity.UserInfo;
import com.linpj.novel.create.service.UserInfoService;
import com.linpj.novel.create.utils.CommonHandle;
import com.linpj.novel.create.utils.JwtUtils;
import com.linpj.novel.create.utils.PasswordUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
* @author HL
* @description 针对表【user_info(用户信息)】的数据库操作Service实现
* @createDate 2025-08-28 23:28:05
*/
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo>
    implements UserInfoService {


    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Map<String, Object> userLogin(UserLoginRequest userLoginRequest) {
        if (StringUtils.isAnyBlank(userLoginRequest.getAccount(),userLoginRequest.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号或密码不能为空");
        }

        String account = userLoginRequest.getAccount();
        String password = userLoginRequest.getPassword();
        verify(account,password);

        // 验证用户是否存在
        LambdaQueryWrapper<UserInfo> userLambdaQueryWrapper = new LambdaQueryWrapper<>();
        userLambdaQueryWrapper.eq(UserInfo::getUsername, account);
        UserInfo user = this.getOne(userLambdaQueryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"用户不存在");
        }
//        如果用户存在，验证密码
        boolean b = PasswordUtil.verifyPassword(password, user.getPassword());
        ThrowsUtils.throwIf(!b, ErrorCode.PASSWORD_ERROR);
        HashMap<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        // 生成短期access_token
        String accessToken = JwtUtils.generateJwt(claims);

        // 长期长期refresh_token
        String refreshToken = UUID.randomUUID().toString();

        // 存入 Redis 并设置过期时间（7天）
        String refreshTokenKey = "refresh_token:" + refreshToken;
        String userRefreshTokensKey = "user:refresh_tokens:" + user.getId();

        redisTemplate.opsForValue().set(refreshTokenKey, user.getId(), 7, TimeUnit.DAYS);
        redisTemplate.opsForSet().add(userRefreshTokensKey, refreshToken);
        redisTemplate.expire(userRefreshTokensKey, 7, TimeUnit.DAYS);

        // 改成使用token缓存
        Map<String, Object> userInfo = new HashMap<>();

        userInfo.put("access_token",accessToken);
        userInfo.put("userId", user.getId());
        userInfo.put("refresh_token", refreshToken);
        return userInfo;
    }

    /**
     * 用户注册
     * @param userRegisterRequest 注册参数
     * @return 用户 id
     */
    @Transactional
    @Override
    public Long userRegister(UserRegisterRequest userRegisterRequest) {
        if (StringUtils.isAnyBlank(userRegisterRequest.getAccount(),userRegisterRequest.getPassword(),userRegisterRequest.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号或密码不能为空");
        }

        if (!Objects.equals(userRegisterRequest.getCheckPassword(), userRegisterRequest.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码与验证密码不一致");
        }

        // 账号密码校验
        String account = userRegisterRequest.getAccount();
        String password = userRegisterRequest.getPassword();
        verify(account,password);

        // 密码加密
        String safePassword = PasswordUtil.encryptPassword(password);

        UserInfo user = new UserInfo();
        user.setUsername(account);
        user.setPassword(safePassword);
        this.save(user);
        return user.getId();
    }

    @Override
    public Map<String, String> refreshToken(Map<String, String> payload) {
        String oldRefreshToken = payload.get("refreshToken");

        String refreshTokenKey = "refresh_token:" + oldRefreshToken;
        Object userId = redisTemplate.opsForValue().get(refreshTokenKey);
        ThrowsUtils.throwIf(CommonHandle.isNull(userId), ErrorCode.NOT_LOGIN_ERROR);

        // 生成新的 access_token
        HashMap<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        String newAccessToken = JwtUtils.generateJwt(claims);

        // 生成新的 refresh_token
        String newRefreshToken = UUID.randomUUID().toString();

        String newRefreshTokenKey = "refresh_token:" + newRefreshToken;
        String userRefreshTokensKey = "user:refresh_tokens:" + userId;

        // 存入 Redis
        redisTemplate.opsForValue().set(newRefreshTokenKey, userId, 7, TimeUnit.DAYS);
        redisTemplate.opsForSet().add(userRefreshTokensKey, newRefreshToken);
        redisTemplate.expire(userRefreshTokensKey, 7, TimeUnit.DAYS);

        // 删除旧的 refresh_token
        redisTemplate.delete(refreshTokenKey);
        HashMap<String, String> map = new HashMap<>();
        map.put("access_token", newAccessToken);
        map.put("refresh_token", newRefreshTokenKey);
        map.put("userId", String.valueOf(userId));
        return map;
    }

    @Override
    public void logout(String authHeader) {
        String accessToken = authHeader.substring(7); // Bearer xxx
        String userId = JwtUtils.getUserIdFromToken(accessToken);

        // 获取该用户所有 refresh_token，并删除
        String userRefreshTokensKey = "user:refresh_tokens:" + userId;
        Set<Object> tokens = redisTemplate.opsForSet().members(userRefreshTokensKey);

        if (tokens != null && !tokens.isEmpty()) {
            for (Object token : tokens) {
                redisTemplate.delete("refresh_token:" + token);
            }
        }

        redisTemplate.delete(userRefreshTokensKey);

        // 将当前 access_token 加入黑名单（可选使用 BloomFilter）
        String blacklistKey = "blacklist:access_token:" + accessToken;
        redisTemplate.opsForValue().set(blacklistKey, "revoked", 30, TimeUnit.MINUTES);
    }


    /**
     * 账户密码校验
     * @param account 账户
     * @param password 密码
     */
    private static void verify(String account,String password) {
        // 账号校验
        if (StringUtils.length(account) < 0 && StringUtils.length(account) > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号不符合");
        }

        // 密码校验
        if (StringUtils.length(password) < 6 && StringUtils.length(account) > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码不符合");
        }
    }
}
