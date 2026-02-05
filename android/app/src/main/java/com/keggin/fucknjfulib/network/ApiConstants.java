package com.keggin.fucknjfulib.network;

/**
 * API地址常量
 * 所有请求都通过 WebVPN 中转
 */
public class ApiConstants {

    // WebVPN 基础URL
    public static final String BASE_URL = "https://webvpn.njfu.edu.cn";
    
    // WebVPN 路径前缀
    private static final String VPN_PREFIX = "/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc=";
    
    // 图书馆系统路径后缀
    private static final String LIB_SUFFIX = "/LjIwNS4xNTguMjAwLjE3MS4xNTMuMTUwLjIxNi45Ny4yMTEuMTU2LjE1OC4xNzMuMTQ4LjE1NS4xNTUuMjE3LjEwMC4xNTAuMTY1";
    
    // 统一认证系统路径后缀
    private static final String EDU_SUFFIX = "/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1";

    // ==================== 统一认证相关 ====================
    
    /**
     * 获取初始 client ticket
     */
    public static final String FRONTEND_LOGIN_URL = BASE_URL + "/rump_frontend/login/";
    
    /**
     * 获取 route cookie
     */
    public static final String ROUTE_COOKIE_URL = BASE_URL + "/webvpn/cookie/?domain=uia.njfu.edu.cn&path=%2Fauthserver%2Flogin";
    
    /**
     * 统一认证登录页面（通过WebVPN）
     */
    public static String getEduLoginPageUrl() {
        return BASE_URL + VPN_PREFIX + EDU_SUFFIX + "/authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F";
    }
    
    /**
     * 统一认证登录提交URL
     */
    public static String getEduLoginSubmitUrl() {
        return BASE_URL + VPN_PREFIX + EDU_SUFFIX + "/authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F";
    }
    
    /**
     * 检查是否需要验证码
     */
    public static String getNeedCaptchaUrl(String username, String salt) {
        return BASE_URL + VPN_PREFIX + EDU_SUFFIX + "/authserver/needCaptcha.html?vpn-12-uia.njfu.edu.cn=&username=" 
               + username + "&pwdEncrypt2=" + salt + "&_=" + System.currentTimeMillis();
    }
    
    /**
     * 获取验证码图片
     */
    public static String getCaptchaUrl() {
        return BASE_URL + VPN_PREFIX + EDU_SUFFIX + "/authserver/captcha.html?ts=" + System.currentTimeMillis();
    }
    
    /**
     * CAS ticket 兑换 URL
     */
    public static String getFinalAuthUrl(String ticket) {
        return BASE_URL + "/rump_frontend/loginFromCas/?ticket=" + ticket;
    }

    // ==================== 图书馆系统相关 ====================
    
    /**
     * 获取公钥（用于密码加密）
     */
    public static String getPublicKeyUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/login/publicKey?vpn-12-libseat.njfu.edu.cn";
    }
    
    /**
     * 图书馆登录
     */
    public static String getLibLoginUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/login/user?vpn-12-libseat.njfu.edu.cn";
    }
    
    /**
     * 座位预约
     */
    public static String getReserveUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/reserve?vpn-12-libseat.njfu.edu.cn";
    }
    
    /**
     * 获取预约信息
     */
    public static String getReservationInfoUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/reserve/resvInfo";
    }
    
    /**
     * 取消预约
     */
    public static String getCancelReserveUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/reserve/delete";
    }
    
    /**
     * 座位查询（根据房间ID和日期）
     */
    public static String getSeatQueryUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/reserve";
    }

    // ==================== 请求Header常量 ====================
    
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    
    public static final String ACCEPT_HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";
    
    public static final String ACCEPT_JSON = "application/json, text/plain, */*";
    
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
}