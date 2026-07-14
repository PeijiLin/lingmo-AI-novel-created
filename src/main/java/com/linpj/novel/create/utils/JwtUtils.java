package com.linpj.novel.create.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;

/**
 * @author HL
 */
@Slf4j
public class JwtUtils {
    private static final String signKey = "hzy123";
    private static Long expire = 1800000L;

    /**
     * 生成JWT令牌
     * @param claims 用户标识
     * @return 令牌
     */
    public static String generateJwt(Map<String,Object> claims) {
        String jwt = Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS256,signKey)
                .setExpiration(new Date(System.currentTimeMillis() + expire))
                .compact();
        return jwt;
    }

    /**
     * 解析JWT令牌
     * @param jwt 令牌
     * @return JWT第二部分负载payload中存储的内容
     */
    public static Claims parseJwt(String jwt) {
        Claims claims = Jwts.parser()
                .setSigningKey(signKey)
                .parseClaimsJws(jwt)
                .getBody();
        return claims;
    }

    public static String getUserIdFromToken(String accessToken) {
        Claims claims = JwtUtils.parseJwt(accessToken);
        return String.valueOf(claims.get("userId"));
    }
}

