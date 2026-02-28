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
    private static final String TRAFFIC_API_URL = "https://libseat.njfu.edu.cn/api.php/spaces/10/stats/today";
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
    public static TrafficInfo queryCurrentTraffic() {
        TrafficInfo info = new TrafficInfo();
        try {
            HttpClientManager httpClient = HttpClientManager.getInstance(null);
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json, text/plain, */*");
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            
            Response response = httpClient.get(TRAFFIC_API_URL, headers);
            if (response != null && response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JSONObject jsonObject = new JSONObject(responseBody);
                
                if (jsonObject.has("data")) {
                    JSONObject data = jsonObject.getJSONObject("data");
                    info.currentCount = data.optInt("checkedIn", 0);
                    info.occupancyRate = (float) info.currentCount / info.totalCapacity * 100;
                    info.updateTime = data.optString("updateTime", "");
                    info.success = true;
                } else {
                    info.success = false;
                    info.errorMessage = "数据格式错误";
                }
                response.close();
            } else {
                info.success = false;
                info.errorMessage = "网络请求失败";
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