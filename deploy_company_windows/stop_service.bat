@echo off
chcp 936 >nul
cd /d "%~dp0"
title 停止 NJFU 直连后台服务

echo ===============================================================
echo   正在停止 NJFU 直连预约服务及穿透隧道...
echo ===============================================================
echo.

:: 1. 写入停止标志，通知循环脚本立即退出
type nul > "%~dp0.stop"

:: 2. 终止 3001 端口的后端服务
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3001" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a >nul 2>&1
)

:: 3. 强行终止 FRP 穿透客户端
taskkill /f /im frpc.exe >nul 2>&1

:: 4. 强行终止后台静默 VBS 引擎
taskkill /f /im wscript.exe >nul 2>&1

if exist "%~dp0logs" (
    echo [%date% %time%] 用户手动停止服务 >> "%~dp0logs\server.log" 2>&1
    echo [%date% %time%] 用户手动停止隧道 >> "%~dp0logs\tunnel.log" 2>&1
)

echo.
echo ===============================================================
echo   [OK] 后台服务及穿透隧道已全部停止，文件夹占用已解除！
echo ===============================================================
echo.
ping 127.0.0.1 -n 3 >nul
exit /b 0
