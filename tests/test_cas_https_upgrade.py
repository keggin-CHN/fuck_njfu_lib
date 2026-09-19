"""回归测试：校内直连必须全程走 HTTPS，避免 CAS ticket 在 301 时丢失。"""
import os
import sys
import urllib.parse as up

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "server_api"))
sys.path.insert(0, os.path.join(ROOT, "backend"))

from utils import auth_manager as am  # noqa: E402

CAS_URL = (
    "https://uia.njfu.edu.cn/authserver/login?service=http%3A%2F%2Flibseat.njfu.edu.cn"
    "%2Fic-web%2Fauth%2Fcas%3FfinalAddress%3Dhttp%253A%252F%252Flibseat.njfu.edu.cn%252F"
    "%26errPageUrl%3Dhttp%253A%252F%252Flibseat.njfu.edu.cn%252F%2523%252Ferror"
    "%26manager%3Dfalse%26consoleType%3D16"
)


def test_get_lib_url_uses_https_in_direct_mode():
    original = am.Config.DIRECT_CAMPUS_MODE
    am.Config.DIRECT_CAMPUS_MODE = True
    try:
        url = am.HttpClient.get_lib_url("ic-web/auth/userInfo")
        assert url.startswith("https://libseat.njfu.edu.cn/"), url
        # 直连模式必须剥掉 WebVPN 参数
        assert "vpn-12-libseat" not in url, url
        url2 = am.HttpClient.get_lib_url("ic-web/reserve?resvDates=20260919&vpn-12-libseat.njfu.edu.cn")
        assert url2 == "https://libseat.njfu.edu.cn/ic-web/reserve?resvDates=20260919", url2
    finally:
        am.Config.DIRECT_CAMPUS_MODE = original


def test_rewrite_cas_service_https_upgrades_outer_and_inner():
    out = am.HttpClient.rewrite_cas_service_https(CAS_URL)
    assert out.startswith("https://uia.njfu.edu.cn/authserver/login?"), out
    # 外层 service 的编码值不能还是 http
    assert "service=http%3A%2F%2Flibseat" not in out, out
    service = up.parse_qs(up.urlparse(out).query)["service"][0]
    assert service.startswith("https://libseat.njfu.edu.cn/"), service
    inner = up.parse_qs(up.urlparse(service).query)
    assert inner["finalAddress"][0].startswith("https://libseat.njfu.edu.cn/"), inner["finalAddress"]
    assert inner["errPageUrl"][0].startswith("https://libseat.njfu.edu.cn/"), inner["errPageUrl"]
    assert inner["manager"][0] == "false", inner
    assert inner["consoleType"][0] == "16", inner


def test_rewrite_keeps_webvpn_service_untouched():
    url = "https://uia.njfu.edu.cn/authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
    out = am.HttpClient.rewrite_cas_service_https(url)
    service = up.parse_qs(up.urlparse(out).query)["service"][0]
    assert service == "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/", service


if __name__ == "__main__":
    test_get_lib_url_uses_https_in_direct_mode()
    test_rewrite_cas_service_https_upgrades_outer_and_inner()
    test_rewrite_keeps_webvpn_service_untouched()
    print("OK: 校内直连 HTTPS 回归测试全部通过")
