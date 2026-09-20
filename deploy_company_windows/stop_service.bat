@echo off
cd /d "%~dp0"
title Stop NJFU Direct Service

echo ===============================================================
echo   Stopping NJFU Direct Service and Tunnel...
echo ===============================================================
echo.

:: 1. Signal stop
type nul > "%~dp0.stop" 2>nul

:: 2. Kill 3001 port process if listening
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3001" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a >nul 2>&1
)

:: 3. Kill all python, frpc, wscript processes cleanly
taskkill /f /im python.exe >nul 2>&1
taskkill /f /im frpc.exe >nul 2>&1
taskkill /f /im wscript.exe >nul 2>&1

echo.
echo ===============================================================
echo   [OK] All services and tunnels have been stopped!
echo ===============================================================
echo.
ping 127.0.0.1 -n 2 >nul
exit /b 0
