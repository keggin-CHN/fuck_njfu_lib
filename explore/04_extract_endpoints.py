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

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
logger = logging.getLogger(__name__)

STUDENT_ID = "2410403132"
EDU_PASSWORD = "Zhouwenjie@790920"

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

def explore_js():
    session = requests.Session()
    session.verify = False
    session.headers.update({"User-Agent": "Mozilla/5.0", "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"})
    
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

    # Now fetch the library index
    lib_front_url = HttpClient.get_lib_url("")
    logger.info(f"Fetching library frontend from {lib_front_url}")
    resp = session.get(lib_front_url, timeout=10)
    
    soup = BeautifulSoup(resp.text, "html.parser")
    scripts = soup.find_all("script", src=True)
    
    js_urls = []
    for s in scripts:
        src = s["src"]
        if src.endswith('.js'):
            if src.startswith('/'): src = src[1:]
            if src.startswith('http'):
                js_urls.append(src)
            else:
                js_urls.append(HttpClient.get_lib_url(src))

    if not js_urls:
         logger.warning("No JS files found! Dumping HTML for inspection.")
         with open("c:\\code\\fuck_njfu_lib\\explore\\frontend_debug.html", "w", encoding="utf-8") as f:
             f.write(resp.text)
         return

    logger.info(f"Found {len(js_urls)} JS files.")
    
    all_endpoints = set()
    
    for url in js_urls:
        logger.info(f"Downloading {url} ...")
        res = session.get(url, timeout=15)
        if res.status_code == 200:
            content = res.text
            matches = re.findall(r'[\'"](/?[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_.-]+)*)[\'"]', content)
            for m in matches:
                # Common patterns for API paths in this system
                if 'ic-web' in m or '/api/' in m or 'reserve/' in m or 'system/' in m or 'auth/' in m:
                    all_endpoints.add(m)
                    
    output_path = "c:\\code\\fuck_njfu_lib\\explore\\frontend_endpoints.txt"
    with open(output_path, "w", encoding="utf-8") as f:
        for ep in sorted(list(all_endpoints)):
            f.write(ep + "\n")
            
    logger.info(f"Extracted {len(all_endpoints)} potential API endpoints. Saved to {output_path}")

if __name__ == '__main__':
    explore_js()
