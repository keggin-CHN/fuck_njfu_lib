import requests
import re
import time
import logging
import traceback
import random
import base64
import os
import json
import urllib3
from bs4 import BeautifulSoup
from Crypto.Cipher import PKCS1_v1_5
from Crypto.PublicKey import RSA
from cryptography.fernet import Fernet
from functools import wraps
import concurrent.futures
from urllib.parse import parse_qsl, urlencode, urljoin, urlsplit, urlunsplit

# 临时注释掉 Config，因为测试脚本在外部覆盖了它
try:
    from config import Config
except ImportError:
    class Config:
        DIRECT_CAMPUS_MODE = False
        @staticmethod
        def get_proxy_url():
            return None

logger = logging.getLogger(__name__)

# ... 后面的部分不需要全改，我可以直接改 test_auth.py 来打补丁重写 LibraryAuthenticator 里的 second_level_auth 方法以方便调试，不动原始代码。
_warp_manager = None
def get_warp_manager():
    """延迟加载 WarpManager 实例"""
    global _warp_manager
    if _warp_manager is None:
        try:
            from utils.warp_manager import WarpManager
            _warp_manager = WarpManager()
        except ImportError:
            logger.warning("无法导入 WarpManager，IP 封禁检测功能将不可用")
            _warp_manager = False  
    return _warp_manager if _warp_manager else None
def check_ip_blocked(response, context=""):
    """
    检查响应是否表明 IP 被封禁
    
    Args:
        response: requests.Response 对象
        context: 请求上下文描述，用于日志
    
    Returns:
        bool: True 表示 IP 被封禁
    """
    if response is None:
        return False
    blocked_status_codes = [403, 502, 503, 504]
    if response.status_code in blocked_status_codes:
        logger.warning(f"[{context}] 检测到可能的 IP 封禁，状态码: {response.status_code}")
        return True
    try:
        text = response.text.lower()
        blocked_keywords = ['blocked', 'banned', 'forbidden', 'access denied', '访问被拒绝', '封禁', 'ip被']
        for keyword in blocked_keywords:
            if keyword in text:
                logger.warning(f"[{context}] 检测到封禁关键词: {keyword}")
                return True
    except:
        pass
    return False
def handle_ip_block(response, context=""):
    """
    处理 IP 封禁情况：重连 WARP 并通知管理员
    
    Args:
        response: requests.Response 对象
        context: 请求上下文描述
    
    Returns:
        bool: True 表示成功处理（重连成功），False 表示处理失败
    """
    warp_mgr = get_warp_manager()
    if not warp_mgr:
        logger.error("WarpManager 不可用，无法处理 IP 封禁")
        return False
    error_details = {
        'context': context,
        'status_code': response.status_code if response else 'N/A',
        'url': response.url if response else 'N/A',
        'response_text': response.text[:500] if response and response.text else 'N/A'
    }
    logger.warning(f"检测到 IP 封禁，正在尝试重连 WARP... 详情: {error_details}")
    success = warp_mgr.reconnect_warp()
    if success:
        new_ip = warp_mgr.get_current_ip()
        logger.info(f"WARP 重连成功，新 IP: {new_ip}")
        try:
            warp_mgr.notify_admin_ip_blocked(
                old_ip="被封禁IP",
                new_ip=new_ip,
                error_details=error_details
            )
        except Exception as e:
            logger.error(f"发送 IP 封禁通知失败: {e}")
        return True
    else:
        logger.error("WARP 重连失败")
        return False
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
captcha_session = None
KEY_FILE = 'encryption_key.key'
def get_or_create_key():
    if os.path.exists(KEY_FILE):
        with open(KEY_FILE, 'rb') as f:
            return f.read()
    else:
        key = Fernet.generate_key()
        with open(KEY_FILE, 'wb') as f:
            f.write(key)
        return key
SECRET_KEY = os.environ.get('ENCRYPTION_KEY') or get_or_create_key()
fernet = Fernet(SECRET_KEY)
def encrypt_password(password):
    return fernet.encrypt(password.encode()).decode()
def decrypt_password(encrypted_password):
    return fernet.decrypt(encrypted_password.encode()).decode()
# 新 CAS 认证使用的硬编码 RSA 公钥 (从 login.js 提取)
CAS_RSA_MODULUS_HEX = "008aed7e057fe8f14c73550b0e6467b023616ddc8fa91846d2613cdb7f7621e3cada4cd5d812d627af6b87727ade4e26d26208b7326815941492b2204c3167ab2d53df1e3a2c9153bdb7c8c2e968df97a5e7e01cc410f92c4c2c2fba529b3ee988ebc1fca99ff5119e036d732c368acf8beba01aa2fdafa45b21e4de4928d0d403"
CAS_RSA_EXPONENT_HEX = "010001"

def encrypt_cas_password(password):
    """
    CAS 新版 RSA 加密 (2025年起 NJFU CAS 不再使用 AES-CBC，改为硬编码 RSA 公钥)
    与前端 security.js 的 RSAUtils.encryptedString 逻辑完全一致 (textbook RSA, 无 PKCS#1 padding)
    """
    n = int(CAS_RSA_MODULUS_HEX, 16)
    e = int(CAS_RSA_EXPONENT_HEX, 16)
    chunk_size = 126  # 2 * biHighIndex(modulus) = 2 * 63
    char_codes = [ord(c) for c in password]
    while len(char_codes) % chunk_size:
        char_codes.append(0)
    m = 0
    for i in range(0, chunk_size, 2):
        digit = char_codes[i] + char_codes[i + 1] * 256
        m += digit * (65536 ** (i // 2))
    c = pow(m, e, n)
    return format(c, '0256x')

def encrypt_cas_password_aes(password, salt):
    """CAS AES-CBC encryption (pwdDefaultEncryptSalt mode)"""
    try:
        from Crypto.Cipher import AES
        from Crypto.Util.Padding import pad
        import base64 as b64
        import random, string
        key = salt.strip()
        prefix = ''.join(random.choices(string.ascii_letters + string.digits, k=64))
        iv = ''.join(random.choices(string.ascii_letters + string.digits, k=16))
        plaintext = prefix + password
        cipher = AES.new(key.encode('utf-8'), AES.MODE_CBC, iv.encode('utf-8'))
        ct = cipher.encrypt(pad(plaintext.encode('utf-8'), AES.block_size))
        return b64.b64encode(ct).decode('utf-8')
    except Exception as e:
        logger.error(f'AES-CBC encrypt error: {e}')
        return password
def encrypt_lib_password(plaintext_password, nonce, public_key_str):
    if "-----BEGIN PUBLIC KEY-----" not in public_key_str:
        public_key_str = "-----BEGIN PUBLIC KEY-----\n" + public_key_str + "\n-----END PUBLIC KEY-----"
    rsa_key = RSA.importKey(public_key_str)
    cipher = PKCS1_v1_5.new(rsa_key)
    message = f"{plaintext_password};{nonce}".encode("utf-8")
    encrypted = cipher.encrypt(message)
    return base64.b64encode(encrypted).decode("utf-8")
class HttpClient:
    BASE_URL_PREFIX = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc="
    LIB_URL_SUFFIX = "/LjIwNS4xNTguMjAwLjE3MS4xNTMuMTUwLjIxNi45Ny4yMTEuMTU2LjE1OC4xNzMuMTQ4LjE1NS4xNTUuMjE3LjEwMC4xNTAuMTY1"
    EDU_URL_SUFFIX = "/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1"
    DEFAULT_HEADERS = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    }
    @staticmethod
    def get_lib_url(path):
        if getattr(Config, "DIRECT_CAMPUS_MODE", False):
            clean_path = path.replace("?vpn-12-libseat.njfu.edu.cn", "").replace("&vpn-12-libseat.njfu.edu.cn", "").lstrip("/")
            # 校内图书馆已强制 HTTPS；HTTP 入口会 301 丢掉 CAS ticket 等查询参数。
            return f"https://libseat.njfu.edu.cn/{clean_path}"
        return f"{HttpClient.BASE_URL_PREFIX}{HttpClient.LIB_URL_SUFFIX}/{path}"
    @staticmethod
    def rewrite_cas_service_https(cas_url):
        """CAS 登录页走 HTTPS，并把 service/finalAddress 从 HTTP 升到 HTTPS，避免 ticket 在 301 时丢失。"""
        parts = urlsplit(cas_url or "")
        query = []
        for key, value in parse_qsl(parts.query, keep_blank_values=True):
            if key == "service":
                service = urlsplit(value)
                if service.hostname == "libseat.njfu.edu.cn":
                    inner = []
                    for inner_key, inner_value in parse_qsl(service.query, keep_blank_values=True):
                        if inner_value.startswith("http://libseat.njfu.edu.cn"):
                            inner_value = "https://" + inner_value[len("http://"):]
                        inner.append((inner_key, inner_value))
                    value = urlunsplit((
                        "https", service.netloc, service.path, urlencode(inner), service.fragment
                    ))
            query.append((key, value))
        host = parts.netloc or "uia.njfu.edu.cn"
        path = parts.path or "/authserver/login"
        return urlunsplit(("https", host, path, urlencode(query), ""))
    @staticmethod
    def get_edu_url(path):
        if getattr(Config, "DIRECT_CAMPUS_MODE", False):
            clean_path = path.lstrip("/")
            return f"https://uia.njfu.edu.cn/{clean_path}"
        return f"{HttpClient.BASE_URL_PREFIX}{HttpClient.EDU_URL_SUFFIX}/{path}"
    @staticmethod
    def _https_upgrade_url(url, response):
        """只接受同一接口的 HTTP -> HTTPS 跳转，保留原查询参数。"""
        if response.status_code not in (301, 302, 307, 308):
            return None

        source = urlsplit(url)
        target = urlsplit(urljoin(url, response.headers.get("Location", "")))
        # 只跟随同一接口的 HTTP -> HTTPS 升级；登录页等跳转交给调用方处理。
        is_https_upgrade = (
            source.scheme == "http"
            and target.scheme == "https"
            and source.hostname == target.hostname
            and source.port in (None, 80)
            and target.port in (None, 443)
            and target.username is None
            and target.password is None
            and source.path == target.path
        )
        if not is_https_upgrade:
            return None

        # 网关的 Location 可能省略查询串，仍保留原请求的预约日期等参数。
        return urlunsplit(("https", target.netloc, source.path, source.query, ""))
    @staticmethod
    def get_lib_response(session, url, headers=None, params=None, timeout=10):
        """读取图书馆接口，区分 HTTPS 升级与登录/拒绝访问跳转。"""
        response = session.get(
            url, headers=headers, params=params, timeout=timeout, allow_redirects=False
        )
        secure_url = HttpClient._https_upgrade_url(url, response)
        if secure_url:
            response.close()
            response = session.get(
                secure_url, headers=headers, params=params, timeout=timeout, allow_redirects=False
            )
        return response
    @staticmethod
    def post_lib_json(session, url, headers=None, json_data=None, timeout=15):
        """提交图书馆 JSON，避免 301/302 将 POST 改为 GET 并丢掉请求体。"""
        response = session.post(
            url, headers=headers, json=json_data, timeout=timeout, allow_redirects=False
        )
        secure_url = HttpClient._https_upgrade_url(url, response)
        if not secure_url:
            return response
        response.close()
        logger.info("图书馆接口升级为 HTTPS，保留 POST 请求体")
        return session.post(
            secure_url, headers=headers, json=json_data, timeout=timeout, allow_redirects=False
        )
    @staticmethod
    def get(url, headers=None, cookies=None, timeout=10, **kwargs):
        """
        保留静态方法以兼容 app.py 中的调用。
        注意：对于认证流程，推荐使用 LibraryAuthenticator 及其内部 Session。
        """
        req_headers = HttpClient.DEFAULT_HEADERS.copy()
        if headers:
            req_headers.update(headers)
        try:
            response = requests.get(
                url,
                headers=req_headers,
                cookies=cookies,
                verify=False,
                timeout=timeout,
                **kwargs
            )
            return response
        except Exception as e:
            logger.error(f"HttpClient.get 请求失败: {e}")
            return None
    @staticmethod
    def post(url, headers=None, cookies=None, data=None, json_data=None, timeout=10, **kwargs):
        """
        保留静态方法以兼容 reservation.py 中的调用。
        """
        req_headers = HttpClient.DEFAULT_HEADERS.copy()
        if headers:
            req_headers.update(headers)
        try:
            response = requests.post(
                url,
                headers=req_headers,
                cookies=cookies,
                data=data,
                json=json_data,
                verify=False,
                timeout=timeout,
                **kwargs
            )
            return response
        except Exception as e:
            logger.error(f"HttpClient.post 请求失败: {e}")
            return None
class LibraryAuthenticator:
    def __init__(self, username, edu_password, lib_password):
        self.username = username
        self.password1 = edu_password
        self.password2 = lib_password
        self.my_client_ticket = None
        self.token = None
        self.acc_no = None
        self.last_auth_time = None
        self.last_dynamic_execution = "e1s2"
        self.session = requests.Session()
        self.session.verify = False
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
        })
        proxy_url = Config.get_proxy_url()
        if proxy_url:
            self.session.proxies = {
                'http': proxy_url,
                'https': proxy_url
            }
            logger.info(f"用户 {username} 的会话已配置 WARP 代理: {proxy_url}")
        self.load_session()

    def _get_cookie_val(self, name, session=None):
        sess = session or self.session
        for c in sess.cookies:
            if c.name == name:
                return c.value
        return None

    def get_route_cookie(self, session=None):
        target_session = session if session else self.session
        try:
            logger.info("正在获取 route cookie...")
            try:
                home_url = "https://webvpn.njfu.edu.cn/"
                logger.info(f"预访问 WebVPN 首页: {home_url}")
                target_session.get(home_url, timeout=5)
            except Exception as e:
                logger.warning(f"预访问 WebVPN 首页失败: {e}")
            route_url = "https://webvpn.njfu.edu.cn/webvpn/cookie/?domain=uia.njfu.edu.cn&path=%2Fauthserver%2Flogin"
            headers = {
                'Accept': '*/*',
                'Referer': "https://webvpn.njfu.edu.cn/",
                'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
                'Accept-Encoding': 'gzip, deflate, br',
                'Connection': 'keep-alive',
                'Sec-Fetch-Dest': 'empty',
                'Sec-Fetch-Mode': 'cors',
                'Sec-Fetch-Site': 'same-origin',
            }
            response = target_session.get(route_url, headers=headers, timeout=5)
            match = re.search(r'route=([^;]+)', response.text)
            if match:
                route = match.group(1)
                target_session.cookies.set('route', route, domain='webvpn.njfu.edu.cn', path='/')
                logger.info(f"成功从响应体获取并设置 route cookie: {route}")
                return route
            if 'route' in response.cookies:
                route = response.cookies.get('route')
                target_session.cookies.set('route', route, domain='webvpn.njfu.edu.cn', path='/')
                logger.info(f"成功从Cookies获取并设置 route cookie: {route}")
                return route
            logger.warning(f"未能在响应中找到 route cookie。状态码: {response.status_code}")
            logger.debug(f"route cookie 响应内容(前500字符): {response.text[:500]}")
            return None
        except Exception as e:
            logger.warning(f"获取 route cookie 失败: {e}")
            return None
    def get_initial_client_ticket(self):
        url = "https://webvpn.njfu.edu.cn/rump_frontend/login/"
        try:
            logger.info(f"正在请求初始 ticket: {url}")
            response = self.session.get(url, timeout=10)
            logger.info(f"初始 ticket 请求响应码: {response.status_code}")
            if response and response.status_code == 200:
                ticket = self._get_cookie_val("my_client_ticket")
                if ticket:
                    logger.info("成功获取 my_client_ticket")
                    return ticket
                else:
                    logger.warning(f"响应成功但未找到 my_client_ticket。Cookies: {self.session.cookies.get_dict()}")
            else:
                logger.warning(f"获取初始 ticket 响应异常: {response.status_code if response else 'None'}")
        except Exception as e:
            logger.error(f"获取初始 ticket 失败: {e}")
            logger.error(traceback.format_exc())
        return None
    def check_need_captcha(self, username, salt=None, client_ticket=None):
        url = HttpClient.get_edu_url("authserver/needCaptcha.html")
        params = {
            "vpn-12-uia.njfu.edu.cn": "",
            "username": username,
            "_": str(int(time.time() * 1000))
        }
        headers = {"X-Requested-With": "XMLHttpRequest"}
        try:
            resp = self.session.get(url, headers=headers, params=params, timeout=10)
            if resp and resp.status_code == 200:
                return resp.text.strip().lower() == "true"
            return True
        except Exception:
            return True
    def first_level_auth(self):
        logger.info("开始第一级认证流程...")
        my_client_ticket = self.get_initial_client_ticket()
        if not my_client_ticket:
            logger.error("第一级认证失败: 无法获取初始 client ticket")
            return None, False
        self.get_route_cookie()
        login_prepare_url = HttpClient.get_edu_url(
            "authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
        )
        max_retries = 2
        response = None
        for retry in range(max_retries):
            try:
                logger.info(f"正在访问登录准备页面: {login_prepare_url} (尝试 {retry + 1}/{max_retries})")
                response = self.session.get(login_prepare_url, timeout=10)
                logger.info(f"登录准备页面响应码: {response.status_code}")
                if check_ip_blocked(response, "第一级认证-登录准备"):
                    if handle_ip_block(response, "第一级认证-登录准备"):
                        logger.info("IP 封禁处理成功，重试请求...")
                        time.sleep(2)
                        continue
                    else:
                        logger.error("IP 封禁处理失败")
                        return None, False
                if response.status_code == 200:
                    break
                logger.warning(f"遇到 {response.status_code} 错误，等待 2 秒后重试...")
                time.sleep(2)
            except Exception as e:
                logger.error(f"访问登录页失败: {e}")
                if retry < max_retries - 1:
                    time.sleep(2)
                    continue
                return None, False
        if not response:
            logger.error("登录准备页面响应为空")
            return None, False
        if response.status_code != 200:
            logger.error(f"访问登录页失败，状态码: {response.status_code}")
            return None, False
        soup = BeautifulSoup(response.text, "html.parser")
        # CAS login: detect encryption mode (AES-CBC salt or RSA fallback)
        execution = soup.find("input", {"name": "execution"})
        event_id = soup.find("input", {"name": "_eventId"})
        salt_input = soup.find("input", {"id": "pwdDefaultEncryptSalt"})
        lt_input = soup.find("input", {"name": "lt"})
        dllt_input = soup.find("input", {"name": "dllt"})
        execution_val = execution["value"] if execution else None
        event_id_val = event_id["value"] if event_id else "submit"
        if not execution_val:
            logger.error(f"Login params error: execution={bool(execution_val)}")
            return None, False
        use_aes = bool(salt_input and salt_input.get("value"))
        if use_aes:
            salt = salt_input["value"]
            encrypted_password = encrypt_cas_password_aes(self.password1, salt)
        else:
            encrypted_password = encrypt_cas_password(self.password1)
        login_url = HttpClient.get_edu_url(
            "authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
        )
        if use_aes:
            lt_val = lt_input["value"] if lt_input else ""
            dllt_val = dllt_input["value"] if dllt_input else "userNamePasswordLogin"
            login_data = {
                "vpn-0": "",
                "service": "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/",
                "username": self.username,
                "password": encrypted_password,
                "lt": lt_val,
                "dllt": dllt_val,
                "execution": "e1s1",
                "_eventId": event_id_val or "submit",
                "rememberMe": "true",
                "rmShown": "1",
                "submit": "\u767b \u5f55"
            }
        else:
            login_type_input = soup.find("input", {"name": "loginType"})
            login_type = login_type_input["value"] if login_type_input else "1"
            login_data = {
                "vpn-0": "",
                "service": "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/",
                "username": self.username,
                "password": encrypted_password,
                "execution": execution_val,
                "encrypted": "true",
                "_eventId": event_id_val or "submit",
                "loginType": login_type,
                "rememberMe": "true",
                "rmShown": "1",
                "submit": "\u767b \u5f55"
            }
        headers = {
            "Origin": "https://webvpn.njfu.edu.cn",
            "Referer": login_prepare_url,
            "Content-Type": "application/x-www-form-urlencoded"
        }
        try:
            login_response = self.session.post(
                login_url,
                headers=headers,
                data=login_data,
                allow_redirects=False,
                timeout=15
            )
        except Exception as e:
            logger.error(f"登录请求失败: {e}")
            return None, False
        if not login_response:
            logger.error("登录响应为空")
            return None, False
        if login_response.status_code == 302:
            location = login_response.headers.get("Location")
            if not location:
                return None, False
            ticket_match = re.search(r'ticket=([^&]+)', location)
            if not ticket_match:
                return None, False
            ticket = ticket_match.group(1)
            final_auth_url = f"https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/?ticket={ticket}"
            try:
                final_response = self.session.get(final_auth_url, timeout=10)
                if final_response and final_response.status_code == 200:
                    self.my_client_ticket = self._get_cookie_val("my_client_ticket")
                    return self.my_client_ticket, False
            except Exception:
                pass
            return None, False
        elif login_response.status_code == 200:
            soup = BeautifulSoup(login_response.text, "html.parser")
            dyn_form = soup.find("form", {"id": "casDynamicLoginForm"}) or soup.find("input", {"name": "dynamicCode"})
            if dyn_form:
                exec_input = soup.find("input", {"name": "execution"})
                self.last_dynamic_execution = exec_input["value"] if exec_input else "e1s2"
                logger.warning(f"用户 {self.username} 第一级认证触发 CAS 短信二次认证 (execution={self.last_dynamic_execution})")
                return None, "need_dynamic_code"
            need_captcha = self.check_need_captcha(self.username)
            return None, need_captcha
        else:
            logger.error(f"登录失败，状态码: {login_response.status_code}")
            try:
                soup = BeautifulSoup(login_response.text, "html.parser")
                msg = soup.find(id="msg")
                if msg:
                    logger.error(f"页面返回错误信息: {msg.text.strip()}")
            except:
                pass
            return None, False
    def get_public_key(self):
        public_key_url = HttpClient.get_lib_url("ic-web/login/publicKey?vpn-12-libseat.njfu.edu.cn")
        api_headers = {"accept": "application/json, text/plain, */*"}
        response = None  
        max_retries = 2
        for retry in range(max_retries):
            try:
                response = self.session.get(
                    public_key_url,
                    headers=api_headers,
                    timeout=10
                )
                if check_ip_blocked(response, "获取公钥"):
                    if handle_ip_block(response, "获取公钥"):
                        logger.info("IP 封禁处理成功，重试获取公钥...")
                        time.sleep(2)
                        continue
                    else:
                        logger.error("IP 封禁处理失败")
                        return None, None
                if response and response.status_code == 200:
                    try:
                        data = response.json()
                        if data.get("code") == 0:
                            pub_data = data.get("data", {})
                            return pub_data.get("publicKey"), pub_data.get("nonceStr")
                        else:
                            logger.warning(f"获取公钥API返回错误代码: {data}")
                    except Exception as e:
                        logger.error(f"解析公钥响应JSON失败: {str(e)}, 内容: {response.text[:100]}")
                else:
                    status = response.status_code if response else "None"
                    logger.warning(f"获取公钥HTTP请求失败, 状态码: {status}")
                    if response:
                        logger.warning(f"响应内容: {response.text[:500]}")
                    else:
                        logger.warning("响应对象为空 (None)")
            except Exception as e:
                logger.error(f"获取公钥过程中发生异常: {str(e)}")
                logger.error(traceback.format_exc())
            if retry < max_retries - 1:
                time.sleep(2)
        return None, None
    def _second_level_auth_legacy(self, max_attempts=5):
        login_url = HttpClient.get_lib_url("ic-web/login/user?vpn-12-libseat.njfu.edu.cn")
        api_headers = {
            "accept": "application/json, text/plain, */*",
            "content-type": "application/json;charset=UTF-8",
        }
        for attempt in range(1, max_attempts + 1):
            logger.info(f"正在进行第二级认证(旧版加密): 尝试第 {attempt} 次")
            try:
                public_key, nonce = self.get_public_key()
                if not public_key or not nonce:
                    logger.warning(f"第 {attempt} 次尝试获取公钥失败，等待重试...")
                    time.sleep(2)
                    continue
                try:
                    encrypted_password = encrypt_lib_password(self.password2, nonce, public_key)
                except Exception as e:
                    logger.error(f"密码加密失败: {str(e)}")
                    time.sleep(2)
                    continue
                payload = {
                    "logonName": self.username,
                    "password": encrypted_password,
                    "captcha": "",
                    "consoleType": 16,
                    "privacy": True
                }
                response = self.session.post(
                    login_url,
                    headers=api_headers,
                    json=payload,
                    timeout=15
                )
                if check_ip_blocked(response, "第二级认证(旧版)"):
                    if handle_ip_block(response, "第二级认证(旧版)"):
                        logger.info("IP 封禁处理成功，重试第二级认证(旧版)...")
                        time.sleep(2)
                        continue
                    else:
                        logger.error("IP 封禁处理失败")
                if response and response.status_code == 200:
                    try:
                        result = response.json()
                        if result.get("code") == 0:
                            user_data = result.get("data", {})
                            self.token = user_data.get("token")
                            self.acc_no = user_data.get("accNo")
                            self.last_auth_time = time.time()
                            return self.token, self.acc_no
                        else:
                            msg = result.get("message", "密码错误")
                            self.last_lib_login_error = f"图书馆系统返回: {msg}"
                            logger.warning(f"第 {attempt} 次认证未成功，图书馆系统提示: {msg}")
                    except Exception as e:
                        logger.warning(f"解析登录响应异常: {e}")
                else:
                    status = response.status_code if response else "None"
                    logger.warning(f"第 {attempt} 次认证HTTP状态异常: {status}")
                time.sleep(1)
            except Exception as e:
                logger.error(f"第 {attempt} 次认证发生异常: {str(e)}")
                time.sleep(1)
        return None, None

    def _second_level_auth_sso(self, max_attempts=5):
        import re
        from urllib.parse import urlparse, parse_qs
        
        for attempt in range(1, max_attempts + 1):
            logger.info(f"正在进行第二级认证(SSO): 尝试第 {attempt} 次")
            try:
                # 1. call auth/address to get CAS URL
                addr_url = HttpClient.get_lib_url("ic-web/auth/address")
                params = {
                    "finalAddress": HttpClient.get_lib_url(""),
                    "errPageUrl": HttpClient.get_lib_url("#/error"),
                    "manager": "false",
                    "consoleType": "16"
                }
                
                resp = self.session.get(addr_url, params=params, timeout=10)
                if check_ip_blocked(resp, "获取CAS地址"):
                    if handle_ip_block(resp, "获取CAS地址"):
                        continue
                
                data = resp.json()
                cas_url = data.get("data")
                if not cas_url:
                    logger.error("未获取到 SSO CAS URL")
                    continue
                    
                # 2. Visit CAS URL through WebVPN
                if "libseat.njfu.edu.cn/" in cas_url:
                    path_and_query = cas_url.split("libseat.njfu.edu.cn/")[1]
                    cas_url = HttpClient.get_lib_url(path_and_query)
                
                self.session.max_redirects = 10
                cas_resp = self.session.get(cas_url, timeout=15, allow_redirects=True)
                
                # 3. Extract JS redirect
                if cas_resp.status_code == 200:
                    match = re.search(r"window\.location\.href\s*=\s*['\"]([^'\"]+)['\"]", cas_resp.text)
                    if match:
                        redirect_url = match.group(1)
                        if "libseat.njfu.edu.cn/" in redirect_url:
                            path_and_query = redirect_url.split("libseat.njfu.edu.cn/")[1]
                            redirect_url = HttpClient.get_lib_url(path_and_query)
                        self.session.get(redirect_url, timeout=15, allow_redirects=True)
                
                # 4. Fetch user info to get token
                user_info_url = HttpClient.get_lib_url("ic-web/auth/userInfo")
                info_resp = self.session.get(user_info_url, timeout=10)
                if info_resp.status_code == 200:
                    info_data = info_resp.json().get("data", {})
                    if info_data and "token" in info_data:
                        self.token = info_data.get("token")
                        self.acc_no = info_data.get("accNo")
                        self.last_auth_time = time.time()
                        logger.info("SSO 认证成功！")
                        return self.token, self.acc_no
                
                logger.warning(f"第 {attempt} 次认证未成功，状态码: {info_resp.status_code}")
                time.sleep(2)
            except Exception as e:
                logger.error(f"第 {attempt} 次认证发生异常: {str(e)}")
                time.sleep(2)
                
        return None, None

    def second_level_auth(self, max_attempts=5):
        if not self.my_client_ticket:
            return None, None
            
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            future_sso = executor.submit(self._second_level_auth_sso, 3)
            future_legacy = executor.submit(self._second_level_auth_legacy, max_attempts)
            
            for future in concurrent.futures.as_completed([future_sso, future_legacy]):
                try:
                    token, acc_no = future.result()
                    if token and acc_no:
                        logger.info("认证成功，返回最快的结果")
                        return token, acc_no
                except Exception as e:
                    logger.error(f"并发验证时发生异常: {e}")
                    
        logger.warning("所有验证方式均失败")
        return None, None
    @staticmethod
    def _auth_response_error(response, stage):
        """记录不含查询串、Cookie 和 Token 的诊断，区分访问失败与密码错误。"""
        def safe_address(url):
            parts = urlsplit(url or "")
            return f"{parts.scheme}://{parts.hostname or ''}{parts.path.split(';', 1)[0]}"

        location = urljoin(response.url or "", response.headers.get("Location", ""))
        soup = BeautifulSoup(response.content, "html.parser")
        title = soup.title.get_text(" ", strip=True)[:80] if soup.title else "无标题"
        logger.warning(
            "%s: HTTP=%s, 地址=%s, 跳转=%s, 页面=%s",
            stage, response.status_code, safe_address(response.url),
            safe_address(location) if response.headers.get("Location") else "无", title
        )
        paths = urlsplit(response.url or "").path + urlsplit(location).path
        if response.status_code == 403 or "access_forbidden" in paths:
            return f"{stage}拒绝访问（HTTP {response.status_code}），请检查服务器电脑的校内访问状态"
        if response.status_code != 200:
            return f"{stage}访问异常（HTTP {response.status_code}），请查看服务器日志"
        return f"{stage}未返回所需登录信息（页面：{title}），请查看服务器日志"

    def _follow_cas_page_redirects(self, response):
        """处理 CAS 回调页的相对/绝对 JS 跳转，并返回最后一页供重新解析。"""
        from html import unescape

        for _ in range(3):
            soup = BeautifulSoup(response.content, "html.parser")
            if response.status_code != 200 or soup.find("input", {"name": "execution"}):
                return response
            script = "\n".join(tag.get_text() for tag in soup.find_all("script", src=False))
            match = re.search(
                r"(?:window\.)?location(?:\.href)?\s*=\s*['\"]([^'\"]+)['\"]"
                r"|(?:window\.)?location\.(?:replace|assign)\(\s*['\"]([^'\"]+)['\"]",
                script
            )
            if not match:
                return response
            target = urljoin(response.url, unescape(match.group(1) or match.group(2)))
            parts = urlsplit(target)
            if parts.scheme not in ("http", "https") or parts.hostname not in (
                "uia.njfu.edu.cn", "libseat.njfu.edu.cn"
            ):
                return response
            if parts.hostname == "libseat.njfu.edu.cn" and parts.scheme == "http":
                target = urlunsplit(("https", parts.netloc, parts.path, parts.query, parts.fragment))
            response = self.session.get(target, timeout=15, allow_redirects=True)
        return response

    def _load_direct_user_info(self, url):
        response = HttpClient.get_lib_response(self.session, url)
        try:
            result = response.json() if response.status_code == 200 else None
        except ValueError:
            result = None
        data = result.get("data") if isinstance(result, dict) else None
        if (isinstance(data, dict) and result.get("code") == 0
                and data.get("token") and data.get("accNo")):
            self.token = data["token"]
            self.acc_no = data["accNo"]
            self.last_auth_time = time.time()
            self.save_session()
            return True, response, result
        return False, response, result

    def direct_campus_cas_auth(self):
        """通过校内 CAS SSO 登录图书馆，不使用 WebVPN 续期流程。"""
        logger.info(f"用户 {self.username} 开始直连校园网 CAS SSO 统一认证流程...")
        library_base = HttpClient.get_lib_url("").rstrip("/")

        def callback_params(base):
            return {
                "finalAddress": base + "/",
                "errPageUrl": base + "/#/error",
                "manager": "false",
                "consoleType": "16",
            }

        params = callback_params(library_base)
        cas_url = None
        try:
            addr_resp = HttpClient.get_lib_response(
                self.session, library_base + "/ic-web/auth/address", params=params
            )
            # 浏览器升级到 HTTPS 后也会用 HTTPS 生成回调地址，保持一致。
            canonical = urlsplit(addr_resp.url)
            if (canonical.scheme == "https"
                    and canonical.hostname == urlsplit(library_base).hostname
                    and urlsplit(library_base).scheme == "http"):
                library_base = urlunsplit(("https", canonical.netloc, "", "", ""))
                params = callback_params(library_base)
                addr_resp = HttpClient.get_lib_response(
                    self.session, library_base + "/ic-web/auth/address", params=params
                )

            if addr_resp.status_code in (301, 302, 303, 307, 308):
                cas_url = urljoin(addr_resp.url, addr_resp.headers.get("Location", ""))
            elif addr_resp.status_code == 200:
                try:
                    result = addr_resp.json()
                except ValueError:
                    result = None
                if isinstance(result, dict):
                    candidate = result.get("data")
                    if isinstance(candidate, str) and candidate:
                        logger.info("图书馆 auth/address 接口返回: %s", candidate)
                        cas_url = candidate
                    elif result.get("code", 0) != 0:
                        logger.warning("获取图书馆登录入口接口返回错误: %s", result.get("message"))
        except requests.RequestException as e:
            logger.warning("获取图书馆登录入口异常: %s", type(e).__name__)

        # 解析最终的 CAS 登录地址
        callback = library_base + "/ic-web/auth/cas?" + urlencode(params)
        default_cas_url = "https://uia.njfu.edu.cn/authserver/login?" + urlencode({"service": callback})

        if not cas_url:
            cas_url = default_cas_url
        else:
            parts = urlsplit(cas_url)
            if parts.hostname == "uia.njfu.edu.cn":
                pass
            elif parts.hostname == "libseat.njfu.edu.cn" or "ic-web/auth/cas" in cas_url:
                cas_url = "https://uia.njfu.edu.cn/authserver/login?" + urlencode({"service": cas_url})
            else:
                logger.warning("无法识别的 CAS 地址: %s，使用默认校内统一认证地址", cas_url)
                cas_url = default_cas_url

        cas_url = HttpClient.rewrite_cas_service_https(cas_url)
        logger.info("CAS SSO 目标地址: %s", cas_url)
        user_info_url = library_base + "/ic-web/auth/userInfo"

        # 已有 SSO 会话可能直接返回回调页；没有表单时仅重新打开一次登录页。
        try:
            for attempt in range(2):
                cas_resp = self.session.get(cas_url, timeout=15, allow_redirects=True)
                cas_resp = self._follow_cas_page_redirects(cas_resp)
                success, info_resp, _ = self._load_direct_user_info(user_info_url)
                if success:
                    logger.info(f"用户 {self.username} 已有校内 SSO 会话有效")
                    return True, None
                soup = BeautifulSoup(cas_resp.content, "html.parser")
                execution = soup.find("input", {"name": "execution"})
                if execution and execution.get("value"):
                    break
                if attempt == 0:
                    logger.info("CAS 未返回密码表单或旧会话失效，清理 Cookie 并重新请求登录页")
                    self.session.cookies.clear()
                    parts = urlsplit(cas_url)
                    query = [(key, value) for key, value in parse_qsl(parts.query) if key != "renew"]
                    query.append(("renew", "true"))
                    cas_url = urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(query), ""))
                    continue
                if cas_resp.status_code != 200:
                    return False, self._auth_response_error(cas_resp, "CAS 登录页")
                if "access_forbidden" in urlsplit(cas_resp.url).path:
                    return False, self._auth_response_error(cas_resp, "图书馆登录回调")
                if info_resp.status_code == 403 or "access_forbidden" in info_resp.headers.get("Location", ""):
                    return False, self._auth_response_error(info_resp, "图书馆会话接口")
                return False, self._auth_response_error(cas_resp, "CAS 登录页缺少 execution 参数")
        except requests.RequestException as e:
            logger.warning("获取 CAS 登录页面异常: %s", type(e).__name__)
            return False, f"连接统一认证或图书馆会话接口失败（{type(e).__name__}）"

        password = self.password1 or self.password2
        if not password:
            return False, "会话已失效，请填写统一认证密码后重新登录"
        form = execution.find_parent("form") or soup
        login_data = {
            item["name"]: item.get("value", "")
            for item in form.find_all("input", {"type": "hidden"}) if item.get("name")
        }
        login_data.update({
            "username": self.username,
            "execution": execution["value"],
            "_eventId": login_data.get("_eventId") or "submit",
            "rememberMe": "true",
            "rmShown": "1",
            "submit": "登 录",
        })
        salt_input = soup.find("input", {"id": "pwdDefaultEncryptSalt"})
        if salt_input and salt_input.get("value"):
            login_data["password"] = encrypt_cas_password_aes(password, salt_input["value"])
            login_data.setdefault("lt", "")
            login_data.setdefault("dllt", "userNamePasswordLogin")
        else:
            login_data["password"] = encrypt_cas_password(password)
            login_data["encrypted"] = "true"
            login_data.setdefault("loginType", "1")

        post_url = urljoin(cas_resp.url, form.get("action") or cas_resp.url)
        post_parts = urlsplit(post_url)
        if post_parts.hostname != "uia.njfu.edu.cn" or post_parts.scheme != "https":
            return False, "统一认证表单提交地址异常"
        headers = {
            "Origin": "https://uia.njfu.edu.cn",
            "Referer": cas_resp.url,
            "Content-Type": "application/x-www-form-urlencoded",
        }
        try:
            submit_resp = self.session.post(
                post_url, headers=headers, data=login_data, allow_redirects=True, timeout=15
            )
            submit_resp = self._follow_cas_page_redirects(submit_resp)
            result_page = BeautifulSoup(submit_resp.content, "html.parser")
            msg_tag = result_page.find("span", {"id": "msg"}) or result_page.find("div", {"class": "auth_error"})
            if msg_tag and msg_tag.get_text(strip=True):
                return False, f"统一认证提示: {msg_tag.get_text(strip=True)}"
            # 普通登录页也有隐藏的动态码表单，不能仅凭其存在判为短信二次认证。
            if (result_page.find("form", {"id": "casDynamicLoginForm"})
                    and not result_page.find("form", {"id": "casLoginForm"})):
                return False, "统一认证提示需要短信验证码"

            success, info_resp, info_result = self._load_direct_user_info(user_info_url)
            if success:
                logger.info(f"用户 {self.username} 校内 CAS SSO 认证成功")
                return True, None
            if submit_resp.status_code != 200:
                return False, self._auth_response_error(submit_resp, "CAS 登录回调")
            if isinstance(info_result, dict):
                message = info_result.get("message") or "未返回有效 Token 和账号"
                return False, f"座位系统授权失败: {message}"
            return False, self._auth_response_error(info_resp, "图书馆会话接口")
        except requests.RequestException as e:
            logger.warning("提交 CAS 登录或获取图书馆会话异常: %s", type(e).__name__)
            return False, f"统一认证请求失败（{type(e).__name__}）"

    def authenticate(self):
        try:
            if self.token and self.acc_no and self.is_valid():
                logger.info(f"用户 {self.username} 复用本地有效会话凭据")
                return True, False, None

            # 直连校园网模式：执行纯 CAS SSO 认证，无需 WebVPN 与图书馆独立密码
            if getattr(Config, "DIRECT_CAMPUS_MODE", False):
                logger.info(f"用户 {self.username} 运行在直连校园网模式，执行 CAS SSO 纯密码认证...")
                success, err_msg = self.direct_campus_cas_auth()
                if success:
                    return True, False, None
                return False, False, err_msg or "直连 CAS 统一认证失败，请检查学号与统一认证密码"

            my_client_ticket, need_captcha = self.first_level_auth()
            if need_captcha == "need_dynamic_code":
                logger.warning(f"用户 {self.username} 统一认证需要手机动态验证码(二次认证)")
                return False, "need_dynamic_code", "统一认证需要手机动态验证码(二次认证)"
            if need_captcha:
                return False, True, "需要验证码,请在统一认证中心登录一次"
            if not my_client_ticket:
                return False, False, "统一认证失败(密码错误或参数解析失败)，请查看后台日志"
            logger.info("统一认证成功，等待 2 秒以同步 WebVPN 会话...")
            time.sleep(2)
            token, acc_no = self.second_level_auth()
            if not token or not acc_no:
                return False, False, "图书馆密码错误，请重新输入"
            self.save_session()
            return True, False, None
        except Exception as e:
            return False, False, f"认证过程中发生错误: {str(e)}"
    def silent_sso_refresh(self):
        """利用已有 CAS SSO 会话（CASTGC）静默换取新 WebVPN Ticket，无需重新输密码和验证码"""
        try:
            cas_service_url = HttpClient.get_edu_url(
                "authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
            )
            resp = self.session.get(cas_service_url, allow_redirects=False, timeout=10)
            ticket = None
            current_resp = resp
            for _ in range(5):
                if current_resp.status_code in [301, 302, 303, 307]:
                    location = current_resp.headers.get("Location", "")
                    ticket_match = re.search(r'ticket=([^&]+)', location)
                    if ticket_match:
                        ticket = ticket_match.group(1)
                        break
                    if location:
                        current_resp = self.session.get(location, allow_redirects=False)
                    else:
                        break
                else:
                    break

            if ticket:
                final_auth_url = f"https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/?ticket={ticket}"
                self.session.get(final_auth_url, timeout=10)
                self.my_client_ticket = self._get_cookie_val("my_client_ticket")
                self.save_session()
                logger.info(f"用户 {self.username} 通过 CAS SSO 静默换取新 WebVPN 票据成功")
                return True
        except Exception as e:
            logger.warning(f"用户 {self.username} 静默 SSO 换票异常: {e}")
        return False

    def is_valid(self):
        is_direct = getattr(Config, "DIRECT_CAMPUS_MODE", False)
        if is_direct:
            if not self.token or not self.acc_no:
                return False
        else:
            if not self.my_client_ticket or not self.token or not self.acc_no:
                return False
        try:
            from utils.date_utils import get_today_date
            today = get_today_date()
            url = HttpClient.get_lib_url("ic-web/reserve/resvInfo")
            params = {
                "needStatus": "8454",
                "unneedStatus": "128",
                "beginDate": today,
                "endDate": today
            }
            if not is_direct:
                params["vpn-12-libseat.njfu.edu.cn"] = ""
            api_headers = {
                "accept": "application/json, text/plain, */*",
                "token": self.token,
                "lan": "1",
            }
            if is_direct:
                response = HttpClient.get_lib_response(
                    self.session, url, headers=api_headers, params=params, timeout=10
                )
            else:
                response = self.session.get(
                    url, headers=api_headers, params=params, timeout=10, allow_redirects=False
                )
            if not is_direct and response is not None and response.status_code in [301, 302, 303, 307, 308]:
                logger.info(f"用户 {self.username} is_valid 遇重定向，尝试静默 SSO 续期...")
                if self.silent_sso_refresh():
                    response = self.session.get(
                        url,
                        headers=api_headers,
                        params=params,
                        timeout=10,
                        allow_redirects=False
                    )
            valid = bool(response is not None and response.status_code == 200 and response.json().get("code") == 0)
            if not valid:
                logger.info(f"用户 {self.username} 的认证已失效 (HTTP {response.status_code if response is not None else 'N/A'})")
            return valid
        except Exception as e:
            logger.error(f"验证认证有效性时出错: {str(e)}")
            return False

    def _get_session_dir(self):
        backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        session_dir = os.path.join(backend_dir, "data", "sessions")
        os.makedirs(session_dir, exist_ok=True)
        return session_dir

    def save_session(self):
        if not self.token or not self.acc_no:
            return
        try:
            session_file = os.path.join(self._get_session_dir(), f"{self.username}.json")
            data = {
                "username": self.username,
                "token": self.token,
                "acc_no": self.acc_no,
                "my_client_ticket": self.my_client_ticket,
                "last_auth_time": self.last_auth_time or time.time(),
                "cookies": [{"name": c.name, "value": c.value, "domain": c.domain, "path": c.path} for c in self.session.cookies]
            }
            with open(session_file, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            logger.info(f"已保存用户 {self.username} 的会话凭据到本地文件")
        except Exception as e:
            logger.warning(f"保存会话失败: {e}")

    def load_session(self):
        try:
            session_file = os.path.join(self._get_session_dir(), f"{self.username}.json")
            if os.path.exists(session_file):
                with open(session_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                self.token = data.get("token")
                self.acc_no = data.get("acc_no")
                self.my_client_ticket = data.get("my_client_ticket")
                self.last_auth_time = data.get("last_auth_time")
                cookies = data.get("cookies", {})
                if isinstance(cookies, list):
                    for c in cookies:
                        self.session.cookies.set(c["name"], c["value"], domain=c.get("domain", ""), path=c.get("path", "/"))
                elif isinstance(cookies, dict):
                    requests.utils.cookiejar_from_dict(cookies, cookiejar=self.session.cookies)
                logger.info(f"已从本地文件恢复用户 {self.username} 的会话凭据")
                return True
        except Exception as e:
            logger.warning(f"读取会话文件失败: {e}")
        return False

    def keep_alive(self):
        """发送心跳请求维持 WebVPN 和图书馆 Session"""
        if not self.token:
            return False
        try:
            is_direct = getattr(Config, "DIRECT_CAMPUS_MODE", False)
            if not is_direct:
                # 1. 访问 WebVPN 前端门户以刷新网关活跃度
                try:
                    self.session.get("https://webvpn.njfu.edu.cn/rump_frontend/", timeout=5, allow_redirects=False)
                except Exception:
                    pass

            # 2. 访问图书馆用户信息接口保活
            url = HttpClient.get_lib_url("ic-web/auth/userInfo")
            headers = {
                "token": self.token,
                "lan": "1"
            }
            resp = self.session.get(url, headers=headers, timeout=10, allow_redirects=False)
            if resp and resp.status_code == 200:
                try:
                    res_data = resp.json()
                    if res_data.get("code") == 0:
                        self.last_auth_time = time.time()
                        self.save_session()
                        logger.debug(f"用户 {self.username} 心跳保活成功")
                        return True
                except Exception:
                    pass

            # 直连模式下若 session 失效，直接使用本地密码自动重新认证，实现无感续期
            if is_direct and self.password2:
                logger.info(f"用户 {self.username} 直连心跳失效，自动执行纯密码重登...")
                token, acc_no = self._second_level_auth_legacy(max_attempts=2)
                if token and acc_no:
                    self.save_session()
                    logger.info(f"用户 {self.username} 密码重登成功，会话已自动恢复")
                    return True

            # 3. 若遇 302 重定向（Ticket超时），尝试静默 SSO 换票（WebVPN 模式）
            if not is_direct and resp and resp.status_code in [301, 302, 303, 307]:
                logger.info(f"用户 {self.username} WebVPN 票据超时 (302)，尝试通过 CAS SSO 静默续期...")
                if self.silent_sso_refresh():
                    resp2 = self.session.get(url, headers=headers, timeout=10, allow_redirects=False)
                    if resp2 and resp2.status_code == 200 and resp2.json().get("code") == 0:
                        self.last_auth_time = time.time()
                        self.save_session()
                        logger.info(f"用户 {self.username} 静默续期后心跳测试通过")
                        return True

            logger.warning(f"用户 {self.username} 心跳保活返回异常: {resp.status_code if resp else 'None'}")
            return False
        except Exception as e:
            logger.error(f"用户 {self.username} 心跳保活失败: {e}")
            return False

    def save_pending_session(self):
        """保存处于二次认证过程中的会话凭据及执行流状态"""
        try:
            pending_file = os.path.join(self._get_session_dir(), f"pending_{self.username}.json")
            data = {
                "username": self.username,
                "password1": self.password1,
                "password2": self.password2,
                "last_dynamic_execution": getattr(self, "last_dynamic_execution", "e1s2"),
                "cookies": [{"name": c.name, "value": c.value, "domain": c.domain, "path": c.path} for c in self.session.cookies]
            }
            with open(pending_file, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            logger.info(f"已保存用户 {self.username} 的待完成二次验证会话")
        except Exception as e:
            logger.warning(f"保存待完成验证会话失败: {e}")

    def load_pending_session(self):
        """恢复待完成二次验证的会话"""
        try:
            pending_file = os.path.join(self._get_session_dir(), f"pending_{self.username}.json")
            if os.path.exists(pending_file):
                with open(pending_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                if not self.password1:
                    self.password1 = data.get("password1", "")
                if not self.password2:
                    self.password2 = data.get("password2", "")
                self.last_dynamic_execution = data.get("last_dynamic_execution", "e1s2")
                cookies = data.get("cookies", [])
                if isinstance(cookies, list):
                    for c in cookies:
                        self.session.cookies.set(c["name"], c["value"], domain=c.get("domain", ""), path=c.get("path", "/"))
                elif isinstance(cookies, dict):
                    requests.utils.cookiejar_from_dict(cookies, cookiejar=self.session.cookies)
                logger.info(f"已恢复用户 {self.username} 的待完成二次验证会话")
                return True
        except Exception as e:
            logger.warning(f"恢复待完成验证会话失败: {e}")
        return False

    def send_dynamic_code(self):
        """向 CAS 请求发送动态短信验证码"""
        try:
            url = HttpClient.get_edu_url("authserver/getDynamicCode.do")
            data = {
                "userName": self.username,
                "authCodeTypeName": "reAuthDynamicCodeType"
            }
            ref_url = HttpClient.get_edu_url("authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F")
            headers = {
                "X-Requested-With": "XMLHttpRequest",
                "Content-Type": "application/x-www-form-urlencoded",
                "Origin": "https://webvpn.njfu.edu.cn",
                "Referer": ref_url,
                "Accept": "application/json, text/javascript, */*; q=0.01"
            }
            resp = self.session.post(url, data=data, headers=headers, timeout=10)
            if resp and resp.status_code == 200:
                res_json = resp.json()
                if res_json.get("res") in ["success", "wechat_success", "cpdaily_success"]:
                    self.save_pending_session()
                    logger.info(f"动态码发送成功: {res_json.get('returnMessage')} {res_json.get('mobile', '')}")
                    return True, res_json.get("returnMessage", "动态码已发送"), res_json.get("mobile", "")
                else:
                    msg = res_json.get("returnMessage", "发送失败")
                    logger.warning(f"动态码发送失败: {msg}")
                    return False, msg, None
            return False, f"请求发送动态码失败，状态码: {resp.status_code if resp else 'None'}", None
        except Exception as e:
            logger.error(f"发送动态码异常: {str(e)}")
            return False, str(e), None

    def submit_dynamic_code(self, dynamic_code, execution=None):
        """提交短信动态码完成第一级及第二级认证"""
        try:
            if not self.session.cookies:
                self.load_pending_session()

            exec_val = execution or getattr(self, "last_dynamic_execution", "e1s2")
            login_url = HttpClient.get_edu_url(
                "authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
            )
            data = {
                "username": self.username,
                "dynamicCode": dynamic_code,
                "execution": exec_val,
                "_eventId": "submit",
                "rememberMe": "true",
                "rmShown": "1"
            }
            headers = {
                "Origin": "https://webvpn.njfu.edu.cn",
                "Referer": login_url,
                "Content-Type": "application/x-www-form-urlencoded"
            }
            resp = self.session.post(login_url, headers=headers, data=data, allow_redirects=False, timeout=15)
            
            # 循环跟踪 301/302 重定向以提取 ticket
            ticket = None
            current_resp = resp
            for _ in range(5):
                if current_resp.status_code in [301, 302, 303, 307]:
                    location = current_resp.headers.get("Location", "")
                    ticket_match = re.search(r'ticket=([^&]+)', location)
                    if ticket_match:
                        ticket = ticket_match.group(1)
                        break
                    if location:
                        current_resp = self.session.get(location, allow_redirects=False)
                    else:
                        break
                else:
                    break

            if ticket:
                final_auth_url = f"https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/?ticket={ticket}"
                final_resp = self.session.get(final_auth_url, timeout=10)
                self.my_client_ticket = self._get_cookie_val("my_client_ticket")
                logger.info("动态码认证成功，第一级票据已就绪，开始第二级认证...")
                time.sleep(1.5)
                token, acc_no = self.second_level_auth()
                if token and acc_no:
                    self.save_session()
                    pending_file = os.path.join(self._get_session_dir(), f"pending_{self.username}.json")
                    if os.path.exists(pending_file):
                        try:
                            os.remove(pending_file)
                        except Exception:
                            pass
                    return True, "认证成功并已获取图书馆Token"
                return False, "第一级认证成功，但图书馆系统密码认证失败"

            if current_resp.status_code == 200:
                soup = BeautifulSoup(current_resp.text, "html.parser")
                msg_el = soup.find(id="msg")
                err_span = soup.find("span", {"id": "dynamicCodeError"})
                err_msg = None
                if msg_el and msg_el.text.strip():
                    err_msg = msg_el.text.strip()
                elif err_span and err_span.text.strip() and "display:none" not in err_span.get("style", ""):
                    err_msg = err_span.text.strip()
                return False, err_msg or "动态码错误或已过期"

            return False, f"提交动态码异常，状态码: {current_resp.status_code}"
        except Exception as e:
            logger.error(f"提交动态码异常: {str(e)}")
            return False, str(e)
    def get_captcha_image(self):
        global captcha_session
        try:
            captcha_session = requests.Session()
            captcha_session.verify = False
            proxy_url = Config.get_proxy_url()
            if proxy_url:
                captcha_session.proxies = {
                    'http': proxy_url,
                    'https': proxy_url
                }
                logger.info(f"验证码会话已配置 WARP 代理: {proxy_url}")
            frontend_url = "https://webvpn.njfu.edu.cn/rump_frontend/login/"
            captcha_session.get(frontend_url, timeout=10)
            if "my_client_ticket" not in captcha_session.cookies:
                return None
            login_url = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc=/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1/authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
            captcha_session.get(login_url, timeout=10)
            captcha_url = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc=/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1/authserver/captcha.html"
            ts = str(int(time.time() * 1000))
            captcha_response = captcha_session.get(f"{captcha_url}?ts={ts}", timeout=10)
            if captcha_response.status_code == 200:
                return captcha_response.content
            return None
        except Exception as e:
            logger.error(f"获取验证码图片失败: {str(e)}")
            return None
    def authenticate_with_captcha(self, captcha):
        global captcha_session
        if not captcha_session:
            return False, "验证码会话无效，请刷新页面重试"
        session = captcha_session
        try:
            self.get_route_cookie(session)
            login_url = HttpClient.get_edu_url(
                "authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
            )
            headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
            }
            login_page = session.get(login_url, headers=headers, timeout=10)
            soup = BeautifulSoup(login_page.text, "html.parser")
            # CAS login: detect encryption mode (AES-CBC salt or RSA fallback)
            execution = soup.find("input", {"name": "execution"})
            event_id = soup.find("input", {"name": "_eventId"})
            salt_input = soup.find("input", {"id": "pwdDefaultEncryptSalt"})
            lt_input = soup.find("input", {"name": "lt"})
            dllt_input = soup.find("input", {"name": "dllt"})
            if not execution:
                return False, "无法获取登录表单信息，请刷新页面重试"
            execution_val = execution["value"]
            event_id_val = event_id["value"] if event_id else "submit"
            use_aes = bool(salt_input and salt_input.get("value"))
            if use_aes:
                salt = salt_input["value"]
                encrypted_password = encrypt_cas_password_aes(self.password1, salt)
            else:
                encrypted_password = encrypt_cas_password(self.password1)
            login_post_url = HttpClient.get_edu_url(
                "authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
            )
            if use_aes:
                lt_val = lt_input["value"] if lt_input else ""
                dllt_val = dllt_input["value"] if dllt_input else "userNamePasswordLogin"
                login_data = {
                    "username": self.username,
                    "password": encrypted_password,
                    "captchaResponse": captcha,
                    "lt": lt_val,
                    "dllt": dllt_val,
                    "execution": "e1s1",
                    "_eventId": event_id_val,
                    "rmShown": "1",
                    "vpn-0": "",
                    "service": "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/",
                    "submit": "\u767b \u5f55"
                }
            else:
                login_type_input = soup.find("input", {"name": "loginType"})
                login_type = login_type_input["value"] if login_type_input else "1"
                login_data = {
                    "username": self.username,
                    "password": encrypted_password,
                    "captchaResponse": captcha,
                    "execution": execution_val,
                    "encrypted": "true",
                    "_eventId": event_id_val,
                    "loginType": login_type,
                    "vpn-0": "",
                    "service": "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/",
                    "submit": "\u767b \u5f55"
                }
            post_headers = {
                "Origin": "https://webvpn.njfu.edu.cn",
                "Referer": login_url,
                "Content-Type": "application/x-www-form-urlencoded",
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }
            login_response = session.post(
                login_post_url,
                headers=post_headers,
                data=login_data,
                allow_redirects=False,
                timeout=15
            )
            if login_response.status_code == 302:
                location = login_response.headers.get("Location", "")
                if "ticket=" in location:
                    ticket_match = re.search(r'ticket=([^&]+)', location)
                    ticket = ticket_match.group(1)
                    final_url = f"https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/?ticket={ticket}"
                    session.get(final_url, timeout=15)
                    ticket = self._get_cookie_val("my_client_ticket", session=session)
                    if ticket:
                        self.my_client_ticket = ticket
                        self.session = session
                        token, acc_no = self.second_level_auth()
                        if token and acc_no:
                            return True, "认证成功"
                        else:
                            return False, "图书馆密码错误，请确认后重试"
                    else:
                        return False, "认证流程异常，未获取有效凭证"
                else:
                    return False, "认证异常，重定向URL中未找到ticket"
            elif login_response.status_code == 200:
                soup = BeautifulSoup(login_response.text, "html.parser")
                error_msg = soup.find('span', {'id': 'msg'}) or soup.find('div', {'id': 'msg'})
                if error_msg:
                    return False, f"认证失败: {error_msg.text.strip()}"
                return False, "认证失败，用户名/密码错误或验证码错误"
            else:
                return False, f"网络错误，状态码: {login_response.status_code}"
        except Exception as e:
            logger.error(f"验证码认证过程发生错误: {str(e)}")
            return False, f"认证过程发生错误: {str(e)}"
class AuthManager:
    _auth_cache = {}
    @staticmethod
    def get_authenticator(user):
        auth = AuthManager._auth_cache.get(user.id)
        if auth and auth.is_valid():
            logger.debug(f"使用缓存的认证器: 用户 {user.username}")
            return auth
        try:
            logger.info(f"为用户 {user.username} 创建新的认证器")
            edu_password = decrypt_password(user.edu_password)
            lib_password = decrypt_password(user.lib_password)
            auth = LibraryAuthenticator(user.username, edu_password, lib_password)
            if auth.is_valid():
                AuthManager._auth_cache[user.id] = auth
                logger.info(f"用户 {user.username} 从本地恢复有效凭据成功，复用会话")
                return auth
            auth_result, _, _ = auth.authenticate()
            if auth_result:
                AuthManager._auth_cache[user.id] = auth
                logger.info(f"用户 {user.username} 的认证器创建成功")
                return auth
            else:
                logger.warning(f"用户 {user.username} 的认证失败")
                return None
        except Exception as e:
            logger.error(f"获取认证对象失败: {str(e)}")
            return None
    @staticmethod
    def clear_authenticator(user_id):
        if user_id in AuthManager._auth_cache:
            logger.info(f"清除用户ID {user_id} 的认证缓存")
            del AuthManager._auth_cache[user_id]
    @staticmethod
    def refresh_authenticator(user):
        AuthManager.clear_authenticator(user.id)
        return AuthManager.get_authenticator(user)
def handle_exception(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        try:
            return func(*args, **kwargs)
        except Exception as e:
            error_message = f"函数 {func.__name__} 执行时发生内部错误"
            logger.error(f"{error_message}: {str(e)}")
            logger.error(traceback.format_exc())
            return False, error_message
    return wrapper
