package com.keggin.fucknjfulib.reservation;
import android.util.Log;
import com.keggin.fucknjfulib.network.HttpClientManager;
import com.keggin.fucknjfulib.utils.Constants;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Response;
public class TrafficQuery {
    private static final String TAG = "TrafficQuery";
    private static final String TRAFFIC_API_URL = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OA==/LjE0Ny4xMDEuMTUyLjEwMi4xMDEuMTAyLjE1Ny45Ny4xNTEuOTkuMTA0LjEwMi4xNTIuMTEyLjExMS4xNTM=/book/view";
    public static class TrafficInfo {
        public int currentCount;
        public int totalCapacity;
        public float occupancyRate;
        public String updateTime;
        public boolean success;
        public String errorMessage;
        public TrafficInfo() {
            this.totalCapacity = Constants.LIBRARY_TOTAL_CAPACITY;
        }
    }
    public static TrafficInfo queryCurrentTraffic(android.content.Context context) {
        TrafficInfo info = new TrafficInfo();
        try {
            com.keggin.fucknjfulib.auth.AuthManager authManager = com.keggin.fucknjfulib.auth.AuthManager
                    .getInstance(context);
            if (!authManager.ensureLoggedIn()) {
                info.success = false;
                info.errorMessage = "认证失败，请重新登录";
                return info;
            }
            String token = authManager.getToken();
            HttpClientManager httpClient = HttpClientManager.getInstance(context);
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.put("Accept-Encoding", "identity");
            headers.put("Connection", "close");
            Response response = httpClient.get(TRAFFIC_API_URL, headers);
            if (response != null && (response.code() == 302 || response.code() == 301)) {
                Log.w(TAG, "WebVPN session Expired (302) in TrafficQuery, forcing re-auth...");
                response.close();
                if (authManager.refreshAuth()) {
                    response = httpClient.get(TRAFFIC_API_URL, headers);
                } else {
                    info.success = false;
                    info.errorMessage = "重新认证失败";
                    return info;
                }
            }
            if (response != null && response.isSuccessful() && response.body() != null) {
                byte[] responseBytes = null;
                try {
                    responseBytes = response.body().bytes();
                } catch (java.io.EOFException e) {
                    Log.e(TAG, "Read bytes failed with EOFException", e);
                    info.success = false;
                    info.errorMessage = "页面加载中断 (EOF)";
                    response.close();
                    return info;
                } catch (Exception e) {
                    Log.e(TAG, "Read bytes failed", e);
                    info.success = false;
                    info.errorMessage = "读取页面时出错";
                    response.close();
                    return info;
                }
                String html = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
                if (!html.contains("图书馆") && !html.contains("可用")) {
                    try {
                        html = new String(responseBytes, "GBK");
                    } catch (Exception ignored) {
                    }
                }
                Log.d(TAG, "HTML info: length=" + html.length());
                int availVal = -1;
                int limitVal = -1;
                try {
                    java.util.regex.Pattern pPreciseAvailable = java.util.regex.Pattern
                            .compile("(?s)>(\\d+)</span\\s*>\\s*(?:<br/?>\\s*)?<span>\\s*剩余可用\\s*</span>");
                    java.util.regex.Pattern pPreciseLimit = java.util.regex.Pattern
                            .compile("(?s)>(\\d+)</span\\s*>\\s*(?:<br/?>\\s*)?<span>\\s*入馆限制\\s*</span>");
                    java.util.regex.Matcher m1 = pPreciseAvailable.matcher(html);
                    if (m1.find())
                        availVal = Integer.parseInt(m1.group(1));
                    java.util.regex.Matcher m2 = pPreciseLimit.matcher(html);
                    if (m2.find())
                        limitVal = Integer.parseInt(m2.group(1));
                    if (availVal == -1 || limitVal == -1) {
                        java.util.regex.Pattern pAll = java.util.regex.Pattern
                                .compile("(\\d+)[^\\d<]{0,100}?(剩余可用|入馆限制)");
                        java.util.regex.Matcher mAll = pAll.matcher(html);
                        while (mAll.find()) {
                            int val = Integer.parseInt(mAll.group(1));
                            if (mAll.group(2).contains("可用"))
                                availVal = val;
                            if (mAll.group(2).contains("限制"))
                                limitVal = val;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parsing error: " + e.getMessage());
                }
                if (availVal != -1 && limitVal != -1) {
                    info.totalCapacity = Constants.LIBRARY_TOTAL_CAPACITY;
                    info.currentCount = Math.max(0, limitVal - availVal);
                    info.occupancyRate = (float) info.currentCount / info.totalCapacity * 100;
                    info.updateTime = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(new java.util.Date());
                    info.success = true;
                    Log.d(TAG, "Parsed successfully: Avail=" + availVal + ", Limit=" + limitVal);
                } else {
                    java.util.regex.Pattern pRseat = java.util.regex.Pattern.compile("id=\"lblRseat\"[^>]*>(\\d+)<");
                    java.util.regex.Matcher mRseat = pRseat.matcher(html);
                    if (mRseat.find()) {
                        int reservedSeat = Integer.parseInt(mRseat.group(1));
                        java.util.regex.Pattern pAseat = java.util.regex.Pattern
                                .compile("id=\"lblAseat\"[^>]*>(\\d+)<");
                        java.util.regex.Matcher mAseat = pAseat.matcher(html);
                        int actualSeat = 0;
                        if (mAseat.find()) {
                            actualSeat = Integer.parseInt(mAseat.group(1));
                        }
                        info.currentCount = reservedSeat + actualSeat;
                        info.occupancyRate = (float) info.currentCount / info.totalCapacity * 100;
                        info.updateTime = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                .format(new java.util.Date());
                        info.success = true;
                    } else {
                        info.success = false;
                        info.errorMessage = "页面解析失败，未找到数据";
                        Log.d(TAG, "Analysis failed. Avail=" + availVal + ", Limit=" + limitVal);
                        Log.d(TAG, "HTML content snippet: " + html.substring(0, Math.min(2000, html.length())));
                    }
                }
                response.close();
            } else {
                info.success = false;
                info.errorMessage = "网络请求失败: " + (response != null ? response.code() : "null");
                if (response != null)
                    response.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "查询人流失败", e);
            info.success = false;
            info.errorMessage = e.getMessage();
        }
        return info;
    }
    public static String getOccupancyDescription(float rate) {
        if (rate < 30) {
            return "空闲";
        } else if (rate < 60) {
            return "适中";
        } else if (rate < 80) {
            return "较多";
        } else {
            return "拥挤";
        }
    }
    public static int getOccupancyColor(float rate) {
        if (rate < 30) {
            return android.R.color.holo_green_dark;
        } else if (rate < 60) {
            return android.R.color.holo_blue_dark;
        } else if (rate < 80) {
            return android.R.color.holo_orange_dark;
        } else {
            return android.R.color.holo_red_dark;
        }
    }
}