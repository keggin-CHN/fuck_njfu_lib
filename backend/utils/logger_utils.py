from functools import wraps
from flask import request
from flask_login import current_user
from models import db, Log

def log_action(action):

    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):

            response = f(*args, **kwargs)

            try:
                log = Log(
                    user_id=current_user.id if current_user.is_authenticated else None,
                    ip_address=request.remote_addr,
                    action=action.format(**kwargs),
                    user_agent=request.user_agent.string,
                    response_code=response.status_code if hasattr(response, 'status_code') else 200
                )

                if hasattr(response, 'get_data'):
                    data = response.get_data(as_text=True)
                    if len(data) < 2000:
                        log.response_content = data

                db.session.add(log)
                db.session.commit()
            except Exception as e:
                db.session.rollback()

                import logging
                logger = logging.getLogger(__name__)
                logger.error(f"Failed to log action '{action}': {e}")

            return response
        return decorated_function
    return decorator

def add_log(action, user=None, response_code=200, response_content=None, error_message=None):

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