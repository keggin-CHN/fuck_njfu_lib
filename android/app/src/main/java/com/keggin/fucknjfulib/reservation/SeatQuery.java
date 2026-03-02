package com.keggin.fucknjfulib.reservation;

import android.util.Log;
import com.keggin.fucknjfulib.auth.AuthManager;
import com.keggin.fucknjfulib.network.ApiConstants;
import com.keggin.fucknjfulib.network.HttpClientManager;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okhttp3.Response;

public class SeatQuery {
    private static final String TAG = "SeatQuery";
    private AuthManager authManager;
    private final HttpClientManager httpClient;

    public SeatQuery() {
        this.httpClient = HttpClientManager.getInstance(null);
    }

    public SeatQuery(AuthManager authManager) {
        this.authManager = authManager;
        this.httpClient = HttpClientManager.getInstance(null);
    }

    public void setAuthManager(AuthManager authManager) {
        this.authManager = authManager;
    }

    public static class SeatInfo {
        public int devId;
        public String devName;
        public int devStatus;
        public String coordinate;
        public float coordX = -1;
        public float coordY = -1;
        public List<ReservationSlot> reservations = new ArrayList<>();

        public boolean isAvailable() {
            return reservations.isEmpty();
        }

        public void parseCoordinate() {
            if (coordinate != null && coordinate.contains(",")) {
                try {
                    String[] parts = coordinate.split(",");
                    coordX = Float.parseFloat(parts[0].trim());
                    coordY = Float.parseFloat(parts[1].trim());
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static class ReservationSlot {
        public long startTime;
        public long endTime;
        public int resvStatus;

        public String getStatusText() {
            switch (resvStatus) {
                case 1027:
                    return "预约中";
                case 1093:
                    return "使用中";
                default:
                    return "未知";
            }
        }
    }

    public static class AreaStats {
        public String areaName;
        public int total;
        public int available;
        public int occupied;
        public float rate;

        public AreaStats(String areaName) {
            this.areaName = areaName;
        }
    }
    public static class RoomBackground {
        public String contentPath;
        public String fullUrl;
        
        public RoomBackground(String contentPath) {
            this.contentPath = contentPath;
            if (contentPath != null && !contentPath.isEmpty()) {
                this.fullUrl = ApiConstants.BASE_URL + "/" + contentPath;
            }
        }
    }

    public static class QueryResult {
        public boolean success;
        public String message;
        public int totalCount;
        public int availableCount;
        public Set<Integer> availableSeatIds;
        public List<SeatInfo> seatsData;
        public RoomBackground background;

        public QueryResult(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.availableSeatIds = new HashSet<>();
        }
    }

    public QueryResult querySeats(String token, Constants.AreaInfo areaInfo, String dateStr) {
        QueryResult result = new QueryResult(false, "未知错误");
        if (areaInfo == null) {
            result.message = "区域信息无效";
            return result;
        }
        String compactDate = dateStr.replace("-", "");
        
        // 获取座位数据和背景图信息
        result.background = getRoomBackground(areaInfo.roomId, compactDate);
        List<SeatInfo> seats = getSeatsData(areaInfo.roomId, compactDate);
        if (seats.isEmpty()) {
            result.message = "获取座位数据失败";
            return result;
        }
        result.success = true;
        result.message = "查询成功";
        result.totalCount = seats.size();
        result.availableCount = 0;
        result.availableSeatIds = new HashSet<>();
        result.seatsData = seats;
        for (SeatInfo seat : seats) {
            if (seat.isAvailable()) {
                result.availableCount++;
                result.availableSeatIds.add(seat.devId);
            }
        }
        return result;
    }

    public RoomBackground getRoomBackground(int roomId, String dateStr) {
        Log.d(TAG, "getRoomBackground: roomId=" + roomId + " date=" + dateStr);
        
        if (authManager != null && !authManager.ensureLoggedIn()) {
            Log.e(TAG, "获取背景图失败: 认证失败");
            return null;
        }
        String token = authManager != null ? authManager.getToken() : null;
        if (token == null) {
            Log.e(TAG, "获取背景图失败: token 无效");
            return null;
        }

        try {
            String url = ApiConstants.getSeatQueryUrl()
                    + "?vpn-12-libseat.njfu.edu.cn"
                    + "&roomIds=" + roomId
                    + "&resvDates=" + dateStr
                    + "&sysKind=8";

            Map<String, String> headers = new HashMap<>();
            headers.put("token", token);
            headers.put("lan", "1");
            headers.put("Accept", ApiConstants.ACCEPT_JSON);
            headers.put("Referer", ApiConstants.BASE_URL);
            headers.put("Origin", ApiConstants.BASE_URL);
            
            Response response = httpClient.get(url, headers);
            
            if (response.code() == 302 || response.code() == 301) {
                response.close();
                if (authManager.refreshAuth()) {
                    token = authManager.getToken();
                    if (token != null) {
                        headers.put("token", token);
                        response = httpClient.get(url, headers);
                    }
                }
            }

            if (!response.isSuccessful()) {
                Log.e(TAG, "获取背景图失败，状态码: " + response.code());
                response.close();
                return null;
            }
            
            String body = HttpClientManager.getResponseBody(response);
            if (body == null) {
                return null;
            }

            JSONObject json = new JSONObject(body);
            if (json.optInt("code", -1) == 0) {
                JSONObject sysInfo = json.optJSONObject("sysInfo");
                if (sysInfo != null) {
                    String contentPath = sysInfo.optString("contentPath", null);
                    if (contentPath != null && !contentPath.isEmpty()) {
                        Log.d(TAG, "找到背景图: " + contentPath);
                        return new RoomBackground(contentPath);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取背景图出错: " + e.getMessage(), e);
        }
        return null;
    }

    public List<SeatInfo> getSeatsData(int roomId, String dateStr) {
        List<SeatInfo> result = new ArrayList<>();
        Log.d(TAG, "getSeatsData 开始: roomId=" + roomId + " date=" + dateStr);

        if (authManager != null && !authManager.ensureLoggedIn()) {
            Log.e(TAG, "获取座位数据失败: 认证失败 - " + authManager.getErrorMessage());
            return result;
        }
        String token = authManager != null ? authManager.getToken() : null;
        if (token == null) {
            Log.e(TAG, "获取座位数据失败: token 无效");
            return result;
        }
        Log.d(TAG, "Token 有效: " + token.substring(0, Math.min(20, token.length())) + "...");

        try {
            String url = ApiConstants.getSeatQueryUrl()
                    + "?vpn-12-libseat.njfu.edu.cn"
                    + "&roomIds=" + roomId
                    + "&resvDates=" + dateStr
                    + "&sysKind=8";
            Log.d(TAG, "请求URL: " + url);

            Map<String, String> headers = new HashMap<>();
            headers.put("token", token);
            headers.put("lan", "1");
            headers.put("Accept", ApiConstants.ACCEPT_JSON);
            headers.put("Referer", ApiConstants.BASE_URL);
            headers.put("Origin", ApiConstants.BASE_URL);
            Response response = httpClient.get(url, headers);
            Log.d(TAG, "HTTP 状态码: " + response.code());

            // 302 表示 WebVPN session 过期，需要重新完整认证
            if (response.code() == 302 || response.code() == 301) {
                Log.w(TAG, "WebVPN session 已过期 (302), 强制重新认证...");
                response.close();
                if (authManager.refreshAuth()) {
                    // 更新 token 并重试
                    token = authManager.getToken();
                    if (token != null) {
                        headers.put("token", token);
                        Log.d(TAG, "重新认证成功, 重试请求...");
                        response = httpClient.get(url, headers);
                        Log.d(TAG, "重试 HTTP 状态码: " + response.code());
                    } else {
                        Log.e(TAG, "重新认证后 token 为空");
                        return result;
                    }
                } else {
                    Log.e(TAG, "重新认证失败: " + authManager.getErrorMessage());
                    return result;
                }
            }

            if (!response.isSuccessful()) {
                Log.e(TAG, "获取座位数据失败，状态码: " + response.code());
                response.close();
                return result;
            }
            String body = HttpClientManager.getResponseBody(response);
            if (body == null) {
                Log.e(TAG, "响应 body 为空");
                return result;
            }
            Log.d(TAG, "响应长度: " + body.length() + " 前100字符: " + body.substring(0, Math.min(100, body.length())));

            JSONObject json = new JSONObject(body);
            if (json.optInt("code", -1) == 0) {
                JSONArray data = json.optJSONArray("data");
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        SeatInfo seat = new SeatInfo();
                        seat.devId = item.optInt("devId");
                        seat.devName = item.optString("devName");
                        seat.devStatus = item.optInt("devStatus");
                        seat.coordinate = item.optString("coordinate", null);
                        seat.parseCoordinate();
                        JSONArray resvInfo = item.optJSONArray("resvInfo");
                        if (resvInfo != null) {
                            for (int j = 0; j < resvInfo.length(); j++) {
                                JSONObject resv = resvInfo.getJSONObject(j);
                                ReservationSlot slot = new ReservationSlot();
                                slot.startTime = resv.optLong("startTime");
                                slot.endTime = resv.optLong("endTime");
                                if (slot.startTime > 0 && slot.startTime < 10000000000L) {
                                    slot.startTime *= 1000;
                                }
                                if (slot.endTime > 0 && slot.endTime < 10000000000L) {
                                    slot.endTime *= 1000;
                                }
                                slot.resvStatus = resv.optInt("resvStatus");
                                seat.reservations.add(slot);
                            }
                        }
                        result.add(seat);
                    }
                }
                Log.d(TAG, "获取到 " + result.size() + " 个座位数据");
            } else {
                Log.e(TAG, "API返回错误 code=" + json.optInt("code", -1) + " msg=" + json.optString("message", ""));
            }
        } catch (Exception e) {
            Log.e(TAG, "获取座位数据出错: " + e.getMessage(), e);
        }
        return result;
    }

    public List<SeatInfo> getSeatsDataByArea(String areaName, int daysOffset) {
        Constants.SeatArea area = Constants.getAreaByName(areaName);
        if (area == null) {
            Log.e(TAG, "无效的区域名称: " + areaName);
            return new ArrayList<>();
        }
        String dateStr = DateUtils.getDateStringCompact(daysOffset);
        return getSeatsData(area.roomId, dateStr);
    }

    public AreaStats getAreaStats(String areaName, int daysOffset) {
        AreaStats stats = new AreaStats(areaName);
        List<SeatInfo> seats = getSeatsDataByArea(areaName, daysOffset);
        stats.total = seats.size();
        for (SeatInfo seat : seats) {
            if (seat.isAvailable()) {
                stats.available++;
            } else {
                stats.occupied++;
            }
        }
        if (stats.total > 0) {
            stats.rate = (float) stats.occupied / stats.total * 100;
        }
        return stats;
    }

    public List<AreaStats> getAllAreasStats(int daysOffset) {
        List<AreaStats> result = new ArrayList<>();
        for (Constants.SeatArea area : Constants.SEAT_AREAS) {
            AreaStats stats = getAreaStats(area.name, daysOffset);
            result.add(stats);
        }
        return result;
    }

    public List<SeatInfo> findAvailableSeats(String areaName, int daysOffset,
            long startTime, long endTime, int limit) {
        List<SeatInfo> result = new ArrayList<>();
        List<SeatInfo> seats = getSeatsDataByArea(areaName, daysOffset);
        for (SeatInfo seat : seats) {
            if (isSeatAvailable(seat, startTime, endTime)) {
                result.add(seat);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        Log.d(TAG, "在 " + areaName + " 找到 " + result.size() + " 个可用座位");
        return result;
    }

    private boolean isSeatAvailable(SeatInfo seat, long startTime, long endTime) {
        for (ReservationSlot slot : seat.reservations) {
            if (!(endTime <= slot.startTime || startTime >= slot.endTime)) {
                return false;
            }
        }
        return true;
    }

    public List<SeatInfo> findAvailableSeats(String areaName, String dateStr,
            String startTimeStr, String endTimeStr, int limit) {
        long startTime = parseTimeToMillis(dateStr, startTimeStr);
        long endTime = parseTimeToMillis(dateStr, endTimeStr);
        int daysOffset = dateStr.equals(DateUtils.getTodayDate()) ? 0 : 1;
        return findAvailableSeats(areaName, daysOffset, startTime, endTime, limit);
    }

    private long parseTimeToMillis(String dateStr, String timeStr) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date date = sdf.parse(dateStr + " " + DateUtils.normalizeTimeFormat(timeStr));
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}