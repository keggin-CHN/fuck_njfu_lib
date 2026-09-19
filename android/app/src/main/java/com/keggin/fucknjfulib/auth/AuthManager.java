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
import com.keggin.fucknjfulib.utils.LocalLogManager;
import com.keggin.fucknjfulib.utils.ProgressListener;
import org.json.JSONObject;
import java.util.Map;
import java.util.HashMap;
import okhttp3.Response;
public class AuthManager {
    private static final String TAG = "AuthManager";
    private static AuthManager instance;
    private final Context context;
    private SharedPreferences securePrefs;
    private CASAuthenticator casAuthenticator;
    private LibraryAuthenticator libAuthenticator;
    private boolean isAuthenticated = false;
    private String errorMessage;
    private LocalLogManager localLog;
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
        this.localLog = LocalLogManager.getInstance(this.context);
    }
    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }
    public Context getContext() {
        return context;
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
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.e(TAG, "初始化加密存储失败 " + e.getMessage());
            securePrefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        }
    }
    public void saveCredentials(String username, String eduPassword, String libPassword) {
        String finalLibPwd = (libPassword == null || libPassword.isEmpty()) ? eduPassword : libPassword;
        securePrefs.edit()
                .putString(Constants.PREF_USERNAME, username)
                .putString(Constants.PREF_EDU_PASSWORD, eduPassword)
                .putString(Constants.PREF_LIB_PASSWORD, finalLibPwd)
                .apply();
    }
    public String getSavedUsername() {
        String u = securePrefs.getString(Constants.PREF_USERNAME, null);
        if (u == null || u.trim().isEmpty()) {
            u = com.keggin.fucknjfulib.storage.PreferenceManager.getInstance(context).getStudentId();
        }
        return (u != null && !u.trim().isEmpty()) ? u : null;
    }
    public String getSavedEduPassword() {
        String p = securePrefs.getString(Constants.PREF_EDU_PASSWORD, null);
        if (p == null || p.trim().isEmpty()) {
            p = com.keggin.fucknjfulib.storage.PreferenceManager.getInstance(context).getCasPassword();
        }
        return (p != null && !p.trim().isEmpty()) ? p : null;
    }
    public String getSavedLibPassword() {
        String p = securePrefs.getString(Constants.PREF_LIB_PASSWORD, null);
        if (p == null || p.trim().isEmpty()) {
            p = com.keggin.fucknjfulib.storage.PreferenceManager.getInstance(context).getLibPassword();
        }
        return (p != null && !p.trim().isEmpty()) ? p : null;
    }
    public boolean hasCredentials() {
        return getSavedUsername() != null && getSavedEduPassword() != null;
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
    public boolean authenticate(ProgressListener listener) {
        String username = getSavedUsername();
        String eduPassword = getSavedEduPassword();
        String libPassword = getSavedLibPassword();
        if (username == null || eduPassword == null) {
            errorMessage = "请先登录";
            return false;
        }
        return authenticate(username, eduPassword, libPassword != null ? libPassword : eduPassword, listener);
    }
    public synchronized boolean authenticate(String username, String eduPassword, String libPassword, ProgressListener listener) {
        com.keggin.fucknjfulib.storage.PreferenceManager prefMgr =
                com.keggin.fucknjfulib.storage.PreferenceManager.getInstance(context);
        String serverUrl = prefMgr.getServerApiUrl();
        if (serverUrl != null && !serverUrl.trim().isEmpty()) {
            if (syncSessionFromServer()) {
                localLog.i(TAG, "通过校内穿透服务器会话恢复登录状态成功");
                Log.d(TAG, "通过校内穿透服务器会话恢复登录状态成功");
                return true;
            }
            Log.d(TAG, "通过校内穿透服务器执行直连登录: " + username);
            AuthResult res = loginViaServer(username, eduPassword, libPassword);
            if (res.success) {
                return true;
            } else {
                errorMessage = res.message;
                return false;
            }
        }
        Log.d(TAG, "开始本地认证流程...");
        HttpClientManager.getInstance(context).clearCookies();
        casAuthenticator = new CASAuthenticator(context, username, eduPassword, listener);
        if (!casAuthenticator.authenticate()) {
            if (casAuthenticator.isNeedCaptcha()) {
                errorMessage = "需要验证码";
                localLog.w(TAG, "CAS认证需要验证码");
            } else {
                errorMessage = casAuthenticator.getErrorMessage();
                localLog.e(TAG, "CAS认证失败: " + errorMessage);
            }
            return false;
        }
        Log.d(TAG, "统一认证成功，等待 2 秒...");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        libAuthenticator = new LibraryAuthenticator(context, username, libPassword, listener);
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
        localLog.i(TAG, "完整认证流程成功");
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
        libAuthenticator = new LibraryAuthenticator(context, username, libPassword, null);
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
                casAuthenticator = new CASAuthenticator(context, username, eduPassword, null);
            }
        }
        if (casAuthenticator != null) {
            return casAuthenticator.getCaptchaImage();
        }
        return null;
    }
    public boolean isAuthValid() {
        if (!isAuthenticated) {
            return false;
        }
        com.keggin.fucknjfulib.storage.PreferenceManager prefMgr =
                com.keggin.fucknjfulib.storage.PreferenceManager.getInstance(context);
        String serverUrl = prefMgr.getServerApiUrl();
        if (serverUrl != null && !serverUrl.trim().isEmpty()) {
            long lastAuthTime = securePrefs.getLong(Constants.PREF_LAST_AUTH_TIME, 0);
            if (System.currentTimeMillis() - lastAuthTime < 60 * 60 * 1000) {
                return true;
            }
            return syncSessionFromServer();
        }
        if (libAuthenticator == null) {
            return false;
        }
        return libAuthenticator.isTokenValid();
    }
    public boolean refreshAuth() {
        isAuthenticated = false;
        casAuthenticator = null;
        libAuthenticator = null;
        HttpClientManager.getInstance(context).clearCookies();

        com.keggin.fucknjfulib.storage.PreferenceManager prefMgr =
                com.keggin.fucknjfulib.storage.PreferenceManager.getInstance(context);
        String serverUrl = prefMgr.getServerApiUrl();
        if (serverUrl != null && !serverUrl.trim().isEmpty()) {
            if (syncSessionFromServer()) {
                return true;
            }
            String username = getSavedUsername();
            String eduPassword = getSavedEduPassword();
            String libPassword = getSavedLibPassword();
            if (username != null && eduPassword != null) {
                Log.d(TAG, "从校内穿透服务器重新登录: " + username);
                AuthResult result = loginViaServer(username, eduPassword, libPassword != null ? libPassword : eduPassword);
                if (result.success) {
                    return true;
                } else {
                    errorMessage = result.message;
                    return false;
                }
            }
            return false;
        }

        return authenticate(null);
    }
    public AuthResult loginCAS(String username, String password) {
        Log.d(TAG, "开始CAS统一认证...");
        HttpClientManager.getInstance(context).clearCookies();
        securePrefs.edit()
                .putString(Constants.PREF_USERNAME, username)
                .putString(Constants.PREF_EDU_PASSWORD, password)
                .apply();
        casAuthenticator = new CASAuthenticator(context, username, password, null);
        if (!casAuthenticator.authenticate()) {
            if (casAuthenticator.isNeedCaptcha()) {
                return new AuthResult(false, "需要验证码，请稍后重试");
            }
            return new AuthResult(false, casAuthenticator.getErrorMessage());
        }
        Log.d(TAG, "CAS统一认证成功");
        localLog.i(TAG, "CAS统一认证成功");
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
        libAuthenticator = new LibraryAuthenticator(context, username, password, null);
        if (!libAuthenticator.authenticate()) {
            localLog.e(TAG, "图书馆认证失败: " + libAuthenticator.getErrorMessage());
            return new AuthResult(false, libAuthenticator.getErrorMessage());
        }
        securePrefs.edit()
                .putString(Constants.PREF_LAST_AUTH_TOKEN, libAuthenticator.getToken())
                .putString(Constants.PREF_LAST_AUTH_ACC_NO, libAuthenticator.getAccNo())
                .putLong(Constants.PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                .apply();
        isAuthenticated = true;
        Log.d(TAG, "图书馆认证成功！Token: " + (libAuthenticator.getToken() != null ? "已获取" : "空"));
        localLog.i(TAG, "图书馆认证成功，Token已获取");
        return new AuthResult(true, "登录成功");
    }
    public boolean syncSessionFromServer() {
        try {
            com.keggin.fucknjfulib.storage.PreferenceManager prefMgr =
                    com.keggin.fucknjfulib.storage.PreferenceManager.getInstance(context);
            String serverUrl = prefMgr.getServerApiUrl();
            String username = securePrefs.getString(Constants.PREF_USERNAME, null);
            if (username == null || username.trim().isEmpty()) {
                username = prefMgr.getStudentId();
            }
            if (serverUrl == null || serverUrl.trim().isEmpty() || username == null || username.trim().isEmpty()) {
                return false;
            }
            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                serverUrl = "http://" + serverUrl;
            }
            String url = serverUrl + "/api/auth/status/" + username;
            HttpClientManager http = HttpClientManager.getInstance(context);
            okhttp3.Response response = http.get(url);
            try {
                if (response.isSuccessful()) {
                    String body = HttpClientManager.getResponseBody(response);
                    if (body != null) {
                        org.json.JSONObject json = new org.json.JSONObject(body);
                        boolean isValid = json.optBoolean("is_valid", false);
                        String token = json.optString("token", null);
                        String accNo = json.optString("acc_no", null);
                        if (isValid && token != null && !token.isEmpty() && !"null".equals(token)) {
                            securePrefs.edit()
                                    .putString(Constants.PREF_USERNAME, username)
                                    .putString(Constants.PREF_LAST_AUTH_TOKEN, token)
                                    .putString(Constants.PREF_LAST_AUTH_ACC_NO, accNo)
                                    .putLong(Constants.PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                                    .apply();

                            // 同步 WebVPN Cookies 到本地 HttpClientManager
                            org.json.JSONObject cookies = json.optJSONObject("cookies");
                            if (cookies != null) {
                                java.util.Iterator<String> it = cookies.keys();
                                while (it.hasNext()) {
                                    String cName = it.next();
                                    String cVal = cookies.optString(cName);
                                    http.addCookie("webvpn.njfu.edu.cn", cName, cVal);
                                }
                            }

                            if (libAuthenticator == null) {
                                String libPassword = securePrefs.getString(Constants.PREF_LIB_PASSWORD, "");
                                libAuthenticator = new LibraryAuthenticator(context, username, libPassword, null);
                            }
                            libAuthenticator.setTokenFromCache(token, accNo);
                            isAuthenticated = true;
                            localLog.i(TAG, "成功从服务器API同步会话: Token=" + token);
                            Log.d(TAG, "成功从服务器API同步会话: Token=" + token);
                            return true;
                        }
                    }
                }
            } finally {
                response.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "从服务器同步会话失败: " + e.getMessage());
        }
        return false;
    }
    public AuthResult loginViaServer(String username, String eduPassword, String libPassword) {
        try {
            com.keggin.fucknjfulib.storage.PreferenceManager prefMgr =
                    com.keggin.fucknjfulib.storage.PreferenceManager.getInstance(context);
            String serverUrl = prefMgr.getServerApiUrl();
            if (serverUrl == null || serverUrl.trim().isEmpty()) {
                return new AuthResult(false, "未配置服务器代理地址");
            }
            if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
                serverUrl = "http://" + serverUrl;
            }
            String apiKey = prefMgr.getApiKey();

            HttpClientManager http = HttpClientManager.getInstance(context);
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.put("X-API-Key", apiKey);
            }

            JSONObject payload = new JSONObject();
            payload.put("username", username);
            payload.put("edu_password", eduPassword);
            payload.put("lib_password", libPassword);

            // 1. 优先尝试 /api/auth/login
            String loginUrl = serverUrl + "/api/auth/login";
            Response loginResp = http.postJson(loginUrl, payload.toString(), headers);
            if (loginResp != null && loginResp.code() != 404) {
                try {
                    String body = HttpClientManager.getResponseBody(loginResp);
                    if (loginResp.isSuccessful() && body != null) {
                        JSONObject json = new JSONObject(body);
                        boolean success = json.optBoolean("success", false);
                        String msg = json.optString("message", success ? "登录成功" : "认证失败");
                        if (success) {
                            String token = json.optString("token", null);
                            String accNo = json.optString("acc_no", null);
                            saveAuthSuccess(username, eduPassword, libPassword, token, accNo, json.optJSONObject("cookies"));
                            return new AuthResult(true, msg);
                        } else {
                            return new AuthResult(false, msg);
                        }
                    } else if (body != null) {
                        try {
                            JSONObject errJson = new JSONObject(body);
                            return new AuthResult(false, errJson.optString("detail", "HTTP " + loginResp.code()));
                        } catch (Exception ignored) {}
                    }
                } finally {
                    loginResp.close();
                }
            } else if (loginResp != null) {
                loginResp.close();
            }

            // 2. 回退调用现有 /api/auth/test + /api/auth/status
            String testUrl = serverUrl + "/api/auth/test";
            Response testResp = http.postJson(testUrl, payload.toString(), headers);
            try {
                if (testResp.isSuccessful()) {
                    String body = HttpClientManager.getResponseBody(testResp);
                    if (body != null) {
                        JSONObject json = new JSONObject(body);
                        boolean success = json.optBoolean("success", false);
                        if (!success) {
                            String msg = json.optString("message", "账号或密码错误");
                            return new AuthResult(false, msg);
                        }
                    }
                } else {
                    return new AuthResult(false, "服务器连接失败，状态码: " + testResp.code());
                }
            } finally {
                testResp.close();
            }

            String statusUrl = serverUrl + "/api/auth/status/" + username;
            Response statusResp = http.get(statusUrl);
            try {
                if (statusResp.isSuccessful()) {
                    String body = HttpClientManager.getResponseBody(statusResp);
                    if (body != null) {
                        JSONObject json = new JSONObject(body);
                        String token = json.optString("token", null);
                        String accNo = json.optString("acc_no", null);
                        saveAuthSuccess(username, eduPassword, libPassword, token, accNo, json.optJSONObject("cookies"));
                        return new AuthResult(true, "校内代理直连登录成功！");
                    }
                }
            } finally {
                statusResp.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "loginViaServer error: " + e.getMessage(), e);
            return new AuthResult(false, "连接代理服务器出错: " + e.getMessage());
        }
        return new AuthResult(false, "登录失败，未能获取会话凭据");
    }

    private void saveAuthSuccess(String username, String eduPassword, String libPassword, String token, String accNo, JSONObject cookies) {
        securePrefs.edit()
                .putString(Constants.PREF_USERNAME, username)
                .putString(Constants.PREF_EDU_PASSWORD, eduPassword)
                .putString(Constants.PREF_LIB_PASSWORD, libPassword)
                .putString(Constants.PREF_LAST_AUTH_TOKEN, token)
                .putString(Constants.PREF_LAST_AUTH_ACC_NO, accNo)
                .putLong(Constants.PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                .apply();

        com.keggin.fucknjfulib.storage.PreferenceManager prefMgr =
                com.keggin.fucknjfulib.storage.PreferenceManager.getInstance(context);
        prefMgr.saveCredentials(username, eduPassword, libPassword);
        prefMgr.setLoggedIn(true);

        if (cookies != null) {
            HttpClientManager http = HttpClientManager.getInstance(context);
            java.util.Iterator<String> it = cookies.keys();
            while (it.hasNext()) {
                String cName = it.next();
                String cVal = cookies.optString(cName);
                http.addCookie("webvpn.njfu.edu.cn", cName, cVal);
                http.addCookie("libseat.njfu.edu.cn", cName, cVal);
            }
        }

        if (libAuthenticator == null) {
            libAuthenticator = new LibraryAuthenticator(context, username, libPassword, null);
        }
        if (token != null) {
            libAuthenticator.setTokenFromCache(token, accNo);
        }
        isAuthenticated = true;
        localLog.i(TAG, "代理登录认证成功，Token=" + token);
    }

    public boolean ensureLoggedIn() {
        if (isAuthenticated && isAuthValid()) {
            return true;
        }
        // 1. 优先尝试从云端服务器同步有效会话
        if (syncSessionFromServer()) {
            return true;
        }
        // 2. 本地缓存检查
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
                        libAuthenticator = new LibraryAuthenticator(context, username, libPassword, null);
                        libAuthenticator.setTokenFromCache(savedToken, savedAccNo);
                    }
                }
                isAuthenticated = true;
                return true;
            }
        }
        // 3. 若配置了服务器代理，使用保存的凭据通过代理自动重新认证，避免手机直连 WebVPN 触发短信验证
        com.keggin.fucknjfulib.storage.PreferenceManager prefMgr =
                com.keggin.fucknjfulib.storage.PreferenceManager.getInstance(context);
        String serverUrl = prefMgr.getServerApiUrl();
        if (serverUrl != null && !serverUrl.trim().isEmpty() && hasCredentials()) {
            String username = getSavedUsername();
            String eduPassword = getSavedEduPassword();
            String libPassword = getSavedLibPassword();
            AuthResult result = loginViaServer(username, eduPassword, libPassword != null ? libPassword : eduPassword);
            if (result.success) {
                return true;
            }
        }
        // 4. 兜底回退本地认证（仅在未配置服务器地址时）
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            return authenticate(null);
        }
        return false;
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
