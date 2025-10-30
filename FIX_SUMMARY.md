# Fix Summary: Duplicate Notifications, Auth Retries, and Timeouts

## Problem Statement
The system was experiencing:
1. **Duplicate job execution**: Each scheduled job running 3 times simultaneously
2. **5 duplicate notifications** for a single reservation (1 success + 4 failures)
3. **Unnecessary re-authentication retries** even for business logic errors
4. **HTTP timeout errors** causing false authentication failures

## Root Causes Identified

### 1. Multi-worker Scheduler Issue
- When deployed with multiple workers (e.g., gunicorn with 3 workers), each worker initialized its own scheduler
- This caused jobs to run N times (N = number of workers)

### 2. Naive Retry Logic
- System always retried with re-authentication on ANY failure
- Didn't distinguish between authentication errors vs business errors like "already has reservation"

### 3. Missing Notification Control
- Every reservation attempt (including retries) sent a notification
- No mechanism to suppress intermediate notifications

### 4. Aggressive Timeouts
- 3-second timeout for auth validity checks
- 10-second timeout for second-level auth
- Too short for peak-time network conditions

## Solutions Implemented

### 1. Prevent Duplicate Job Execution
**File:** `backend/scheduler.py`
```python
scheduler.add_job(
    func=reserve_for_users,
    # ... other params ...
    coalesce=True,        # ← NEW: Merge multiple pending executions
    max_instances=1       # ← NEW: Only one instance at a time
)
```
Applied to all 6 scheduled jobs:
- auth_all_users
- reserve_for_admins
- reserve_for_normal_users
- check_late_protection_morning
- collect_traffic_data
- cleanup_traffic_data

### 2. Smart Retry Logic
**File:** `backend/scheduler.py` - `reserve_for_user()`
```python
# Check error message before retrying
no_retry_keywords = [
    "已有预约", "有预约", "已预约", "already",
    "正在被预约", "设备正在被预约",
    "时长不足", "时间段",
    "座位号无效", "配置无效"
]
if not any(keyword in message_lower for keyword in no_retry_keywords):
    should_retry = True
    logger.warning(f"用户 {user.username} 预约失败，尝试重新认证: {message}")
else:
    logger.info(f"用户 {user.username} 预约失败（业务逻辑错误，不重试）: {message}")
```

### 3. Notification Control
**Files:** `backend/utils/reservation.py`, `backend/scheduler.py`

Added `send_notification` parameter (default=True) to:
- `reserve_seat()`
- `_do_reserve()`
- `_record_reservation_history()`
- `record_auth_failure()`

For scheduled auto-reservations:
- First attempt: `send_notification=False` (might retry)
- Final result: `send_notification=True` (send notification)
- Manual reservations: Always `send_notification=True`

### 4. Increased Timeouts
**File:** `backend/utils/auth_manager.py`

Changed timeouts:
- `is_valid()`: 3s → **10s**
- `second_level_auth()`: 10s → **15s**

## Expected Results

### Before Fix
```
07:00:00 - Running job "Reserve seats..." (instance 1)
07:00:00 - Running job "Reserve seats..." (instance 2)
07:00:00 - Running job "Reserve seats..." (instance 3)
07:00:01 - Notification: ❌ Failed (race condition)
07:00:02 - Notification: ✅ Success
07:00:03 - Re-auth + retry → Notification: ❌ Already has reservation
07:00:04 - Re-auth + retry → Notification: ❌ Already has reservation
07:00:05 - Re-auth + retry → Notification: ❌ Already has reservation
```
**Result: 5 notifications** (1 success, 4 failures)

### After Fix
```
07:00:00 - Running job "Reserve seats..." (single instance)
07:00:02 - Notification: ✅ Success
```
**Result: 1 notification** (final result only)

Or if first attempt fails due to race condition:
```
07:00:00 - Running job "Reserve seats..." (single instance)
07:00:01 - First attempt failed: "设备正在被预约" (no notification)
07:00:01 - No retry (business logic error)
07:00:01 - Notification: ❌ Failed (race condition)
```
**Result: 1 notification** (no retry for race condition)

## Testing Checklist

- [ ] Verify only 1 "Running job" log per scheduled time
- [ ] Verify only 1 notification per user per reservation attempt
- [ ] Verify "已有预约" errors don't trigger re-authentication
- [ ] Verify HTTP timeout errors are reduced
- [ ] Test with multiple gunicorn workers
- [ ] Monitor for 24 hours to ensure stability

## Files Modified

1. `backend/scheduler.py` - Job configuration and retry logic
2. `backend/utils/reservation.py` - Notification control
3. `backend/utils/auth_manager.py` - Timeout increases

## Rollback Plan

If issues occur, revert these commits. The changes are isolated and non-breaking for existing functionality.
