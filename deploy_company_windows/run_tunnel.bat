@echo off
chcp 936 >nul
cd /d "%~dp0"
if not exist "%~dp0logs" mkdir "%~dp0logs"

:tunnel_loop
if exist "%~dp0.stop" exit /b 0
echo [%date% %time%] 启动 FRP 穿透客户端... >> "%~dp0logs\tunnel.log"
"%~dp0frpc.exe" -c "%~dp0frpc.toml" >> "%~dp0logs\tunnel.log" 2>&1
if exist "%~dp0.stop" exit /b 0
echo [%date% %time%] FRP 客户端已断开，5秒后重试... >> "%~dp0logs\tunnel.log"
ping 127.0.0.1 -n 6 >nul
if exist "%~dp0.stop" exit /b 0
goto tunnel_loop
