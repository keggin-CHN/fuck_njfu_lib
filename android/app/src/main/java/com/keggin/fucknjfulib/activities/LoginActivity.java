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
    private TextInputLayout tilStudentId, tilCasPassword, tilLibPassword;
    private TextInputEditText etStudentId, etCasPassword, etLibPassword;
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
        tilLibPassword = findViewById(R.id.tilLibPassword);
        etStudentId = findViewById(R.id.etStudentId);
        etCasPassword = findViewById(R.id.etCasPassword);
        etLibPassword = findViewById(R.id.etLibPassword);
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
        tilLibPassword.setError(null);
        String studentId = etStudentId.getText() != null ? etStudentId.getText().toString().trim() : "";
        String casPassword = etCasPassword.getText() != null ? etCasPassword.getText().toString() : "";
        String libPassword = etLibPassword.getText() != null ? etLibPassword.getText().toString() : "";
        boolean hasError = false;
        if (studentId.isEmpty()) {
            tilStudentId.setError("请输入学号");
            hasError = true;
        }
        if (casPassword.isEmpty()) {
            tilCasPassword.setError("请输入统一认证密码");
            hasError = true;
        }
        if (libPassword.isEmpty()) {
            tilLibPassword.setError("请输入图书馆密码");
            hasError = true;
        }
        if (hasError) {
            return;
        }
        setLoading(true);
        updateStatus(getString(R.string.login_status_cas));
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
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
                AuthManager.AuthResult libResult = authManager.loginLibrary(studentId, libPassword);
                if (!libResult.success) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showError("图书馆登录失败：" + libResult.message);
                    });
                    return;
                }
                preferenceManager.saveCredentials(studentId, casPassword, libPassword);
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
        etLibPassword.setEnabled(!isLoading);
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