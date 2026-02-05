package com.keggin.fucknjfulib.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 日期时间工具类
 */
public class DateUtils {
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat DATE_FORMAT_COMPACT = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    
    /**
     * 获取今天日期 yyyy-MM-dd
     */
    public static String getTodayDate() {
        return DATE_FORMAT.format(new Date());
    }
    
    /**
     * 获取明天日期 yyyy-MM-dd
     */
    public static String getTomorrowDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        return DATE_FORMAT.format(cal.getTime());
    }
    
    /**
     * 获取指定偏移天数的日期 yyyy-MM-dd
     */
    public static String getDateWithOffset(int daysOffset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, daysOffset);
        return DATE_FORMAT.format(cal.getTime());
    }
    
    /**
     * 获取日期字符串（紧凑格式）yyyyMMdd
     */
    public static String getDateStringCompact(int daysOffset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, daysOffset);
        return DATE_FORMAT_COMPACT.format(cal.getTime());
    }
    
    /**
     * 标准化时间格式
     * 将 HH:mm 转换为 HH:mm:ss
     */
    public static String normalizeTimeFormat(String time) {
        if (time == null) {
            return "09:30:00";
        }
        
        time = time.trim();
        
        // 如果已经是 HH:mm:ss 格式
        if (time.matches("\\d{2}:\\d{2}:\\d{2}")) {
            return time;
        }
        
        // 如果是 HH:mm 格式
        if (time.matches("\\d{2}:\\d{2}")) {
            return time + ":00";
        }
        
        // 如果是 H:mm 格式
        if (time.matches("\\d:\\d{2}")) {
            return "0" + time + ":00";
        }
        
        return time;
    }
    
    /**
     * 检查时间段是否满足最小时长要求
     */
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
    
    /**
     * 格式化时间戳为可读字符串
     */
    public static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "未知";
        }
        return DATETIME_FORMAT.format(new Date(timestamp));
    }
    
    /**
     * 格式化时间戳为时间 HH:mm
     */
    public static String formatTimestampToTime(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    /**
     * 获取图书馆闭馆时间
     * 周五为 18:00，其他日期为 22:00
     */
    public static String getEndTime(String dateStr) {
        try {
            Date date = DATE_FORMAT.parse(dateStr);
            if (date == null) {
                return "22:00:00";
            }
            
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            
            // 周五
            if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
                return "18:00:00";
            }
            
            return "22:00:00";
        } catch (ParseException e) {
            return "22:00:00";
        }
    }
    
    /**
     * 判断是否为周五
     */
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
    
    /**
     * 获取当前时间 HH:mm:ss
     */
    public static String getCurrentTime() {
        return TIME_FORMAT.format(new Date());
    }
    
    /**
     * 计算从现在到指定时间的毫秒数
     * @param targetHour 目标小时
     * @param targetMinute 目标分钟
     * @param targetSecond 目标秒
     * @return 毫秒数，如果目标时间已过，返回到第二天该时间的毫秒数
     */
    public static long getMillisUntil(int targetHour, int targetMinute, int targetSecond) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        
        target.set(Calendar.HOUR_OF_DAY, targetHour);
        target.set(Calendar.MINUTE, targetMinute);
        target.set(Calendar.SECOND, targetSecond);
        target.set(Calendar.MILLISECOND, 0);
        
        // 如果目标时间已过，设为明天
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        return target.getTimeInMillis() - now.getTimeInMillis();
    }
    
    /**
     * 解析时间字符串为 Calendar
     */
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
            // ignore
        }
        return null;
    }
    
    /**
     * 添加小时数
     */
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
}