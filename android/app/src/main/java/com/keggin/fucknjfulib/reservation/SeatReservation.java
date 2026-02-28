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
import java.util.List;
import java.util.Map;
import okhttp3.Response;
public class SeatReservation {
    private static final String TAG = "SeatReservation";
    private final AuthManager authManager;
    private final HttpClientManager httpClient;
    public SeatReservation(AuthManager authManager) {
        this.authManager = authManager;
        this.httpClient = HttpClientManager.getInstance(null);
    }
    public static class ReservationResult {
        public boolean success;
        public String message;
        public String uuid;
        public ReservationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        public ReservationResult(boolean success, String message, String uuid) {
            this.success = success;
            this.message = message;
            this.uuid = uuid;
        }
    }
    public static class ReserveResult {
        public boolean success;
        public String message;
        public String uuid;
        public ReserveResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        public ReserveResult(boolean success, String message, String uuid) {
            this.success = success;
            this.message = message;
            this.uuid = uuid;
        }
    }
    public static class OperationResult {
        public boolean success;
        public String message;
        public OperationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
    public static class ReservationInfo {
        public boolean hasReservation = false;
        public String uuid;
        public String resvId;  
        public String areaName;
        public String seatLabel;
        public String seatName;
        public int devId;
        public String startTime;
        public String endTime;
        public long beginTime;
        public long endTimestamp;
        public String state;
        public String statusName;
        @Override
        public String toString() {
            return "ReservationInfo{" +
                    "seatName='" + seatName + '\'' +
                    ", startTime='" + startTime + '\'' +
                    ", endTime='" + endTime + '\'' +
                    ", state='" + state + '\'' +
                    '}';
        }
    }
    public ReserveResult reserveSeat(String areaName, int seatNumber, 
                                     String dateStr, String startTime, String endTime) {
        Constants.SeatArea area = Constants.getAreaByName(areaName);
        if (area == null) {
            return new ReserveResult(false, "无效的区域: " + areaName);
        }
        int seatId = area.getSeatId(seatNumber);
        if (seatId < 0) {
            return new ReserveResult(false, "无效的座位号: " + seatNumber);
        }
        if (dateStr == null) {
            dateStr = DateUtils.getTomorrowDate();
        }
        startTime = DateUtils.normalizeTimeFormat(startTime);
        endTime = DateUtils.normalizeTimeFormat(endTime);
        if (!DateUtils.isValidDuration(startTime, endTime, 2)) {
            return new ReserveResult(false, "预约时长必须至少2小时");
        }
        String beginTime = dateStr + " " + startTime;
        String fullEndTime = dateStr + " " + endTime;
        return doReserve(seatId, beginTime, fullEndTime);
    }
    public ReservationResult reserveSeat(String token, String accNo, Constants.AreaInfo areaInfo,
                                         int seatNumber, String startTime, String endTime, String dateStr) {
        if (areaInfo == null) {
            return new ReservationResult(false, "区域信息无效");
        }
        if (dateStr == null) {
            dateStr = DateUtils.getTomorrowDate();
        }
        if (seatNumber < 1 || seatNumber > areaInfo.seatCount) {
            return new ReservationResult(false, "座位号无效");
        }
        int seatId = areaInfo.seatIds[seatNumber - 1];
        startTime = DateUtils.normalizeTimeFormat(startTime);
        endTime = DateUtils.normalizeTimeFormat(endTime);
        String beginTime = dateStr + " " + startTime;
        String fullEndTime = dateStr + " " + endTime;
        ReserveResult result = doReserve(seatId, beginTime, fullEndTime);
        return new ReservationResult(result.success, result.message, result.uuid);
    }
    private ReserveResult doReserve(int seatId, String beginTime, String endTime) {
        if (!authManager.refreshAuth()) {
            return new ReserveResult(false, "认证失败: " + authManager.getErrorMessage());
        }
        String token = authManager.getToken();
        String accNo = authManager.getAccNo();
        if (token == null || accNo == null) {
            return new ReserveResult(false, "认证信息无效，请重新登录");
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("sysKind", 8);
            payload.put("appAccNo", accNo);
            payload.put("memberKind", 1);
            payload.put("resvMember", new JSONArray().put(accNo));
            payload.put("resvBeginTime", beginTime);
            payload.put("resvEndTime", endTime);
            payload.put("resvDev", new JSONArray().put(seatId));
            payload.put("resvProperty", 0);
            payload.put("memo", "");
            payload.put("captcha", "");
            payload.put("testName", "");
            Map<String, String> headers = new HashMap<>();
            headers.put("token", token);
            headers.put("lan", "1");
            Log.d(TAG, "发起预约请求: seatId=" + seatId + ", time=" + beginTime + " ~ " + endTime);
            Response response = httpClient.postJson(ApiConstants.getReserveUrl(), 
                    payload.toString(), headers);
            if (!response.isSuccessful()) {
                String msg = "预约请求失败，状态码: " + response.code();
                response.close();
                return new ReserveResult(false, msg);
            }
            String body = HttpClientManager.getResponseBody(response);
            if (body == null) {
                return new ReserveResult(false, "预约响应为空");
            }
            JSONObject json = new JSONObject(body);
            if (json.getInt("code") == 0) {
                String uuid = null;
                if (json.has("data")) {
                    JSONObject data = json.getJSONObject("data");
                    uuid = data.optString("uuid", null);
                }
                String message = json.optString("message", "预约成功");
                Log.d(TAG, "预约成功: " + message);
                return new ReserveResult(true, message, uuid);
            } else {
                String errorMsg = json.optString("message", "预约失败");
                Log.e(TAG, "预约失败: " + errorMsg);
                return new ReserveResult(false, errorMsg);
            }
        } catch (Exception e) {
            Log.e(TAG, "预约过程出错: " + e.getMessage(), e);
            return new ReserveResult(false, "预约过程出错: " + e.getMessage());
        }
    }
    public ReserveResult cancelReservation(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return new ReserveResult(false, "预约UUID无效");
        }
        if (!authManager.refreshAuth()) {
            return new ReserveResult(false, "认证失败: " + authManager.getErrorMessage());
        }
        String token = authManager.getToken();
        if (token == null) {
            return new ReserveResult(false, "认证信息无效，请重新登录");
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("uuid", uuid);
            Map<String, String> headers = new HashMap<>();
            headers.put("token", token);
            headers.put("lan", "1");
            String url = ApiConstants.getCancelReserveUrl() + "?vpn-12-libseat.njfu.edu.cn=";
            Response response = httpClient.postJson(url, payload.toString(), headers);
            if (!response.isSuccessful()) {
                String msg = "取消预约请求失败，状态码: " + response.code();
                response.close();
                return new ReserveResult(false, msg);
            }
            String body = HttpClientManager.getResponseBody(response);
            if (body == null) {
                return new ReserveResult(false, "取消预约响应为空");
            }
            JSONObject json = new JSONObject(body);
            if (json.getInt("code") == 0) {
                Log.d(TAG, "取消预约成功");
                return new ReserveResult(true, "取消预约成功");
            } else {
                String errorMsg = json.optString("message", "取消预约失败");
                Log.e(TAG, "取消预约失败: " + errorMsg);
                return new ReserveResult(false, errorMsg);
            }
        } catch (Exception e) {
            Log.e(TAG, "取消预约过程出错: " + e.getMessage(), e);
            return new ReserveResult(false, "取消预约过程出错: " + e.getMessage());
        }
    }
    public OperationResult cancelReservation(String token, String accNo, String resvId) {
        ReserveResult result = cancelReservation(resvId);
        return new OperationResult(result.success, result.message);
    }
    public ReservationInfo getCurrentReservation(String token, String accNo) {
        ReservationInfo info = new ReservationInfo();
        try {
            List<ReservationInfo> reservations = getTodayAndTomorrowReservations();
            if (!reservations.isEmpty()) {
                ReservationInfo first = reservations.get(0);
                first.hasReservation = true;
                first.resvId = first.uuid;  
                return first;
            }
        } catch (Exception e) {
            Log.e(TAG, "获取当前预约失败: " + e.getMessage());
        }
        info.hasReservation = false;
        return info;
    }
    public OperationResult signIn(String token, String accNo, String resvId) {
        return performAction(resvId, "in");
    }
    public OperationResult signOut(String token, String accNo, String resvId) {
        return performAction(resvId, "over");
    }
    public OperationResult away(String resvId) {
        return performAction(resvId, "away");
    }
    public OperationResult back(String resvId) {
        return performAction(resvId, "back");
    }
    private OperationResult performAction(String resvId, String action) {
        if (!authManager.refreshAuth()) {
            return new OperationResult(false, "认证失败: " + authManager.getErrorMessage());
        }
        String token = authManager.getToken();
        if (token == null) {
            return new OperationResult(false, "认证信息无效");
        }
        try {
            String url = ApiConstants.BASE_URL + "/wengine-vpn/443/https/libseat.njfu.edu.cn/ic-web/reserve/"
                    + action + "?vpn-12-libseat.njfu.edu.cn=";
            JSONObject payload = new JSONObject();
            payload.put("uuid", resvId);
            Map<String, String> headers = new HashMap<>();
            headers.put("token", token);
            headers.put("lan", "1");
            Response response = httpClient.postJson(url, payload.toString(), headers);
            if (!response.isSuccessful()) {
                response.close();
                return new OperationResult(false, "操作失败，状态码: " + response.code());
            }
            String body = HttpClientManager.getResponseBody(response);
            if (body == null) {
                return new OperationResult(false, "响应为空");
            }
            JSONObject json = new JSONObject(body);
            if (json.getInt("code") == 0) {
                return new OperationResult(true, "操作成功");
            } else {
                return new OperationResult(false, json.optString("message", "操作失败"));
            }
        } catch (Exception e) {
            Log.e(TAG, "操作失败: " + e.getMessage(), e);
            return new OperationResult(false, "操作失败: " + e.getMessage());
        }
    }
    public List<ReservationInfo> getReservations(String beginDate, String endDate) {
        List<ReservationInfo> result = new ArrayList<>();
        if (!authManager.refreshAuth()) {
            Log.e(TAG, "获取预约列表失败: 认证失败 - " + authManager.getErrorMessage());
            return result;
        }
        String token = authManager.getToken();
        if (token == null) {
            Log.e(TAG, "获取预约列表失败: token 无效");
            return result;
        }
        try {
            String url = ApiConstants.getReservationInfoUrl() 
                    + "?vpn-12-libseat.njfu.edu.cn="
                    + "&needStatus=8454"
                    + "&unneedStatus=128"
                    + "&beginDate=" + beginDate
                    + "&endDate=" + endDate;
            Map<String, String> headers = new HashMap<>();
            headers.put("token", token);
            headers.put("lan", "1");
            headers.put("Accept", ApiConstants.ACCEPT_JSON);
            Response response = httpClient.get(url, headers);
            if (!response.isSuccessful()) {
                Log.e(TAG, "获取预约列表失败，状态码: " + response.code());
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
                        ReservationInfo info = new ReservationInfo();
                        info.uuid = item.optString("uuid");
                        info.resvId = info.uuid;
                        info.beginTime = item.optLong("resvBeginTime");
                        info.endTimestamp = item.optLong("resvEndTime");
                        info.statusName = item.optString("statusName");
                        info.state = convertStatusToState(info.statusName);
                        info.startTime = DateUtils.formatTimestampToTime(info.beginTime);
                        info.endTime = DateUtils.formatTimestampToTime(info.endTimestamp);
                        JSONArray devInfoList = item.optJSONArray("resvDevInfoList");
                        if (devInfoList != null && devInfoList.length() > 0) {
                            JSONObject devInfo = devInfoList.getJSONObject(0);
                            info.seatName = devInfo.optString("devName");
                            info.devId = devInfo.optInt("devId");
                            String[] areaAndSeat = Constants.getAreaAndSeatNumber(info.devId);
                            if (areaAndSeat != null) {
                                info.areaName = areaAndSeat[0];
                                info.seatLabel = areaAndSeat[1];
                            } else {
                                info.areaName = info.seatName;
                                info.seatLabel = String.valueOf(info.devId);
                            }
                        }
                        info.hasReservation = true;
                        result.add(info);
                    }
                }
                Log.d(TAG, "获取到 " + result.size() + " 条预约记录");
            }
        } catch (Exception e) {
            Log.e(TAG, "获取预约列表出错: " + e.getMessage(), e);
        }
        return result;
    }
    private String convertStatusToState(String statusName) {
        if (statusName == null) return null;
        if (statusName.contains("预约") && !statusName.contains("使用")) {
            return "RESERVE";
        } else if (statusName.contains("使用") || statusName.contains("签到")) {
            return "CHECK_IN";
        } else if (statusName.contains("暂离")) {
            return "AWAY";
        } else if (statusName.contains("迟到")) {
            return "LATE";
        }
        return statusName;
    }
    public List<ReservationInfo> getTodayReservations() {
        String today = DateUtils.getTodayDate();
        return getReservations(today, today);
    }
    public List<ReservationInfo> getTodayAndTomorrowReservations() {
        String today = DateUtils.getTodayDate();
        String tomorrow = DateUtils.getTomorrowDate();
        return getReservations(today, tomorrow);
    }
    public ReserveResult reserveTodaySeat(String areaName, int seatNumber, 
                                          String startTime, String endTime) {
        return reserveSeat(areaName, seatNumber, DateUtils.getTodayDate(), startTime, endTime);
    }
    public ReserveResult reserveTomorrowSeat(String areaName, int seatNumber, 
                                              String startTime, String endTime) {
        return reserveSeat(areaName, seatNumber, DateUtils.getTomorrowDate(), startTime, endTime);
    }
}