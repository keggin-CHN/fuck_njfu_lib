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
import json
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.Util.Padding import pad
from Crypto.PublicKey import RSA
from datetime import datetime

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
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
        "User-Agent": "Mozilla/5.0",
        "Accept": "application/json"
    })
    
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
    resp = session.post(lib_login_url, json={
        "logonName": STUDENT_ID, "password": lib_pwd_enc, "captcha": "", "consoleType": 16, "privacy": True
    }, timeout=15).json()
    token = resp.get("data", {}).get("token")
    
    if not token:
        logger.error("Token fetched failed!")
        return

    # Deep Explore
    AREAS = {
        '二层A区': 100455344, '二层B区': 100455346, '三层A区': 100455350,
        '三层B区': 100455352, '三层C区': 100455354, '三楼夹层': 111488386,
        '四层A区': 100455356, '四层夹层': 111488388, '五层A区': 100455358,
        '六层A区': 100455360, '七层北侧': 106658017, '七层南侧': 111488396,
    }
    today_str = datetime.now().strftime('%Y%m%d')
    api_headers = {"token": token, "lan": "1"}
    
    results = {}
    sample_occupied_seat = None

    for area_name, room_id in AREAS.items():
        url = HttpClient.get_lib_url("ic-web/reserve")
        resp = session.get(url, headers=api_headers, params={
            "vpn-12-libseat.njfu.edu.cn": "", "roomIds": room_id, "resvDates": today_str, "sysKind": 8
        }, timeout=15)
        
        if resp.status_code == 200:
            seats = resp.json().get("data", [])
            total = len(seats)
            occupied = 0
            
            for seat in seats:
                resv_info = seat.get("resvInfo", [])
                if len(resv_info) > 0:
                    occupied += 1
                    if sample_occupied_seat is None:
                        sample_occupied_seat = seat

            results[area_name] = {
                "total": total,
                "occupied": occupied,
                "available": total - occupied
            }
            logger.info(f"[{area_name}] Total: {total}, Occupied: {occupied}, Available: {total - occupied}")

    # Output details of sample occupied seat
    with open("c:\\code\\fuck_njfu_lib\\explore\\sample_occupied_seat.json", "w", encoding="utf-8") as f:
        json.dump(sample_occupied_seat, f, ensure_ascii=False, indent=2)

    with open("c:\\code\\fuck_njfu_lib\\explore\\all_areas_stats.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    logger.info("Exploration data saved to sample_occupied_seat.json and all_areas_stats.json")

    # Look into personal reservations history
    history_url = HttpClient.get_lib_url("ic-web/reserve/resvInfo")
    r = session.get(history_url, headers=api_headers, params={
        "vpn-12-libseat.njfu.edu.cn": "", "needStatus": "8454", "unneedStatus": "128",
        "beginDate": today_str, "endDate": today_str
    })
    if r.status_code == 200:
        with open("c:\\code\\fuck_njfu_lib\\explore\\my_reservations.json", "w", encoding="utf-8") as f:
            json.dump(r.json().get("data", []), f, ensure_ascii=False, indent=2)
        logger.info("Personal reservations saved to my_reservations.json")

if __name__ == '__main__':
    explore()
