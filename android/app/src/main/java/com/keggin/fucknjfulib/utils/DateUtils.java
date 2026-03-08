package com.keggin.fucknjfulib.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DateUtils {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat DATE_FORMAT_COMPACT = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
            Locale.getDefault());

    public static String getTodayDate() {
        return DATE_FORMAT.format(new Date());
    }

    public static String getTomorrowDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        return DATE_FORMAT.format(cal.getTime());
    }

    public static String getDateWithOffset(int daysOffset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, daysOffset);
        return DATE_FORMAT.format(cal.getTime());
    }

    public static String getDateStringCompact(int daysOffset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, daysOffset);
        return DATE_FORMAT_COMPACT.format(cal.getTime());
    }

    public static String normalizeTimeFormat(String time) {
        if (time == null) {
            return "09:30:00";
        }
        time = time.trim();
        if (time.matches("\\d{2}:\\d{2}:\\d{2}")) {
            return time;
        }
        if (time.matches("\\d{2}:\\d{2}")) {
            return time + ":00";
        }
        if (time.matches("\\d:\\d{2}")) {
            return "0" + time + ":00";
        }
        return time;
    }

    public static boolean isValidDuration(String startTime, String endTime, int minHours) {
        try {
            Date start = TIME_FORMAT.parse(startTime);
            Date end = TIME_FORMAT.parse(endTime);
            if (start == null || end == null) {
                return false;
            }
            long diffMillis = end.getTime() - start.getTime();
            long diffHours = diffMillis / (1000 * 60 * 60);
            return diffHours >= minHours;
        } catch (ParseException e) {
            return false;
        }
    }

    public static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "未知";
        }
        return DATETIME_FORMAT.format(new Date(timestamp));
    }

    public static String formatTimestampToTime(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public static String getEndTime(String dateStr) {
        try {
            Date date = DATE_FORMAT.parse(dateStr);
            if (date == null) {
                return Constants.DEFAULT_END_TIME + ":00";
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
                return "20:00:00";
            }
            return Constants.DEFAULT_END_TIME + ":00";
        } catch (ParseException e) {
            return Constants.DEFAULT_END_TIME + ":00";
        }
    }

    public static String getEndTimeWithoutSeconds(String dateStr) {
        String full = getEndTime(dateStr);
        return full.substring(0, 5); // 返回闭馆时间（去掉秒）
    }

    public static boolean isFriday(String dateStr) {
        try {
            Date date = DATE_FORMAT.parse(dateStr);
            if (date == null) {
                return false;
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            return cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
        } catch (ParseException e) {
            return false;
        }
    }

    public static String getCurrentTime() {
        return TIME_FORMAT.format(new Date());
    }

    public static long getMillisUntil(int targetHour, int targetMinute, int targetSecond) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, targetHour);
        target.set(Calendar.MINUTE, targetMinute);
        target.set(Calendar.SECOND, targetSecond);
        target.set(Calendar.MILLISECOND, 0);
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }
        return target.getTimeInMillis() - now.getTimeInMillis();
    }

    public static Calendar parseTimeToCalendar(String dateStr, String timeStr) {
        try {
            String fullStr = dateStr + " " + normalizeTimeFormat(timeStr);
            Date date = DATETIME_FORMAT.parse(fullStr);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                return cal;
            }
        } catch (ParseException e) {
        }
        return null;
    }

    public static String addHours(String timeStr, int hours) {
        try {
            Date time = TIME_FORMAT.parse(normalizeTimeFormat(timeStr));
            if (time == null) {
                return timeStr;
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(time);
            cal.add(Calendar.HOUR_OF_DAY, hours);
            return TIME_FORMAT.format(cal.getTime());
        } catch (ParseException e) {
            return timeStr;
        }
    }

    public static List<java.lang.String> getReservationTimeOptions() {
        List<java.lang.String> options = new java.util.ArrayList<>();
        for (int h = 7; h <= 22; h++) {
            String hour = h < 10 ? "0" + h : String.valueOf(h);
            options.add(hour + ":00");
            if (h < 22) {
                options.add(hour + ":30");
            }
        }
        return options;
    }
}