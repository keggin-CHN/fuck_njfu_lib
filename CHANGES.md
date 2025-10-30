# Fix for Duplicate Notifications, Authentication Retries, and Timeouts

## Issues Fixed

### 1. Duplicate Job Execution
**Problem:** Each scheduled job (authentication, reservation, traffic collection) was running 3 times simultaneously when deployed with multiple workers (e.g., gunicorn with 3 workers).

**Root Cause:** Each worker process initialized its own scheduler instance, leading to multiple concurrent job executions.

**Solution:** Added `coalesce=True` and `max_instances=1` parameters to all scheduled jobs in `backend/scheduler.py`. This ensures only one instance of each job runs at a time, even across multiple worker processes.

### 2. Unnecessary Re-authentication Retries
**Problem:** After any reservation failure, the system would always retry with re-authentication, even when the failure was due to business logic errors like "user already has a reservation" or "seat is being reserved by someone else".

**Root Cause:** The retry logic in `reserve_for_user()` didn't distinguish between authentication errors and business logic errors.

**Solution:** 
- Implemented smart retry logic that checks the error message before retrying
- Only retries on potential authentication issues
- Skips retry for known business logic errors:
  - User already has a reservation
  - Seat is being reserved (race condition)
  - Duration too short
  - Invalid seat configuration

### 3. Duplicate Notifications
**Problem:** Users received multiple notifications for a single reservation attempt (up to 5 in the logs - 1 success and 4 failures).

**Root Cause:** 
- Multiple job instances running simultaneously (now fixed)
- Notifications sent on every retry attempt
- No deduplication logic

**Solution:**
- Added `send_notification` parameter to `reserve_seat()` and related methods
- In scheduled auto-reservations, notifications are suppressed on the first attempt
- Only one notification is sent for the final result (success or failure after retry)
- Manual reservations (from web UI) still send immediate notifications

### 4. HTTP Timeout Issues
**Problem:** Aggressive timeouts (3 seconds for GET, 10 seconds for POST) caused false "authentication failed" errors and unnecessary re-authentication attempts.

**Root Cause:** Network latency and server response times sometimes exceed the configured timeouts, especially during peak hours.

**Solution:**
- Increased authentication validity check timeout from 3 to 10 seconds
- Increased second-level authentication POST timeout from 10 to 15 seconds
- These more generous timeouts reduce false failures while still catching real issues

## Files Modified

1. `backend/scheduler.py`
   - Added `coalesce=True` and `max_instances=1` to all job definitions
   - Implemented smart retry logic with error message analysis
   - Added notification control for scheduled reservations
   - Updated `record_auth_failure()` to support notification control

2. `backend/utils/reservation.py`
   - Added `send_notification` parameter to `reserve_seat()`, `_do_reserve()`, and `_record_reservation_history()`
   - Updated `record_auth_failure()` to support notification control
   - Modified notification sending to be conditional

3. `backend/utils/auth_manager.py`
   - Increased `is_valid()` timeout from 3 to 10 seconds
   - Increased `second_level_auth()` timeout from 10 to 15 seconds

## Expected Behavior After Fix

1. **Single Job Execution:** Each scheduled job runs exactly once at the scheduled time, even with multiple workers
2. **Smart Retries:** Only retry when authentication might be the issue, not on business logic errors
3. **Single Notification:** Users receive exactly one notification per reservation attempt (the final result)
4. **Fewer Timeout Errors:** Increased timeouts reduce false authentication failures

## Testing Recommendations

1. Monitor logs during scheduled reservation times (7:00 AM for admins, 7:05 AM for users)
2. Verify only one "Running job" message appears per scheduled job
3. Verify users receive only one notification per reservation attempt
4. Check that "already has reservation" errors don't trigger re-authentication
5. Monitor HTTP timeout errors - should be significantly reduced
