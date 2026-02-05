package com.keggin.fucknjfulib.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.keggin.fucknjfulib.utils.Constants;

/**
 * 加密存储管理器
 * 使用 EncryptedSharedPreferences 安全存储敏感数据
 */
public class PreferenceManager {

    // 与服务端/业务模块统一的存储命名
    private static final String PREF_NAME = Constants.PREF_NAME;          // 明文首选项（与 BootReceiver/Service 保持一致）
    private static final String ENCRYPTED_PREF_NAME = "secure_prefs";     // 加密首选项（与 AuthManager 保持一致）

    // 仅本地使用的登录状态标记
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    // 兼容旧版键名（迁移时可读取）
    private static final String LEGACY_STUDENT_ID = "student_id";
    private static final String LEGACY_CAS_PASSWORD = "cas_password";
    private static final String LEGACY_LIB_PASSWORD = "lib_password";
    private static final String LEGACY_TARGET_AREA = "target_area";
    private static final String LEGACY_TARGET_SEAT = "target_seat";
    private static final String LEGACY_START_TIME = "start_time";
    private static final String LEGACY_END_TIME = "end_time";
    private static final String LEGACY_AUTO_RESERVE_ENABLED = "auto_reserve_enabled";
    private static final String LEGACY_LATE_PROTECTION_ENABLED = "late_protection_enabled";
    private static final String LEGACY_AUTO_FIND_SEAT_ENABLED = "auto_find_seat_enabled";

    private SharedPreferences prefs;
    private SharedPreferences encryptedPrefs;
    private Context context;

    public PreferenceManager(Context context) {
        this.context = context.getApplicationContext();
        initPreferences();
    }

    private void initPreferences() {
        // 普通偏好设置（与服务/接收器一致）
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // 加密偏好设置（与 AuthManager 一致）
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // 如果加密存储创建失败，回退到普通存储（保证可用）
            encryptedPrefs = prefs;
        }
    }

    // ==================== 凭据相关 ====================

    public void saveCredentials(String studentId, String casPassword, String libPassword) {
        encryptedPrefs.edit()
                .putString(Constants.PREF_USERNAME, studentId)
                .putString(Constants.PREF_EDU_PASSWORD, casPassword)
                .putString(Constants.PREF_LIB_PASSWORD, libPassword)
                .apply();
    }

    public String getStudentId() {
        String v = encryptedPrefs.getString(Constants.PREF_USERNAME, null);
        if (v == null) {
            v = encryptedPrefs.getString(LEGACY_STUDENT_ID, "");
        }
        return v == null ? "" : v;
    }

    public String getCasPassword() {
        String v = encryptedPrefs.getString(Constants.PREF_EDU_PASSWORD, null);
        if (v == null) {
            v = encryptedPrefs.getString(LEGACY_CAS_PASSWORD, "");
        }
        return v == null ? "" : v;
    }

    public String getLibPassword() {
        String v = encryptedPrefs.getString(Constants.PREF_LIB_PASSWORD, null);
        if (v == null) {
            v = encryptedPrefs.getString(LEGACY_LIB_PASSWORD, "");
        }
        return v == null ? "" : v;
    }

    public boolean hasValidCredentials() {
        String studentId = getStudentId();
        String casPassword = getCasPassword();
        String libPassword = getLibPassword();
        return studentId != null && !studentId.isEmpty()
                && casPassword != null && !casPassword.isEmpty()
                && libPassword != null && !libPassword.isEmpty()
                && isLoggedIn();
    }

    public void clearCredentials() {
        encryptedPrefs.edit()
                .remove(Constants.PREF_USERNAME)
                .remove(Constants.PREF_EDU_PASSWORD)
                .remove(Constants.PREF_LIB_PASSWORD)
                .remove(Constants.PREF_LAST_AUTH_TOKEN)
                .remove(Constants.PREF_LAST_AUTH_ACC_NO)
                .apply();
        setLoggedIn(false);
    }

    // ==================== 登录状态 ====================

    public void setLoggedIn(boolean loggedIn) {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, loggedIn).apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    // ==================== 图书馆Token ====================

    public void saveLibraryToken(String token, String accNo) {
        encryptedPrefs.edit()
                .putString(Constants.PREF_LAST_AUTH_TOKEN, token)
                .putString(Constants.PREF_LAST_AUTH_ACC_NO, accNo)
                .apply();
    }

    public String getLibraryToken() {
        return encryptedPrefs.getString(Constants.PREF_LAST_AUTH_TOKEN, "");
    }

    public String getLibraryAccNo() {
        return encryptedPrefs.getString(Constants.PREF_LAST_AUTH_ACC_NO, "");
    }

    // ==================== 预约设置 ====================

    public void setTargetArea(String areaKey) {
        prefs.edit().putString(Constants.PREF_TARGET_AREA, areaKey).apply();
    }

    public String getTargetArea() {
        String v = prefs.getString(Constants.PREF_TARGET_AREA, null);
        if (v == null) {
            v = prefs.getString(LEGACY_TARGET_AREA, Constants.DEFAULT_AREA);
        }
        return v == null ? Constants.DEFAULT_AREA : v;
    }

    public void setTargetSeat(int seatNumber) {
        prefs.edit().putInt(Constants.PREF_TARGET_SEAT, seatNumber).apply();
    }

    public int getTargetSeat() {
        int legacy = prefs.getInt(LEGACY_TARGET_SEAT, Constants.DEFAULT_SEAT);
        return prefs.getInt(Constants.PREF_TARGET_SEAT, legacy);
    }

    public void setStartTime(String time) {
        prefs.edit().putString(Constants.PREF_START_TIME, time).apply();
    }

    public String getStartTime() {
        String v = prefs.getString(Constants.PREF_START_TIME, null);
        if (v == null) {
            v = prefs.getString(LEGACY_START_TIME, Constants.DEFAULT_START_TIME);
        }
        return v == null ? Constants.DEFAULT_START_TIME : v;
    }

    public void setEndTime(String time) {
        prefs.edit().putString(Constants.PREF_END_TIME, time).apply();
    }

    public String getEndTime() {
        String v = prefs.getString(Constants.PREF_END_TIME, null);
        if (v == null) {
            v = prefs.getString(LEGACY_END_TIME, Constants.DEFAULT_END_TIME);
        }
        return v == null ? Constants.DEFAULT_END_TIME : v;
    }

    // ==================== 自动化设置 ====================

    public void setAutoReserveEnabled(boolean enabled) {
        prefs.edit().putBoolean(Constants.PREF_AUTO_RESERVE, enabled).apply();
    }

    public boolean isAutoReserveEnabled() {
        boolean legacy = prefs.getBoolean(LEGACY_AUTO_RESERVE_ENABLED, false);
        return prefs.getBoolean(Constants.PREF_AUTO_RESERVE, legacy);
    }

    public void setLateProtectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(Constants.PREF_PREVENT_LATE, enabled).apply();
    }

    public boolean isLateProtectionEnabled() {
        boolean legacy = prefs.getBoolean(LEGACY_LATE_PROTECTION_ENABLED, false);
        return prefs.getBoolean(Constants.PREF_PREVENT_LATE, legacy);
    }

    public void setAutoFindSeatEnabled(boolean enabled) {
        prefs.edit().putBoolean(Constants.PREF_AUTO_FIND_SEAT, enabled).apply();
    }

    public boolean isAutoFindSeatEnabled() {
        boolean legacy = prefs.getBoolean(LEGACY_AUTO_FIND_SEAT_ENABLED, false);
        return prefs.getBoolean(Constants.PREF_AUTO_FIND_SEAT, legacy);
    }

    // ==================== 外观设置 ====================

    public void setDarkModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(Constants.PREF_DARK_MODE, enabled).apply();
    }

    public boolean isDarkModeEnabled() {
        return prefs.getBoolean(Constants.PREF_DARK_MODE, false);
    }

    // ==================== 权限检查设置 ====================

    public void setHidePermissionCheck(boolean hide) {
        prefs.edit().putBoolean(Constants.PREF_HIDE_PERMISSION_CHECK, hide).apply();
    }

    public boolean isHidePermissionCheck() {
        return prefs.getBoolean(Constants.PREF_HIDE_PERMISSION_CHECK, false);
    }

    // ==================== 获取区域信息 ====================

    /**
     * 根据区域key获取区域名称
     */
    public String getAreaName(String areaKey) {
        Constants.AreaInfo areaInfo = Constants.SEAT_AREAS_MAP.get(areaKey);
        if (areaInfo != null) {
            return areaInfo.name;
        }
        return areaKey;
    }

    /**
     * 获取当前目标区域的信息
     */
    public Constants.AreaInfo getTargetAreaInfo() {
        return Constants.SEAT_AREAS_MAP.get(getTargetArea());
    }
}