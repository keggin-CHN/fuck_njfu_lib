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
    public CASAuthenticator(String username, String eduPassword) {
        this.username = username;
        this.eduPassword = eduPassword;
        this.httpClient = HttpClientManager.getInstance();
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
        Response response = httpClient.get(ApiConstants.FRONTEND_LOGIN_URL);
        if (response.isSuccessful()) {
            response.close();
            clientTicket = httpClient.getClientTicket();
            if (clientTicket != null) {
                Log.d(TAG, "成功获取 client ticket");
                return true;
            }
        }
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
                    Log.d(TAG, "成功获取 route cookie: " + matcher.group(1));
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
        Response response = httpClient.get(ApiConstants.getEduLoginPageUrl(), headers);
        String html = HttpClientManager.getResponseBody(response);
        if (html == null) {
            errorMessage = "无法获取登录页面";
            return false;
        }
        Document doc = Jsoup.parse(html);
        lt = getInputValue(doc, "lt");
        salt = getInputValue(doc, "pwdDefaultEncryptSalt");
        dllt = getInputValue(doc, "dllt");
        execution = getInputValue(doc, "execution");
        eventId = getInputValue(doc, "_eventId");
        rmShown = getInputValue(doc, "rmShown");
        if (lt == null || salt == null) {
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
        formData.put("service", "https://webvpn.njfu.edu.cn/login?cas_login=true");
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
    private boolean completeAuthWithTicket(String ticket) throws IOException {
        Log.d(TAG, "用 ticket 完成认证...");
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Encoding", "identity");
        headers.put("Connection", "close");
        headers.put("Accept", "*/*");
        String url = ApiConstants.FRONTEND_LOGIN_URL + "?ticket=" + ticket;
        Response response = httpClient.get(url, headers);
        response.close();
        Log.d(TAG, "认证完成");
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