package com.keggin.fucknjfulib.reservation;
import android.util.Log;
import com.keggin.fucknjfulib.auth.AuthManager;
import com.keggin.fucknjfulib.utils.Constants;
import com.keggin.fucknjfulib.utils.DateUtils;
import java.util.List;
public class AutoFinder {
    private static final String TAG = "AutoFinder";
    private final AuthManager authManager;
    private final SeatQuery seatQuery;
    private final SeatReservation seatReservation;
    private static final int DEFAULT_ALTERNATIVES_LIMIT = 50;
    public AutoFinder(AuthManager authManager) {
        this.authManager = authManager;
        this.seatQuery = new SeatQuery(authManager);
        this.seatReservation = new SeatReservation(authManager);
    }
    public static class AutoFindResult {
        public boolean success;
        public String message;
        public SeatQuery.SeatInfo reservedSeat;
        public List<SeatQuery.SeatInfo> alternatives;
        public AutoFindResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
    public AutoFindResult tryReserveWithAutoFind(String areaName, int seatNumber,
                                                   String dateStr, String startTime, String endTime,
                                                   boolean autoReserve) {
        Log.d(TAG, "尝试预约: " + areaName + " " + seatNumber + "号");
        SeatReservation.ReserveResult result = seatReservation.reserveSeat(
                areaName, seatNumber, dateStr, startTime, endTime);
        if (result.success) {
            Log.d(TAG, "目标座位预约成功");
            AutoFindResult findResult = new AutoFindResult(true, "预约成功: " + result.message);
            return findResult;
        }
        String errorMsg = result.message != null ? result.message : "";
        String normalizedMsg = errorMsg.toLowerCase();
        boolean isSeatOccupied = normalizedMsg.contains("已被预约")
                || normalizedMsg.contains("已预约")
                || normalizedMsg.contains("被占用")
                || normalizedMsg.contains("正在被预约")
                || normalizedMsg.contains("time conflict")
                || (errorMsg.contains("时间段") && (errorMsg.contains("预约") || errorMsg.contains("占用")))
                || (errorMsg.contains("该时间段") && (errorMsg.contains("预约") || errorMsg.contains("占用")));
        if (!isSeatOccupied) {
            Log.d(TAG, "非座位占用错误，不进行自动寻座: " + result.message);
            return new AutoFindResult(false, result.message);
        }
        Log.d(TAG, "目标座位被占用，开始寻找备选座位...");
        List<SeatQuery.SeatInfo> alternatives = seatQuery.findAvailableSeats(
                areaName, dateStr, startTime, endTime, DEFAULT_ALTERNATIVES_LIMIT);
        if (alternatives.isEmpty()) {
            Log.d(TAG, "未找到可用的备选座位");
            AutoFindResult findResult = new AutoFindResult(false,
                    "目标座位已被占用，且该区域无其他可用座位");
            return findResult;
        }
        Log.d(TAG, "找到 " + alternatives.size() + " 个备选座位");
        if (!autoReserve) {
            AutoFindResult findResult = new AutoFindResult(false,
                    "目标座位已被占用，找到 " + alternatives.size() + " 个备选座位");
            findResult.alternatives = alternatives;
            return findResult;
        }
        for (SeatQuery.SeatInfo alt : alternatives) {
            String[] areaAndSeat = Constants.getAreaAndSeatNumber(alt.devId);
            if (areaAndSeat == null) {
                continue;
            }
            String altArea = areaAndSeat[0];
            int altSeatNumber = Integer.parseInt(areaAndSeat[1]);
            Log.d(TAG, "尝试预约备选座位: " + altArea + " " + altSeatNumber + "号");
            SeatReservation.ReserveResult altResult = seatReservation.reserveSeat(
                    altArea, altSeatNumber, dateStr, startTime, endTime);
            if (altResult.success) {
                Log.d(TAG, "备选座位预约成功: " + alt.devName);
                AutoFindResult findResult = new AutoFindResult(true,
                        "自动寻座成功: " + alt.devName);
                findResult.reservedSeat = alt;
                return findResult;
            }
        }
        Log.d(TAG, "所有备选座位预约均失败");
        AutoFindResult findResult = new AutoFindResult(false,
                "目标座位已被占用，备选座位预约也失败了");
        findResult.alternatives = alternatives;
        return findResult;
    }
    public AutoFindResult autoFindAndReserveToday(String areaName, int seatNumber,
                                                   String startTime, String endTime) {
        return tryReserveWithAutoFind(areaName, seatNumber,
                DateUtils.getTodayDate(), startTime, endTime, true);
    }
    public AutoFindResult autoFindAndReserveTomorrow(String areaName, int seatNumber,
                                                      String startTime, String endTime) {
        return tryReserveWithAutoFind(areaName, seatNumber,
                DateUtils.getTomorrowDate(), startTime, endTime, true);
    }
    public List<SeatQuery.SeatInfo> getAlternatives(String areaName, String dateStr,
                                                     String startTime, String endTime) {
        return seatQuery.findAvailableSeats(areaName, dateStr, startTime, endTime,
                DEFAULT_ALTERNATIVES_LIMIT);
    }
    public SeatReservation.ReserveResult reserveAlternative(SeatQuery.SeatInfo seat,
                                                             String dateStr,
                                                             String startTime, String endTime) {
        String[] areaAndSeat = Constants.getAreaAndSeatNumber(seat.devId);
        if (areaAndSeat == null) {
            return new SeatReservation.ReserveResult(false, "无法识别座位信息");
        }
        String areaName = areaAndSeat[0];
        int seatNumber = Integer.parseInt(areaAndSeat[1]);
        return seatReservation.reserveSeat(areaName, seatNumber, dateStr, startTime, endTime);
    }
}