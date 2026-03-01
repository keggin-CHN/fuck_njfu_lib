import os
import requests
import re
import urllib3
import logging
from bs4 import BeautifulSoup
import base64
import random
import json
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.Util.Padding import pad
from Crypto.PublicKey import RSA
from datetime import datetime

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
logger = logging.getLogger(__name__)

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
    session.headers.update({"User-Agent": "Mozilla/5.0", "Accept": "application/json"})
    
    # Auth Flow
    session.get("https://webvpn.njfu.edu.cn/", timeout=5)
    r = session.get("https://webvpn.njfu.edu.cn/webvpn/cookie/?domain=uia.njfu.edu.cn&path=%2Fauthserver%2Flogin", timeout=5)
    match = re.search(r'route=([^;]+)', r.text)
    if match: session.cookies.set('route', match.group(1), domain='webvpn.njfu.edu.cn', path='/')
    
    login_prepare_url = HttpClient.get_edu_url("authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F")
    resp = session.get(login_prepare_url, timeout=10)
    soup = BeautifulSoup(resp.text, "html.parser")
    lt = soup.find("input", {"name": "lt"})["value"]
    salt = soup.find("input", {"id": "pwdDefaultEncryptSalt"})["value"]
    dllt = soup.find("input", {"name": "dllt"})["value"]
    execution = soup.find("input", {"name": "execution"})["value"]
    event_id = soup.find("input", {"name": "_eventId"})["value"]
    rm_shown = soup.find("input", {"name": "rmShown"})["value"]

    enc_pwd = encrypt_cas_password(EDU_PASSWORD, salt)
    login_url = HttpClient.get_edu_url("authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F")
    resp = session.post(login_url, data={
        "vpn-0": "", "service": "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/",
        "username": STUDENT_ID, "password": enc_pwd, "lt": lt, "dllt": dllt,
        "execution": execution, "_eventId": event_id, "rmShown": rm_shown
    }, allow_redirects=False, timeout=15)
    if resp.status_code == 302:
        ticket = re.search(r'ticket=([^&]+)', resp.headers.get("Location")).group(1)
        session.get(f"https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/?ticket={ticket}", timeout=10)

    pub_url = HttpClient.get_lib_url("ic-web/login/publicKey?vpn-12-libseat.njfu.edu.cn")
    pub_data = session.get(pub_url, timeout=10).json().get("data", {})
    
    lib_pwd_enc = encrypt_lib_password(LIB_PASSWORD, pub_data.get("nonceStr"), pub_data.get("publicKey"))
    lib_login_url = HttpClient.get_lib_url("ic-web/login/user?vpn-12-libseat.njfu.edu.cn")
    login_resp = session.post(lib_login_url, json={
        "logonName": STUDENT_ID, "password": lib_pwd_enc, "captcha": "", "consoleType": 16, "privacy": True
    }, timeout=15).json()
    token = login_resp.get("data", {}).get("token")
    
    if not token:
        logger.error("Token fetched failed!")
        return

    # Probing more endpoints!
    logger.info("=== 探索隐蔽API接口 ===")
    api_headers = {"token": token, "lan": "1"}
    
    endpoints_to_probe = [
        # 获取基础用户信息大类
        ("ic-web/auth/userInfo", "GET"),
        ("ic-web/userInfo", "GET"),
        
        # 违约与信用
        ("ic-web/violation", "GET"),
        ("ic-web/violation/list", "GET"),
        ("ic-web/credit", "GET"),
        ("ic-web/credit/score", "GET"),

        # 其他系统配置信息
        ("ic-web/system/roomList", "GET"), # 场馆列表（可以动态获取所有自习室，不用写死）
        ("ic-web/reserve/menu", "GET"), # 预约菜单
        ("ic-web/system/area", "GET"),
        
        # 签到打卡相关
        ("ic-web/checkIn", "GET"),
        ("ic-web/reserve/checkIn", "GET")
    ]
    
    with open("c:\\code\\fuck_njfu_lib\\explore\\probing_results.txt", "w", encoding="utf-8") as f:
        f.write("User info from login response:\n")
        f.write(json.dumps(login_resp.get("data", {}), indent=2, ensure_ascii=False))
        f.write("\n\n=== Probing API Endpoints ===\n")
        
        for endpoint, method in endpoints_to_probe:
            url = HttpClient.get_lib_url(f"{endpoint}?vpn-12-libseat.njfu.edu.cn")
            try:
                if method == "GET":
                    resp = session.get(url, headers=api_headers, timeout=5)
                f.write(f"\n--- [ {method} {endpoint} ] ---\n")
                f.write(f"Status Code: {resp.status_code}\n")
                
                if resp.status_code == 200:
                    try:
                        content = resp.json()
                        f.write(json.dumps(content, indent=2, ensure_ascii=False) + "\n")
                    except:
                        f.write(resp.text[:500] + "\n")
                elif resp.status_code == 404:
                    f.write("404 Not Found\n")
                else:
                    f.write(resp.text[:200] + "\n")
                    
            except Exception as e:
                f.write(f"Error: {str(e)}\n")

    logger.info("Probing complete. Results saved to probing_results.txt.")

if __name__ == '__main__':
    explore()
