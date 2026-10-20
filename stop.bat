@echo off
chcp 65001 >nul
title 无人机巡检系统 - 停止所有服务

echo 正在停止 5173 / 8080 / 8000 端口的服务进程 ...

for %%p in (5173 8080 8000) do (
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr "LISTENING" ^| findstr ":%%p "') do (
        echo   结束 PID %%a ^(端口 %%p^)
        taskkill /PID %%a /F >nul 2>&1
    )
)

echo.
echo 已尝试结束所有服务进程。
pause
