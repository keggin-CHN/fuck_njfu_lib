package com.keggin.fucknjfulib.activities;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        holder.tvLogTag.setText("[" + log.tag + "]");
        holder.tvLogMessage.setText(log.message);
        holder.tvLogLevel.setText(log.level);

        switch (log.level) {
            case LocalLogManager.LEVEL_INFO:
                holder.tvLogLevel.setBackgroundColor(Color.parseColor("#4CAF50")); // Green
                holder.tvLogMessage.setTextColor(Color.parseColor("#333333"));
                break;
            case LocalLogManager.LEVEL_WARN:
                holder.tvLogLevel.setBackgroundColor(Color.parseColor("#FF9800")); // Orange
                holder.tvLogMessage.setTextColor(Color.parseColor("#FF9800"));
                break;
            case LocalLogManager.LEVEL_ERROR:
                holder.tvLogLevel.setBackgroundColor(Color.parseColor("#F44336")); // Red
                holder.tvLogMessage.setTextColor(Color.parseColor("#F44336"));
                break;
            default:
                holder.tvLogLevel.setBackgroundColor(Color.parseColor("#9E9E9E")); // Grey
                holder.tvLogMessage.setTextColor(Color.parseColor("#333333"));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return logList != null ? logList.size() : 0;
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvLogTime, tvLogLevel, tvLogTag, tvLogMessage;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLogTime = itemView.findViewById(R.id.tvLogTime);
            tvLogLevel = itemView.findViewById(R.id.tvLogLevel);
            tvLogTag = itemView.findViewById(R.id.tvLogTag);
            tvLogMessage = itemView.findViewById(R.id.tvLogMessage);
        }
    }
}
