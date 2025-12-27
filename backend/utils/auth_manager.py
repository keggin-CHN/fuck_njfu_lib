import requests
import re
import time
import logging
import traceback
import random
import base64
import os
import urllib3
from bs4 import BeautifulSoup
from Crypto.Cipher import AES, PKCS1_v1_5
from Crypto.Util.Padding import pad
from Crypto.PublicKey import RSA
from cryptography.fernet import Fernet
from functools import wraps

logger = logging.getLogger(__name__)

# 禁用 InsecureRequestWarning
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
        return f"{HttpClient.BASE_URL_PREFIX}{HttpClient.LIB_URL_SUFFIX}/{path}"

    @staticmethod
    def get_edu_url(path):
        return f"{HttpClient.BASE_URL_PREFIX}{HttpClient.EDU_URL_SUFFIX}/{path}"

    @staticmethod
    def request(method, url, headers=None, cookies=None, params=None, data=None, json_data=None, timeout=10,
                allow_redirects=True):
        try:
            request_headers = {**HttpClient.DEFAULT_HEADERS}
            if headers:
                request_headers.update(headers)

            response = requests.request(
                method=method, url=url, headers=request_headers, cookies=cookies,
                params=params, data=data, json=json_data, timeout=timeout, allow_redirects=allow_redirects,
                verify=False
            )
            return response
        except Exception as e:
            logger.error(f"HTTP请求错误 [{method}] {url}: {str(e)}")
            return None

    @staticmethod
    def get(url, **kwargs):
        return HttpClient.request("GET", url, **kwargs)

    @staticmethod
    def post(url, **kwargs):
        return HttpClient.request("POST", url, **kwargs)


class LibraryAuthenticator:
    def __init__(self, username, edu_password, lib_password):
        self.username = username
        self.password1 = edu_password
        self.password2 = lib_password
        self.my_client_ticket = None
        self.token = None
        self.acc_no = None
        self.last_auth_time = None

    def get_initial_client_ticket(self):
        url = "https://webvpn.njfu.edu.cn/rump_frontend/login/"
        response = HttpClient.get(url)
        if response and response.status_code == 200:
            return response.cookies.get_dict().get("my_client_ticket")
        return None

    def check_need_captcha(self, username, salt, client_ticket):
        url = HttpClient.get_edu_url("authserver/needCaptcha.html")
        params = {
            "vpn-12-uia.njfu.edu.cn": "",
            "username": username,
            "pwdEncrypt2": salt,
            "_": str(int(time.time() * 1000))
        }
        headers = {"X-Requested-With": "XMLHttpRequest"}
        cookies = {"my_client_ticket": client_ticket}

        try:
            resp = HttpClient.get(url, headers=headers, params=params, cookies=cookies)
            if resp and resp.status_code == 200:
                return resp.text.strip().lower() == "true"
            return True
        except Exception:
            return True

    def first_level_auth(self):
        my_client_ticket = self.get_initial_client_ticket()
        if not my_client_ticket:
            return None, False

        login_prepare_url = HttpClient.get_edu_url(
            "authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
        )
        cookies = {"my_client_ticket": my_client_ticket}
        response = HttpClient.get(login_prepare_url, cookies=cookies)

        if not response or response.status_code != 200:
            return None, False

        soup = BeautifulSoup(response.text, "html.parser")
        lt = soup.find("input", {"name": "lt"})["value"] if soup.find("input", {"name": "lt"}) else None
        salt = soup.find("input", {"id": "pwdDefaultEncryptSalt"})["value"] if soup.find("input", {
            "id": "pwdDefaultEncryptSalt"}) else None
        dllt = soup.find("input", {"name": "dllt"})["value"] if soup.find("input", {"name": "dllt"}) else None
        execution = soup.find("input", {"name": "execution"})["value"] if soup.find("input",
                                                                                    {"name": "execution"}) else None
        event_id = soup.find("input", {"name": "_eventId"})["value"] if soup.find("input",
                                                                                  {"name": "_eventId"}) else None
        rm_shown = soup.find("input", {"name": "rmShown"})["value"] if soup.find("input", {"name": "rmShown"}) else None

        if not all([lt, salt, dllt, execution, event_id, rm_shown]):
            return None, False

        encrypted_password = encrypt_cas_password(self.password1, salt)
        login_url = HttpClient.get_edu_url(
            "authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
        )
        login_data = {
            "vpn-0": "",
            "service": "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/",
            "username": self.username,
            "password": encrypted_password,
            "lt": lt,
            "dllt": dllt,
            "execution": execution,
            "_eventId": event_id,
            "rmShown": rm_shown
        }

        login_response = HttpClient.post(
            login_url,
            cookies=cookies,
            data=login_data,
            allow_redirects=False
        )

        if not login_response or login_response.status_code != 302:
            if login_response and login_response.status_code == 200:
                need_captcha = self.check_need_captcha(self.username, salt, my_client_ticket)
                return None, need_captcha
            else:
                return None, False

        location = login_response.headers.get("Location")
        if not location:
            return None, False

        ticket_match = re.search(r'ticket=([^&]+)', location)
        if not ticket_match:
            return None, False

        ticket = ticket_match.group(1)
        final_auth_url = f"https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/?ticket={ticket}"
        final_response = HttpClient.get(final_auth_url, cookies=cookies)

        if not final_response or final_response.status_code != 200:
            return None, False

        final_cookies = final_response.cookies.get_dict()
        final_my_client_ticket = final_cookies.get("my_client_ticket", my_client_ticket)

        self.my_client_ticket = final_my_client_ticket
        return final_my_client_ticket, False

    def get_public_key(self):
        public_key_url = HttpClient.get_lib_url("ic-web/login/publicKey?vpn-12-libseat.njfu.edu.cn")
        api_headers = {"accept": "application/json, text/plain, */*"}
        try:
            response = HttpClient.get(
                public_key_url,
                headers=api_headers,
                cookies={"my_client_ticket": self.my_client_ticket}
            )

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
                    logger.warning(f"响应内容: {response.text[:200]}")
        except Exception as e:
            logger.error(f"获取公钥过程中发生异常: {str(e)}")
            
        return None, None

    def second_level_auth(self, max_attempts=5):
        if not self.my_client_ticket:
            return None, None

        login_url = HttpClient.get_lib_url("ic-web/login/user?vpn-12-libseat.njfu.edu.cn")
        api_headers = {
            "accept": "application/json, text/plain, */*",
            "content-type": "application/json;charset=UTF-8",
        }

        for attempt in range(1, max_attempts + 1):
            logger.info(f"正在进行第二级认证: 尝试第 {attempt} 次")
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

                response = HttpClient.post(
                    login_url,
                    headers=api_headers,
                    cookies={"my_client_ticket": self.my_client_ticket},
                    json_data=payload,
                    timeout=15
                )

                if response and response.status_code == 200:
                    try:
                        result = response.json()
                        if result.get("code") == 0:
                            user_data = result.get("data", {})
                            self.token = user_data.get("token")
                            self.acc_no = user_data.get("accNo")
                            self.last_auth_time = time.time()
                            return self.token, self.acc_no
                    except:
                        pass
                logger.warning(f"第 {attempt} 次认证尝试未获成功，等待重试...")
                time.sleep(2)
            except Exception as e:
                logger.error(f"第 {attempt} 次认证发生异常: {str(e)}")
                time.sleep(2)
        return None, None

    def authenticate(self):
        try:
            my_client_ticket, need_captcha = self.first_level_auth()

            if need_captcha:
                return False, True, "需要验证码,请在统一认证中心登录一次"

            if not my_client_ticket:
                return False, False, "统一认证密码错误，请重新输入"

            # 增加缓冲时间，等待 WebVPN 同步 Session
            logger.info("统一认证成功，等待 2 秒以同步 WebVPN 会话...")
            time.sleep(2)

            token, acc_no = self.second_level_auth()
            if not token or not acc_no:
                return False, False, "图书馆密码错误，请重新输入"

            return True, False, None
        except Exception as e:
            return False, False, f"认证过程中发生错误: {str(e)}"

    def is_valid(self):
        if not self.my_client_ticket or not self.token or not self.acc_no:
            return False

        try:
            from utils.date_utils import get_today_date
            today = get_today_date()
            url = HttpClient.get_lib_url("ic-web/reserve/resvInfo")

            params = {
                "vpn-12-libseat.njfu.edu.cn": "",
                "needStatus": "8454",
                "unneedStatus": "128",
                "beginDate": today,
                "endDate": today
            }

            api_headers = {
                "accept": "application/json, text/plain, */*",
                "token": self.token,
                "lan": "1",
            }

            response = HttpClient.get(
                url,
                headers=api_headers,
                params=params,
                cookies={"my_client_ticket": self.my_client_ticket},
                timeout=10
            )

            valid = response and response.status_code == 200 and response.json().get("code") == 0
            if not valid:
                logger.info(f"用户 {self.username} 的认证已失效")
            return valid
        except Exception as e:
            logger.error(f"验证认证有效性时出错: {str(e)}")
            return False

    def get_captcha_image(self):
        global captcha_session

        try:
            captcha_session = requests.Session()

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

    def get_route_cookie(self, session):
        if not session:
            return None

        try:
            route_cookie_url = "https://webvpn.njfu.edu.cn/webvpn/cookie/?domain=uia.njfu.edu.cn&path=%2Fauthserver%2Flogin"
            login_page_url = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc=/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1/authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
            headers = {'accept': '*/*', 'referer': login_page_url}

            response = session.get(route_cookie_url, headers=headers, timeout=10)
            match = re.search(r'route=([^;]+)', response.text)
            if match:
                return match.group(1)
            if 'route' in session.cookies:
                return session.cookies.get('route')
            return None
        except Exception:
            return None

    def authenticate_with_captcha(self, captcha):
        global captcha_session

        if not captcha_session:
            return False, "验证码会话无效，请刷新页面重试"

        session = captcha_session

        try:
            login_url = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc=/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1/authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
            login_page = session.get(login_url, timeout=10)

            soup = BeautifulSoup(login_page.text, "html.parser")

            form_elements = {
                "lt": soup.find("input", {"name": "lt"}),
                "salt": soup.find("input", {"id": "pwdDefaultEncryptSalt"}),
                "dllt": soup.find("input", {"name": "dllt"}),
                "execution": soup.find("input", {"name": "execution"}),
                "event_id": soup.find("input", {"name": "_eventId"}),
                "rm_shown": soup.find("input", {"name": "rmShown"})
            }

            if not all(form_elements.values()):
                return False, "无法获取登录表单信息，请刷新页面重试"

            form_data = {key: element["value"] for key, element in form_elements.items()}

            try:
                route = self.get_route_cookie(session)
                if route:
                    session.cookies.set('route', route, domain='webvpn.njfu.edu.cn', path='/')
            except Exception:
                pass

            encrypted_password = encrypt_cas_password(self.password1, form_data["salt"])

            login_post_url = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc=/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1/authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"

            login_data = {
                "username": self.username,
                "password": encrypted_password,
                "captchaResponse": captcha,
                "lt": form_data["lt"],
                "dllt": form_data["dllt"],
                "execution": form_data["execution"],
                "_eventId": form_data["event_id"],
                "rmShown": form_data["rm_shown"],
                "vpn-0": "",
                "service": "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/"
            }

            login_response = session.post(
                login_post_url,
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

                    if "my_client_ticket" in session.cookies:
                        self.my_client_ticket = session.cookies.get("my_client_ticket")

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
                    error_text = error_msg.text.strip()
                    if "验证码" in error_text:
                        return False, "验证码错误或已过期，请刷新页面后重新输入"
                    elif "用户名或密码" in error_text:
                        return False, "用户名或密码错误"
                    else:
                        return False, f"认证失败: {error_text}"
                else:
                    return False, "认证失败，但未找到具体错误原因"
            else:
                return False, f"网络错误，状态码: {login_response.status_code}"
        except Exception as e:
            logger.error(f"认证过程发生错误: {str(e)}")
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
