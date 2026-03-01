"""快速提取 pointSize/pointProperty 等渲染字段"""
import json, requests, urllib3, re, random, base64, logging
from bs4 import BeautifulSoup
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.Util.Padding import pad
from Crypto.PublicKey import RSA
from datetime import datetime

urllib3.disable_warnings()
logging.basicConfig(level=logging.INFO, format='%(message)s')

STUDENT_ID = "2410403132"
EDU_PASSWORD = "Zhouwenjie@790920"
LIB_PASSWORD = "njfu23001x!"
BASE = "https://webvpn.njfu.edu.cn"
VPN = "/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc="
LIB = "/LjIwNS4xNTguMjAwLjE3MS4xNTMuMTUwLjIxNi45Ny4yMTEuMTU2LjE1OC4xNzMuMTQ4LjE1NS4xNTUuMjE3LjEwMC4xNTAuMTY1"
EDU = "/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1"

def lib_url(p): return f"{BASE}{VPN}{LIB}/{p}"
def edu_url(p): return f"{BASE}{VPN}{EDU}/{p}"

def enc_cas(pwd, key):
    pre = "".join(random.choice("ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678") for _ in range(64))
    iv = "".join(random.choice("ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678") for _ in range(16)).encode()
    return base64.b64encode(AES.new(key.encode(), AES.MODE_CBC, iv).encrypt(pad((pre+pwd).encode(), AES.block_size))).decode()

def enc_lib(pwd, nonce, pub):
    if "BEGIN" not in pub: pub = f"-----BEGIN PUBLIC KEY-----\n{pub}\n-----END PUBLIC KEY-----"
    return base64.b64encode(PKCS1_v1_5.new(RSA.importKey(pub)).encrypt(f"{pwd};{nonce}".encode())).decode()

s = requests.Session()
s.verify = False
s.headers.update({"User-Agent": "Mozilla/5.0"})
s.get(f"{BASE}/", timeout=5)
r = s.get(f"{BASE}/webvpn/cookie/?domain=uia.njfu.edu.cn&path=%2Fauthserver%2Flogin", timeout=5)
m = re.search(r'route=([^;]+)', r.text)
if m: s.cookies.set('route', m.group(1), domain='webvpn.njfu.edu.cn', path='/')

resp = s.get(edu_url("authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"), timeout=10)
soup = BeautifulSoup(resp.text, "html.parser")
lt=soup.find("input",{"name":"lt"})["value"]
salt=soup.find("input",{"id":"pwdDefaultEncryptSalt"})["value"]
dllt=soup.find("input",{"name":"dllt"})["value"]
ex=soup.find("input",{"name":"execution"})["value"]
ev=soup.find("input",{"name":"_eventId"})["value"]
rm=soup.find("input",{"name":"rmShown"})["value"]
resp=s.post(edu_url("authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"),
    data={"vpn-0":"","service":"https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/","username":STUDENT_ID,
          "password":enc_cas(EDU_PASSWORD,salt),"lt":lt,"dllt":dllt,"execution":ex,"_eventId":ev,"rmShown":rm},
    allow_redirects=False, timeout=15)
if resp.status_code==302:
    ticket=re.search(r'ticket=([^&]+)',resp.headers.get("Location")).group(1)
    s.get(f"{BASE}/rump_frontend/loginFromCas/?ticket={ticket}",timeout=10)
pub=s.get(lib_url("ic-web/login/publicKey?vpn-12-libseat.njfu.edu.cn"),timeout=10).json().get("data",{})
lr=s.post(lib_url("ic-web/login/user?vpn-12-libseat.njfu.edu.cn"),
    json={"logonName":STUDENT_ID,"password":enc_lib(LIB_PASSWORD,pub.get("nonceStr"),pub.get("publicKey")),
          "captcha":"","consoleType":16,"privacy":True},timeout=15).json()
token=lr.get("data",{}).get("token")
print(f"Token: {'OK' if token else 'FAIL'}")

headers={"token":token,"lan":"1","Accept":"application/json"}
today=datetime.now().strftime("%Y%m%d")
resp=s.get(lib_url(f"ic-web/reserve?vpn-12-libseat.njfu.edu.cn&roomIds=100455344&resvDates={today}&sysKind=8"),headers=headers,timeout=15)
data=resp.json()
seats=data.get("data",[])
print(f"Seats: {len(seats)}")

# 提取渲染关键字段
if seats:
    first = seats[0]
    print(f"\n=== 第一个座位的渲染字段 ===")
    print(f"devName: {first.get('devName')}")
    print(f"coordinate: {first.get('coordinate')}")
    print(f"pointSize: {first.get('pointSize')}")
    print(f"textSize: {first.get('textSize')}")
    print(f"pointProperty: {first.get('pointProperty')}")
    print(f"icon: {first.get('icon')}")
    print(f"kindUrl: {first.get('kindUrl')}")
    print(f"devProp: {first.get('devProp')}")
    print(f"msideCoordinate: {first.get('msideCoordinate')}")

    # 统计不同的 pointSize 和 pointProperty 值
    from collections import Counter
    ps = Counter(str(s.get('pointSize')) for s in seats)
    pp = Counter(str(s.get('pointProperty')) for s in seats)
    ts = Counter(str(s.get('textSize')) for s in seats)
    ic = Counter(str(s.get('icon',''))[:30] for s in seats)
    print(f"\npointSize 分布: {dict(ps)}")
    print(f"pointProperty 分布: {dict(pp)}")
    print(f"textSize 分布: {dict(ts)}")
    print(f"icon 分布: {dict(ic)}")

    # 输出几个有代表性的座位完整数据
    print(f"\n=== 前3个座位完整数据 ===")
    for seat in seats[:3]:
        clean = {k:v for k,v in seat.items() if k not in ['resvRule','openTimes','resvInfo','endDayOpenInfo','addServices','deviceAttributes','timeScopeOpenInfo']}
        print(json.dumps(clean, ensure_ascii=False, indent=2))
