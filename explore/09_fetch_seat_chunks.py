"""下载并分析 seatPredetermine 的渲染 JS chunk"""
import requests, urllib3, re, random, base64, json, logging
from bs4 import BeautifulSoup
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.Util.Padding import pad
from Crypto.PublicKey import RSA

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
resp=s.post(edu_url("authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"),
    data={"vpn-0":"","service":"https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/","username":STUDENT_ID,
          "password":enc_cas(EDU_PASSWORD,salt),"lt":lt,"dllt":soup.find("input",{"name":"dllt"})["value"],
          "execution":soup.find("input",{"name":"execution"})["value"],"_eventId":soup.find("input",{"name":"_eventId"})["value"],
          "rmShown":soup.find("input",{"name":"rmShown"})["value"]}, allow_redirects=False, timeout=15)
if resp.status_code==302:
    ticket=re.search(r'ticket=([^&]+)',resp.headers.get("Location")).group(1)
    s.get(f"{BASE}/rump_frontend/loginFromCas/?ticket={ticket}",timeout=10)
pub=s.get(lib_url("ic-web/login/publicKey?vpn-12-libseat.njfu.edu.cn"),timeout=10).json().get("data",{})
lr=s.post(lib_url("ic-web/login/user?vpn-12-libseat.njfu.edu.cn"),
    json={"logonName":STUDENT_ID,"password":enc_lib(LIB_PASSWORD,pub.get("nonceStr"),pub.get("publicKey")),
          "captcha":"","consoleType":16,"privacy":True},timeout=15).json()
token=lr.get("data",{}).get("token")
print(f"认证: {'OK' if token else 'FAIL'}")

# 获取主页面的 JS 文件列表
resp = s.get(lib_url("?vpn-0"), timeout=10)
soup = BeautifulSoup(resp.text, "html.parser")
js_files = [sc['src'] for sc in soup.find_all("script", src=True)]
print(f"\nJS文件: {len(js_files)}")

# 找到 index.js 来获取 chunk 名
for js_src in js_files:
    if 'index' in js_src:
        url = js_src if js_src.startswith("http") else lib_url(js_src.lstrip("/") + "?vpn-12-libseat.njfu.edu.cn")
        resp = s.get(url, timeout=20)
        # 找 seatPredetermine 相关的 chunk 名
        chunks = re.findall(r'chunk-([a-f0-9]+)', resp.text)
        unique_chunks = list(set(chunks))
        print(f"\n发现 {len(unique_chunks)} 个 chunk")
        
        # 找 seatPredetermine 附近引用的 chunks
        for m in re.finditer(r'seatPredetermine.*?chunk-([a-f0-9]+)', resp.text):
            print(f"  seatPredetermine 引用: chunk-{m.group(1)}")
        
        # 提取 seatPredetermine 附近的所有 chunk 引用
        idx = resp.text.find('seatPredetermine')
        if idx > 0:
            region = resp.text[idx-50:idx+500]
            chunk_refs = re.findall(r'chunk-([a-f0-9]+)', region)
            print(f"  seatPredetermine 区域 chunks: {chunk_refs}")

# 下载所有 JS chunks 并搜索座位渲染代码
print("\n=== 搜索座位渲染逻辑 ===")
for js_src in js_files:
    url = js_src if js_src.startswith("http") else lib_url(js_src.lstrip("/") + "?vpn-12-libseat.njfu.edu.cn")
    resp = s.get(url, timeout=20)
    js = resp.text

# 下载关键chunk
# 从 index.js 中提取实际 chunk URL 路径
resp2 = s.get(lib_url("?vpn-0"), timeout=10)
soup2 = BeautifulSoup(resp2.text, "html.parser")

# 尝试直接下载可能的 chunk
chunk_names = ["chunk-4fc8d707", "chunk-bea8137a", "chunk-8395d758", "chunk-28ae968a"]
for chunk in chunk_names:
    for suffix in ["_1729153927461.js", ".js"]:
        chunk_url = lib_url(f"js/{chunk}{suffix}?vpn-12-libseat.njfu.edu.cn")
        try:
            resp = s.get(chunk_url, timeout=15)
            if resp.status_code == 200 and len(resp.text) > 100:
                js = resp.text
                print(f"\n--- {chunk}{suffix} ({len(js)} bytes) ---")
                
                # 搜索渲染相关关键词
                for kw in ["coordinate", "pointSize", "left:", "top:", "style", "position",
                           "seat", "desk", "table", "background", "border", "width", "height",
                           "canvas", "svg", "rect", "circle", "devPosition"]:
                    count = js.lower().count(kw.lower())
                    if count > 0:
                        # 找第一个匹配的上下文
                        idx = js.lower().find(kw.lower())
                        ctx = js[max(0,idx-60):idx+100].replace('\n',' ')
                        print(f"  '{kw}' x{count}: ...{ctx}...")
                
                # 保存 chunk
                with open(f"c:\\code\\fuck_njfu_lib\\explore\\{chunk}.js", "w", encoding="utf-8") as f:
                    f.write(js)
                print(f"  已保存: {chunk}.js")
                break
        except:
            pass

print("\n=== 完成 ===")
