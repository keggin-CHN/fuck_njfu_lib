@echo off
chcp 936 >nul
cd /d "%~dp0"
title NJFU 直连服务实时日志追踪

echo ===============================================================
echo   正在显示服务与隧道实时日志 (按 Ctrl+C 可退出查看，不影响后台)
echo ===============================================================
echo.

if not exist "%~dp0logs\server.log" (
    echo [提示] 尚未产生日志文件，请先运行 [一键后台启动.bat]。
    pause
    exit /b
)

echo --- 最近的隧道状态 (tunnel.log) ---
powershell -Command "if (Test-Path 'logs\tunnel.log') { Get-Content -Path 'logs\tunnel.log' -Tail 10 } else { Write-Host '暂无隧道日志' }"
echo.
echo --- 实时监控业务日志 (server.log) ---
powershell -Command "Get-Content -Path 'logs\server.log' -Wait -Tail 30"
