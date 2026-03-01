package com.keggin.fucknjfulib.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.keggin.fucknjfulib.R;
import com.keggin.fucknjfulib.utils.LocalLogManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<LocalLogManager.LogEntry> logList;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());

    public LogAdapter(List<LocalLogManager.LogEntry> logList) {
        this.logList = logList;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LocalLogManager.LogEntry log = logList.get(position);

        holder.tvLogTime.setText(dateFormat.format(new Date(log.timestamp)));
        holder.tvLogTag.setText(log.tag);
        holder.tvLogMessage.setText(log.message);
        holder.tvLogLevel.setText(log.level);

        int levelColor;
        switch (log.level) {
            case LocalLogManager.LEVEL_INFO:
                levelColor = Color.parseColor("#4CAF50");
                break;
            case LocalLogManager.LEVEL_WARN:
                levelColor = Color.parseColor("#FF9800");
                break;
            case LocalLogManager.LEVEL_ERROR:
                levelColor = Color.parseColor("#F44336");
                break;
            default:
                levelColor = Color.parseColor("#9E9E9E");
                break;
        }

        // 设置左侧指示条颜色
        holder.viewLevelIndicator.setBackgroundColor(levelColor);

        // 设置 level pill 背景颜色
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setShape(GradientDrawable.RECTANGLE);
        pillBg.setCornerRadius(24f);
        pillBg.setColor(levelColor);
        holder.tvLogLevel.setBackground(pillBg);

        // Show indicator and click-to-expand for entries with extra (headers) data
        boolean hasExtra = log.extra != null && !log.extra.isEmpty();
        holder.tvLogMessage.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, hasExtra ? R.drawable.ic_arrow_right : 0, 0);

        holder.itemView.setOnClickListener(v -> {
            if (!hasExtra)
                return;
            Context ctx = v.getContext();
            ScrollView sv = new ScrollView(ctx);
            TextView tv = new TextView(ctx);
            tv.setText(log.extra);
            tv.setTextSize(11f);
            tv.setTextColor(Color.parseColor("#212121"));
            tv.setPadding(40, 20, 40, 20);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setTextIsSelectable(true);
            sv.addView(tv);
            new AlertDialog.Builder(ctx)
                    .setTitle("HTTP 详情")
                    .setView(sv)
                    .setPositiveButton("关闭", null)
                    .show();
        });
    }

    @Override
    public int getItemCount() {
        return logList != null ? logList.size() : 0;
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvLogTime, tvLogLevel, tvLogTag, tvLogMessage;
        View viewLevelIndicator;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLogTime = itemView.findViewById(R.id.tvLogTime);
            tvLogLevel = itemView.findViewById(R.id.tvLogLevel);
            tvLogTag = itemView.findViewById(R.id.tvLogTag);
            tvLogMessage = itemView.findViewById(R.id.tvLogMessage);
            viewLevelIndicator = itemView.findViewById(R.id.viewLevelIndicator);
        }
    }
}
