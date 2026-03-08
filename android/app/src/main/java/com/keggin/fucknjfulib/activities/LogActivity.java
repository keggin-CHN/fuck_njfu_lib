package com.keggin.fucknjfulib.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.utils.LocalLogManager;

import java.util.ArrayList;
import java.util.List;

public class LogActivity extends AppCompatActivity {

    private RecyclerView rvLogs;
    private LogAdapter adapter;
    private List<LocalLogManager.LogEntry> logList;
    private LocalLogManager logManager;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        Toolbar toolbar = findViewById(R.id.toolbarLogs);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        logManager = LocalLogManager.getInstance(this);
        mainHandler = new Handler(Looper.getMainLooper());
        
        rvLogs = findViewById(R.id.rvLogs);
        rvLogs.setLayoutManager(new LinearLayoutManager(this));
        
        logList = new ArrayList<>();
        adapter = new LogAdapter(logList);
        rvLogs.setAdapter(adapter);

        loadLogs();
    }

    private void loadLogs() {
        new Thread(() -> {
            List<LocalLogManager.LogEntry> logs = logManager.getRecentLogs(500);
            mainHandler.post(() -> {
                logList.clear();
                if (logs != null) {
                    logList.addAll(logs);
                }
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "清空").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            new AlertDialog.Builder(this)
                    .setTitle("清空日志")
                    .setMessage("确定要清空所有系统日志吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        logManager.clearLogs();
                        loadLogs();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
