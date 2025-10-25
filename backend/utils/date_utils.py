import datetime

def get_today_date():
    """获取今天的日期字符串，格式为YYYY-MM-DD"""
    return datetime.datetime.now().strftime("%Y-%m-%d")

def get_tomorrow_date():
    """获取明天的日期字符串，格式为YYYY-MM-DD"""
    tomorrow = datetime.datetime.now() + datetime.timedelta(days=1)
    return tomorrow.strftime("%Y-%m-%d")

def normalize_time_format(time_str):
    """标准化时间格式为HH:MM:SS"""
    if time_str and ":" in time_str and time_str.count(":") == 1:
        return f"{time_str}:00"
    return time_str

def get_week_day(date_str=None):
    """获取指定日期是星期几，不指定则获取今天"""
    if date_str:
        date_obj = datetime.datetime.strptime(date_str, "%Y-%m-%d").date()
    else:
        date_obj = datetime.datetime.now().date()
    return date_obj.weekday()

def is_friday(date_str=None):
    """判断指定日期是否为星期五"""
    return get_week_day(date_str) == 4  # 4表示星期五

def get_end_time(date_str=None):
    """根据日期(星期五与否)获取结束时间"""
    if is_friday(date_str):
        return "20:00:00"  # 星期五结束时间
    else:
        return "22:00:00"  # 其他日子结束时间