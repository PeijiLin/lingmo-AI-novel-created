package com.linpj.novel.create.interceptor;

import com.linpj.novel.create.context.UserStatusContext;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：校验 Bearer 前缀 + 请求结束后清理 ThreadLocal，防止线程池复用导致的用户信息串号
 *
 * @author HL
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    public AuthInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        String requestURI = request.getRequestURI(); // 获取真实请求路径
        String method = request.getMethod();
        log.info("=== AuthInterceptor 访问路径: {} {}", method, requestURI);
        // 必须携带合法的 Bearer 前缀（原先 substring(7) 会把非 Bearer 头也当作 token 解析）
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        String accessToken = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        try {
            String userId = jwtUtils.getUserIdFromToken(accessToken);
            UserStatusContext.set("userId", userId);
            return true;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束清理 ThreadLocal，避免线程复用携带上一个请求的用户上下文
        UserStatusContext.clear();
    }
}
