package com.keggin.fucknjfulib.network;
public class ApiConstants {
    public static final String BASE_URL = "https://webvpn.njfu.edu.cn";
    private static final String VPN_PREFIX = "/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc=";
    private static final String LIB_SUFFIX = "/LjIwNS4xNTguMjAwLjE3MS4xNTMuMTUwLjIxNi45Ny4yMTEuMTU2LjE1OC4xNzMuMTQ4LjE1NS4xNTUuMjE3LjEwMC4xNTAuMTY1";
    private static final String EDU_SUFFIX = "/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1";
    public static final String FRONTEND_LOGIN_URL = BASE_URL + "/rump_frontend/login/";
    public static final String ROUTE_COOKIE_URL = BASE_URL
            + "/webvpn/cookie/?domain=uia.njfu.edu.cn&path=%2Fauthserver%2Flogin";
    public static String getEduLoginPageUrl() {
        return BASE_URL + VPN_PREFIX + EDU_SUFFIX
                + "/authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F";
    }
    public static String getEduLoginSubmitUrl() {
        return BASE_URL + VPN_PREFIX + EDU_SUFFIX
                + "/authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F";
    }
    public static String getNeedCaptchaUrl(String username) {
        return BASE_URL + VPN_PREFIX + EDU_SUFFIX + "/authserver/needCaptcha.html?vpn-12-uia.njfu.edu.cn=&username="
                + username + "&_=" + System.currentTimeMillis();
    }
    public static String getCaptchaUrl() {
        return BASE_URL + VPN_PREFIX + EDU_SUFFIX + "/authserver/captcha.html?ts=" + System.currentTimeMillis();
    }
    public static String getFinalAuthUrl(String ticket) {
        return BASE_URL + "/rump_frontend/loginFromCas/?ticket=" + ticket;
    }
    public static String getPublicKeyUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/login/publicKey?vpn-12-libseat.njfu.edu.cn";
    }
    public static String getLibLoginUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/login/user?vpn-12-libseat.njfu.edu.cn";
    }
    public static String getReserveUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/reserve?vpn-12-libseat.njfu.edu.cn";
    }
    public static String getReservationInfoUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/reserve/resvInfo";
    }
    public static String getCancelReserveUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/reserve/delete";
    }
    public static String getReserveActionUrl(String action) {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/reserve/" + action + "?vpn-12-libseat.njfu.edu.cn";
    }
    public static String getSeatQueryUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/reserve";
    }
    public static String getUserInfoUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/auth/userInfo?vpn-12-libseat.njfu.edu.cn";
    }
    public static String getPunishInfoUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/reserve/punishInfo?vpn-12-libseat.njfu.edu.cn";
    }
    public static String getCreditRecUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/creditRec/getOwn?vpn-12-libseat.njfu.edu.cn";
    }
    public static String getCreditSurplusUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/creditPunishRec/surPlus?vpn-12-libseat.njfu.edu.cn";
    }
    public static String getSeatOperationUrl(String action) {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/seatOperation/" + action + "?vpn-12-libseat.njfu.edu.cn";
    }
    public static String getEndAheadUrl() {
        return BASE_URL + VPN_PREFIX + LIB_SUFFIX + "/ic-web/reserve/endAhaed?vpn-12-libseat.njfu.edu.cn";
    }
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final String ACCEPT_HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";
    public static final String ACCEPT_JSON = "application/json, text/plain, */*";
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
}