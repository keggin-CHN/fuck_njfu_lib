package com.keggin.fucknjfulib.crypto;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * RSA 加密工具类
 * 用于南京林业大学图书馆系统密码加密
 * 
 * 加密规则（与后端 auth_manager.py 中 encrypt_lib_password 保持一致）：
 * 1. 从服务器获取公钥和 nonce
 * 2. 明文格式：password;nonce
 * 3. 使用 RSA PKCS1_v1.5 填充模式加密
 * 4. 返回 Base64 编码的密文
 */
public class RSACipher {
    
    private static final String TAG = "RSACipher";
    
    /**
     * 加密图书馆密码
     * 
     * @param password  原始密码
     * @param nonce     服务器返回的随机数
     * @param publicKey 服务器返回的公钥（Base64格式，可能不含头尾）
     * @return Base64编码的密文
     */
    public static String encrypt(String password, String nonce, String publicKey) {
        try {
            // 处理公钥格式
            String cleanedKey = cleanPublicKey(publicKey);
            
            // Base64 解码公钥
            byte[] keyBytes = Base64.decode(cleanedKey, Base64.DEFAULT);
            
            // 构建公钥对象
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey pubKey = keyFactory.generatePublic(keySpec);
            
            // 明文：password;nonce
            String plaintext = password + ";" + nonce;
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            
            // RSA 加密（PKCS1Padding 对应 Python 的 PKCS1_v1_5）
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);
            
            byte[] encrypted = cipher.doFinal(plaintextBytes);
            
            // Base64 编码
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
            
        } catch (Exception e) {
            Log.e(TAG, "RSA加密失败: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 清理公钥格式
     * 移除 PEM 头尾和换行符
     */
    private static String cleanPublicKey(String publicKey) {
        if (publicKey == null) {
            return null;
        }
        
        return publicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .trim();
    }
}