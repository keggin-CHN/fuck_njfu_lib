import datetime
def get_today_date():
    return datetime.datetime.now().strftime("%Y-%m-%d")
def get_tomorrow_date():
    tomorrow = datetime.datetime.now() + datetime.timedelta(days=1)
    return tomorrow.strftime("%Y-%m-%d")
def normalize_time_format(time_str):
    if time_str and ":" in time_str and time_str.count(":") == 1:
        return f"{time_str}:00"
    return time_str
def get_week_day(date_str=None):
    if date_str:
        date_obj = datetime.datetime.strptime(date_str, "%Y-%m-%d").date()
    else:
        date_obj = datetime.datetime.now().date()
    return date_obj.weekday()
def is_friday(date_str=None):
    return get_week_day(date_str) == 4
def get_end_time(date_str=None):
    if is_friday(date_str):
        return "20:00:00"
    else:
        return "22:00:00"