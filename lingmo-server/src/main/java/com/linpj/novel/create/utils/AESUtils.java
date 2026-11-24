package com.linpj.novel.create.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES加密工具类(改进版)
 * 使用PBKDF2从用户提供的盐值派生密钥
 * 使用CBC模式，PKCS5Padding填充方式
 */
public class AESUtils {
    // AES加密算法，使用CBC模式，PKCS5Padding填充
    public static final String SALT = "student-system-123";

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    // AES密钥长度可以是128, 192或256位(16, 24或32字节)
    private static final int KEY_SIZE = 128; // 128位 = 16字节
    // 初始化向量(IV)长度必须与块大小相同，AES块大小为128位(16字节)
    private static final int IV_SIZE = 16;
    // PBKDF2迭代次数
    private static final int ITERATIONS = 65536;
    // PBKDF2密钥长度
    private static final int KEY_LENGTH = 128; // 128位 = 16字节

    /**
     * 加密方法
     * @param content 待加密的内容
     * @param salt 盐值(可以是任意长度的字符串)
     * @return Base64编码的加密结果
     * @throws Exception 加密过程中可能出现的异常
     */
    public static String encrypt(String content, String salt) throws Exception {
        // 从盐值派生密钥
        SecretKey secretKey = deriveKey(salt);

        // 使用固定IV(不推荐)
        byte[] iv = new byte[IV_SIZE];
        // 这里使用全0的IV作为示例，你可以使用任何固定的值
        Arrays.fill(iv, (byte)0x00);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

        // 初始化加密器
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

        // 执行加密
        byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));

        // 将IV和加密后的数据合并，然后进行Base64编码
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 解密方法
     * @param encryptedContent Base64编码的加密内容
     * @param salt 盐值(可以是任意长度的字符串)
     * @return 解密后的原始内容
     * @throws Exception 解密过程中可能出现的异常
     */
    public static String decrypt(String encryptedContent, String salt) throws Exception {
        // 从盐值派生密钥
        SecretKey secretKey = deriveKey(salt);

        // Base64解码
        byte[] combined = Base64.getDecoder().decode(encryptedContent);

        // 使用固定IV(必须与加密时使用的相同)
        byte[] iv = new byte[IV_SIZE];
        Arrays.fill(iv, (byte)0x00);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

        // 提取加密数据(注意这里不再从combined中提取IV)
        byte[] encrypted = new byte[combined.length - IV_SIZE];
        System.arraycopy(combined, IV_SIZE, encrypted, 0, encrypted.length);

        // 初始化解密器
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);

        // 执行解密
        byte[] decrypted = cipher.doFinal(encrypted);

        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * 从盐值派生密钥
     * @param salt 盐值(可以是任意长度的字符串)
     * @return 派生出的密钥
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     */
    private static SecretKey deriveKey(String salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        // 将盐值转换为字节数组
        byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);

        // 使用PBKDF2从盐值派生密钥
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(salt.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    public static void main(String[] args) {
        try {
            // 示例用法
            String content = "这是一段需要加密的敏感数据";
            String salt = "mySecretSalt123"; // 可以是任意长度的字符串

            // 加密
            String encrypted = encrypt(content, salt);
            System.out.println("加密后的内容: " + encrypted);

            // 解密
            String decrypted = decrypt("AAAAAAAAAAAAAAAAAAAAADF7m1bppN4xESHQRZ7PQwI=", SALT);
            System.out.println("解密后的内容: " + decrypted);

            String encrypt = encrypt("12345678", "efsas");
            System.out.println(encrypt);

            String enPassword = encrypt("hzy666123", SALT);
            System.out.println(enPassword);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}