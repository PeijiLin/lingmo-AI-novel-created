package com.linpj.novel.create.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * JWT 工具（实例 Bean，密钥与过期时间由外部配置注入，禁止硬编码）
 *
 * 配置项（application.yml）：
 *   novel.jwt.secret-key    HS256 签名密钥，长度须 >= 32 字节；生产环境通过环境变量覆盖
 *   novel.jwt.expire-millis access token 有效期（毫秒），默认 30 分钟
 *
 * @author HL
 */
@Slf4j
@Component
public class JwtUtils {

    private final String signKey;
    private final long expireMillis;

    public JwtUtils(@Value("${novel.jwt.secret-key}") String signKey,
                    @Value("${novel.jwt.expire-millis:1800000}") long expireMillis) {
        this.signKey = signKey;
        this.expireMillis = expireMillis;
    }

    /**
     * 生成JWT令牌
     * @param claims 用户标识
     * @return 令牌
     */
    public String generateJwt(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS256, signKey)
                .setExpiration(new Date(System.currentTimeMillis() + expireMillis))
                .compact();
    }

    /**
     * 解析JWT令牌
     * @param jwt 令牌
     * @return JWT第二部分负载payload中存储的内容
     */
    public Claims parseJwt(String jwt) {
        return Jwts.parser()
                .setSigningKey(signKey)
                .parseClaimsJws(jwt)
                .getBody();
    }

    public String getUserIdFromToken(String accessToken) {
        Claims claims = parseJwt(accessToken);
        return String.valueOf(claims.get("userId"));
    }
}
