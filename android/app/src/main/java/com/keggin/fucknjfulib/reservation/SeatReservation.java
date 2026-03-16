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
import com.keggin.fucknjfulib.utils.ProgressListener;

public class SeatReservation {
    private static final String TAG = "SeatReservation";
    private final AuthManager authManager;
    private final HttpClientManager httpClient;
    private ProgressListener progressListener;

    public SeatReservation(AuthManager authManager) {
        this.authManager = authManager;
        this.httpClient = HttpClientManager.getInstance(null);
    }
    
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
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
        public int resvIdInt;           // 整数形式的预约ID（seatOperation API需要）
        public String areaName;
        public String seatLabel;
        public String seatName;
        public int devId;
        public String onDate;
        public String startTime;
        public String endTime;
        public long beginTime;
        public long endTimestamp;
        public String state;
        public String statusName;
        public int resvStatus;          // 原始状态码
        public boolean canEndEarly;     // 是否可以提前结束
        public int tempLeaveEndTime;    // 暂离倒计时分钟数
        public long latestCheckInTime;  // 最晚签到时间戳（毫秒）

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
        if (!DateUtils.isValidDuration(startTime, endTime, 2)) {
            return new ReservationResult(false, "预约时长必须至少2小时");
        }
        String beginTime = dateStr + " " + startTime;
        String fullEndTime = dateStr + " " + endTime;
        ReserveResult result = doReserve(seatId, beginTime, fullEndTime);
        return new ReservationResult(result.success, result.message, result.uuid);
    }

    private ReserveResult doReserve(int seatId, String beginTime, String endTime) {
        if (!authManager.ensureLoggedIn()) {
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

            if (response.code() == 302 || response.code() == 301) {
                Log.w(TAG, "WebVPN session Expired (302) in doReserve, forcing re-auth...");
                response.close();
                if (authManager.refreshAuth()) {
                    token = authManager.getToken();
                    headers.put("token", token);
                    response = httpClient.postJson(ApiConstants.getReserveUrl(), payload.toString(), headers);
                } else {
                    return new ReserveResult(false, "重新认证失败: " + authManager.getErrorMessage());
                }
            }

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
        if (!authManager.ensureLoggedIn()) {
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
            String url = ApiConstants.getCancelReserveUrl() + "?vpn-12-libseat.njfu.edu.cn";
            Response response = httpClient.postJson(url, payload.toString(), headers);

            if (response.code() == 302 || response.code() == 301) {
                Log.w(TAG, "WebVPN session Expired (302) in cancelReservation, forcing re-auth...");
                response.close();
                if (authManager.refreshAuth()) {
                    token = authManager.getToken();
                    headers.put("token", token);
                    response = httpClient.postJson(url, payload.toString(), headers);
                } else {
                    return new ReserveResult(false, "重新认证失败: " + authManager.getErrorMessage());
                }
            }

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

    /**
     * 暂时离开 — 使用 /ic-web/seatOperation/tempLeave
     * @param resvIdInt 整数形式的预约ID
     */
    public OperationResult tempLeave(int resvIdInt) {
        return performSeatOperation("tempLeave", "resvId", resvIdInt);
    }

    /**
     * 返回座位 — 使用 /ic-web/seatOperation/back
     * @param resvIdInt 整数形式的预约ID
     */
    public OperationResult backFromLeave(int resvIdInt) {
        return performSeatOperation("back", "resvId", resvIdInt);
    }

    /**
     * 提前结束 — 使用 /ic-web/reserve/endAhaed
     * @param uuid 字符串形式的预约UUID
     */
    public OperationResult endAhead(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return new OperationResult(false, "预约UUID无效");
        }
        if (!authManager.ensureLoggedIn()) {
            return new OperationResult(false, "认证失败: " + authManager.getErrorMessage());
        }
        String token = authManager.getToken();
        if (token == null) {
            return new OperationResult(false, "认证信息无效");
        }
        try {
            String url = ApiConstants.getEndAheadUrl();
            JSONObject payload = new JSONObject();
            payload.put("uuid", uuid);
            Map<String, String> headers = new HashMap<>();
            headers.put("token", token);
            headers.put("lan", "1");
            Response response = httpClient.postJson(url, payload.toString(), headers);

            if (response.code() == 302 || response.code() == 301) {
                Log.w(TAG, "WebVPN session Expired (302) in endAhead, forcing re-auth...");
                response.close();
                if (authManager.refreshAuth()) {
                    token = authManager.getToken();
                    headers.put("token", token);
                    response = httpClient.postJson(url, payload.toString(), headers);
                } else {
                    return new OperationResult(false, "重新认证失败: " + authManager.getErrorMessage());
                }
            }

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
                return new OperationResult(true, json.optString("message", "操作成功"));
            } else {
                return new OperationResult(false, json.optString("message", "操作失败"));
            }
        } catch (Exception e) {
            Log.e(TAG, "提前结束操作失败: " + e.getMessage(), e);
            return new OperationResult(false, "操作失败: " + e.getMessage());
        }
    }

    /**
     * 通用 seatOperation 调用（tempLeave、back 等）
     */
    private OperationResult performSeatOperation(String action, String idKey, int idValue) {
        if (!authManager.ensureLoggedIn()) {
            return new OperationResult(false, "认证失败: " + authManager.getErrorMessage());
        }
        String token = authManager.getToken();
        if (token == null) {
            return new OperationResult(false, "认证信息无效");
        }
        try {
            String url = ApiConstants.getSeatOperationUrl(action);
            JSONObject payload = new JSONObject();
            payload.put(idKey, idValue);
            Map<String, String> headers = new HashMap<>();
            headers.put("token", token);
            headers.put("lan", "1");
            Response response = httpClient.postJson(url, payload.toString(), headers);

            if (response.code() == 302 || response.code() == 301) {
                Log.w(TAG, "WebVPN session Expired (302) in seatOperation/" + action + ", forcing re-auth...");
                response.close();
                if (authManager.refreshAuth()) {
                    token = authManager.getToken();
                    headers.put("token", token);
                    response = httpClient.postJson(url, payload.toString(), headers);
                } else {
                    return new OperationResult(false, "重新认证失败: " + authManager.getErrorMessage());
                }
            }

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
                return new OperationResult(true, json.optString("message", "操作成功"));
            } else {
                return new OperationResult(false, json.optString("message", "操作失败"));
            }
        } catch (Exception e) {
            Log.e(TAG, "seatOperation/" + action + " 失败: " + e.getMessage(), e);
            return new OperationResult(false, "操作失败: " + e.getMessage());
        }
    }


    private String resolveActionUrl(String action, String token, String resvId) {
        // 主路径（当前实现）
        String primary = ApiConstants.getReserveActionUrl(action);
        // 备选路径（你日志里历史出现过）
        String legacy = ApiConstants.BASE_URL + "/wengine-vpn/443/https/libseat.njfu.edu.cn/ic-web/reserve/"
                + action + "?vpn-12-libseat.njfu.edu.cn";

        String[] candidates = new String[] { primary, legacy };
        for (String url : candidates) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("uuid", resvId);
                Map<String, String> headers = new HashMap<>();
                headers.put("token", token);
                headers.put("lan", "1");
                Response r = httpClient.postJson(url, payload.toString(), headers);
                int c = r.code();
                String body = HttpClientManager.getResponseBody(r);
                // 404 明确说明路径错误；其余码认为路径可达（权限/状态另说）
                if (c != 404) {
                    Log.i(TAG, "Action endpoint selected for " + action + ": " + url + " (code=" + c + ")");
                    return url;
                }
                Log.w(TAG, "Action endpoint 404 for " + action + ": " + url + " body=" + body);
            } catch (Exception e) {
                Log.w(TAG, "Action endpoint probe failed for " + action + ": " + url + " err=" + e.getMessage());
            }
        }
        // 两个都404则仍返回主路径，便于统一报错
        return primary;
    }

    private OperationResult performAction(String resvId, String action) {
        if (!authManager.ensureLoggedIn()) {
            return new OperationResult(false, "认证失败: " + authManager.getErrorMessage());
        }
        String token = authManager.getToken();
        if (token == null) {
            return new OperationResult(false, "认证信息无效");
        }
        try {
            String url = resolveActionUrl(action, token, resvId);
            JSONObject payload = new JSONObject();
            payload.put("uuid", resvId);
            Map<String, String> headers = new HashMap<>();
            headers.put("token", token);
            headers.put("lan", "1");
            Response response = httpClient.postJson(url, payload.toString(), headers);

            if (response.code() == 302 || response.code() == 301) {
                Log.w(TAG, "WebVPN session Expired (302) in performAction, forcing re-auth...");
                response.close();
                if (authManager.refreshAuth()) {
                    token = authManager.getToken();
                    headers.put("token", token);
                    response = httpClient.postJson(url, payload.toString(), headers);
                } else {
                    return new OperationResult(false, "重新认证失败: " + authManager.getErrorMessage());
                }
            }

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
        if (!authManager.ensureLoggedIn()) {
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
                    + "?vpn-12-libseat.njfu.edu.cn"
                    + "&beginDate=" + beginDate
                    + "&endDate=" + endDate
                    + "&needStatus=6"
                    + "&page=1"
                    + "&pageNum=10"
                    + "&orderKey=gmt_create"
                    + "&orderModel=desc";
            Map<String, String> headers = new HashMap<>();
            headers.put("token", token);
            headers.put("lan", "1");
            headers.put("Accept", ApiConstants.ACCEPT_JSON);
            Response response = httpClient.get(url, headers);

            if (response.code() == 302 || response.code() == 301) {
                Log.w(TAG, "WebVPN session Expired (302) in getReservations, forcing re-auth...");
                response.close();
                if (authManager.refreshAuth()) {
                    token = authManager.getToken();
                    headers.put("token", token);
                    response = httpClient.get(url, headers);
                } else {
                    Log.e(TAG, "重新认证失败: " + authManager.getErrorMessage());
                    return result;
                }
            }

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
                        info.resvIdInt = item.optInt("resvId", 0);
                        info.beginTime = item.optLong("resvBeginTime");
                        info.endTimestamp = item.optLong("resvEndTime");
                        info.resvStatus = item.optInt("resvStatus", 0);
                        info.canEndEarly = item.optBoolean("endEarly", false);
                        info.tempLeaveEndTime = item.optInt("tempLeaveEndTime", 0);
                        info.latestCheckInTime = item.optLong("latestCheckInTime", 0);
                        info.statusName = item.optString("statusName", null);
                        info.state = convertStatusToState(info.statusName, info.resvStatus);
                        info.onDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                .format(new java.util.Date(info.beginTime));
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
        return convertStatusToState(statusName, 0);
    }

    private String convertStatusToState(String statusName, int resvStatus) {
        // 先用 resvStatus 数值判断（更可靠）
        if (resvStatus > 0) {
            // 1027 = 预约中, 1093 = 使用中(已签到), 3141 = 暂离(暂时离开)
            if (resvStatus == 1027) return "RESERVE";
            if (resvStatus == 1093) return "CHECK_IN";
            if (resvStatus == 3141) return "AWAY";
        }
        // 再用 statusName 文字判断
        if (statusName != null) {
            String s = statusName.trim();
            if (s.contains("暂离") || s.contains("离座") || s.contains("暂时离开")) {
                return "AWAY";
            } else if (s.contains("迟到")) {
                return "LATE";
            } else if (s.contains("未开始") || s.contains("待开始") || s.contains("预约中") || s.contains("待签到") || s.contains("未签到") || s.contains("预约")) {
                return "RESERVE";
            } else if (s.contains("使用") || s.contains("签到") || s.contains("在馆") || s.contains("入座") || s.contains("学习中") || s.contains("进行中")) {
                return "CHECK_IN";
            }
            if (!s.isEmpty()) return s;
        }
        return null;
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

    private void reportProgress(int percent, String message) {
        if (progressListener != null) {
            progressListener.onProgress(percent, message);
        }
    }
}