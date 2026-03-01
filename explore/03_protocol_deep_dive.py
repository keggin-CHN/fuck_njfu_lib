import os
import re
import json
import time
import base64
import random
import logging
from datetime import datetime, timedelta
from urllib.parse import urlparse, parse_qs

import requests
import urllib3
from bs4 import BeautifulSoup
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.Util.Padding import pad
from Crypto.PublicKey import RSA

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(message)s"
)
logger = logging.getLogger("protocol_probe")

# ===== 可通过环境变量覆盖 =====
STUDENT_ID = os.getenv("NJFU_STUDENT_ID", "2410403132")
EDU_PASSWORD = os.getenv("NJFU_EDU_PASSWORD", "Zhouwenjie@790920")
LIB_PASSWORD = os.getenv("NJFU_LIB_PASSWORD", "njfu23001x!")
TARGET_URL = os.getenv(
    "NJFU_TARGET_URL",
    "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc=/LjIwNS4xNTguMjAwLjE3MS4xNTMuMTUwLjIxNi45Ny4yMTEuMTU2LjE1OC4xNzMuMTQ4LjE1NS4xNTUuMjE3LjEwMC4xNTAuMTY1/?vpn-0#/ic/home"
)

BASE_URL_PREFIX = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc="
LIB_URL_SUFFIX = "/LjIwNS4xNTguMjAwLjE3MS4xNTMuMTUwLjIxNi45Ny4yMTEuMTU2LjE1OC4xNzMuMTQ4LjE1NS4xNTUuMjE3LjEwMC4xNTAuMTY1"
EDU_URL_SUFFIX = "/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1"

OUT_DIR = os.path.join("explore", "probe_03")
os.makedirs(OUT_DIR, exist_ok=True)


def get_lib_url(path: str) -> str:
    return f"{BASE_URL_PREFIX}{LIB_URL_SUFFIX}/{path}"


def get_edu_url(path: str) -> str:
    return f"{BASE_URL_PREFIX}{EDU_URL_SUFFIX}/{path}"


def encrypt_cas_password(password: str, key: str) -> str:
    chars = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
    prefix = "".join(random.choice(chars) for _ in range(64))
    iv = "".join(random.choice(chars) for _ in range(16)).encode("utf-8")
    plaintext = (prefix + password).encode("utf-8")
    key_bytes = key.encode("utf-8")
    cipher = AES.new(key_bytes, AES.MODE_CBC, iv)
    ciphertext = cipher.encrypt(pad(plaintext, AES.block_size))
    return base64.b64encode(ciphertext).decode("utf-8")


def encrypt_lib_password(plaintext_password: str, nonce: str, public_key_str: str) -> str:
    if "-----BEGIN PUBLIC KEY-----" not in public_key_str:
        public_key_str = "-----BEGIN PUBLIC KEY-----\n" + public_key_str + "\n-----END PUBLIC KEY-----"
    rsa_key = RSA.importKey(public_key_str)
    cipher = PKCS1_v1_5.new(rsa_key)
    message = f"{plaintext_password};{nonce}".encode("utf-8")
    encrypted = cipher.encrypt(message)
    return base64.b64encode(encrypted).decode("utf-8")


def mask_secret(s: str, keep=2) -> str:
    if not s:
        return ""
    if len(s) <= keep * 2:
        return "*" * len(s)
    return s[:keep] + "*" * (len(s) - keep * 2) + s[-keep:]


class ProbeRunner:
    def __init__(self):
        self.session = requests.Session()
        self.session.verify = False
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
        })
        self.trace = []
        self.summary = {
            "target_url": TARGET_URL,
            "credentials": {
                "student_id": STUDENT_ID,
                "edu_password_masked": mask_secret(EDU_PASSWORD),
                "lib_password_masked": mask_secret(LIB_PASSWORD)
            },
            "steps": {},
            "tokens": {},
            "endpoints": [],
            "seat_stats_today": {},
            "fields_catalog": {},
            "samples": {}
        }

    def record(self, step_name: str, response: requests.Response, note: str = ""):
        location = response.headers.get("Location")
        content_type = response.headers.get("Content-Type", "")
        entry = {
            "step": step_name,
            "note": note,
            "method": response.request.method if response.request else "",
            "url": response.url,
            "status_code": response.status_code,
            "content_type": content_type,
            "location": location,
            "headers_subset": {
                "server": response.headers.get("Server"),
                "set-cookie": response.headers.get("Set-Cookie"),
                "content-length": response.headers.get("Content-Length"),
                "cache-control": response.headers.get("Cache-Control")
            },
            "cookies_snapshot": requests.utils.dict_from_cookiejar(self.session.cookies),
            "response_preview": (response.text[:300] if "text" in content_type or "json" in content_type else "")
        }
        self.trace.append(entry)

    @staticmethod
    def parse_page_structure(html: str):
        soup = BeautifulSoup(html, "html.parser")
        forms = []
        for f in soup.find_all("form"):
            hidden_fields = {}
            for inp in f.find_all("input", {"type": "hidden"}):
                name = inp.get("name") or inp.get("id") or ""
                hidden_fields[name] = inp.get("value")
            forms.append({
                "action": f.get("action"),
                "method": (f.get("method") or "GET").upper(),
                "id": f.get("id"),
                "name": f.get("name"),
                "hidden_fields": hidden_fields
            })

        scripts = []
        for s in soup.find_all("script"):
            src = s.get("src")
            if src:
                scripts.append({"src": src, "inline": False})
            else:
                txt = (s.get_text() or "").strip()
                scripts.append({"src": None, "inline": True, "inline_preview": txt[:120]})

        meta_refresh = []
        for m in soup.find_all("meta"):
            if (m.get("http-equiv") or "").lower() == "refresh":
                meta_refresh.append(m.get("content"))

        return {
            "title": (soup.title.string.strip() if soup.title and soup.title.string else ""),
            "forms": forms,
            "scripts_count": len(scripts),
            "scripts": scripts[:40],
            "meta_refresh": meta_refresh
        }

    def step_1_observe_target(self):
        logger.info("Step1: 观察目标页面结构")
        r = self.session.get(TARGET_URL, allow_redirects=False, timeout=15)
        self.record("step1_target_page", r)
        html_path = os.path.join(OUT_DIR, "step1_target_page.html")
        with open(html_path, "w", encoding="utf-8") as f:
            f.write(r.text)

        parsed = self.parse_page_structure(r.text)
        self.summary["steps"]["target_page"] = {
            "status": r.status_code,
            "location": r.headers.get("Location"),
            "parsed": parsed
        }

    def step_2_prepare_cas(self):
        logger.info("Step2: 获取 route cookie + CAS 登录页参数")
        home = self.session.get("https://webvpn.njfu.edu.cn/", timeout=10)
        self.record("step2_webvpn_home", home)

        route_url = "https://webvpn.njfu.edu.cn/webvpn/cookie/?domain=uia.njfu.edu.cn&path=%2Fauthserver%2Flogin"
        rr = self.session.get(route_url, timeout=10)
        self.record("step2_route_cookie", rr)

        route_match = re.search(r"route=([^;]+)", rr.text or "")
        route_value = route_match.group(1) if route_match else None
        if route_value:
            self.session.cookies.set("route", route_value, domain="webvpn.njfu.edu.cn", path="/")

        login_prepare_url = get_edu_url(
            "authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
        )
        lp = self.session.get(login_prepare_url, timeout=15)
        self.record("step2_cas_login_page", lp)

        with open(os.path.join(OUT_DIR, "step2_cas_login_page.html"), "w", encoding="utf-8") as f:
            f.write(lp.text)

        parsed = self.parse_page_structure(lp.text)
        soup = BeautifulSoup(lp.text, "html.parser")

        def val(name, by_id=False):
            if by_id:
                e = soup.find("input", {"id": name})
            else:
                e = soup.find("input", {"name": name})
            return e.get("value") if e else None

        form_params = {
            "lt": val("lt"),
            "pwdDefaultEncryptSalt": val("pwdDefaultEncryptSalt", by_id=True),
            "dllt": val("dllt"),
            "execution": val("execution"),
            "_eventId": val("_eventId"),
            "rmShown": val("rmShown")
        }

        self.summary["steps"]["cas_prepare"] = {
            "route": route_value,
            "status": lp.status_code,
            "parsed": parsed,
            "form_params_found": {k: bool(v) for k, v in form_params.items()}
        }

        return form_params

    def step_3_need_captcha(self, username: str, salt: str):
        logger.info("Step3: 检测是否需要验证码")
        url = get_edu_url("authserver/needCaptcha.html")
        params = {
            "vpn-12-uia.njfu.edu.cn": "",
            "username": username,
            "pwdEncrypt2": salt,
            "_": str(int(time.time() * 1000))
        }
        r = self.session.get(url, params=params, headers={"X-Requested-With": "XMLHttpRequest"}, timeout=10)
        self.record("step3_need_captcha", r)
        need = (r.text or "").strip().lower() == "true"
        self.summary["steps"]["need_captcha"] = {"need_captcha": need, "response_text": (r.text or "").strip()}
        return need

    def step_4_cas_login(self, form_params: dict):
        logger.info("Step4: 提交统一认证登录")
        salt = form_params["pwdDefaultEncryptSalt"]
        encrypted_password = encrypt_cas_password(EDU_PASSWORD, salt)

        login_submit_url = get_edu_url(
            "authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
        )
        payload = {
            "vpn-0": "",
            "service": "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/",
            "username": STUDENT_ID,
            "password": encrypted_password,
            "lt": form_params["lt"],
            "dllt": form_params["dllt"],
            "execution": form_params["execution"],
            "_eventId": form_params["_eventId"],
            "rmShown": form_params["rmShown"]
        }

        r = self.session.post(
            login_submit_url,
            data=payload,
            headers={
                "Origin": "https://webvpn.njfu.edu.cn",
                "Referer": get_edu_url("authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"),
                "Content-Type": "application/x-www-form-urlencoded"
            },
            allow_redirects=False,
            timeout=20
        )
        self.record("step4_cas_login_submit", r)

        ticket = None
        location = r.headers.get("Location", "")
        m = re.search(r"ticket=([^&]+)", location)
        if m:
            ticket = m.group(1)

        self.summary["steps"]["cas_login"] = {
            "status": r.status_code,
            "location": location,
            "ticket_found": bool(ticket)
        }

        if not ticket:
            return None

        callback_url = f"https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/?ticket={ticket}"
        cb = self.session.get(callback_url, timeout=15)
        self.record("step4_cas_ticket_callback", cb)

        self.summary["steps"]["cas_ticket_callback"] = {
            "status": cb.status_code,
            "my_client_ticket_exists": bool(self.session.cookies.get("my_client_ticket"))
        }

        return ticket

    def step_5_library_login(self):
        logger.info("Step5: 图书馆二级认证")
        pub_url = get_lib_url("ic-web/login/publicKey?vpn-12-libseat.njfu.edu.cn")
        pr = self.session.get(pub_url, headers={"accept": "application/json, text/plain, */*"}, timeout=15)
        self.record("step5_library_public_key", pr)

        pub_json = pr.json()
        pub_data = pub_json.get("data", {}) if isinstance(pub_json, dict) else {}
        public_key = pub_data.get("publicKey")
        nonce = pub_data.get("nonceStr")

        if not public_key or not nonce:
            self.summary["steps"]["library_public_key"] = {"ok": False, "response": pub_json}
            return None, None

        enc_lib_pwd = encrypt_lib_password(LIB_PASSWORD, nonce, public_key)
        login_url = get_lib_url("ic-web/login/user?vpn-12-libseat.njfu.edu.cn")
        payload = {
            "logonName": STUDENT_ID,
            "password": enc_lib_pwd,
            "captcha": "",
            "consoleType": 16,
            "privacy": True
        }
        lr = self.session.post(
            login_url,
            json=payload,
            headers={
                "accept": "application/json, text/plain, */*",
                "content-type": "application/json;charset=UTF-8"
            },
            timeout=20
        )
        self.record("step5_library_login", lr)

        j = lr.json()
        ok = isinstance(j, dict) and j.get("code") == 0
        token = j.get("data", {}).get("token") if ok else None
        acc_no = j.get("data", {}).get("accNo") if ok else None

        self.summary["steps"]["library_login"] = {
            "ok": ok,
            "code": j.get("code") if isinstance(j, dict) else None,
            "message": j.get("message") if isinstance(j, dict) else None
        }
        self.summary["tokens"] = {
            "token_exists": bool(token),
            "acc_no": acc_no
        }
        return token, acc_no

    def step_6_query_reservations(self, token: str):
        logger.info("Step6: 查询个人预约")
        today = datetime.now().strftime("%Y-%m-%d")
        tomorrow = (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%d")
        url = get_lib_url("ic-web/reserve/resvInfo")
        params = {
            "vpn-12-libseat.njfu.edu.cn": "",
            "needStatus": "8454",
            "unneedStatus": "128",
            "beginDate": today,
            "endDate": tomorrow
        }
        r = self.session.get(url, params=params, headers={"token": token, "lan": "1"}, timeout=15)
        self.record("step6_resv_info", r)
        j = r.json()
        data = j.get("data") if isinstance(j, dict) else None
        with open(os.path.join(OUT_DIR, "my_reservations_today_tomorrow.json"), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        self.summary["steps"]["my_reservations"] = {
            "code": j.get("code") if isinstance(j, dict) else None,
            "message": j.get("message") if isinstance(j, dict) else None,
            "count": (len(data) if isinstance(data, list) else None)
        }

    def step_7_query_all_areas(self, token: str):
        logger.info("Step7: 拉取全部区域座位数据并统计字段")
        areas = {
            "二层A区": 100455344,
            "二层B区": 100455346,
            "三层A区": 100455350,
            "三层B区": 100455352,
            "三层C区": 100455354,
            "三楼夹层": 111488386,
            "四层A区": 100455356,
            "四层夹层": 111488388,
            "五层A区": 100455358,
            "六层A区": 100455360,
            "七层北侧": 106658017,
            "七层南侧": 111488396
        }

        today_compact = datetime.now().strftime("%Y%m%d")
        seat_url = get_lib_url("ic-web/reserve")
        stats = {}
        all_seats = []
        sample_occupied = None
        sample_available = None

        for area_name, room_id in areas.items():
            params = {
                "vpn-12-libseat.njfu.edu.cn": "",
                "roomIds": room_id,
                "resvDates": today_compact,
                "sysKind": 8
            }
            r = self.session.get(
                seat_url,
                params=params,
                headers={"token": token, "lan": "1", "Accept": "application/json, text/plain, */*"},
                timeout=20
            )
            self.record(f"step7_seat_query_{area_name}", r)
            j = r.json()
            seats = j.get("data", []) if isinstance(j, dict) and j.get("code") == 0 else []
            occupied = 0
            for s in seats:
                if s.get("resvInfo"):
                    occupied += 1
                    if sample_occupied is None:
                        sample_occupied = s
                else:
                    if sample_available is None:
                        sample_available = s
            stats[area_name] = {
                "roomId": room_id,
                "total": len(seats),
                "occupied": occupied,
                "available": len(seats) - occupied
            }
            all_seats.extend(seats)

        with open(os.path.join(OUT_DIR, "all_areas_stats_today.json"), "w", encoding="utf-8") as f:
            json.dump(stats, f, ensure_ascii=False, indent=2)
        with open(os.path.join(OUT_DIR, "sample_occupied_seat.json"), "w", encoding="utf-8") as f:
            json.dump(sample_occupied, f, ensure_ascii=False, indent=2)
        with open(os.path.join(OUT_DIR, "sample_available_seat.json"), "w", encoding="utf-8") as f:
            json.dump(sample_available, f, ensure_ascii=False, indent=2)

        fields_catalog = self.build_fields_catalog(all_seats)
        with open(os.path.join(OUT_DIR, "fields_catalog.json"), "w", encoding="utf-8") as f:
            json.dump(fields_catalog, f, ensure_ascii=False, indent=2)

        self.summary["seat_stats_today"] = stats
        self.summary["fields_catalog"] = fields_catalog
        self.summary["samples"] = {
            "sample_occupied_devId": (sample_occupied or {}).get("devId"),
            "sample_available_devId": (sample_available or {}).get("devId")
        }

    @staticmethod
    def build_fields_catalog(all_seats):
        seat_keys = set()
        resv_info_keys = set()
        resv_rule_keys = set()
        open_times_keys = set()
        point_size_keys = set()
        text_size_keys = set()

        for seat in all_seats:
            seat_keys.update(seat.keys())
            rr = seat.get("resvRule")
            if isinstance(rr, dict):
                resv_rule_keys.update(rr.keys())

            for item in seat.get("resvInfo", []) or []:
                if isinstance(item, dict):
                    resv_info_keys.update(item.keys())

            for item in seat.get("openTimes", []) or []:
                if isinstance(item, dict):
                    open_times_keys.update(item.keys())

            ps = seat.get("pointSize")
            if isinstance(ps, dict):
                point_size_keys.update(ps.keys())

            ts = seat.get("textSize")
            if isinstance(ts, dict):
                text_size_keys.update(ts.keys())

        return {
            "seat_top_level_keys": sorted(seat_keys),
            "resvInfo_keys": sorted(resv_info_keys),
            "resvRule_keys": sorted(resv_rule_keys),
            "openTimes_keys": sorted(open_times_keys),
            "pointSize_subkeys": sorted(point_size_keys),
            "textSize_subkeys": sorted(text_size_keys)
        }

    def step_8_emit_artifacts(self):
        logger.info("Step8: 输出 trace / summary / markdown 报告")
        with open(os.path.join(OUT_DIR, "network_trace.json"), "w", encoding="utf-8") as f:
            json.dump(self.trace, f, ensure_ascii=False, indent=2)

        endpoint_rows = []
        seen = set()
        for item in self.trace:
            key = (item.get("method"), item.get("url").split("?")[0])
            if key in seen:
                continue
            seen.add(key)
            endpoint_rows.append({
                "method": item.get("method"),
                "endpoint": item.get("url").split("?")[0],
                "sample_status": item.get("status_code")
            })
        self.summary["endpoints"] = endpoint_rows

        with open(os.path.join(OUT_DIR, "summary.json"), "w", encoding="utf-8") as f:
            json.dump(self.summary, f, ensure_ascii=False, indent=2)

        md = self.render_markdown_report()
        with open(os.path.join(OUT_DIR, "exploration_report.md"), "w", encoding="utf-8") as f:
            f.write(md)

    def render_markdown_report(self) -> str:
        lines = []
        lines.append("# Probe 03 深度协议探索报告")
        lines.append("")
        lines.append(f"- 目标URL: `{TARGET_URL}`")
        lines.append(f"- 探测时间: `{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}`")
        lines.append(f"- 学号: `{STUDENT_ID}`")
        lines.append(f"- 统一认证密码(掩码): `{mask_secret(EDU_PASSWORD)}`")
        lines.append(f"- 图书馆密码(掩码): `{mask_secret(LIB_PASSWORD)}`")
        lines.append("")
        lines.append("## 1) 登录链路结论")
        lines.append("")
        lines.append("1. 访问 WebVPN / 目标页，初始化 cookie。")
        lines.append("2. 调用 `/webvpn/cookie/?domain=uia.njfu.edu.cn&path=%2Fauthserver%2Flogin` 获取 `route`。")
        lines.append("3. 打开统一认证登录页，解析隐藏字段：`lt`、`pwdDefaultEncryptSalt`、`execution`、`_eventId` 等。")
        lines.append("4. 通过 `authserver/needCaptcha.html` 检查验证码要求。")
        lines.append("5. CAS 登录 POST 成功后 302，`Location` 中拿到 `ticket`。")
        lines.append("6. 请求 `/rump_frontend/loginFromCas/?ticket=...` 完成 WebVPN-CAS 票据跳转。")
        lines.append("7. 调用 `ic-web/login/publicKey` 获取 RSA 公钥与 nonce。")
        lines.append("8. 图书馆登录 `ic-web/login/user`，拿到 `token` + `accNo`。")
        lines.append("9. 携带 `token` 访问 `ic-web/reserve` / `ic-web/reserve/resvInfo` 完成预约数据查询。")
        lines.append("")
        lines.append("## 2) 座位查询统计（今日）")
        lines.append("")
        lines.append("| 区域 | 总数 | 占用 | 可用 |")
        lines.append("|---|---:|---:|---:|")
        for area, s in self.summary.get("seat_stats_today", {}).items():
            lines.append(f"| {area} | {s.get('total', 0)} | {s.get('occupied', 0)} | {s.get('available', 0)} |")
        lines.append("")
        lines.append("## 3) 字段全量目录（结构层）")
        lines.append("")
        cat = self.summary.get("fields_catalog", {})
        lines.append(f"- seat 顶层字段数: `{len(cat.get('seat_top_level_keys', []))}`")
        lines.append(f"- resvInfo 字段数: `{len(cat.get('resvInfo_keys', []))}`")
        lines.append(f"- resvRule 字段数: `{len(cat.get('resvRule_keys', []))}`")
        lines.append("")
        lines.append("### seat 顶层字段")
        lines.append("```json")
        lines.append(json.dumps(cat.get("seat_top_level_keys", []), ensure_ascii=False, indent=2))
        lines.append("```")
        lines.append("")
        lines.append("### resvInfo 字段")
        lines.append("```json")
        lines.append(json.dumps(cat.get("resvInfo_keys", []), ensure_ascii=False, indent=2))
        lines.append("```")
        lines.append("")
        lines.append("### resvRule 字段")
        lines.append("```json")
        lines.append(json.dumps(cat.get("resvRule_keys", []), ensure_ascii=False, indent=2))
        lines.append("```")
        lines.append("")
        lines.append("## 4) 产物文件")
        lines.append("")
        lines.append("- `probe_03/step1_target_page.html`")
        lines.append("- `probe_03/step2_cas_login_page.html`")
        lines.append("- `probe_03/network_trace.json`")
        lines.append("- `probe_03/summary.json`")
        lines.append("- `probe_03/all_areas_stats_today.json`")
        lines.append("- `probe_03/my_reservations_today_tomorrow.json`")
        lines.append("- `probe_03/sample_occupied_seat.json`")
        lines.append("- `probe_03/sample_available_seat.json`")
        lines.append("- `probe_03/fields_catalog.json`")
        lines.append("")
        return "\n".join(lines)

    def run(self):
        self.step_1_observe_target()
        form_params = self.step_2_prepare_cas()
        need_captcha = self.step_3_need_captcha(STUDENT_ID, form_params["pwdDefaultEncryptSalt"])
        if need_captcha:
            logger.warning("当前账号触发验证码，脚本默认不处理图形验证码流程，已终止后续步骤。")
            self.summary["steps"]["halt_reason"] = "need_captcha"
            self.step_8_emit_artifacts()
            return

        ticket = self.step_4_cas_login(form_params)
        if not ticket:
            logger.error("CAS 登录未拿到 ticket，流程终止。")
            self.summary["steps"]["halt_reason"] = "no_ticket"
            self.step_8_emit_artifacts()
            return

        token, acc_no = self.step_5_library_login()
        if not token or not acc_no:
            logger.error("图书馆登录未拿到 token/accNo，流程终止。")
            self.summary["steps"]["halt_reason"] = "no_token_or_acc_no"
            self.step_8_emit_artifacts()
            return

        self.step_6_query_reservations(token)
        self.step_7_query_all_areas(token)
        self.step_8_emit_artifacts()
        logger.info("探索完成，结果已写入 explore/probe_03/")


if __name__ == "__main__":
    runner = ProbeRunner()
    runner.run()