package com.keggin.fucknjfulib.activities;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.auth.AuthManager;
import com.keggin.fucknjfulib.reservation.SeatQuery;
import com.keggin.fucknjfulib.storage.PreferenceManager;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.DateUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class SeatQueryActivity extends AppCompatActivity {
    private Toolbar toolbar;
    private Spinner spinnerArea;
    private Spinner spinnerDate;
    private MaterialButton btnQuery;
    private TextView tvAvailableCount;
    private TextView tvTotalCount;
    private RecyclerView rvSeats;
    private FrameLayout loadingOverlay;
    private ExecutorService executor;
    private PreferenceManager preferenceManager;
    private List<String> areaKeys;
    private List<String> areaNames;
    private List<String> dateOptions;
    private SeatAdapter seatAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seat_query);
        executor = Executors.newSingleThreadExecutor();
        preferenceManager = new PreferenceManager(this);
        initViews();
        setupToolbar();
        setupSpinners();
        setupClickListeners();
    }
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        spinnerArea = findViewById(R.id.spinnerArea);
        spinnerDate = findViewById(R.id.spinnerDate);
        btnQuery = findViewById(R.id.btnQuery);
        tvAvailableCount = findViewById(R.id.tvAvailableCount);
        tvTotalCount = findViewById(R.id.tvTotalCount);
        rvSeats = findViewById(R.id.rvSeats);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        rvSeats.setLayoutManager(new GridLayoutManager(this, 6));
        seatAdapter = new SeatAdapter();
        rvSeats.setAdapter(seatAdapter);
    }
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.seat_query_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }
    private void setupSpinners() {
        areaKeys = new ArrayList<>(Constants.SEAT_AREAS_MAP.keySet());
        areaNames = new ArrayList<>();
        for (String key : areaKeys) {
            Constants.AreaInfo info = Constants.SEAT_AREAS_MAP.get(key);
            areaNames.add(info != null ? info.name : key);
        }
        ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, areaNames);
        areaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerArea.setAdapter(areaAdapter);
        String defaultArea = preferenceManager.getTargetArea();
        int defaultIndex = areaKeys.indexOf(defaultArea);
        if (defaultIndex >= 0) {
            spinnerArea.setSelection(defaultIndex);
        }
        dateOptions = new ArrayList<>();
        dateOptions.add("今天 (" + DateUtils.getTodayDate() + ")");
        dateOptions.add("明天 (" + DateUtils.getTomorrowDate() + ")");
        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, dateOptions);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDate.setAdapter(dateAdapter);
    }
    private void setupClickListeners() {
        btnQuery.setOnClickListener(v -> performQuery());
    }
    private void performQuery() {
        int areaIndex = spinnerArea.getSelectedItemPosition();
        int dateIndex = spinnerDate.getSelectedItemPosition();
        if (areaIndex < 0 || areaIndex >= areaKeys.size()) {
            Toast.makeText(this, "请选择区域", Toast.LENGTH_SHORT).show();
            return;
        }
        String areaKey = areaKeys.get(areaIndex);
        Constants.AreaInfo areaInfo = Constants.SEAT_AREAS_MAP.get(areaKey);
        if (areaInfo == null) {
            Toast.makeText(this, "区域信息无效", Toast.LENGTH_SHORT).show();
            return;
        }
        String queryDate = dateIndex == 0 ? DateUtils.getTodayDate() : DateUtils.getTomorrowDate();
        showLoading(true);
        executor.execute(() -> {
            try {
                AuthManager authManager = AuthManager.getInstance(this);
                if (!authManager.ensureLoggedIn()) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, "登录已过期，请重新登录", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                SeatQuery seatQuery = new SeatQuery(authManager);
                SeatQuery.QueryResult result = seatQuery.querySeats(
                        authManager.getToken(),
                        areaInfo,
                        queryDate
                );
                runOnUiThread(() -> {
                    showLoading(false);
                    if (result.success) {
                        updateSeatDisplay(result, areaInfo);
                    } else {
                        Toast.makeText(this, "查询失败: " + result.message, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, "查询出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private void updateSeatDisplay(SeatQuery.QueryResult result, Constants.AreaInfo areaInfo) {
        tvAvailableCount.setText(String.format(getString(R.string.available_seats), result.availableCount));
        tvTotalCount.setText(String.format(getString(R.string.total_seats), areaInfo.seatCount));
        List<SeatItem> seatItems = new ArrayList<>();
        for (int i = 0; i < areaInfo.seatCount; i++) {
            SeatItem item = new SeatItem();
            item.number = i + 1;
            item.seatId = areaInfo.seatIds.length > i ? areaInfo.seatIds[i] : 0;
            item.isAvailable = result.availableSeatIds != null && 
                               result.availableSeatIds.contains(item.seatId);
            seatItems.add(item);
        }
        seatAdapter.setSeats(seatItems);
    }
    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
    static class SeatItem {
        int number;
        int seatId;
        boolean isAvailable;
    }
    class SeatAdapter extends RecyclerView.Adapter<SeatAdapter.SeatViewHolder> {
        private List<SeatItem> seats = new ArrayList<>();
        void setSeats(List<SeatItem> seats) {
            this.seats = seats;
            notifyDataSetChanged();
        }
        @Override
        public SeatViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_seat, parent, false);
            return new SeatViewHolder(view);
        }
        @Override
        public void onBindViewHolder(SeatViewHolder holder, int position) {
            SeatItem item = seats.get(position);
            holder.bind(item);
        }
        @Override
        public int getItemCount() {
            return seats.size();
        }
        class SeatViewHolder extends RecyclerView.ViewHolder {
            TextView tvSeatNumber;
            SeatViewHolder(View itemView) {
                super(itemView);
                tvSeatNumber = itemView.findViewById(R.id.tvSeatNumber);
            }
            void bind(SeatItem item) {
                tvSeatNumber.setText(String.valueOf(item.number));
                if (item.isAvailable) {
                    tvSeatNumber.setBackgroundResource(R.drawable.bg_seat_available);
                    tvSeatNumber.setTextColor(getColor(R.color.text_white));
                } else {
                    tvSeatNumber.setBackgroundResource(R.drawable.bg_seat_occupied);
                    tvSeatNumber.setTextColor(getColor(R.color.text_white));
                }
                itemView.setOnClickListener(v -> {
                    if (item.isAvailable) {
                        Toast.makeText(SeatQueryActivity.this, 
                                "座位 " + item.number + " 可用", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(SeatQueryActivity.this, 
                                "座位 " + item.number + " 已被占用", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
}