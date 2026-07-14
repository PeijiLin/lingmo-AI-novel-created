package com.linpj.novel.create.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * 纯Java密码加密工具类 - 基于BCrypt，无需Spring Security
 * @author HL
 */
public class PasswordUtil {

    /**
     * 使用 BCrypt 对密码进行哈希加密
     * @param password 明文密码
     * @return 加密后的哈希字符串（包含盐值）
     */
    public static String encryptPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        // 使用 12 轮加密（推荐强度）
        return BCrypt.withDefaults().hashToString(12, password.toCharArray());
    }

    /**
     * 验证明文密码与已加密的哈希是否匹配
     * @param password 明文密码
     * @param hashed   数据库中存储的加密密码（格式如：$2a$12$...）
     * @return 匹配返回 true，否则 false
     */
    public static boolean verifyPassword(String password, String hashed) {
        if (password == null || hashed == null) {
            return false;
        }
        return BCrypt.verifyer().verify(password.toCharArray(), hashed).verified;
    }

    // ------------------------
    // 测试主函数
    // ------------------------
    public static void main(String[] args) {
        String rawPassword = "123456";

        // 加密
        String hashed = encryptPassword(rawPassword);
        System.out.println("原始密码: " + rawPassword);
        System.out.println("加密结果: " + hashed);

        // 验证
        System.out.println("验证通过: " + verifyPassword("123456", hashed));     // true
        System.out.println("验证失败: " + verifyPassword("wrong123", hashed));    // false

        // 再次加密，结果不同（安全特性）
        System.out.println("再次加密: " + encryptPassword(rawPassword));
    }
}