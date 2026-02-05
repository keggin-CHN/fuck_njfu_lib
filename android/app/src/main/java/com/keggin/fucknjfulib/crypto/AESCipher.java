package com.keggin.fucknjfulib.crypto;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-CBC 加密工具类
 * 用于南京林业大学统一认证系统密码加密
 * 
 * 加密规则（与后端 auth_manager.py 中 encrypt_cas_password 保持一致）：
 * 1. 生成64位随机前缀（从指定字符集选取）
 * 2. 生成16位随机IV
 * 3. 明文 = 64位随机前缀 + 原始密码
 * 4. 使用 AES-CBC 模式，PKCS7 填充
 * 5. 返回 Base64 编码的密文
 */
public class AESCipher {
    
    private static final String TAG = "AESCipher";
    
    // 随机字符集（与后端保持一致，排除了容易混淆的字符）
    private static final String RANDOM_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
    
    // 前缀长度
    private static final int PREFIX_LENGTH = 64;
    
    // IV长度
    private static final int IV_LENGTH = 16;
    
    /**
     * 加密统一认证密码
     * 
     * @param password 原始密码
     * @param key      加密密钥（从登录页面获取的 pwdDefaultEncryptSalt）
     * @return Base64编码的密文
     */
    public static String encrypt(String password, String key) {
        try {
            // 生成64位随机前缀
            String prefix = generateRandomString(PREFIX_LENGTH);
            
            // 生成16位随机IV
            String ivString = generateRandomString(IV_LENGTH);
            byte[] iv = ivString.getBytes(StandardCharsets.UTF_8);
            
            // 明文 = 前缀 + 密码
            String plaintext = prefix + password;
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            
            // 密钥
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            
            // AES-CBC 加密
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            
            byte[] encrypted = cipher.doFinal(plaintextBytes);
            
            // Base64 编码
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
            
        } catch (Exception e) {
            Log.e(TAG, "AES加密失败: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成指定长度的随机字符串
     */
    private static String generateRandomString(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(RANDOM_CHARS.length());
            sb.append(RANDOM_CHARS.charAt(index));
        }
        return sb.toString();
    }
}