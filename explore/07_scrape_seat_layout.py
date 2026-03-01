"""
抓取可视化选座页面的前端渲染逻辑
目标: 理解座位+桌子的HTML/CSS/JS布局方式
"""
import os, sys, re, json, random, base64, logging
import requests, urllib3
from bs4 import BeautifulSoup
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.Util.Padding import pad
from Crypto.PublicKey import RSA

urllib3.disable_warnings()
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')
logger = logging.getLogger(__name__)

STUDENT_ID = "2410403132"
EDU_PASSWORD = "Zhouwenjie@790920"
LIB_PASSWORD = "njfu23001x!"

BASE = "https://webvpn.njfu.edu.cn"
VPN = "/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc="
LIB = "/LjIwNS4xNTguMjAwLjE3MS4xNTMuMTUwLjIxNi45Ny4yMTEuMTU2LjE1OC4xNzMuMTQ4LjE1NS4xNTUuMjE3LjEwMC4xNTAuMTY1"
EDU = "/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1"

def lib_url(path): return f"{BASE}{VPN}{LIB}/{path}"
def edu_url(path): return f"{BASE}{VPN}{EDU}/{path}"

def encrypt_cas(password, key):
    prefix = "".join(random.choice("ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678") for _ in range(64))
    iv = "".join(random.choice("ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678") for _ in range(16)).encode()
    cipher = AES.new(key.encode(), AES.MODE_CBC, iv)
    return base64.b64encode(cipher.encrypt(pad((prefix + password).encode(), AES.block_size))).decode()

def encrypt_lib(pwd, nonce, pub_key_str):
    if "BEGIN" not in pub_key_str:
        pub_key_str = f"-----BEGIN PUBLIC KEY-----\n{pub_key_str}\n-----END PUBLIC KEY-----"
    cipher = PKCS1_v1_5.new(RSA.importKey(pub_key_str))
    return base64.b64encode(cipher.encrypt(f"{pwd};{nonce}".encode())).decode()

def auth(session):
    session.get(f"{BASE}/", timeout=5)
    r = session.get(f"{BASE}/webvpn/cookie/?domain=uia.njfu.edu.cn&path=%2Fauthserver%2Flogin", timeout=5)
    m = re.search(r'route=([^;]+)', r.text)
    if m: session.cookies.set('route', m.group(1), domain='webvpn.njfu.edu.cn', path='/')

    resp = session.get(edu_url("authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"), timeout=10)
    soup = BeautifulSoup(resp.text, "html.parser")
    lt = soup.find("input", {"name": "lt"})["value"]
    salt = soup.find("input", {"id": "pwdDefaultEncryptSalt"})["value"]
    dllt = soup.find("input", {"name": "dllt"})["value"]
    execution = soup.find("input", {"name": "execution"})["value"]
    event_id = soup.find("input", {"name": "_eventId"})["value"]
    rm = soup.find("input", {"name": "rmShown"})["value"]

    resp = session.post(edu_url("authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"),
        data={"vpn-0":"","service":"https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/",
              "username":STUDENT_ID,"password":encrypt_cas(EDU_PASSWORD,salt),"lt":lt,"dllt":dllt,
              "execution":execution,"_eventId":event_id,"rmShown":rm}, allow_redirects=False, timeout=15)
    if resp.status_code == 302:
        ticket = re.search(r'ticket=([^&]+)', resp.headers.get("Location")).group(1)
        session.get(f"{BASE}/rump_frontend/loginFromCas/?ticket={ticket}", timeout=10)

    pub = session.get(lib_url("ic-web/login/publicKey?vpn-12-libseat.njfu.edu.cn"), timeout=10).json().get("data",{})
    login_resp = session.post(lib_url("ic-web/login/user?vpn-12-libseat.njfu.edu.cn"),
        json={"logonName":STUDENT_ID,"password":encrypt_lib(LIB_PASSWORD,pub.get("nonceStr"),pub.get("publicKey")),
              "captcha":"","consoleType":16,"privacy":True}, timeout=15).json()
    token = login_resp.get("data",{}).get("token")
    logger.info(f"认证{'成功' if token else '失败'}!")
    return token

def main():
    session = requests.Session()
    session.verify = False
    session.headers.update({"User-Agent": "Mozilla/5.0"})

    token = auth(session)
    if not token:
        return
    headers = {"token": token, "lan": "1", "Accept": "application/json"}

    # 1. 获取主页面 HTML，提取 JS bundle 列表
    logger.info("\n=== 1. 获取 SPA 入口页面 ===")
    resp = session.get(lib_url("?vpn-0"), timeout=10)
    soup = BeautifulSoup(resp.text, "html.parser")
    js_files = [s['src'] for s in soup.find_all("script", src=True)]
    logger.info(f"找到 {len(js_files)} 个 JS 文件")
    for f in js_files:
        logger.info(f"  {f}")

    # 2. 下载并搜索座位渲染相关的 JS 代码
    logger.info("\n=== 2. 分析 JS 中的座位/桌子渲染逻辑 ===")
    for js_src in js_files:
        url = js_src if js_src.startswith("http") else lib_url(js_src.lstrip("/") + "?vpn-12-libseat.njfu.edu.cn")
        try:
            resp = session.get(url, timeout=20)
            js = resp.text
            if len(js) < 500:
                continue

            # 搜索关键渲染逻辑
            kws = ["seatPredetermine", "coordinate", "desk", "table", "devSeat", "roomLayout",
                   "backgroundColor", "border-radius", "seat-item", "seat-map", "devPosition",
                   "seatBg", "SEAT", "occupied", "available", "width:", "height:", "position:absolute"]
            found_any = False
            for kw in kws:
                matches = [(m.start(), m) for m in re.finditer(re.escape(kw), js, re.IGNORECASE)]
                if matches:
                    if not found_any:
                        logger.info(f"\n--- {js_src[-50:]} ---")
                        found_any = True
                    logger.info(f"  '{kw}' x{len(matches)}")
                    # 展示前2个上下文
                    for pos, m in matches[:2]:
                        ctx = js[max(0,pos-100):pos+150].replace('\n',' ').strip()
                        logger.info(f"    ...{ctx}...")
        except Exception as e:
            logger.info(f"  下载失败: {e}")

    # 3. 查看是否有房间布局 / SVG / 背景图等 API
    logger.info("\n=== 3. 探测布局相关 API ===")
    room_id = 100455344
    endpoints = [
        f"ic-web/seatPredetermine/{room_id}?vpn-12-libseat.njfu.edu.cn",
        f"ic-web/reserve/roomInfo?roomId={room_id}&vpn-12-libseat.njfu.edu.cn",
        f"ic-web/reserve/roomLayout?roomId={room_id}&vpn-12-libseat.njfu.edu.cn",
        f"ic-web/roomDevice/roomInfoById?roomId={room_id}&vpn-12-libseat.njfu.edu.cn",
    ]
    for ep in endpoints:
        url = lib_url(ep)
        try:
            resp = session.get(url, headers=headers, timeout=10)
            logger.info(f"\n[{ep.split('?')[0]}] => {resp.status_code}")
            if resp.status_code == 200:
                try:
                    data = resp.json()
                    txt = json.dumps(data, ensure_ascii=False)
                    logger.info(f"  长度: {len(txt)}")
                    logger.info(f"  内容: {txt[:600]}")
                    if len(txt) > 200:
                        fname = ep.split("?")[0].replace("/","_") + ".json"
                        with open(os.path.join(r"c:\code\fuck_njfu_lib\explore", fname), "w", encoding="utf-8") as f:
                            json.dump(data, f, ensure_ascii=False, indent=2)
                        logger.info(f"  已保存: {fname}")
                except:
                    logger.info(f"  非JSON: {resp.text[:300]}")
        except Exception as e:
            logger.info(f"  失败: {e}")

    # 4. 从座位数据中分析桌子分组
    logger.info("\n=== 4. 桌子分组分析 ===")
    from datetime import datetime
    today = datetime.now().strftime("%Y%m%d")
    seat_url = lib_url(f"ic-web/reserve?vpn-12-libseat.njfu.edu.cn&roomIds={room_id}&resvDates={today}&sysKind=8")
    resp = session.get(seat_url, headers=headers, timeout=15)
    if resp.status_code == 200:
        seat_data = resp.json()
        if seat_data.get("code") == 0:
            seats = seat_data.get("data", [])
            logger.info(f"座位总数: {len(seats)}")
            # 看看第一个座位有没有额外的布局字段
            if seats:
                first = seats[0]
                logger.info(f"第一个座位字段名: {list(first.keys())}")
                # 找桌子: 查找 coordinate 相近的座位对
                parsed = []
                for s in seats:
                    c = s.get("coordinate","")
                    if c and "," in c:
                        x, y = map(float, c.split(","))
                        parsed.append({"name": s["devName"], "x": x, "y": y, "id": s["devId"]})

                # 检测桌子对：同一 X，Y 差约 3.2-3.5
                tables = []
                used = set()
                parsed.sort(key=lambda s: (s["x"], s["y"]))
                for i, s1 in enumerate(parsed):
                    if s1["id"] in used:
                        continue
                    for j, s2 in enumerate(parsed):
                        if i == j or s2["id"] in used:
                            continue
                        xgap = abs(s1["x"] - s2["x"])
                        ygap = abs(s1["y"] - s2["y"])
                        if xgap < 1.0 and 2.5 < ygap < 4.0:
                            tables.append((s1, s2))
                            used.add(s1["id"])
                            used.add(s2["id"])
                            break

                logger.info(f"检测到 {len(tables)} 张桌子 (2人对坐)")
                logger.info(f"未配对座位: {len(parsed) - len(used)} 个")
                # 打印前5张桌子
                for i, (s1, s2) in enumerate(tables[:5]):
                    cx = (s1["x"]+s2["x"])/2
                    cy = (s1["y"]+s2["y"])/2
                    logger.info(f"  桌{i+1}: {s1['name']}({s1['x']:.1f},{s1['y']:.1f}) + {s2['name']}({s2['x']:.1f},{s2['y']:.1f}) => 桌中心({cx:.1f},{cy:.1f})")

    logger.info("\n=== 完成 ===")

if __name__ == '__main__':
    main()
