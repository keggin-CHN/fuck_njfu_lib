package com.keggin.fucknjfulib.auth;
import android.util.Log;
import com.keggin.fucknjfulib.crypto.RSACipher;
import com.keggin.fucknjfulib.network.ApiConstants;
import com.keggin.fucknjfulib.network.HttpClientManager;
import org.json.JSONObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import android.content.Context;
import okhttp3.Response;
import com.keggin.fucknjfulib.utils.ProgressListener;
public class LibraryAuthenticator {
    private static final String TAG = "LibraryAuthenticator";
    private final String username;
    private final String libPassword;
    private final HttpClientManager httpClient;
    private String token;
    private String accNo;
    private long lastAuthTime;
    private String errorMessage;
    private final Context context;
    private final ProgressListener progressListener;
    public LibraryAuthenticator(Context context, String username, String libPassword, ProgressListener progressListener) {
        this.context = context.getApplicationContext();
        this.username = username;
        this.libPassword = libPassword;
        this.httpClient = HttpClientManager.getInstance(this.context);
        this.progressListener = progressListener;
    }
    public boolean authenticate() {
        return authenticate(5);
    }
    public boolean authenticate(int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Log.d(TAG, "图书馆认证尝试 " + attempt + "/" + maxAttempts);
            try {
                reportProgress(80, "获取图书馆加密钥...");
                String[] keyAndNonce = getPublicKeyAndNonce();
                if (keyAndNonce == null) {
                    Log.w(TAG, "获取公钥失败，等待重试...");
                    Thread.sleep(2000);
                    continue;
                }
                String publicKey = keyAndNonce[0];
                String nonce = keyAndNonce[1];
                String encryptedPassword = RSACipher.encrypt(libPassword, nonce, publicKey);
                if (encryptedPassword == null) {
                    errorMessage = "密码加密失败";
                    continue;
                }
                reportProgress(90, "提交图书馆登录...");
                if (submitLogin(encryptedPassword)) {
                    return true;
                }
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e(TAG, "认证过程出错: " + e.getMessage(), e);
                errorMessage = "认证过程出错: " + e.getMessage();
            }
        }
        return false;
    }
    private String[] getPublicKeyAndNonce() {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", ApiConstants.ACCEPT_JSON);
            Response response = httpClient.get(ApiConstants.getPublicKeyUrl(), headers);
            if (!response.isSuccessful()) {
                Log.e(TAG, "获取公钥失败，状态码: " + response.code());
                response.close();
                return null;
            }
            String body = HttpClientManager.getResponseBody(response);
            if (body == null) {
                Log.e(TAG, "公钥响应为空");
                return null;
            }
            JSONObject json = new JSONObject(body);
            if (json.getInt("code") != 0) {
                Log.e(TAG, "获取公钥API返回错误: " + json.optString("message"));
                return null;
            }
            JSONObject data = json.getJSONObject("data");
            String publicKey = data.getString("publicKey");
            String nonce = data.getString("nonceStr");
            Log.d(TAG, "成功获取公钥和 nonce");
            return new String[] { publicKey, nonce };
        } catch (Exception e) {
            Log.e(TAG, "获取公钥出错: " + e.getMessage(), e);
            return null;
        }
    }
    private boolean submitLogin(String encryptedPassword) throws IOException {
        Log.d(TAG, "提交图书馆登录...");
        JSONObject payload = new JSONObject();
        try {
            payload.put("logonName", username);
            payload.put("password", encryptedPassword);
            payload.put("captcha", "");
            payload.put("consoleType", 16);
            payload.put("privacy", true);
        } catch (Exception e) {
            errorMessage = "构建请求数据失败";
            return false;
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", ApiConstants.ACCEPT_JSON);
        Response response = httpClient.postJson(ApiConstants.getLibLoginUrl(), payload.toString(), headers);
        if (!response.isSuccessful()) {
            Log.e(TAG, "图书馆登录请求失败，状态码: " + response.code());
            response.close();
            errorMessage = "登录请求失败，状态码: " + response.code();
            return false;
        }
        String body = HttpClientManager.getResponseBody(response);
        if (body == null) {
            errorMessage = "登录响应为空";
            return false;
        }
        try {
            JSONObject json = new JSONObject(body);
            if (json.getInt("code") == 0) {
                JSONObject data = json.getJSONObject("data");
                token = data.getString("token");
                accNo = data.getString("accNo");
                lastAuthTime = System.currentTimeMillis();
                Log.d(TAG, "图书馆登录成功！accNo: " + accNo);
                return true;
            } else {
                errorMessage = json.optString("message", "图书馆密码错误");
                Log.e(TAG, "图书馆登录失败: " + errorMessage);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "解析登录响应失败: " + e.getMessage(), e);
            errorMessage = "解析登录响应失败";
            return false;
        }
    }
    public boolean isTokenValid() {
        if (token == null || accNo == null) {
            return false;
        }
        try {
            String url = ApiConstants.getReservationInfoUrl();
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            url += "?vpn-12-libseat.njfu.edu.cn&needStatus=8454&unneedStatus=128&beginDate="
                    + today + "&endDate=" + today;
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", ApiConstants.ACCEPT_JSON);
            headers.put("token", token);
            headers.put("lan", "1");
            Response response = httpClient.get(url, headers);
            String body = HttpClientManager.getResponseBody(response);
            if (body != null) {
                JSONObject json = new JSONObject(body);
                return json.getInt("code") == 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "验证 token 失败: " + e.getMessage());
        }
        return false;
    }
    public void setTokenFromCache(String token, String accNo) {
        this.token = token;
        this.accNo = accNo;
        this.lastAuthTime = System.currentTimeMillis();
    }
    public String getToken() {
        return token;
    }
    public String getAccNo() {
        return accNo;
    }
    public long getLastAuthTime() {
        return lastAuthTime;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    private void reportProgress(int percent, String message) {
        if (progressListener != null) {
            progressListener.onProgress(percent, message);
        }
    }
}