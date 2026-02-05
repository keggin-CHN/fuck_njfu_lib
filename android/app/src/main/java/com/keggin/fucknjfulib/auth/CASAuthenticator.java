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

/**
 * 南京林业大学统一认证系统（CAS）登录
 * 通过 WebVPN 中转访问
 * 
 * 认证流程：
 * 1. 访问 /rump_frontend/login/ 获取 my_client_ticket
 * 2. 访问登录页面获取表单参数（lt, salt, execution 等）
 * 3. 加密密码并提交登录
 * 4. 处理重定向，获取 CAS ticket
 * 5. 用 ticket 完成最终认证
 */
public class CASAuthenticator {
    
    private static final String TAG = "CASAuthenticator";
    
    private final String username;
    private final String eduPassword;
    private final HttpClientManager httpClient;
    
    // 登录表单参数
    private String lt;
    private String salt;
    private String dllt;
    private String execution;
    private String eventId;
    private String rmShown;
    
    // 是否需要验证码
    private boolean needCaptcha = false;
    
    // 认证结果
    private String clientTicket;
    private String errorMessage;
    
    public CASAuthenticator(String username, String eduPassword) {
        this.username = username;
        this.eduPassword = eduPassword;
        this.httpClient = HttpClientManager.getInstance();
    }
    
    /**
     * 执行统一认证登录
     * @return true 成功，false 失败
     */
    public boolean authenticate() {
        try {
            // 步骤1：获取初始 client ticket
            if (!getInitialClientTicket()) {
                errorMessage = "无法获取初始认证凭证";
                return false;
            }
            
            // 步骤2：获取 route cookie
            getRouteCookie();
            
            // 步骤3：获取登录页面参数
            if (!fetchLoginPageParams()) {
                return false;
            }
            
            // 步骤4：检查是否需要验证码
            if (checkNeedCaptcha()) {
                needCaptcha = true;
                errorMessage = "需要输入验证码";
                return false;
            }
            
            // 步骤5：提交登录
            return submitLogin();
            
        } catch (Exception e) {
            Log.e(TAG, "认证过程出错: " + e.getMessage(), e);
            errorMessage = "认证过程出错: " + e.getMessage();
            return false;
        }
    }
    
    /**
     * 带验证码认证
     */
    public boolean authenticateWithCaptcha(String captcha) {
        try {
            // 确保已经获取了登录页面参数
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
    
    /**
     * 步骤1：获取初始 client ticket
     */
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
    
    /**
     * 步骤2：获取 route cookie
     */
    private void getRouteCookie() {
        try {
            Log.d(TAG, "获取 route cookie...");
            Response response = httpClient.get(ApiConstants.ROUTE_COOKIE_URL);
            String body = HttpClientManager.getResponseBody(response);
            
            if (body != null) {
                // 从响应体中提取 route
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
    
    /**
     * 步骤3：获取登录页面参数
     */
    private boolean fetchLoginPageParams() throws IOException {
        Log.d(TAG, "获取登录页面参数...");
        
        // 禁用 Gzip 压缩，避免 source exhausted prematurely 错误
        // 同时调整 Accept 和 Connection 头，尝试规避 WebVPN 的问题
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Encoding", "identity");
        headers.put("Connection", "close");
        headers.put("Accept", "*/*");
        Response response = httpClient.get(ApiConstants.getEduLoginPageUrl(), headers);
        
        // 处理重定向 (3xx)
        if (isRedirect(response.code())) {
            String location = HttpClientManager.getRedirectLocation(response);
            response.close();

            if (location != null) {
                Log.d(TAG, "登录页面重定向到: " + location);
                location = normalizeLocation(location);
                Log.d(TAG, "跟随重定向: " + location);
                response = httpClient.get(location, headers);
            } else {
                errorMessage = "登录页面重定向但未获取到 Location";
                return false;
            }
        }
        
        if (!response.isSuccessful()) {
            errorMessage = "访问登录页面失败，状态码: " + response.code();
            response.close();
            return false;
        }
        
        String html = HttpClientManager.getResponseBody(response);
        if (html == null) {
            errorMessage = "登录页面响应为空";
            return false;
        }
        
        // 解析 HTML
        Document doc = Jsoup.parse(html);
        
        // 获取表单参数
        lt = getInputValue(doc, "lt");
        salt = getInputValue(doc, "pwdDefaultEncryptSalt");
        dllt = getInputValue(doc, "dllt");
        execution = getInputValue(doc, "execution");
        eventId = getInputValue(doc, "_eventId");
        rmShown = getInputValue(doc, "rmShown");
        
        if (lt == null || salt == null || execution == null) {
            // 检查是否已经登录或者页面异常
            Element msg = doc.getElementById("msg");
            if (msg != null) {
                errorMessage = "登录页面提示: " + msg.text();
            } else {
                errorMessage = "无法解析登录表单参数";
            }
            Log.e(TAG, errorMessage);
            return false;
        }
        
        Log.d(TAG, "成功获取登录表单参数");
        return true;
    }
    
    /**
     * 获取 input 元素的值
     */
    private String getInputValue(Document doc, String idOrName) {
        // 先尝试 ID
        Element element = doc.getElementById(idOrName);
        if (element != null) {
            return element.attr("value");
        }
        
        // 再尝试 name
        element = doc.selectFirst("input[name=" + idOrName + "]");
        if (element != null) {
            return element.attr("value");
        }
        
        return null;
    }
    
    /**
     * 步骤4：检查是否需要验证码
     */
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
    
    /**
     * 步骤5：提交登录
     */
    private boolean submitLogin() throws IOException {
        return doSubmitLogin(null);
    }
    
    /**
     * 带验证码提交登录
     */
    private boolean submitLoginWithCaptcha(String captcha) throws IOException {
        return doSubmitLogin(captcha);
    }
    
    /**
     * 执行登录提交
     */
    private boolean doSubmitLogin(String captcha) throws IOException {
        Log.d(TAG, "提交登录...");
        
        // 加密密码
        String encryptedPassword = AESCipher.encrypt(eduPassword, salt);
        if (encryptedPassword == null) {
            errorMessage = "密码加密失败";
            return false;
        }
        
        // 构建表单数据
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
        
        // 请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", ApiConstants.BASE_URL);
        headers.put("Referer", ApiConstants.getEduLoginPageUrl());
        
        Response response = httpClient.postForm(ApiConstants.getEduLoginSubmitUrl(), formData, headers);
        
        int statusCode = response.code();
        Log.d(TAG, "登录响应状态码: " + statusCode);
        
        if (isRedirect(statusCode)) {
            // 成功，处理重定向
            String location = HttpClientManager.getRedirectLocation(response);
            response.close();

            if (location == null) {
                errorMessage = "登录成功但未获取到重定向地址";
                return false;
            }

            // 从重定向 URL 中提取 ticket
            Pattern pattern = Pattern.compile("ticket=([^&]+)");
            Matcher matcher = pattern.matcher(location);

            if (!matcher.find()) {
                errorMessage = "登录成功但未获取到 ticket，重定向地址: " + location;
                return false;
            }

            String ticket = matcher.group(1);
            Log.d(TAG, "获取到 ticket: " + ticket);

            // 用 ticket 完成最终认证
            return completeAuthWithTicket(ticket);

        } else if (statusCode == 200) {
            // 登录失败，解析错误信息
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
            
            // 检查是否需要验证码
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
    
    /**
     * 用 ticket 完成最终认证
     */
    private boolean completeAuthWithTicket(String ticket) throws IOException {
        Log.d(TAG, "用 ticket 完成认证...");
        
        // 使用与登录页面一致的请求头，避免 EOFException
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Encoding", "identity");
        headers.put("Connection", "close");
        headers.put("Accept", "*/*");

        String finalUrl = ApiConstants.getFinalAuthUrl(ticket);
        Response response = httpClient.get(finalUrl, headers);
        
        // 手动处理重定向，确保获取所有必要的 Cookie
        int maxRedirects = 5;
        int redirects = 0;
        
        while (isRedirect(response.code()) && redirects < maxRedirects) {
            String location = HttpClientManager.getRedirectLocation(response);
            int code = response.code();
            response.close();

            if (location == null) {
                Log.w(TAG, "收到重定向(" + code + ")但 Location 为空");
                break;
            }

            Log.d(TAG, "跟随重定向(" + code + "): " + location);
            location = normalizeLocation(location);

            response = httpClient.get(location, headers);
            redirects++;
        }
        
        if (response.isSuccessful()) {
            response.close();
            clientTicket = httpClient.getClientTicket();
            
            if (clientTicket != null) {
                Log.d(TAG, "统一认证成功！");
                return true;
            } else {
                errorMessage = "认证完成但未获取到凭证";
                return false;
            }
        } else {
            response.close();
            errorMessage = "最终认证失败，状态码: " + response.code();
            return false;
        }
    }
    
    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static String normalizeLocation(String location) {
        if (location == null) return null;

        // 如果是相对路径，需要拼接
        if (!location.startsWith("http")) {
            if (location.startsWith("/")) {
                return ApiConstants.BASE_URL + location;
            }
            // 可能是相对当前路径，为了保险起见，假设是相对于根目录（因为WebVPN通常如此）
            return ApiConstants.BASE_URL + "/" + location;
        }

        return location;
    }

    /**
     * 获取验证码图片 URL
     */
    public String getCaptchaUrl() {
        return ApiConstants.getCaptchaUrl();
    }
    
    /**
     * 获取验证码图片字节数据
     */
    public byte[] getCaptchaImage() {
        try {
            // 先确保已经访问过登录页面
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
    
    // Getters
    
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