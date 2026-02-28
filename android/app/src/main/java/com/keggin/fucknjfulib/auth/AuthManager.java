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
public class AuthManager {
    private static final String TAG = "AuthManager";
    private static AuthManager instance;
    private final Context context;
    private SharedPreferences securePrefs;
    private CASAuthenticator casAuthenticator;
    private LibraryAuthenticator libAuthenticator;
    private boolean isAuthenticated = false;
    private String errorMessage;
    public static class AuthResult {
        public final boolean success;
        public final String message;
        public AuthResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
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
            Log.e(TAG, "初始化加密存储失败 " + e.getMessage());
            securePrefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        }
    }
    public void saveCredentials(String username, String eduPassword, String libPassword) {
        securePrefs.edit()
                .putString(Constants.PREF_USERNAME, username)
                .putString(Constants.PREF_EDU_PASSWORD, eduPassword)
                .putString(Constants.PREF_LIB_PASSWORD, libPassword)
                .apply();
    }
    public String getSavedUsername() {
        return securePrefs.getString(Constants.PREF_USERNAME, null);
    }
    public boolean hasCredentials() {
        return getSavedUsername() != null 
               && securePrefs.getString(Constants.PREF_EDU_PASSWORD, null) != null
               && securePrefs.getString(Constants.PREF_LIB_PASSWORD, null) != null;
    }
    public void clearCredentials() {
        securePrefs.edit()
                .remove(Constants.PREF_USERNAME)
                .remove(Constants.PREF_EDU_PASSWORD)
                .remove(Constants.PREF_LIB_PASSWORD)
                .remove(Constants.PREF_LAST_AUTH_TOKEN)
                .remove(Constants.PREF_LAST_AUTH_ACC_NO)
                .remove(Constants.PREF_LAST_AUTH_TIME)
                .apply();
        HttpClientManager.getInstance(context).clearCookies();
        isAuthenticated = false;
        casAuthenticator = null;
        libAuthenticator = null;
    }
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
    public synchronized boolean authenticate(String username, String eduPassword, String libPassword) {
        Log.d(TAG, "开始认证流程...");
        HttpClientManager.getInstance(context).clearCookies();
        casAuthenticator = new CASAuthenticator(context, username, eduPassword);
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
        libAuthenticator = new LibraryAuthenticator(context, username, libPassword);
        if (!libAuthenticator.authenticate()) {
            errorMessage = libAuthenticator.getErrorMessage();
            return false;
        }
        securePrefs.edit()
                .putString(Constants.PREF_LAST_AUTH_TOKEN, libAuthenticator.getToken())
                .putString(Constants.PREF_LAST_AUTH_ACC_NO, libAuthenticator.getAccNo())
                .putLong(Constants.PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                .apply();
        isAuthenticated = true;
        Log.d(TAG, "完整认证流程成功！");
        return true;
    }
    public boolean authenticateWithCaptcha(String captcha) {
        if (casAuthenticator == null) {
            errorMessage = "请先开始认证流程";
            return false;
        }
        String username = securePrefs.getString(Constants.PREF_USERNAME, null);
        String libPassword = securePrefs.getString(Constants.PREF_LIB_PASSWORD, null);
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
        libAuthenticator = new LibraryAuthenticator(context, username, libPassword);
        if (!libAuthenticator.authenticate()) {
            errorMessage = libAuthenticator.getErrorMessage();
            return false;
        }
        securePrefs.edit()
                .putString(Constants.PREF_LAST_AUTH_TOKEN, libAuthenticator.getToken())
                .putString(Constants.PREF_LAST_AUTH_ACC_NO, libAuthenticator.getAccNo())
                .putLong(Constants.PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                .apply();
        isAuthenticated = true;
        return true;
    }
    public byte[] getCaptchaImage() {
        if (casAuthenticator == null) {
            String username = securePrefs.getString(Constants.PREF_USERNAME, null);
            String eduPassword = securePrefs.getString(Constants.PREF_EDU_PASSWORD, null);
            if (username != null && eduPassword != null) {
                casAuthenticator = new CASAuthenticator(context, username, eduPassword);
            }
        }
        if (casAuthenticator != null) {
            return casAuthenticator.getCaptchaImage();
        }
        return null;
    }
    public boolean isAuthValid() {
        if (!isAuthenticated || libAuthenticator == null) {
            return false;
        }
        return libAuthenticator.isTokenValid();
    }
    public boolean refreshAuth() {
        isAuthenticated = false;
        casAuthenticator = null;
        libAuthenticator = null;
        HttpClientManager.getInstance(context).clearCookies();
        return authenticate();
    }
    public AuthResult loginCAS(String username, String password) {
        Log.d(TAG, "开始CAS统一认证...");
        HttpClientManager.getInstance(context).clearCookies();
        securePrefs.edit()
                .putString(Constants.PREF_USERNAME, username)
                .putString(Constants.PREF_EDU_PASSWORD, password)
                .apply();
        casAuthenticator = new CASAuthenticator(context, username, password);
        if (!casAuthenticator.authenticate()) {
            if (casAuthenticator.isNeedCaptcha()) {
                return new AuthResult(false, "需要验证码，请稍后重试");
            }
            return new AuthResult(false, casAuthenticator.getErrorMessage());
        }
        Log.d(TAG, "CAS统一认证成功");
        return new AuthResult(true, "统一认证成功");
    }
    public AuthResult loginLibrary(String username, String password) {
        Log.d(TAG, "开始图书馆认证...");
        securePrefs.edit()
                .putString(Constants.PREF_LIB_PASSWORD, password)
                .apply();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        libAuthenticator = new LibraryAuthenticator(context, username, password);
        if (!libAuthenticator.authenticate()) {
            return new AuthResult(false, libAuthenticator.getErrorMessage());
        }
        securePrefs.edit()
                .putString(Constants.PREF_LAST_AUTH_TOKEN, libAuthenticator.getToken())
                .putString(Constants.PREF_LAST_AUTH_ACC_NO, libAuthenticator.getAccNo())
                .putLong(Constants.PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                .apply();
        isAuthenticated = true;
        Log.d(TAG, "图书馆认证成功！Token: " + (libAuthenticator.getToken() != null ? "已获取" : "空"));
        return new AuthResult(true, "登录成功");
    }
    public boolean ensureLoggedIn() {
        if (isAuthenticated && isAuthValid()) {
            return true;
        }
        String savedToken = securePrefs.getString(Constants.PREF_LAST_AUTH_TOKEN, null);
        String savedAccNo = securePrefs.getString(Constants.PREF_LAST_AUTH_ACC_NO, null);
        long lastAuthTime = securePrefs.getLong(Constants.PREF_LAST_AUTH_TIME, 0);
        if (savedToken != null && savedAccNo != null) {
            long elapsed = System.currentTimeMillis() - lastAuthTime;
            if (elapsed < 30 * 60 * 1000) {
                if (libAuthenticator == null) {
                    String username = securePrefs.getString(Constants.PREF_USERNAME, null);
                    String libPassword = securePrefs.getString(Constants.PREF_LIB_PASSWORD, null);
                    if (username != null && libPassword != null) {
                        libAuthenticator = new LibraryAuthenticator(context, username, libPassword);
                        libAuthenticator.setTokenFromCache(savedToken, savedAccNo);
                    }
                }
                isAuthenticated = true;
                return true;
            }
        }
        return authenticate();
    }
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
