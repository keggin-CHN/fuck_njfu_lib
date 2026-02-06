package com.keggin.fucknjfulib.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应用常量定义
 * 包含座位区域配置、API地址等核心常量
 */
public class Constants {

    // ==================== 默认预约设置 ====================
    public static final String DEFAULT_AREA = "二层A区";
    public static final int DEFAULT_SEAT = 1;
    public static final String DEFAULT_START_TIME = "09:30";
    public static final String DEFAULT_END_TIME = "22:00";

    // ==================== 座位区域配置 ====================
    // 与后端 config.py 中的 SEAT_AREAS 保持一致
    public static class SeatArea {
        public final String name;
        public final int firstSeatId;
        public final int seatsCount;
        public final int roomId;  // 用于座位查询
        public final int floor;
        public final String area;

        public SeatArea(String name, int firstSeatId, int seatsCount, int roomId, int floor, String area) {
            this.name = name;
            this.firstSeatId = firstSeatId;
            this.seatsCount = seatsCount;
            this.roomId = roomId;
            this.floor = floor;
            this.area = area;
        }

        /**
         * 根据座位号获取座位ID
         */
        public int getSeatId(int seatNumber) {
            if (seatNumber < 1 || seatNumber > seatsCount) {
                return -1;
            }
            return firstSeatId + seatNumber - 1;
        }
        
        /**
         * 获取所有座位ID数组
         */
        public int[] getSeatIds() {
            int[] ids = new int[seatsCount];
            for (int i = 0; i < seatsCount; i++) {
                ids[i] = firstSeatId + i;
            }
            return ids;
        }
    }
    
    /**
     * 兼容 AreaInfo 类（供UI层使用）
     */
    public static class AreaInfo {
        public final String name;
        public final int roomId;
        public final int seatCount;
        public final int[] seatIds;
        public final int floor;
        public final String area;
        
        public AreaInfo(SeatArea seatArea) {
            this.name = seatArea.name;
            this.roomId = seatArea.roomId;
            this.seatCount = seatArea.seatsCount;
            this.seatIds = seatArea.getSeatIds();
            this.floor = seatArea.floor;
            this.area = seatArea.area;
        }
    }

    // 所有座位区域（数组形式，供内部使用）
    public static final SeatArea[] SEAT_AREAS_ARRAY = {
        new SeatArea("二层A区", 100455361, 441, 100455344, 2, "A"),
        new SeatArea("二层B区", 100455802, 96, 100455346, 2, "B"),
        new SeatArea("三层A区", 100456256, 404, 100455350, 3, "A"),
        new SeatArea("三楼B区", 100456660, 132, 100455352, 3, "B"),
        new SeatArea("三楼C区", 100499567, 162, 100455354, 3, "C"),
        new SeatArea("三楼夹层", 111488493, 20, 111488386, 3, "夹层"),
        new SeatArea("四楼A区", 100499729, 428, 100455356, 4, "A"),
        new SeatArea("四楼夹层", 111488513, 24, 111488388, 4, "夹层"),
        new SeatArea("五楼A区", 100500173, 360, 100455358, 5, "A"),
        new SeatArea("六楼A区", 100500602, 344, 100455360, 6, "A"),
        new SeatArea("七楼北侧", 106744855, 224, 106658017, 7, "北"),
        new SeatArea("七楼南侧", 111488640, 114, 111488396, 7, "南"),
    };
    
    // 兼容旧代码的数组引用
    public static final SeatArea[] SEAT_AREAS = SEAT_AREAS_ARRAY;
    
    // 座位区域Map（供UI层使用，key为区域名称）
    public static final Map<String, AreaInfo> SEAT_AREAS_MAP;
    
    static {
        SEAT_AREAS_MAP = new LinkedHashMap<>();
        for (SeatArea area : SEAT_AREAS_ARRAY) {
            SEAT_AREAS_MAP.put(area.name, new AreaInfo(area));
        }
    }

    /**
     * 根据区域名称获取区域配置
     */
    public static SeatArea getAreaByName(String name) {
        for (SeatArea area : SEAT_AREAS) {
            if (area.name.equals(name)) {
                return area;
            }
        }
        return null;
    }

    /**
     * 根据座位ID反向获取区域和座位号
     */
    public static String[] getAreaAndSeatNumber(int devId) {
        for (SeatArea area : SEAT_AREAS) {
            int endId = area.firstSeatId + area.seatsCount - 1;
            if (devId >= area.firstSeatId && devId <= endId) {
                int seatNumber = devId - area.firstSeatId + 1;
                return new String[]{area.name, String.valueOf(seatNumber)};
            }
        }
        return null;
    }

    // ==================== 定时预约配置 ====================
    public static final int DEFAULT_RESERVE_HOUR = 7;
    public static final int DEFAULT_RESERVE_MINUTE = 0;
    public static final int DEFAULT_RESERVE_SECOND = 20;

    // ==================== 迟到保护配置 ====================
    public static final int LATE_CHECK_MINUTES_BEFORE = 20;  // 预约开始前20分钟检查
    public static final int LATE_PROTECTION_DELAY_HOURS = 1; // 延后1小时重新预约

    // ==================== 图书馆容量 ====================
    public static final int LIBRARY_TOTAL_CAPACITY = 2749;

    // ==================== SharedPreferences Keys ====================
    public static final String PREF_NAME = "njfu_lib_prefs";
    public static final String PREF_USERNAME = "username";
    public static final String PREF_EDU_PASSWORD = "edu_password";
    public static final String PREF_LIB_PASSWORD = "lib_password";
    public static final String PREF_TARGET_AREA = "target_area";
    public static final String PREF_TARGET_SEAT = "target_seat";
    public static final String PREF_START_TIME = "start_time";
    public static final String PREF_END_TIME = "end_time";
    public static final String PREF_AUTO_RESERVE = "auto_reserve";
    public static final String PREF_PREVENT_LATE = "prevent_late";
    public static final String PREF_AUTO_FIND_SEAT = "auto_find_seat";
    public static final String PREF_DARK_MODE = "dark_mode";
    public static final String PREF_LAST_AUTH_TOKEN = "last_auth_token";
    public static final String PREF_LAST_AUTH_ACC_NO = "last_auth_acc_no";
    public static final String PREF_LAST_AUTH_TIME = "last_auth_time";
    public static final String PREF_HIDE_PERMISSION_CHECK = "hide_permission_check";

    // ==================== 周计划任务（计划任务） ====================
    public static final String PREF_WEEKLY_PLAN_TASKS = "weekly_plan_tasks_json";
}