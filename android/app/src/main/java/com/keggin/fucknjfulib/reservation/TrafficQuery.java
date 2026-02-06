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
    private static final String TRAFFIC_API_URL = "https:
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
            HttpClientManager httpClient = HttpClientManager.getInstance();
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json, text/plain, *
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