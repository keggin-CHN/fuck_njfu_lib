package com.keggin.fucknjfulib.auth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.keggin.fucknjfulib.network.HttpClientManager;
import com.keggin.fucknjfulib.services.LateProtectionService;
import com.keggin.fucknjfulib.utils.Constants;

/**
 * 认证管理器
 * 整合统一认证和图书馆认证，管理认证状态
 */
public class AuthManager {
    
    private static final String TAG = "AuthManager";
    private static AuthManager instance;
    
    private final Context context;
    private SharedPreferences securePrefs;
    
    // 认证器实例
    private CASAuthenticator casAuthenticator;
    private LibraryAuthenticator libAuthenticator;
    
    // 认证状态
    private boolean isAuthenticated = false;
    private String errorMessage;
    
    /**
     * 认证结果类（供UI层使用）
     */
    public static class AuthResult {
        public final boolean success;
        public final String message;
        
        public AuthResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
    
    // 认证回调接口
    public interface AuthCallback {
        void onSuccess(String token, String accNo);
        void onNeedCaptcha(byte[] captchaImage);
        void onFailure(String errorMessage);
    }
    
    private AuthManager(Context context) {
        this.context = context.getApplicationContext();
        initSecurePrefs();
    }
    
    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }
    
    /**
     * 初始化加密存储
     */
    private void initSecurePrefs() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            securePrefs = EncryptedSharedPreferences.create(
                    context,
                    "secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "初始化加密存储失败: " + e.getMessage());
            // 降级使用普通 SharedPreferences
            securePrefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        }
    }
    
    /**
     * 保存用户凭证
     */
    public void saveCredentials(String username, String eduPassword, String libPassword) {
        securePrefs.edit()
                .putString(Constants.PREF_USERNAME, username)
                .putString(Constants.PREF_EDU_PASSWORD, eduPassword)
                .putString(Constants.PREF_LIB_PASSWORD, libPassword)
                .apply();
    }
    
    /**
     * 获取保存的用户名
     */
    public String getSavedUsername() {
        return securePrefs.getString(Constants.PREF_USERNAME, null);
    }
    
    /**
     * 检查是否有保存的凭证
     */
    public boolean hasCredentials() {
        return getSavedUsername() != null 
               && securePrefs.getString(Constants.PREF_EDU_PASSWORD, null) != null
               && securePrefs.getString(Constants.PREF_LIB_PASSWORD, null) != null;
    }
    
    /**
     * 清除所有凭证
     */
    public void clearCredentials() {
        securePrefs.edit()
                .remove(Constants.PREF_USERNAME)
                .remove(Constants.PREF_EDU_PASSWORD)
                .remove(Constants.PREF_LIB_PASSWORD)
                .remove(Constants.PREF_LAST_AUTH_TOKEN)
                .remove(Constants.PREF_LAST_AUTH_ACC_NO)
                .remove(Constants.PREF_LAST_AUTH_TIME)
                .apply();
        
        HttpClientManager.getInstance().clearCookies();
        isAuthenticated = false;
        casAuthenticator = null;
        libAuthenticator = null;
    }
    
    /**
     * 执行完整认证流程（同步方法，需在后台线程调用）
     */
    public boolean authenticate() {
        String username = securePrefs.getString(Constants.PREF_USERNAME, null);
        String eduPassword = securePrefs.getString(Constants.PREF_EDU_PASSWORD, null);
        String libPassword = securePrefs.getString(Constants.PREF_LIB_PASSWORD, null);
        
        if (username == null || eduPassword == null || libPassword == null) {
            errorMessage = "请先登录";
            return false;
        }
        
        return authenticate(username, eduPassword, libPassword);
    }
    
    /**
     * 执行完整认证流程
     */
    public synchronized boolean authenticate(String username, String eduPassword, String libPassword) {
        Log.d(TAG, "开始认证流程...");
        
        // 清除旧的 Cookie
        HttpClientManager.getInstance().clearCookies();
        
        // 第一级：统一认证
        casAuthenticator = new CASAuthenticator(username, eduPassword);
        if (!casAuthenticator.authenticate()) {
            if (casAuthenticator.isNeedCaptcha()) {
                errorMessage = "需要验证码";
            } else {
                errorMessage = casAuthenticator.getErrorMessage();
            }
            return false;
        }
        
        Log.d(TAG, "统一认证成功，等待 2 秒...");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 第二级：图书馆认证
        libAuthenticator = new LibraryAuthenticator(username, libPassword);
        if (!libAuthenticator.authenticate()) {
            errorMessage = libAuthenticator.getErrorMessage();
            return false;
        }
        
        // 保存认证信息
        securePrefs.edit()
                .putString(Constants.PREF_LAST_AUTH_TOKEN, libAuthenticator.getToken())
                .putString(Constants.PREF_LAST_AUTH_ACC_NO, libAuthenticator.getAccNo())
                .putLong(Constants.PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                .apply();
        
        isAuthenticated = true;
        Log.d(TAG, "完整认证流程成功！");
        return true;
    }
    
    /**
     * 使用验证码认证
     */
    public boolean authenticateWithCaptcha(String captcha) {
        if (casAuthenticator == null) {
            errorMessage = "请先开始认证流程";
            return false;
        }
        
        String username = securePrefs.getString(Constants.PREF_USERNAME, null);
        String libPassword = securePrefs.getString(Constants.PREF_LIB_PASSWORD, null);
        
        // 使用验证码完成统一认证
        if (!casAuthenticator.authenticateWithCaptcha(captcha)) {
            errorMessage = casAuthenticator.getErrorMessage();
            return false;
        }
        
        Log.d(TAG, "验证码认证成功，等待 2 秒...");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 第二级：图书馆认证
        libAuthenticator = new LibraryAuthenticator(username, libPassword);
        if (!libAuthenticator.authenticate()) {
            errorMessage = libAuthenticator.getErrorMessage();
            return false;
        }
        
        // 保存认证信息
        securePrefs.edit()
                .putString(Constants.PREF_LAST_AUTH_TOKEN, libAuthenticator.getToken())
                .putString(Constants.PREF_LAST_AUTH_ACC_NO, libAuthenticator.getAccNo())
                .putLong(Constants.PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                .apply();
        
        isAuthenticated = true;
        return true;
    }
    
    /**
     * 获取验证码图片
     */
    public byte[] getCaptchaImage() {
        if (casAuthenticator == null) {
            String username = securePrefs.getString(Constants.PREF_USERNAME, null);
            String eduPassword = securePrefs.getString(Constants.PREF_EDU_PASSWORD, null);
            if (username != null && eduPassword != null) {
                casAuthenticator = new CASAuthenticator(username, eduPassword);
            }
        }
        
        if (casAuthenticator != null) {
            return casAuthenticator.getCaptchaImage();
        }
        return null;
    }
    
    /**
     * 验证当前认证是否有效
     */
    public boolean isAuthValid() {
        if (!isAuthenticated || libAuthenticator == null) {
            return false;
        }
        return libAuthenticator.isTokenValid();
    }
    
    /**
     * 刷新认证（重新认证）
     */
    public boolean refreshAuth() {
        isAuthenticated = false;
        casAuthenticator = null;
        libAuthenticator = null;
        HttpClientManager.getInstance().clearCookies();
        return authenticate();
    }
    
    /**
     * CAS统一认证登录（供UI层使用）
     * @param username 学号
     * @param password 统一认证密码
     * @return 认证结果
     */
    public AuthResult loginCAS(String username, String password) {
        Log.d(TAG, "开始CAS统一认证...");
        
        // 清除旧的 Cookie
        HttpClientManager.getInstance().clearCookies();
        
        // 保存用户名（仅用于认证过程）
        securePrefs.edit()
                .putString(Constants.PREF_USERNAME, username)
                .putString(Constants.PREF_EDU_PASSWORD, password)
                .apply();
        
        // 执行统一认证
        casAuthenticator = new CASAuthenticator(username, password);
        if (!casAuthenticator.authenticate()) {
            if (casAuthenticator.isNeedCaptcha()) {
                return new AuthResult(false, "需要验证码，请稍后重试");
            }
            return new AuthResult(false, casAuthenticator.getErrorMessage());
        }
        
        Log.d(TAG, "CAS统一认证成功");
        return new AuthResult(true, "统一认证成功");
    }
    
    /**
     * 图书馆系统登录（供UI层使用）
     * @param username 学号
     * @param password 图书馆密码
     * @return 认证结果
     */
    public AuthResult loginLibrary(String username, String password) {
        Log.d(TAG, "开始图书馆认证...");
        
        // 保存图书馆密码
        securePrefs.edit()
                .putString(Constants.PREF_LIB_PASSWORD, password)
                .apply();
        
        // 等待一下确保CAS认证生效
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 执行图书馆认证
        libAuthenticator = new LibraryAuthenticator(username, password);
        if (!libAuthenticator.authenticate()) {
            return new AuthResult(false, libAuthenticator.getErrorMessage());
        }
        
        // 保存认证信息
        securePrefs.edit()
                .putString(Constants.PREF_LAST_AUTH_TOKEN, libAuthenticator.getToken())
                .putString(Constants.PREF_LAST_AUTH_ACC_NO, libAuthenticator.getAccNo())
                .putLong(Constants.PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                .apply();
        
        isAuthenticated = true;
        Log.d(TAG, "图书馆认证成功！Token: " + (libAuthenticator.getToken() != null ? "已获取" : "空"));
        return new AuthResult(true, "登录成功");
    }
    
    /**
     * 确保已登录（如果token过期则重新认证）
     * @return 是否成功登录
     */
    public boolean ensureLoggedIn() {
        // 如果当前已认证且有效，直接返回
        if (isAuthenticated && isAuthValid()) {
            return true;
        }
        
        // 检查是否有保存的token
        String savedToken = securePrefs.getString(Constants.PREF_LAST_AUTH_TOKEN, null);
        String savedAccNo = securePrefs.getString(Constants.PREF_LAST_AUTH_ACC_NO, null);
        long lastAuthTime = securePrefs.getLong(Constants.PREF_LAST_AUTH_TIME, 0);
        
        // 如果token存在且在30分钟内，认为仍有效
        if (savedToken != null && savedAccNo != null) {
            long elapsed = System.currentTimeMillis() - lastAuthTime;
            if (elapsed < 30 * 60 * 1000) {
                // 创建一个带有已保存token的认证器
                if (libAuthenticator == null) {
                    String username = securePrefs.getString(Constants.PREF_USERNAME, null);
                    String libPassword = securePrefs.getString(Constants.PREF_LIB_PASSWORD, null);
                    if (username != null && libPassword != null) {
                        libAuthenticator = new LibraryAuthenticator(username, libPassword);
                        libAuthenticator.setTokenFromCache(savedToken, savedAccNo);
                    }
                }
                isAuthenticated = true;
                return true;
            }
        }
        
        // Token过期，尝试重新认证
        return authenticate();
    }
    
    /**
     * 调度迟到保护检查
     */
    public void scheduleLateProtection() {
        boolean lateProtectionEnabled = securePrefs.getBoolean(Constants.PREF_PREVENT_LATE, false);
        if (lateProtectionEnabled) {
            Intent serviceIntent = new Intent(context, LateProtectionService.class);
            serviceIntent.setAction(LateProtectionService.ACTION_SCHEDULE);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
    
    // Getters
    
    public String getToken() {
        if (libAuthenticator != null) {
            return libAuthenticator.getToken();
        }
        return securePrefs.getString(Constants.PREF_LAST_AUTH_TOKEN, null);
    }
    
    public String getAccNo() {
        if (libAuthenticator != null) {
            return libAuthenticator.getAccNo();
        }
        return securePrefs.getString(Constants.PREF_LAST_AUTH_ACC_NO, null);
    }
    
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
    
    public boolean isNeedCaptcha() {
        return casAuthenticator != null && casAuthenticator.isNeedCaptcha();
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public SharedPreferences getSecurePrefs() {
        return securePrefs;
    }
}