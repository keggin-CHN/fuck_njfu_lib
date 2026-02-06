package com.keggin.fucknjfulib.crypto;
import android.util.Base64;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
public class RSACipher {
    private static final String TAG = "RSACipher";
    public static String encrypt(String password, String nonce, String publicKey) {
        try {
            String cleanedKey = cleanPublicKey(publicKey);
            byte[] keyBytes = Base64.decode(cleanedKey, Base64.DEFAULT);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey pubKey = keyFactory.generatePublic(keySpec);
            String plaintext = password + ";" + nonce;
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);
            byte[] encrypted = cipher.doFinal(plaintextBytes);
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "RSA加密失败: " + e.getMessage(), e);
            return null;
        }
    }
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