package com.keggin.fucknjfulib.crypto;

import android.util.Log;
import java.math.BigInteger;

/**
 * CAS 新版 RSA 加密 (2025+ NJFU CAS 不再使用 AES-CBC，改为硬编码 RSA 公钥)
 * 与前端 security.js 的 RSAUtils.encryptedString 逻辑完全一致
 * 注意：这是 textbook RSA（无 PKCS#1 padding），与 RSACipher 不同！
 */
public class CASRSACipher {
    private static final String TAG = "CASRSACipher";

    // 从 login.js 提取的硬编码 RSA 公钥
    private static final String MODULUS_HEX =
            "008aed7e057fe8f14c73550b0e6467b023616ddc8fa91846d2613cdb7f7621e3"
            + "cada4cd5d812d627af6b87727ade4e26d26208b7326815941492b2204c3167ab"
            + "2d53df1e3a2c9153bdb7c8c2e968df97a5e7e01cc410f92c4c2c2fba529b3ee9"
            + "88ebc1fca99ff5119e036d732c368acf8beba01aa2fdafa45b21e4de4928d0d403";
    private static final String EXPONENT_HEX = "010001";
    // chunkSize = 2 * 63 = 126（与 JS 端一致）
    private static final int CHUNK_SIZE = 126;

    /**
     * CAS 密码加密 — textbook RSA，与前端 RSAUtils.encryptedString 完全一致
     *
     * @param password 明文密码
     * @return 256 字符的十六进制密文，失败返回 null
     */
    public static String encrypt(String password) {
        try {
            BigInteger n = new BigInteger(MODULUS_HEX, 16);
            BigInteger e = new BigInteger(EXPONENT_HEX, 16);

            // 将密码转为 charCode 数组
            int[] charCodes = new int[password.length()];
            for (int i = 0; i < password.length(); i++) {
                charCodes[i] = password.charAt(i);
            }

            // 填充到 CHUNK_SIZE 的倍数
            int paddedLength = charCodes.length;
            while (paddedLength % CHUNK_SIZE != 0) {
                paddedLength++;
            }
            int[] padded = new int[paddedLength];
            System.arraycopy(charCodes, 0, padded, 0, charCodes.length);
            // 剩余位默认 0

            // 构建消息整数（与 JS 完全一致的 little-endian 打包方式）
            // JS: block.digits[j] = a[k++]; block.digits[j] += a[k++] << 8;
            BigInteger m = BigInteger.ZERO;
            for (int i = 0; i < CHUNK_SIZE; i += 2) {
                int digit = padded[i] + (padded[i + 1] << 8);
                BigInteger term = BigInteger.valueOf(digit).multiply(
                        BigInteger.valueOf(65536).pow(i / 2));
                m = m.add(term);
            }

            // Textbook RSA: c = m^e mod n
            BigInteger c = m.modPow(e, n);

            // 转 hex，填充到 256 字符
            String hex = c.toString(16);
            while (hex.length() < 256) {
                hex = "0" + hex;
            }

            return hex;
        } catch (Exception ex) {
            Log.e(TAG, "CAS RSA 加密失败: " + ex.getMessage(), ex);
            return null;
        }
    }
}
