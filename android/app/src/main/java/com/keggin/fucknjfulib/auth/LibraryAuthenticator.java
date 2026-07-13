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
        if (authenticateSso(3)) {
            return true;
        }
        Log.w(TAG, "SSO认证失败，尝试降级为旧版加密认证流程...");
        return authenticateLegacy(maxAttempts);
    }
    
    public boolean authenticateSso(int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Log.d(TAG, "图书馆认证尝试(SSO) " + attempt + "/" + maxAttempts);
            try {
                reportProgress(80, "获取 CAS 跳转地址...");
                Map<String, String> addressHeaders = new HashMap<>();
                addressHeaders.put("Accept", ApiConstants.ACCEPT_JSON);
                String addressUrl = ApiConstants.getAuthAddressUrl() + "?finalAddress=" + android.net.Uri.encode(ApiConstants.getLibRootUrl()) + "&errPageUrl=" + android.net.Uri.encode(ApiConstants.getErrorPageUrl()) + "&manager=false&consoleType=16";
                Response addressResp = httpClient.get(addressUrl, addressHeaders);
                if (!addressResp.isSuccessful()) {
                    addressResp.close();
                    continue;
                }
                String body = HttpClientManager.getResponseBody(addressResp);
                if (body == null) continue;
                JSONObject json = new JSONObject(body);
                String casUrl = json.optString("data", "");
                if (casUrl.isEmpty()) continue;
                
                if (casUrl.contains("libseat.njfu.edu.cn/")) {
                    String path = casUrl.split("libseat.njfu.edu.cn/")[1];
                    casUrl = ApiConstants.getLibPathUrl(path);
                }
                
                reportProgress(85, "通过 CAS 获取票据...");
                Response casResp = httpClient.get(casUrl, null); // Will follow redirects
                String html = HttpClientManager.getResponseBody(casResp);
                if (html != null) {
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("window\\.location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(html);
                    if (matcher.find()) {
                        String redirectUrl = matcher.group(1);
                        if (redirectUrl != null && redirectUrl.contains("libseat.njfu.edu.cn/")) {
                            String path = redirectUrl.split("libseat.njfu.edu.cn/")[1];
                            redirectUrl = ApiConstants.getLibPathUrl(path);
                        }
                        reportProgress(90, "换取 Library Token...");
                        Response tokenResp = httpClient.get(redirectUrl, null);
                        tokenResp.close();
                    }
                }
                
                reportProgress(95, "抓取用户信息...");
                Map<String, String> infoHeaders = new HashMap<>();
                infoHeaders.put("Accept", ApiConstants.ACCEPT_JSON);
                Response infoResp = httpClient.get(ApiConstants.getUserInfoUrl(), infoHeaders);
                if (infoResp.isSuccessful()) {
                    String infoBody = HttpClientManager.getResponseBody(infoResp);
                    if (infoBody != null) {
                        JSONObject infoJson = new JSONObject(infoBody);
                        if (infoJson.optInt("code") == 0) {
                            JSONObject data = infoJson.optJSONObject("data");
                            if (data != null && data.has("token")) {
                                this.token = data.getString("token");
                                this.accNo = data.getString("accNo");
                                this.lastAuthTime = System.currentTimeMillis();
                                Log.d(TAG, "图书馆 SSO 认证成功！accNo: " + accNo);
                                return true;
                            }
                        }
                    }
                } else {
                    infoResp.close();
                }
                
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e(TAG, "SSO认证过程出错: " + e.getMessage(), e);
            }
        }
        return false;
    }
    public boolean authenticateLegacy(int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Log.d(TAG, "图书馆认证尝试(旧版加密) " + attempt + "/" + maxAttempts);
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