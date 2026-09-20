@echo off
cd /d "%~dp0"
if not exist "%~dp0logs" mkdir "%~dp0logs"

:tunnel_loop
if exist "%~dp0.stop" exit /b 0
echo [%date% %time%] Starting FRP tunnel client... >> "%~dp0logs\tunnel.log"
"%~dp0frpc.exe" -c "%~dp0frpc.toml" >> "%~dp0logs\tunnel.log" 2>&1
if exist "%~dp0.stop" exit /b 0
echo [%date% %time%] FRP disconnected, retrying in 5s... >> "%~dp0logs\tunnel.log"
ping 127.0.0.1 -n 6 >nul
if exist "%~dp0.stop" exit /b 0
goto tunnel_loop
