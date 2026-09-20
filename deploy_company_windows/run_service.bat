@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist "%~dp0logs" mkdir "%~dp0logs"

set DIRECT_CAMPUS_MODE=true
set USE_WARP_PROXY=false
set PORT=3001
set HOST=127.0.0.1
set API_KEY=Gcbja4L9Jq5h87Npx6WkR23v0ZmYtPd1S
set PYTHONUNBUFFERED=1
set "PYTHONPATH=%~dp0server_api;%~dp0backend;%~dp0"

echo [%date% %time%] 启动直连 API 服务 (端口: 3001)... >> "%~dp0logs\server.log"
"%~dp0venv\Scripts\python.exe" -m uvicorn server_api.main:app --host 127.0.0.1 --port 3001 >> "%~dp0logs\server.log" 2>&1
exit /b 0
