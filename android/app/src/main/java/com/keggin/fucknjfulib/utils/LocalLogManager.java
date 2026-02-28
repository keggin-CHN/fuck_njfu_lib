package com.keggin.fucknjfulib.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class LocalLogManager extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "system_logs.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_LOGS = "logs";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TIMESTAMP = "timestamp_ms";
    public static final String COLUMN_LEVEL = "level";
    public static final String COLUMN_TAG = "tag";
    public static final String COLUMN_MESSAGE = "message";

    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_ERROR = "ERROR";

    private static LocalLogManager instance;

    public static class LogEntry {
        public long id;
        public long timestamp;
        public String level;
        public String tag;
        public String message;
        
        public LogEntry(long id, long timestamp, String level, String tag, String message) {
            this.id = id;
            this.timestamp = timestamp;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }
    }

    private LocalLogManager(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized LocalLogManager getInstance(Context context) {
        if (instance == null) {
            instance = new LocalLogManager(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableSQL = "CREATE TABLE " + TABLE_LOGS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_TIMESTAMP + " INTEGER,"
                + COLUMN_LEVEL + " TEXT,"
                + COLUMN_TAG + " TEXT,"
                + COLUMN_MESSAGE + " TEXT"
                + ")";
        db.execSQL(createTableSQL);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        onCreate(db);
    }

    public void log(String level, String tag, String message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        values.put(COLUMN_LEVEL, level);
        values.put(COLUMN_TAG, tag);
        values.put(COLUMN_MESSAGE, message);
        db.insert(TABLE_LOGS, null, values);

        // Auto clean up old logs to prevent bloating (keep last 500)
        trimLogs(db, 500);
    }

    public void i(String tag, String message) { log(LEVEL_INFO, tag, message); }
    public void w(String tag, String message) { log(LEVEL_WARN, tag, message); }
    public void e(String tag, String message) { log(LEVEL_ERROR, tag, message); }
    
    public void e(String tag, String message, Throwable t) { 
        log(LEVEL_ERROR, tag, message + "\n" + android.util.Log.getStackTraceString(t)); 
    }

    private void trimLogs(SQLiteDatabase db, int limit) {
        db.execSQL("DELETE FROM " + TABLE_LOGS + " WHERE " + COLUMN_ID + " NOT IN " +
                "(SELECT " + COLUMN_ID + " FROM " + TABLE_LOGS + " ORDER BY " + COLUMN_TIMESTAMP + " DESC LIMIT " + limit + ")");
    }

    public List<LogEntry> getRecentLogs(int limit) {
        List<LogEntry> logList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_LOGS,
                null, null, null, null, null,
                COLUMN_TIMESTAMP + " DESC", String.valueOf(limit));

        if (cursor != null && cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                long ts = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                String level = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LEVEL));
                String tag = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TAG));
                String msg = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE));
                logList.add(new LogEntry(id, ts, level, tag, msg));
            } while (cursor.moveToNext());
            cursor.close();
        }
        return logList;
    }

    public void clearLogs() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_LOGS, null, null);
    }
}
