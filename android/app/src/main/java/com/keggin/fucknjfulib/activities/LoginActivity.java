package com.keggin.fucknjfulib.activities;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.auth.AuthManager;
import com.keggin.fucknjfulib.storage.PreferenceManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class LoginActivity extends AppCompatActivity {
    private TextInputLayout tilStudentId, tilCasPassword;
    private TextInputEditText etStudentId, etCasPassword;
    private MaterialButton btnLogin;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private ExecutorService executor;
    private PreferenceManager preferenceManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        executor = Executors.newSingleThreadExecutor();
        preferenceManager = new PreferenceManager(this);
        initViews();
        loadSavedCredentials();
        setupClickListeners();
        if (preferenceManager.hasValidCredentials()) {
            navigateToDashboard();
        }
    }
    private void initViews() {
        tilStudentId = findViewById(R.id.tilStudentId);
        tilCasPassword = findViewById(R.id.tilCasPassword);
        etStudentId = findViewById(R.id.etStudentId);
        etCasPassword = findViewById(R.id.etCasPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
    }
    private void loadSavedCredentials() {
        String savedStudentId = preferenceManager.getStudentId();
        if (savedStudentId != null && !savedStudentId.isEmpty()) {
            etStudentId.setText(savedStudentId);
        }
    }
    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());
    }
    private void attemptLogin() {
        tilStudentId.setError(null);
        tilCasPassword.setError(null);
        String studentId = etStudentId.getText() != null ? etStudentId.getText().toString().trim() : "";
        String casPassword = etCasPassword.getText() != null ? etCasPassword.getText().toString() : "";
        boolean hasError = false;
        if (studentId.isEmpty()) {
            tilStudentId.setError("请输入学号");
            hasError = true;
        }
        if (casPassword.isEmpty()) {
            tilCasPassword.setError("请输入统一认证密码");
            hasError = true;
        }
        if (hasError) {
            return;
        }
        setLoading(true);
        updateStatus("正在准备登录...");
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                String serverUrl = preferenceManager.getServerApiUrl();
                if (serverUrl != null && !serverUrl.trim().isEmpty()) {
                    runOnUiThread(() -> updateStatus("正在通过校内代理隧道直连登录..."));
                    AuthManager.AuthResult proxyResult = authManager.loginViaServer(studentId, casPassword, casPassword);
                    if (proxyResult.success) {
                        preferenceManager.saveCredentials(studentId, casPassword, casPassword);
                        authManager.saveCredentials(studentId, casPassword, casPassword);
                        preferenceManager.setLoggedIn(true);
                        runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(this, "校内隧道直连登录成功！", Toast.LENGTH_SHORT).show();
                            navigateToDashboard();
                        });
                        return;
                    } else {
                        runOnUiThread(() -> {
                            setLoading(false);
                            showError("代理登录失败: " + proxyResult.message);
                        });
                        return;
                    }
                }

                runOnUiThread(() -> updateStatus(getString(R.string.login_status_cas)));
                AuthManager.AuthResult casResult = authManager.loginCAS(studentId, casPassword);
                if (!casResult.success) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showError("统一认证失败：" + casResult.message);
                    });
                    return;
                }
                runOnUiThread(() -> updateStatus(getString(R.string.login_status_lib)));
                AuthManager.AuthResult libResult = authManager.loginLibrary(studentId, casPassword);
                if (!libResult.success) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showError("图书馆登录失败：" + libResult.message);
                    });
                    return;
                }
                preferenceManager.saveCredentials(studentId, casPassword, casPassword);
                preferenceManager.setLoggedIn(true);
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
                    navigateToDashboard();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError("登录出错：" + e.getMessage());
                });
            }
        });
    }
    private void setLoading(boolean isLoading) {
        btnLogin.setEnabled(!isLoading);
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        tvStatus.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        etStudentId.setEnabled(!isLoading);
        etCasPassword.setEnabled(!isLoading);
    }
    private void updateStatus(String status) {
        tvStatus.setText(status);
        tvStatus.setTextColor(getColor(R.color.text_secondary));
    }
    private void showError(String error) {
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(error);
        tvStatus.setTextColor(getColor(R.color.error));
    }
    private void navigateToDashboard() {
        Intent intent = new Intent(this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}