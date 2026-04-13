package com.keggin.fucknjfulib.activities;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.auth.AuthManager;
import com.keggin.fucknjfulib.network.ApiConstants;
import com.keggin.fucknjfulib.network.HttpClientManager;
import com.keggin.fucknjfulib.storage.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Response;
public class AccountInfoActivity extends AppCompatActivity {
    private TextView tvUserName, tvUserDept, tvStudentId, tvUserClass;
    private TextView tvPunishCount, tvPunishStatus, tvNoPunish;
    private LinearLayout layoutPunishList;
    private FrameLayout loadingOverlay;
    private ExecutorService executor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_info);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());
        tvUserName = findViewById(R.id.tvUserName);
        tvUserDept = findViewById(R.id.tvUserDept);
        tvStudentId = findViewById(R.id.tvStudentId);
        tvUserClass = findViewById(R.id.tvUserClass);
        tvPunishCount = findViewById(R.id.tvPunishCount);
        tvPunishStatus = findViewById(R.id.tvPunishStatus);
        tvNoPunish = findViewById(R.id.tvNoPunish);
        layoutPunishList = findViewById(R.id.layoutPunishList);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        executor = Executors.newSingleThreadExecutor();
        loadAccountInfo();
    }
    private void loadAccountInfo() {
        loadingOverlay.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                AuthManager auth = AuthManager.getInstance(this);
                if (!auth.ensureLoggedIn()) {
                    runOnUiThread(() -> {
                        loadingOverlay.setVisibility(View.GONE);
                        Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }
                String token = auth.getToken();
                HttpClientManager http = HttpClientManager.getInstance(null);
                Map<String, String> headers = new HashMap<>();
                headers.put("token", token);
                headers.put("lan", "1");
                headers.put("Accept", ApiConstants.ACCEPT_JSON);
                String userName = "", userDept = "";
                String studentId = new PreferenceManager(this).getStudentId();
                String userClass = "";
                Response userResp = http.get(ApiConstants.getUserInfoUrl(), headers);
                if (userResp.isSuccessful()) {
                    String body = HttpClientManager.getResponseBody(userResp);
                    if (body != null) {
                        JSONObject json = new JSONObject(body);
                        if (json.optInt("code") == 0) {
                            JSONObject data = json.optJSONObject("data");
                            if (data != null) {
                                userName = data.optString("trueName", "");
                                userDept = data.optString("deptName", "");
                                String pid = data.optString("pid", "");
                                if (!pid.isEmpty())
                                    studentId = pid;
                                userClass = data.optString("className", "");
                            }
                        }
                    }
                }
                int creditTotal = -1, creditRemain = -1;
                Response surplusResp = http.get(ApiConstants.getCreditSurplusUrl(), headers);
                if (surplusResp.isSuccessful()) {
                    String body = HttpClientManager.getResponseBody(surplusResp);
                    if (body != null) {
                        JSONObject json = new JSONObject(body);
                        if (json.optInt("code") == 0) {
                            JSONObject data = json.optJSONObject("data");
                            if (data != null) {
                                creditTotal = data.optInt("total8", -1);
                                creditRemain = data.optInt("8", -1);
                            }
                        }
                    }
                }
                JSONArray creditList = new JSONArray();
                Response creditResp = http.get(ApiConstants.getCreditRecUrl() + "&page=1&pageNum=20", headers);
                if (creditResp.isSuccessful()) {
                    String body = HttpClientManager.getResponseBody(creditResp);
                    if (body != null) {
                        JSONObject json = new JSONObject(body);
                        if (json.optInt("code") == 0) {
                            JSONArray arr = json.optJSONArray("data");
                            if (arr != null)
                                creditList = arr;
                        }
                    }
                }
                final String finalName = userName.isEmpty() ? studentId : userName;
                final String finalDept = userDept;
                final String finalStudentId = studentId;
                final String finalClass = userClass.isEmpty() ? "--" : userClass;
                final JSONArray finalList = creditList;
                final int finalTotal = creditTotal;
                final int finalRemain = creditRemain;
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    tvUserName.setText(finalName);
                    tvUserDept.setText(finalDept);
                    tvStudentId.setText(finalStudentId);
                    tvUserClass.setText(finalClass);
                    int count = finalList.length();
                    if (finalTotal >= 0) {
                        tvPunishCount.setText(finalRemain + " / " + finalTotal);
                    } else {
                        tvPunishCount.setText(count == 0 ? "正常" : "违约中");
                    }
                    if (count == 0) {
                        tvPunishStatus.setText("正常");
                        tvPunishStatus.setTextColor(0xFF388E3C);
                        tvNoPunish.setVisibility(View.VISIBLE);
                    } else {
                        tvPunishStatus.setText("违约中");
                        tvPunishStatus.setTextColor(0xFFE53935);
                        buildCreditRows(finalList);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private String getSemesterString(long timestamp) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        int year = cal.get(java.util.Calendar.YEAR);
        int month = cal.get(java.util.Calendar.MONTH) + 1;
        if (month >= 8) {
            return year + "-" + (year + 1) + "-1";
        } else if (month <= 1) {
            return (year - 1) + "-" + year + "-1";
        } else {
            return (year - 1) + "-" + year + "-2";
        }
    }
    private void buildCreditRows(JSONArray creditList) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        layoutPunishList.removeAllViews();
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < creditList.length(); i++) {
            JSONObject item = creditList.optJSONObject(i);
            if (item != null) list.add(item);
        }
        java.util.Collections.sort(list, (a, b) -> Long.compare(b.optLong("gmtCreate", 0), a.optLong("gmtCreate", 0)));
        String currentSemester = "";
        for (int i = 0; i < list.size(); i++) {
            try {
                JSONObject item = list.get(i);
                String kind = item.optString("creditKindName", "违规行为");
                String dev = item.optString("devName", "");
                int score = item.optInt("thisUseScore", 0);
                long ts = item.optLong("gmtCreate", 0);
                int status = item.optInt("status", 1);
                String semester = ts > 0 ? getSemesterString(ts) : "未知学期";
                if (!semester.equals(currentSemester)) {
                    currentSemester = semester;
                    TextView header = new TextView(this);
                    header.setText(semester);
                    header.setTextSize(14f);
                    header.setTextColor(0xFF666666);
                    int dp16 = (int) (16 * getResources().getDisplayMetrics().density);
                    int dp8 = (int) (8 * getResources().getDisplayMetrics().density);
                    header.setPadding(0, dp16, 0, dp8);
                    header.setTypeface(null, android.graphics.Typeface.BOLD);
                    layoutPunishList.addView(header);
                }
                View row = LayoutInflater.from(this).inflate(R.layout.item_punish_record, layoutPunishList, false);
                TextView tvTitle = row.findViewById(R.id.tvPunishTitle);
                TextView tvTime = row.findViewById(R.id.tvPunishTime);
                TextView tvPoints = row.findViewById(R.id.tvPunishPoints);
                String titleText = (i + 1) + ". " + kind;
                if (!dev.isEmpty())
                    titleText += "（" + dev + "）";
                tvTitle.setText(titleText);
                tvTime.setText(ts > 0 ? sdf.format(new Date(ts)) : "");
                if (status == 2) {
                    tvPoints.setText("已撤销");
                    tvPoints.setTextColor(0xFF9E9E9E);
                } else {
                    tvPoints.setText(score > 0 ? "-" + score + "信用" : "");
                    tvPoints.setTextColor(0xFFE53935);
                }
                layoutPunishList.addView(row);
            } catch (Exception ignored) {
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
