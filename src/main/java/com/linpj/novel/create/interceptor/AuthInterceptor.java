package com.linpj.novel.create.interceptor;

import com.linpj.novel.create.context.UserStatusContext;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @author HL
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String accessToken = StringUtils.substring(request.getHeader("Authorization"),7);
        String requestURI = request.getRequestURI(); // 获取真实请求路径
        String method = request.getMethod();
        log.info("=== AuthInterceptor 访问路径: {} {}", method, requestURI);
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        try {
            String userId = JwtUtils.getUserIdFromToken(accessToken);
            UserStatusContext.set("userId", userId);
            return true;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }
}
