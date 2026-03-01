import os
import sys
import time
import requests
import re
import urllib3
import logging
from bs4 import BeautifulSoup
import base64
import random
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.Util.Padding import pad
from Crypto.PublicKey import RSA
from datetime import datetime

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# User Config
STUDENT_ID = "2410403132"
EDU_PASSWORD = "Zhouwenjie@790920"
LIB_PASSWORD = "njfu23001x!"

class HttpClient:
    BASE_URL_PREFIX = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc="
    LIB_URL_SUFFIX = "/LjIwNS4xNTguMjAwLjE3MS4xNTMuMTUwLjIxNi45Ny4yMTEuMTU2LjE1OC4xNzMuMTQ4LjE1NS4xNTUuMjE3LjEwMC4xNTAuMTY1"
    EDU_URL_SUFFIX = "/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1"

    @staticmethod
    def get_lib_url(path):
        return f"{HttpClient.BASE_URL_PREFIX}{HttpClient.LIB_URL_SUFFIX}/{path}"

    @staticmethod
    def get_edu_url(path):
        return f"{HttpClient.BASE_URL_PREFIX}{HttpClient.EDU_URL_SUFFIX}/{path}"

def encrypt_cas_password(password, key):
    CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
    prefix = "".join(random.choice(CHARS) for _ in range(64))
    iv = "".join(random.choice(CHARS) for _ in range(16)).encode("utf-8")
    plaintext = (prefix + password).encode("utf-8")
    key_bytes = key.encode("utf-8")
    cipher = AES.new(key_bytes, AES.MODE_CBC, iv)
    ciphertext = cipher.encrypt(pad(plaintext, AES.block_size))
    return base64.b64encode(ciphertext).decode("utf-8")

def encrypt_lib_password(plaintext_password, nonce, public_key_str):
    if "-----BEGIN PUBLIC KEY-----" not in public_key_str:
        public_key_str = "-----BEGIN PUBLIC KEY-----\n" + public_key_str + "\n-----END PUBLIC KEY-----"
    rsa_key = RSA.importKey(public_key_str)
    cipher = PKCS1_v1_5.new(rsa_key)
    message = f"{plaintext_password};{nonce}".encode("utf-8")
    encrypted = cipher.encrypt(message)
    return base64.b64encode(encrypted).decode("utf-8")

def explore():
    session = requests.Session()
    session.verify = False
    session.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    })

    logger.info("=== 第一层认证 (Unified Auth) ===")
    
    # 1. 预访问
    home_url = "https://webvpn.njfu.edu.cn/"
    session.get(home_url, timeout=5)

    # 2. 获取 Route Cookie
    route_url = "https://webvpn.njfu.edu.cn/webvpn/cookie/?domain=uia.njfu.edu.cn&path=%2Fauthserver%2Flogin"
    r = session.get(route_url, timeout=5)
    match = re.search(r'route=([^;]+)', r.text)
    if match:
        route = match.group(1)
        session.cookies.set('route', route, domain='webvpn.njfu.edu.cn', path='/')
        logger.info(f"Got route cookie: {route}")
    
    # 3. 登录准备
    login_prepare_url = HttpClient.get_edu_url("authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F")
    resp = session.get(login_prepare_url, timeout=10)
    logger.info(f"Login prepare status: {resp.status_code}")
    
    soup = BeautifulSoup(resp.text, "html.parser")
    lt = soup.find("input", {"name": "lt"})["value"] if soup.find("input", {"name": "lt"}) else None
    salt = soup.find("input", {"id": "pwdDefaultEncryptSalt"})["value"] if soup.find("input", {"id": "pwdDefaultEncryptSalt"}) else None
    dllt = soup.find("input", {"name": "dllt"})["value"] if soup.find("input", {"name": "dllt"}) else None
    execution = soup.find("input", {"name": "execution"})["value"] if soup.find("input", {"name": "execution"}) else None
    event_id = soup.find("input", {"name": "_eventId"})["value"] if soup.find("input", {"name": "_eventId"}) else None
    rm_shown = soup.find("input", {"name": "rmShown"})["value"] if soup.find("input", {"name": "rmShown"}) else None

    logger.info(f"Extracted Salt: {salt}, LT: {lt}")

    # 4. 统一认证登录
    encrypted_password = encrypt_cas_password(EDU_PASSWORD, salt)
    login_url = HttpClient.get_edu_url("authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F")
    login_data = {
        "vpn-0": "",
        "service": "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/",
        "username": STUDENT_ID,
        "password": encrypted_password,
        "lt": lt,
        "dllt": dllt,
        "execution": execution,
        "_eventId": event_id,
        "rmShown": rm_shown
    }
    
    resp = session.post(login_url, data=login_data, allow_redirects=False, timeout=15)
    logger.info(f"Login POST status: {resp.status_code}")
    if resp.status_code == 302:
        loc = resp.headers.get("Location")
        logger.info(f"Redirect Location: {loc}")
        ticket_match = re.search(r'ticket=([^&]+)', loc)
        if ticket_match:
            ticket = ticket_match.group(1)
            logger.info(f"Got ticket: {ticket}")
            final_auth_url = f"https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/?ticket={ticket}"
            session.get(final_auth_url, timeout=10)
            logger.info(f"Cookies after CAS: {dict(session.cookies)}")

    logger.info("\n=== 第二层认证 (Library Auth) ===")
    
    # 1. 获取公钥
    pub_url = HttpClient.get_lib_url("ic-web/login/publicKey?vpn-12-libseat.njfu.edu.cn")
    resp = session.get(pub_url, headers={"accept": "application/json"}, timeout=10)
    pub_data = resp.json().get("data", {})
    public_key = pub_data.get("publicKey")
    nonce = pub_data.get("nonceStr")
    logger.info(f"Got library public key & nonce")

    # 2. 图书馆登录
    lib_pwd_enc = encrypt_lib_password(LIB_PASSWORD, nonce, public_key)
    lib_login_url = HttpClient.get_lib_url("ic-web/login/user?vpn-12-libseat.njfu.edu.cn")
    payload = {
        "logonName": STUDENT_ID,
        "password": lib_pwd_enc,
        "captcha": "",
        "consoleType": 16,
        "privacy": True
    }
    resp = session.post(lib_login_url, json=payload, headers={"accept": "application/json"}, timeout=15)
    res_data = resp.json()
    logger.info(f"Lib Login Result: {res_data}")

    token = None
    if res_data.get("code") == 0:
        user_data = res_data.get("data", {})
        token = user_data.get("token")
        logger.info(f"Successfully got Token: {token}")

    if not token:
        logger.error("Failed to get token, stopping exploration.")
        return

    logger.info("\n=== 探索座位预约信息 (Seat Exploration) ===")
    
    # 尝试获取所有区域的房间号，然后拉取当天数据
    AREAS = {
        '二层A区': 100455344, '二层B区': 100455346, '三层A区': 100455350,
        '三层B区': 100455352, '三层C区': 100455354, '三楼夹层': 111488386,
        '四层A区': 100455356, '四层夹层': 111488388, '五层A区': 100455358,
        '六层A区': 100455360, '七层北侧': 106658017, '七层南侧': 111488396,
    }
    
    today_str = datetime.now().strftime('%Y%m%d')
    api_headers = {
        "token": token,
        "lan": "1"
    }
    
    for area_name, room_id in AREAS.items():
        url = HttpClient.get_lib_url("ic-web/reserve")
        params = {
            "vpn-12-libseat.njfu.edu.cn": "",
            "roomIds": room_id,
            "resvDates": today_str,
            "sysKind": 8
        }
        resp = session.get(url, headers=api_headers, params=params, timeout=15)
        if resp.status_code == 200:
            data = resp.json().get("data", [])
            logger.info(f"{area_name} ({room_id}) - Found {len(data)} seats.")
            
            # Print sample of the first seat to analyze its properties
            if data and len(data) > 0:
                print(f"[Sample Seat Info for {area_name}]:")
                for k, v in data[0].items():
                    print(f"  {k}: {v}")
                break # Just sample one room closely for exploration

if __name__ == '__main__':
    explore()
