package com.keggin.fucknjfulib.network;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * HTTP 客户端管理器
 * 使用 OkHttp，支持 Cookie 自动管理
 */
public class HttpClientManager {
    
    private static final String TAG = "HttpClientManager";
    
    private static HttpClientManager instance;
    private final OkHttpClient client;
    private final CookieStore cookieStore;
    
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private HttpClientManager() {
        cookieStore = new CookieStore();
        
        client = new OkHttpClient.Builder()
                .cookieJar(cookieStore)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)  // 手动处理重定向以获取 ticket
                .followSslRedirects(false)
                .build();
    }
    
    public static synchronized HttpClientManager getInstance() {
        if (instance == null) {
            instance = new HttpClientManager();
        }
        return instance;
    }
    
    /**
     * 清除所有 Cookie（用于重新登录）
     */
    public void clearCookies() {
        cookieStore.clear();
    }
    
    /**
     * 获取指定 Cookie 的值
     */
    public String getCookie(String domain, String name) {
        return cookieStore.getCookieValue(domain, name);
    }
    
    /**
     * 获取 my_client_ticket
     */
    public String getClientTicket() {
        String ticket = getCookie("webvpn.njfu.edu.cn", "my_client_ticket");
        if (ticket == null) {
            // 尝试从所有域中查找
            ticket = cookieStore.findCookieAnywhere("my_client_ticket");
        }
        return ticket;
    }
    
    /**
     * 发送 GET 请求
     */
    public Response get(String url) throws IOException {
        return get(url, null);
    }
    
    /**
     * 发送 GET 请求（带自定义 Header）
     */
    public Response get(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", ApiConstants.USER_AGENT)
                .header("Accept", ApiConstants.ACCEPT_HTML);
        
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        
        Request request = builder.build();
        Log.d(TAG, "GET: " + url);
        return client.newCall(request).execute();
    }
    
    /**
     * 发送 POST 请求（表单数据）
     */
    public Response postForm(String url, Map<String, String> formData, Map<String, String> headers) throws IOException {
        FormBody.Builder formBuilder = new FormBody.Builder();
        if (formData != null) {
            for (Map.Entry<String, String> entry : formData.entrySet()) {
                formBuilder.add(entry.getKey(), entry.getValue());
            }
        }
        
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(formBuilder.build())
                .header("User-Agent", ApiConstants.USER_AGENT)
                .header("Content-Type", ApiConstants.CONTENT_TYPE_FORM);
        
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        
        Request request = builder.build();
        Log.d(TAG, "POST Form: " + url);
        return client.newCall(request).execute();
    }
    
    /**
     * 发送 POST 请求（JSON 数据）
     */
    public Response postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", ApiConstants.USER_AGENT)
                .header("Content-Type", ApiConstants.CONTENT_TYPE_JSON)
                .header("Accept", ApiConstants.ACCEPT_JSON);
        
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        
        Request request = builder.build();
        Log.d(TAG, "POST JSON: " + url);
        return client.newCall(request).execute();
    }
    
    /**
     * 获取响应体字符串（自动关闭响应）
     */
    public static String getResponseBody(Response response) throws IOException {
        if (response == null || response.body() == null) {
            return null;
        }
        try {
            return response.body().string();
        } finally {
            response.close();
        }
    }
    
    /**
     * 获取重定向 Location
     */
    public static String getRedirectLocation(Response response) {
        if (response == null) {
            return null;
        }
        return response.header("Location");
    }
    
    /**
     * Cookie 存储实现
     */
    private static class CookieStore implements CookieJar {
        
        private final Map<String, List<Cookie>> cookieMap = new HashMap<>();
        
        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            String host = url.host();
            List<Cookie> existingCookies = cookieMap.get(host);
            if (existingCookies == null) {
                existingCookies = new ArrayList<>();
                cookieMap.put(host, existingCookies);
            }
            
            // 更新或添加 Cookie
            for (Cookie newCookie : cookies) {
                // 移除同名旧 Cookie
                existingCookies.removeIf(c -> c.name().equals(newCookie.name()));
                existingCookies.add(newCookie);
                Log.d(TAG, "Cookie saved: " + host + " - " + newCookie.name() + "=" + newCookie.value());
            }
        }
        
        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            String host = url.host();
            List<Cookie> cookies = cookieMap.get(host);
            
            // 同时检查父域名的 Cookie
            List<Cookie> result = new ArrayList<>();
            if (cookies != null) {
                result.addAll(cookies);
            }
            
            // 处理子域名情况
            for (Map.Entry<String, List<Cookie>> entry : cookieMap.entrySet()) {
                if (host.endsWith("." + entry.getKey()) || entry.getKey().endsWith("." + host)) {
                    for (Cookie cookie : entry.getValue()) {
                        if (!containsCookie(result, cookie.name())) {
                            result.add(cookie);
                        }
                    }
                }
            }
            
            return result;
        }
        
        private boolean containsCookie(List<Cookie> cookies, String name) {
            for (Cookie cookie : cookies) {
                if (cookie.name().equals(name)) {
                    return true;
                }
            }
            return false;
        }
        
        public String getCookieValue(String domain, String name) {
            List<Cookie> cookies = cookieMap.get(domain);
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie.name().equals(name)) {
                        return cookie.value();
                    }
                }
            }
            return null;
        }

        public String findCookieAnywhere(String name) {
            for (List<Cookie> cookies : cookieMap.values()) {
                for (Cookie cookie : cookies) {
                    if (cookie.name().equals(name)) {
                        Log.d(TAG, "Found cookie " + name + " in domain " + cookie.domain());
                        return cookie.value();
                    }
                }
            }
            return null;
        }
        
        public void clear() {
            cookieMap.clear();
            Log.d(TAG, "All cookies cleared");
        }
    }
}