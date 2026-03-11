"""
超轻量预约服务器配置
"""
import os
import pytz


class Config:
    # --- 时区 ---
    TIMEZONE = pytz.timezone("Asia/Shanghai")

    # --- 定时任务默认执行时间 ---
    DEFAULT_AUTH_TIME = "06:55"       # 每日认证时间
    DEFAULT_RESERVE_TIME = "07:03"    # 每日预约时间

    # --- 密码加密密钥 (用于 JSON 文件中存储的密码) ---
    ENCRYPT_KEY = os.environ.get("ENCRYPT_KEY", "njfu-lib-server-api-key-2026")

    # --- 代理 ---
    USE_WARP_PROXY = os.environ.get("USE_WARP_PROXY", "false").lower() == "true"
    WARP_PROXY_HOST = os.environ.get("WARP_PROXY_HOST", "127.0.0.1")
    WARP_PROXY_PORT = int(os.environ.get("WARP_PROXY_PORT", "40000"))

    @staticmethod
    def get_proxy_url():
        if Config.USE_WARP_PROXY:
            return f"socks5h://{Config.WARP_PROXY_HOST}:{Config.WARP_PROXY_PORT}"
        return None

    # --- 座位区域映射 ---
    SEAT_AREAS = {
        "二层A区": {"first_seat_id": 100455361, "seats_count": 441},
        "二层B区": {"first_seat_id": 100455802, "seats_count": 96},
        "三层A区": {"first_seat_id": 100456256, "seats_count": 404},
        "三楼B区": {"first_seat_id": 100456660, "seats_count": 132},
        "三楼C区": {"first_seat_id": 100499567, "seats_count": 162},
        "三楼夹层": {"first_seat_id": 111488493, "seats_count": 20},
        "四楼A区": {"first_seat_id": 100499729, "seats_count": 428},
        "四楼夹层": {"first_seat_id": 111488513, "seats_count": 24},
        "五楼A区": {"first_seat_id": 100500173, "seats_count": 360},
        "六楼A区": {"first_seat_id": 100500602, "seats_count": 344},
        "七楼北侧": {"first_seat_id": 106744855, "seats_count": 224},
        "七楼南侧": {"first_seat_id": 111488640, "seats_count": 114},
    }

    @staticmethod
    def get_seat_id(area: str, seat_number: int):
        if area in Config.SEAT_AREAS and 1 <= seat_number <= Config.SEAT_AREAS[area]["seats_count"]:
            return Config.SEAT_AREAS[area]["first_seat_id"] + seat_number - 1
        return None

    # --- 服务器端口 ---
    HOST = os.environ.get("HOST", "0.0.0.0")
    PORT = int(os.environ.get("PORT", "8000"))

    # --- 任务文件目录 ---
    TASKS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tasks")

    # --- API Key 鉴权 ---
    @staticmethod
    def get_api_key():
        """从环境变量或 .api_key 文件读取 API Key。"""
        key = os.environ.get("API_KEY")
        if key:
            return key
        key_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".api_key")
        if os.path.exists(key_file):
            with open(key_file, "r") as f:
                return f.read().strip()
        return None
