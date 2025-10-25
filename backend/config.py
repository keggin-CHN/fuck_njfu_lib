import os
import pytz


# 基本配置
class Config:
    SECRET_KEY = os.environ.get('SECRET_KEY') or 'secret-key-for-library-reservation'
    SQLALCHEMY_DATABASE_URI = os.environ.get('DATABASE_URL') or 'sqlite:///database.db'
    SQLALCHEMY_TRACK_MODIFICATIONS = False

    # 定时任务配置
    AUTH_TIME = "06:55:00"  # 每天认证时间
    ADMIN_RESERVE_TIME = "07:00:20"  # 管理员预约时间
    USER_RESERVE_TIME = "07:03:20"  # 普通用户预约时间

    # 时区设置
    TIMEZONE = pytz.timezone('Asia/Shanghai')  # 东八区

    # 座位区域配置
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
    def get_seat_id(area, seat_number):
        """根据区域和座位号计算实际的座位ID"""
        if area in Config.SEAT_AREAS and 1 <= seat_number <= Config.SEAT_AREAS[area]["seats_count"]:
            return Config.SEAT_AREAS[area]["first_seat_id"] + seat_number - 1
        return None
