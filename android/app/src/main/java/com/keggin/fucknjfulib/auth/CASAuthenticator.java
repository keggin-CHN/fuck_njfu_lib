package com.keggin.fucknjfulib.auth;

import android.util.Log;
import com.keggin.fucknjfulib.crypto.AESCipher;
import com.keggin.fucknjfulib.network.ApiConstants;
import com.keggin.fucknjfulib.network.HttpClientManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.Context;
import okhttp3.Response;

public class CASAuthenticator {
    private static final String TAG = "CASAuthenticator";
    private final String username;
    private final String eduPassword;
    private final HttpClientManager httpClient;
    private String lt;
    private String salt;
    private String dllt;
    private String execution;
    private String eventId;
    private String rmShown;
    private boolean needCaptcha = false;
    private String clientTicket;
    private String errorMessage;
    private final Context context;

    public CASAuthenticator(Context context, String username, String eduPassword) {
        this.context = context.getApplicationContext();
        this.username = username;
        this.eduPassword = eduPassword;
        this.httpClient = HttpClientManager.getInstance(this.context);
    }

    public boolean authenticate() {
        try {
            if (!getInitialClientTicket()) {
                errorMessage = "无法获取初始认证凭证";
                return false;
            }
            getRouteCookie();
            if (!fetchLoginPageParams()) {
                return false;
            }
            if (checkNeedCaptcha()) {
                needCaptcha = true;
                errorMessage = "需要输入验证码";
                return false;
            }
            return submitLogin();
        } catch (Exception e) {
            Log.e(TAG, "认证过程出错: " + e.getMessage(), e);
            errorMessage = "认证过程出错: " + e.getMessage();
            return false;
        }
    }

    public boolean authenticateWithCaptcha(String captcha) {
        try {
            if (salt == null) {
                if (!getInitialClientTicket()) {
                    errorMessage = "无法获取初始认证凭证";
                    return false;
                }
                getRouteCookie();
                if (!fetchLoginPageParams()) {
                    return false;
                }
            }
            return submitLoginWithCaptcha(captcha);
        } catch (Exception e) {
            Log.e(TAG, "验证码认证过程出错: " + e.getMessage(), e);
            errorMessage = "认证过程出错: " + e.getMessage();
            return false;
        }
    }

    private boolean getInitialClientTicket() throws IOException {
        Log.d(TAG, "获取初始 client ticket...");
        Response response = followRedirects(ApiConstants.FRONTEND_LOGIN_URL, null);
        if (response != null && response.isSuccessful()) {
            response.close();
            clientTicket = httpClient.getClientTicket();
            if (clientTicket != null) {
                Log.d(TAG, "成功获取 client ticket");
                return true;
            }
        }
        if (response != null)
            response.close();
        Log.e(TAG, "获取 client ticket 失败");
        return false;
    }

    private void getRouteCookie() {
        try {
            Log.d(TAG, "获取 route cookie...");
            Response response = httpClient.get(ApiConstants.ROUTE_COOKIE_URL);
            String body = HttpClientManager.getResponseBody(response);
            if (body != null) {
                Pattern pattern = Pattern.compile("route=([^;]+)");
                Matcher matcher = pattern.matcher(body);
                if (matcher.find()) {
                    String route = matcher.group(1);
                    Log.d(TAG, "成功获取 route cookie: " + route);
                    httpClient.addCookie("webvpn.njfu.edu.cn", "route", route);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "获取 route cookie 失败（可以继续）: " + e.getMessage());
        }
    }

    private boolean fetchLoginPageParams() throws IOException {
        Log.d(TAG, "获取登录页面参数...");
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Encoding", "identity");
        headers.put("Connection", "close");
        headers.put("Accept", "*/*");
        Response response = followRedirects(ApiConstants.getEduLoginPageUrl(), headers);
        String html = HttpClientManager.getResponseBody(response);
        if (html == null) {
            errorMessage = "无法获取登录页面";
            return false;
        }
        Log.d(TAG, "登录页面响应长度: " + html.length());
        Document doc = Jsoup.parse(html);
        lt = getInputValue(doc, "lt");
        salt = getInputValue(doc, "pwdDefaultEncryptSalt");
        dllt = getInputValue(doc, "dllt");
        execution = getInputValue(doc, "execution");
        eventId = getInputValue(doc, "_eventId");
        rmShown = getInputValue(doc, "rmShown");
        if (lt == null || salt == null) {
            Log.e(TAG, "登录参数解析失败, lt=" + lt + ", salt=" + salt);
            Log.e(TAG, "HTML前500字符: " + html.substring(0, Math.min(500, html.length())));
            errorMessage = "无法获取登录参数";
            return false;
        }
        Log.d(TAG, "成功获取登录参数");
        return true;
    }

    private String getInputValue(Document doc, String idOrName) {
        Element element = doc.getElementById(idOrName);
        if (element != null) {
            return element.attr("value");
        }
        element = doc.selectFirst("input[name=" + idOrName + "]");
        if (element != null) {
            return element.attr("value");
        }
        return null;
    }

    /**
     * 手动跟随重定向，因为 OkHttp 全局禁用了自动重定向。
     * 对于 GET 请求（如获取登录页面）需要跟随重定向才能拿到实际内容。
     */
    private Response followRedirects(String url, Map<String, String> headers) throws IOException {
        int maxRedirects = 10;
        String currentUrl = url;
        for (int i = 0; i < maxRedirects; i++) {
            Response response = httpClient.get(currentUrl, headers);
            int code = response.code();
            if (code >= 300 && code < 400) {
                String location = response.header("Location");
                response.close();
                if (location == null) {
                    Log.e(TAG, "重定向无 Location 头");
                    return null;
                }
                // Handle relative URLs
                if (location.startsWith("/")) {
                    java.net.URL base = new java.net.URL(currentUrl);
                    location = base.getProtocol() + "://" + base.getHost() + location;
                }
                Log.d(TAG, "跟随重定向 -> " + location);
                currentUrl = location;
            } else {
                return response;
            }
        }
        Log.e(TAG, "超过最大重定向次数");
        return null;
    }

    private boolean checkNeedCaptcha() {
        try {
            String url = ApiConstants.getNeedCaptchaUrl(username, salt);
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Requested-With", "XMLHttpRequest");
            Response response = httpClient.get(url, headers);
            String body = HttpClientManager.getResponseBody(response);
            if (body != null && body.trim().equalsIgnoreCase("true")) {
                Log.d(TAG, "需要验证码");
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "检查验证码失败，假设需要: " + e.getMessage());
            return true;
        }
        Log.d(TAG, "不需要验证码");
        return false;
    }

    private boolean submitLogin() throws IOException {
        return doSubmitLogin(null);
    }

    private boolean submitLoginWithCaptcha(String captcha) throws IOException {
        return doSubmitLogin(captcha);
    }

    private boolean doSubmitLogin(String captcha) throws IOException {
        Log.d(TAG, "提交登录...");
        String encryptedPassword = AESCipher.encrypt(eduPassword, salt);
        if (encryptedPassword == null) {
            errorMessage = "密码加密失败";
            return false;
        }
        Map<String, String> formData = new HashMap<>();
        formData.put("vpn-0", "");
        formData.put("service", "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/");
        formData.put("username", username);
        formData.put("password", encryptedPassword);
        formData.put("lt", lt);
        formData.put("dllt", dllt != null ? dllt : "userNamePasswordLogin");
        formData.put("execution", execution);
        formData.put("_eventId", eventId != null ? eventId : "submit");
        formData.put("rmShown", rmShown != null ? rmShown : "1");
        if (captcha != null) {
            formData.put("captchaResponse", captcha);
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", ApiConstants.BASE_URL);
        headers.put("Referer", ApiConstants.getEduLoginPageUrl());
        Response response = httpClient.postForm(ApiConstants.getEduLoginSubmitUrl(), formData, headers);
        int statusCode = response.code();
        Log.d(TAG, "登录响应状态码: " + statusCode);
        if (isRedirect(statusCode)) {
            String location = HttpClientManager.getRedirectLocation(response);
            response.close();
            if (location == null) {
                errorMessage = "登录成功但未获取到重定向地址";
                return false;
            }
            Pattern pattern = Pattern.compile("ticket=([^&]+)");
            Matcher matcher = pattern.matcher(location);
            if (!matcher.find()) {
                errorMessage = "登录成功但未获取到 ticket，重定向地址: " + location;
                return false;
            }
            String ticket = matcher.group(1);
            Log.d(TAG, "获取到 ticket: " + ticket);
            return completeAuthWithTicket(ticket);
        } else if (statusCode == 200) {
            String html = HttpClientManager.getResponseBody(response);
            if (html != null) {
                Document doc = Jsoup.parse(html);
                Element msg = doc.getElementById("msg");
                if (msg != null) {
                    errorMessage = msg.text();
                } else {
                    Element span = doc.selectFirst("span#msg");
                    if (span != null) {
                        errorMessage = span.text();
                    } else {
                        errorMessage = "用户名或密码错误";
                    }
                }
            } else {
                errorMessage = "登录失败，请检查用户名和密码";
            }
            if (html != null && html.contains("captcha")) {
                needCaptcha = true;
            }
            return false;
        } else {
            response.close();
            errorMessage = "登录请求失败，状态码: " + statusCode;
            return false;
        }
    }

    private boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    private boolean completeAuthWithTicket(String ticket) throws IOException {
        Log.d(TAG, "用 ticket 完成认证...");
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Encoding", "identity");
        headers.put("Connection", "close");
        headers.put("Accept", "*/*");
        String url = ApiConstants.getFinalAuthUrl(ticket);
        Response response = httpClient.get(url, headers);
        response.close();
        clientTicket = httpClient.getClientTicket();
        Log.d(TAG, "认证完成, clientTicket: " + (clientTicket != null ? "已获取" : "未获取"));
        return true;
    }

    public String getCaptchaUrl() {
        return ApiConstants.getCaptchaUrl();
    }

    public byte[] getCaptchaImage() {
        try {
            if (salt == null) {
                getInitialClientTicket();
                getRouteCookie();
                httpClient.get(ApiConstants.getEduLoginPageUrl()).close();
            }
            Response response = httpClient.get(getCaptchaUrl());
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            }
        } catch (Exception e) {
            Log.e(TAG, "获取验证码图片失败: " + e.getMessage());
        }
        return null;
    }

    public boolean isNeedCaptcha() {
        return needCaptcha;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getClientTicket() {
        return clientTicket;
    }
}