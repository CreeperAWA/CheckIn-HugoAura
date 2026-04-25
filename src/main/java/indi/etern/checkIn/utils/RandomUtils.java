package indi.etern.checkIn.utils;

import java.security.SecureRandom;

/**
 * 随机字符串生成工具类
 * 
 * 注意：此类使用 SecureRandom 生成随机数，适用于安全敏感场景（如验证码生成）
 */
public class RandomUtils {
    
    private static final String ALPHABETIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * 生成随机字母字符串
     * 
     * @param length 字符串长度
     * @return 随机字母字符串
     */
    public static String generateRandomAlphabetic(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABETIC_CHARS.charAt(SECURE_RANDOM.nextInt(ALPHABETIC_CHARS.length())));
        }
        return sb.toString();
    }
    
    /**
     * 生成12位随机验证码（字母）
     * 
     * @return 12位随机字母字符串
     */
    public static String generateRandomVerifyCode() {
        return generateRandomAlphabetic(12);
    }
}
