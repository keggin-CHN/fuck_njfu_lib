package com.keggin.fucknjfulib.network;

import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import android.content.Context;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpClientManager {
    private static final String TAG = "HttpClientManager";
    private static HttpClientManager instance;
    private final OkHttpClient client;
    private PersistentCookieStore cookieStore;
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private HttpClientManager(Context context) {
        cookieStore = new PersistentCookieStore(context.getApplicationContext());
        client = new OkHttpClient.Builder()
                .cookieJar(cookieStore)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public static synchronized HttpClientManager getInstance(Context context) {
        if (instance == null && context != null) {
            instance = new HttpClientManager(context);
        }
        return instance;
    }

    public static synchronized HttpClientManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("HttpClientManager is not initialized, call getInstance(Context) first.");
        }
        return instance;
    }

    public void clearCookies() {
        cookieStore.clear();
    }

    public String getCookie(String domain, String name) {
        return cookieStore.getCookieValue(domain, name);
    }

    public void addCookie(String domain, String name, String value) {
        Cookie cookie = new Cookie.Builder()
                .domain(domain)
                .path("/")
                .name(name)
                .value(value)
                .build();
        HttpUrl url = new HttpUrl.Builder().scheme("https").host(domain).build();
        cookieStore.addCookie(url, cookie);
    }

    public String getClientTicket() {
        String ticket = getCookie("webvpn.njfu.edu.cn", "my_client_ticket");
        if (ticket == null) {
            ticket = cookieStore.findCookieAnywhere("my_client_ticket");
        }
        return ticket;
    }

    public Response get(String url) throws IOException {
        return get(url, null);
    }

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

    public static String getRedirectLocation(Response response) {
        if (response == null) {
            return null;
        }
        return response.header("Location");
    }
    // Removed inner CookieStore class as it's replaced by PersistentCookieStore
}