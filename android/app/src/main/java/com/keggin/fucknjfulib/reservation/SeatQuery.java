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

/**
 * 座位查询模块
 * 查询各区域座位实时状态
 */
public class SeatQuery {
    
    private static final String TAG = "SeatQuery";
    
    private AuthManager authManager;
    private final HttpClientManager httpClient;
    
    /**
     * 默认构造器（需要后续设置AuthManager）
     */
    public SeatQuery() {
        this.httpClient = HttpClientManager.getInstance();
    }
    
    public SeatQuery(AuthManager authManager) {
        this.authManager = authManager;
        this.httpClient = HttpClientManager.getInstance();
    }
    
    public void setAuthManager(AuthManager authManager) {
        this.authManager = authManager;
    }
    
    /**
     * 座位信息
     */
    public static class SeatInfo {
        public int devId;
        public String devName;
        public int devStatus;
        public List<ReservationSlot> reservations = new ArrayList<>();
        
        public boolean isAvailable() {
            return reservations.isEmpty();
        }
    }
    
    /**
     * 预约时间段
     */
    public static class ReservationSlot {
        public long startTime;
        public long endTime;
        public int resvStatus;
        
        public String getStatusText() {
            switch (resvStatus) {
                case 1027: return "预约中";
                case 1093: return "使用中";
                default: return "未知";
            }
        }
    }
    
    /**
     * 区域统计信息
     */
    public static class AreaStats {
        public String areaName;
        public int total;
        public int available;
        public int occupied;
        public float rate;  // 占用率
        
        public AreaStats(String areaName) {
            this.areaName = areaName;
        }
    }
    
    /**
     * 查询结果（供UI层使用）
     */
    public static class QueryResult {
        public boolean success;
        public String message;
        public int totalCount;
        public int availableCount;
        public Set<Integer> availableSeatIds;
        
        public QueryResult(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.availableSeatIds = new HashSet<>();
        }
    }
    
    /**
     * 查询座位（供UI层使用）
     */
    public QueryResult querySeats(String token, Constants.AreaInfo areaInfo, String dateStr) {
        QueryResult result = new QueryResult(false, "未知错误");
        
        if (areaInfo == null) {
            result.message = "区域信息无效";
            return result;
        }
        
        // 将日期格式从 yyyy-MM-dd 转换为 yyyyMMdd
        String compactDate = dateStr.replace("-", "");
        
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
        
        for (SeatInfo seat : seats) {
            if (seat.isAvailable()) {
                result.availableCount++;
                result.availableSeatIds.add(seat.devId);
            }
        }
        
        return result;
    }
    
    /**
     * 获取指定区域的座位数据
     */
    public List<SeatInfo> getSeatsData(int roomId, String dateStr) {
        List<SeatInfo> result = new ArrayList<>();

        // 强制重新认证
        if (authManager != null && !authManager.refreshAuth()) {
            Log.e(TAG, "获取座位数据失败: 认证失败 - " + authManager.getErrorMessage());
            return result;
        }
        
        String token = authManager != null ? authManager.getToken() : null;
        if (token == null) {
            Log.e(TAG, "获取座位数据失败: token 无效");
            return result;
        }
        
        try {
            String url = ApiConstants.getSeatQueryUrl()
                    + "?vpn-12-libseat.njfu.edu.cn="
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
            
            if (!response.isSuccessful()) {
                Log.e(TAG, "获取座位数据失败，状态码: " + response.code());
                response.close();
                return result;
            }
            
            String body = HttpClientManager.getResponseBody(response);
            if (body == null) {
                return result;
            }
            
            JSONObject json = new JSONObject(body);
            
            if (json.getInt("code") == 0) {
                JSONArray data = json.optJSONArray("data");
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        SeatInfo seat = new SeatInfo();
                        seat.devId = item.optInt("devId");
                        seat.devName = item.optString("devName");
                        seat.devStatus = item.optInt("devStatus");
                        
                        // 解析预约信息
                        JSONArray resvInfo = item.optJSONArray("resvInfo");
                        if (resvInfo != null) {
                            for (int j = 0; j < resvInfo.length(); j++) {
                                JSONObject resv = resvInfo.getJSONObject(j);
                                ReservationSlot slot = new ReservationSlot();
                                slot.startTime = resv.optLong("startTime");
                                slot.endTime = resv.optLong("endTime");
                                slot.resvStatus = resv.optInt("resvStatus");
                                seat.reservations.add(slot);
                            }
                        }
                        
                        result.add(seat);
                    }
                }
                Log.d(TAG, "获取到 " + result.size() + " 个座位数据");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "获取座位数据出错: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 获取指定区域的座位数据（按区域名称）
     */
    public List<SeatInfo> getSeatsDataByArea(String areaName, int daysOffset) {
        Constants.SeatArea area = Constants.getAreaByName(areaName);
        if (area == null) {
            Log.e(TAG, "无效的区域名称: " + areaName);
            return new ArrayList<>();
        }
        
        String dateStr = DateUtils.getDateStringCompact(daysOffset);
        return getSeatsData(area.roomId, dateStr);
    }
    
    /**
     * 获取区域统计信息
     */
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
    
    /**
     * 获取所有区域的统计信息
     */
    public List<AreaStats> getAllAreasStats(int daysOffset) {
        List<AreaStats> result = new ArrayList<>();
        
        for (Constants.SeatArea area : Constants.SEAT_AREAS) {
            AreaStats stats = getAreaStats(area.name, daysOffset);
            result.add(stats);
        }
        
        return result;
    }
    
    /**
     * 查找指定区域在指定时间段内可用的座位
     * @param areaName 区域名称
     * @param daysOffset 日期偏移（0=今天，1=明天）
     * @param startTime 开始时间（毫秒）
     * @param endTime 结束时间（毫秒）
     * @param limit 最多返回多少个
     * @return 可用座位列表
     */
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
    
    /**
     * 检查座位在指定时间段是否可用
     */
    private boolean isSeatAvailable(SeatInfo seat, long startTime, long endTime) {
        for (ReservationSlot slot : seat.reservations) {
            // 检查是否有时间冲突
            if (!(endTime <= slot.startTime || startTime >= slot.endTime)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 根据时间字符串查找可用座位
     */
    public List<SeatInfo> findAvailableSeats(String areaName, String dateStr,
                                              String startTimeStr, String endTimeStr, int limit) {
        // 转换时间字符串为毫秒
        long startTime = parseTimeToMillis(dateStr, startTimeStr);
        long endTime = parseTimeToMillis(dateStr, endTimeStr);
        
        int daysOffset = dateStr.equals(DateUtils.getTodayDate()) ? 0 : 1;
        
        return findAvailableSeats(areaName, daysOffset, startTime, endTime, limit);
    }
    
    /**
     * 解析时间字符串为毫秒时间戳
     */
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