package com.keggin.fucknjfulib.activities;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;

import com.keggin.fucknjfulib.R;

public class UserGuideActivity extends AppCompatActivity {

    private NestedScrollView guideScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_guide);

        Toolbar toolbar = findViewById(R.id.toolbarGuide);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        guideScroll = findViewById(R.id.guideScroll);

        setupTocJump(R.id.tocOverview, R.id.anchorOverview);
        setupTocJump(R.id.tocAutoReserve, R.id.anchorAutoReserve);
        setupTocJump(R.id.tocAutoFind, R.id.anchorAutoFind);
        setupTocJump(R.id.tocLateProtection, R.id.anchorLateProtection);
        setupTocJump(R.id.tocVisualSeat, R.id.anchorVisualSeat);
        setupTocJump(R.id.tocServerDeploy, R.id.anchorServerDeploy);
    }

    private void setupTocJump(int tocId, int anchorId) {
        View toc = findViewById(tocId);
        View anchor = findViewById(anchorId);
        if (toc == null || anchor == null) {
            return;
        }
        toc.setOnClickListener(v -> scrollToAnchor(anchor));
    }

    private void scrollToAnchor(View anchor) {
        if (guideScroll == null || anchor == null) {
            return;
        }
        guideScroll.post(() -> {
            int y = Math.max(anchor.getTop() - dp(12), 0);
            guideScroll.smoothScrollTo(0, y);
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}