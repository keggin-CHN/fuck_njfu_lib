from functools import wraps
from flask import request
from flask_login import current_user
from models import db, Log

def log_action(action):
    """
    装饰器：记录用户操作到数据库日志。
    """
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            # 执行视图函数
            response = f(*args, **kwargs)

            # 准备日志信息
            try:
                log = Log(
                    user_id=current_user.id if current_user.is_authenticated else None,
                    ip_address=request.remote_addr,
                    action=action.format(**kwargs),  # 格式化操作字符串
                    user_agent=request.user_agent.string,
                    response_code=response.status_code if hasattr(response, 'status_code') else 200
                )
                
                # 记录响应内容或错误信息
                if hasattr(response, 'get_data'):
                    data = response.get_data(as_text=True)
                    if len(data) < 2000:  # 避免记录过大的响应体
                        log.response_content = data
                
                db.session.add(log)
                db.session.commit()
            except Exception as e:
                db.session.rollback()
                # 在主日志文件中记录日志模块本身的错误
                import logging
                logger = logging.getLogger(__name__)
                logger.error(f"Failed to log action '{action}': {e}")

            return response
        return decorated_function
    return decorator

def add_log(action, user=None, response_code=200, response_content=None, error_message=None):
    """
    手动添加日志记录的辅助函数。
    """
    try:
        log = Log(
            user_id=user.id if user else (current_user.id if current_user.is_authenticated else None),
            ip_address=request.remote_addr if request else 'N/A',
            action=action,
            user_agent=request.user_agent.string if request else 'N/A',
            response_code=response_code,
            response_content=response_content,
            error_message=error_message
        )
        db.session.add(log)
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        import logging
        logger = logging.getLogger(__name__)
        logger.error(f"Failed to manually add log for action '{action}': {e}")