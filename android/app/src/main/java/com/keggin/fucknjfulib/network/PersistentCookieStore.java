package com.keggin.fucknjfulib.network;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
public class PersistentCookieStore implements CookieJar {
    private static final String PREF_NAME = "cookie_store";
    private final SharedPreferences prefs;
    private final Map<String, ConcurrentHashMap<String, Cookie>> cookies;
    public PersistentCookieStore(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        cookies = new HashMap<>();
    }
    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        if (cookies != null && cookies.size() > 0) {
            for (Cookie item : cookies) {
                if (!this.cookies.containsKey(url.host())) {
                    this.cookies.put(url.host(), new ConcurrentHashMap<String, Cookie>());
                }
                this.cookies.get(url.host()).put(item.name(), item);
            }
        }
    }
    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        List<Cookie> result = new ArrayList<>();
        if (this.cookies.containsKey(url.host())) {
            result.addAll(this.cookies.get(url.host()).values());
        }
        return result;
    }
    public void addCookie(HttpUrl url, Cookie cookie) {
        if (cookie != null) {
            if (!this.cookies.containsKey(url.host())) {
                this.cookies.put(url.host(), new ConcurrentHashMap<String, Cookie>());
            }
            this.cookies.get(url.host()).put(cookie.name(), cookie);
        }
    }
    public void clear() {
        cookies.clear();
        prefs.edit().clear().apply();
    }
    public String getCookieValue(String domain, String name) {
        if (cookies.containsKey(domain)) {
            ConcurrentHashMap<String, Cookie> domainCookies = cookies.get(domain);
            if (domainCookies != null && domainCookies.containsKey(name)) {
                Cookie cookie = domainCookies.get(name);
                if (cookie != null) {
                    return cookie.value();
                }
            }
        }
        return null;
    }
    public String findCookieAnywhere(String name) {
        for (ConcurrentHashMap<String, Cookie> domainCookies : cookies.values()) {
            if (domainCookies.containsKey(name)) {
                Cookie cookie = domainCookies.get(name);
                if (cookie != null) {
                    return cookie.value();
                }
            }
        }
        return null;
    }
}
