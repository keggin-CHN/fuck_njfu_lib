package com.keggin.fucknjfulib.crypto;
import android.util.Base64;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
public class AESCipher {
    private static final String TAG = "AESCipher";
    private static final String RANDOM_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
    private static final int PREFIX_LENGTH = 64;
    private static final int IV_LENGTH = 16;
    public static String encrypt(String password, String key) {
        try {
            String prefix = generateRandomString(PREFIX_LENGTH);
            String ivString = generateRandomString(IV_LENGTH);
            byte[] iv = ivString.getBytes(StandardCharsets.UTF_8);
            String plaintext = prefix + password;
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintextBytes);
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "AES加密失败: " + e.getMessage(), e);
            return null;
        }
    }
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