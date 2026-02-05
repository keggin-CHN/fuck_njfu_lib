package com.keggin.fucknjfulib.reservation;

import android.util.Log;

import com.keggin.fucknjfulib.network.HttpClientManager;
import com.keggin.fucknjfulib.utils.Constants;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import okhttp3.Response;

/**
 * 图书馆实时人数查询
 * 按需查询，不做后台监控
 */
public class TrafficQuery {
    
    private static final String TAG = "TrafficQuery";
    
    // 图书馆人数查询API（直接访问，不需要登录）
    private static final String TRAFFIC_API_URL = "https://libseat.njfu.edu.cn/ic-web/heatMap?vpn-12-libseat.njfu.edu.cn";
    
    /**
     * 查询结果
     */
    public static class TrafficInfo {
        public int currentCount;      // 当前在馆人数
        public int totalCapacity;     // 总容量
        public float occupancyRate;   // 占用率（百分比）
        public String updateTime;     // 更新时间
        public boolean success;
        public String errorMessage;
        
        public TrafficInfo() {
            this.totalCapacity = Constants.LIBRARY_TOTAL_CAPACITY;
        }
    }
    
    /**
     * 查询当前图书馆人数
     * 这个接口可能不需要认证，直接访问
     */
    public static TrafficInfo queryCurrentTraffic() {
        TrafficInfo info = new TrafficInfo();
        
        try {
            HttpClientManager httpClient = HttpClientManager.getInstance();
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json, text/plain, */*");
            headers.put("Referer", "https://libseat.njfu.edu.cn/");
            
            Response response = httpClient.get(TRAFFIC_API_URL, headers);
            
            if (response == null || !response.isSuccessful()) {
                info.success = false;
                info.errorMessage = "请求失败";
                if (response != null) {
                    response.close();
                }
                return info;
            }
            
            String body = HttpClientManager.getResponseBody(response);
            if (body == null) {
                info.success = false;
                info.errorMessage = "响应为空";
                return info;
            }
            
            // 解析响应
            JSONObject json = new JSONObject(body);
            
            if (json.getInt("code") == 0) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    // 实际的数据结构可能需要根据API调整
                    info.currentCount = data.optInt("num", 0);
                    info.success = true;
                    
                    // 计算占用率
                    if (info.totalCapacity > 0) {
                        info.occupancyRate = (float) info.currentCount / info.totalCapacity * 100;
                    }
                    
                    Log.d(TAG, "查询成功: 当前 " + info.currentCount + " 人");
                } else {
                    info.success = false;
                    info.errorMessage = "数据格式异常";
                }
            } else {
                info.success = false;
                info.errorMessage = json.optString("message", "查询失败");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "查询图书馆人数出错: " + e.getMessage(), e);
            info.success = false;
            info.errorMessage = "查询出错: " + e.getMessage();
        }
        
        return info;
    }
    
    /**
     * 获取占用率描述文字
     */
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
    
    /**
     * 获取占用率对应的颜色资源ID
     */
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