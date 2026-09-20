@echo off
cd /d "%~dp0"
title Stop NJFU Direct Service

echo ===============================================================
echo   Stopping NJFU Direct Service and Tunnel (Deep Clean)...
echo ===============================================================
echo.

:: 1. Signal stop to loops
type nul > "%~dp0.stop" 2>nul

:: 2. Deep clean: Kill any process (hidden cmd, python, frpc, wscript) tied to this directory
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process | Where-Object { ($_.CommandLine -like '*deploy_company_windows*' -or $_.CommandLine -like '*run_service*' -or $_.CommandLine -like '*run_tunnel*' -or $_.Name -match 'frpc|uvicorn') -and $_.ProcessId -ne $PID } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" 2>nul

:: 3. Port 3001 cleanup just in case
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3001" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a >nul 2>&1
)

:: 4. Direct image cleanup
taskkill /f /im python.exe >nul 2>&1
taskkill /f /im frpc.exe >nul 2>&1
taskkill /f /im wscript.exe >nul 2>&1

echo.
echo ===============================================================
echo   [OK] All processes killed and directory lock released!
echo ===============================================================
echo.
ping 127.0.0.1 -n 2 >nul
exit /b 0
