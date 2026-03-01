"""
分析图书馆可视化选座页面的结构
目标: 获取座位布局数据，理解前端如何渲染座位图
"""
import os
import requests
import re
import urllib3
import logging
import json
from bs4 import BeautifulSoup
import base64
import random
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.Util.Padding import pad
from Crypto.PublicKey import RSA

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

def do_full_auth(session):
    """完整双层认证"""
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
    acc_no = login_resp.get("data", {}).get("accNo")
    
    if not token:
        logger.error("Token获取失败!")
        return None, None
    logger.info(f"认证成功! Token: {token[:20]}...")
    return token, acc_no

def analyze_seat_page():
    session = requests.Session()
    session.verify = False
    session.headers.update({"User-Agent": "Mozilla/5.0", "Accept": "*/*"})
    
    token, acc_no = do_full_auth(session)
    if not token:
        return
    
    api_headers = {"token": token, "lan": "1", "Accept": "application/json"}
    
    # ===== 1. 获取座位页面的 HTML (SPA 入口) =====
    logger.info("========= 1. 获取座位选择页面 HTML =========")
    seat_page_url = HttpClient.get_lib_url("?vpn-0#/ic/seatPredetermine/100455344")
    resp = session.get(seat_page_url, timeout=10)
    logger.info(f"页面状态码: {resp.status_code}, 长度: {len(resp.text)}")
    
    # 保存 HTML
    with open("c:\\code\\fuck_njfu_lib\\explore\\seat_page.html", "w", encoding="utf-8") as f:
        f.write(resp.text)
    
    # 分析页面中的 JS/CSS 资源
    soup = BeautifulSoup(resp.text, "html.parser")
    scripts = soup.find_all("script", src=True)
    styles = soup.find_all("link", rel="stylesheet")
    logger.info(f"JS 文件: {len(scripts)} 个")
    for s in scripts:
        logger.info(f"  - {s['src']}")
    logger.info(f"CSS 文件: {len(styles)} 个")
    for s in styles:
        logger.info(f"  - {s.get('href', 'N/A')}")

    # ===== 2. 获取房间列表 API =====
    logger.info("\n========= 2. 获取房间/区域列表 =========")
    # 二层A区的 roomId 是 100455344
    room_id = 100455344
    
    # 尝试获取房间详情 (布局相关)
    room_detail_endpoints = [
        f"ic-web/roomDevice/roomInfoById?roomId={room_id}&vpn-12-libseat.njfu.edu.cn",
        f"ic-web/reserve/roomLayout?roomId={room_id}&vpn-12-libseat.njfu.edu.cn",
        f"ic-web/roomDevice/deviceList?roomId={room_id}&vpn-12-libseat.njfu.edu.cn",
    ]
    
    for ep in room_detail_endpoints:
        url = HttpClient.get_lib_url(ep)
        try:
            resp = session.get(url, headers=api_headers, timeout=10)
            logger.info(f"[{ep.split('?')[0]}] 状态: {resp.status_code}")
            if resp.status_code == 200:
                try:
                    data = resp.json()
                    logger.info(f"  响应: {json.dumps(data, ensure_ascii=False)[:500]}")
                except:
                    logger.info(f"  非JSON响应: {resp.text[:200]}")
        except Exception as e:
            logger.info(f"  请求失败: {e}")

    # ===== 3. 获取座位数据(含坐标信息) =====
    logger.info("\n========= 3. 获取座位详细数据(含坐标) =========")
    from datetime import datetime
    today = datetime.now().strftime("%Y%m%d")
    
    seat_url = HttpClient.get_lib_url(
        f"ic-web/reserve?vpn-12-libseat.njfu.edu.cn&roomIds={room_id}&resvDates={today}&sysKind=8"
    )
    resp = session.get(seat_url, headers=api_headers, timeout=15)
    if resp.status_code == 200:
        seat_data = resp.json()
        if seat_data.get("code") == 0:
            seats = seat_data.get("data", [])
            logger.info(f"座位总数: {len(seats)}")
            
            # 分析第一个座位的完整字段
            if seats:
                first_seat = seats[0]
                logger.info(f"\n第一个座位的所有字段:")
                for key, val in first_seat.items():
                    val_str = str(val)
                    if len(val_str) > 200:
                        val_str = val_str[:200] + "..."
                    logger.info(f"  {key}: {val_str}")
                
                # 分析坐标数据 - 关键！
                logger.info(f"\n========= 座位坐标分析 =========")
                coords_data = []
                for seat in seats:
                    coord = seat.get("coordinate")
                    dev_id = seat.get("devId")
                    dev_name = seat.get("devName", "")
                    dev_status = seat.get("devStatus")
                    resv_info = seat.get("resvInfo", [])
                    is_occupied = len(resv_info) > 0
                    
                    seat_info = {
                        "devId": dev_id,
                        "devName": dev_name,
                        "devStatus": dev_status,
                        "coordinate": coord,
                        "occupied": is_occupied,
                        "resvCount": len(resv_info)
                    }
                    if resv_info:
                        seat_info["firstResv"] = {
                            "startTime": resv_info[0].get("startTime"),
                            "endTime": resv_info[0].get("endTime"),
                            "resvStatus": resv_info[0].get("resvStatus"),
                        }
                    coords_data.append(seat_info)
                
                # 统计坐标分布
                has_coord = sum(1 for s in coords_data if s["coordinate"])
                no_coord = sum(1 for s in coords_data if not s["coordinate"])
                logger.info(f"有坐标数据: {has_coord}, 无坐标数据: {no_coord}")
                
                if has_coord > 0:
                    # 分析坐标格式
                    sample_coords = [s["coordinate"] for s in coords_data if s["coordinate"]][:10]
                    logger.info(f"坐标样本 (前10个):")
                    for c in sample_coords:
                        logger.info(f"  {c}")
                
                # 保存完整的座位坐标数据
                with open("c:\\code\\fuck_njfu_lib\\explore\\seat_coordinates.json", "w", encoding="utf-8") as f:
                    json.dump(coords_data, f, ensure_ascii=False, indent=2)
                logger.info(f"\n座位坐标数据已保存到 seat_coordinates.json ({len(coords_data)} 条)")
        else:
            logger.error(f"座位查询失败: {seat_data}")
    
    # ===== 4. 尝试获取房间布局图 =====
    logger.info("\n========= 4. 探测房间布局相关 API =========")
    layout_endpoints = [
        f"ic-web/roomDevice/roomLayout?roomId={room_id}&vpn-12-libseat.njfu.edu.cn",
        f"ic-web/roomDevice/roomDeviceByRoomId?roomId={room_id}&vpn-12-libseat.njfu.edu.cn",
        f"ic-web/seatMap/roomMap?roomId={room_id}&vpn-12-libseat.njfu.edu.cn",
        f"ic-web/seatPredetermine/roomInfo/{room_id}?vpn-12-libseat.njfu.edu.cn",
        f"ic-web/seatPredetermine/{room_id}?vpn-12-libseat.njfu.edu.cn",
    ]
    
    for ep in layout_endpoints:
        url = HttpClient.get_lib_url(ep)
        try:
            resp = session.get(url, headers=api_headers, timeout=10)
            logger.info(f"[{ep.split('?')[0]}] 状态: {resp.status_code}")
            if resp.status_code == 200:
                try:
                    data = resp.json()
                    data_str = json.dumps(data, ensure_ascii=False)
                    logger.info(f"  响应长度: {len(data_str)}")
                    logger.info(f"  内容: {data_str[:800]}")
                    # 如果内容有价值，保存
                    if len(data_str) > 100:
                        safe_name = ep.split("?")[0].replace("/", "_").replace("-", "_")
                        with open(f"c:\\code\\fuck_njfu_lib\\explore\\layout_{safe_name}.json", "w", encoding="utf-8") as f:
                            json.dump(data, f, ensure_ascii=False, indent=2)
                except:
                    logger.info(f"  非JSON: {resp.text[:200]}")
        except Exception as e:
            logger.info(f"  请求失败: {e}")

    # ===== 5. 下载并分析前端 JS bundle 中的座位渲染逻辑 =====
    logger.info("\n========= 5. 分析前端 JS 中的座位渲染逻辑 =========")
    # 重新获取主页面找到 JS 文件
    main_page_url = HttpClient.get_lib_url("?vpn-0")
    resp = session.get(main_page_url, timeout=10)
    soup = BeautifulSoup(resp.text, "html.parser")
    js_files = [s['src'] for s in soup.find_all("script", src=True)]
    
    for js_url in js_files:
        if not js_url.startswith("http"):
            js_url = HttpClient.get_lib_url(js_url.lstrip("/") + "?vpn-12-libseat.njfu.edu.cn")
        
        try:
            logger.info(f"\n分析 JS: {js_url[-60:]}")
            resp = session.get(js_url, timeout=15)
            js_content = resp.text
            
            # 搜索座位渲染相关关键词
            keywords = ["coordinate", "seatMap", "seatPredetermine", "roomLayout", 
                        "devPosition", "transform", "left:", "top:", "position:"]
            
            for kw in keywords:
                matches = [m.start() for m in re.finditer(re.escape(kw), js_content, re.IGNORECASE)]
                if matches:
                    logger.info(f"  关键词 '{kw}' 出现 {len(matches)} 次")
                    # 展示第一个匹配的上下文
                    pos = matches[0]
                    context = js_content[max(0, pos-80):pos+120]
                    logger.info(f"    上下文: ...{context}...")
                    
        except Exception as e:
            logger.info(f"  下载失败: {e}")
    
    logger.info("\n========= 分析完成 =========")

if __name__ == '__main__':
    analyze_seat_page()
