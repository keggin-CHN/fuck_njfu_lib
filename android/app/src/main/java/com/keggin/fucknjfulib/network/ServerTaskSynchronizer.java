package com.keggin.fucknjfulib.network;

import android.util.Log;

import com.keggin.fucknjfulib.storage.PreferenceManager;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Response;

public class ServerTaskSynchronizer {
    private static final String TAG = "ServerTaskSynchronizer";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void syncTaskToServer(PreferenceManager preferenceManager) {
        executor.execute(() -> {
            try {
                String serverUrl = preferenceManager.getServerApiUrl();
                String apiKey = preferenceManager.getApiKey();
                if (serverUrl == null || serverUrl.isEmpty()) {
                    Log.w(TAG, "未配置服务器地址，跳过同步");
                    return;
                }
                JSONObject body = new JSONObject();
                body.put("username", preferenceManager.getStudentId());
                body.put("edu_password", preferenceManager.getCasPassword());
                body.put("lib_password", preferenceManager.getLibPassword());
                body.put("area", preferenceManager.getAreaName(preferenceManager.getTargetArea()));
                body.put("seat_number", preferenceManager.getTargetSeat());
                body.put("start_time", preferenceManager.getStartTime());
                body.put("end_time", preferenceManager.getEndTime());
                body.put("auto_reserve", preferenceManager.isAutoReserveEnabled());
                body.put("prevent_late", preferenceManager.isLateProtectionEnabled());

                String weeklyPlanJson = preferenceManager.getWeeklyPlanTasksJson();
                if (weeklyPlanJson != null && !weeklyPlanJson.trim().isEmpty()) {
                    body.put("weekly_plan", new JSONObject(weeklyPlanJson));
                }

                HttpClientManager httpClient = HttpClientManager.getInstance(null);
                String url = serverUrl + "/api/task/register";
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                if (apiKey != null && !apiKey.isEmpty()) {
                    headers.put("X-API-Key", apiKey);
                }
                Response response = httpClient.postJson(url, body.toString(), headers);
                try {
                    if (response.isSuccessful()) {
                        String respBody = HttpClientManager.getResponseBody(response);
                        JSONObject result = new JSONObject(respBody);
                        String taskId = result.optString("task_id", "");
                        if (!taskId.isEmpty()) {
                            preferenceManager.setServerTaskId(taskId);
                        }
                        Log.i(TAG, "任务同步到服务器成功: " + taskId);
                    } else {
                        Log.e(TAG, "同步任务失败: HTTP " + response.code());
                    }
                } finally {
                    response.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "同步任务到服务器失败: " + e.getMessage());
            }
        });
    }

    public static void deleteServerTask(PreferenceManager preferenceManager) {
        executor.execute(() -> {
            try {
                String serverUrl = preferenceManager.getServerApiUrl();
                String apiKey = preferenceManager.getApiKey();
                String taskId = preferenceManager.getServerTaskId();
                if (serverUrl == null || serverUrl.isEmpty() || taskId == null || taskId.isEmpty()) {
                    Log.w(TAG, "未配置服务器或未注册任务，无需删除");
                    return;
                }

                HttpClientManager httpClient = HttpClientManager.getInstance(null);
                String url = serverUrl + "/api/task/" + taskId;
                Map<String, String> headers = new HashMap<>();
                if (apiKey != null && !apiKey.isEmpty()) {
                    headers.put("X-API-Key", apiKey);
                }
                Response response = httpClient.delete(url, headers);
                try {
                    if (response.isSuccessful()) {
                        Log.i(TAG, "服务器端任务已删除");
                        // 只需要清除 taskId
                        preferenceManager.setServerTaskId("");
                    } else {
                        Log.e(TAG, "删除服务器任务失败: HTTP " + response.code());
                    }
                } finally {
                    response.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "删除服务器任务失败: " + e.getMessage());
            }
        });
    }
}
