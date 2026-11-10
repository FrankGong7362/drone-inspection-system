@echo off
chcp 65001 >nul
title 无人机巡检系统 - 一键启动

REM ============================================================
REM 无人机巡检系统 - 启动服务
REM 弹出三个独立窗口：算法(8000) / 后端(8080) / 前端(5173)
REM ============================================================

set "PROJECT=%~dp0.."

echo ============================================================
echo  启动无人机巡检系统
echo ============================================================
echo.

REM ---------------- 算法服务 ----------------
echo [1/3] 启动算法服务 FastAPI (127.0.0.1:8000) ...
start "算法服务 FastAPI" cmd /k "cd /d "%PROJECT%\algorithm" && python -m uvicorn app.main:app --host 127.0.0.1 --port 8000"

REM ---------------- 后端 ----------------
echo [2/3] 启动后端 Spring Boot (127.0.0.1:8080) ...
REM 优先使用 JDK 17（Spring Boot 3 要求），找不到则用全局 java
set "JAVA17=C:\Program Files\Java\jdk-17\bin\java.exe"
if exist "%JAVA17%" (
    start "后端 Spring Boot" cmd /k "cd /d "%PROJECT%\backend" && "%JAVA17%" -jar target\uav-backend-0.0.1-SNAPSHOT.jar"
) else (
    start "后端 Spring Boot" cmd /k "cd /d "%PROJECT%\backend" && java -jar target\uav-backend-0.0.1-SNAPSHOT.jar"
)

REM ---------------- 前端 ----------------
echo [3/3] 启动前端 Vite (localhost:5173) ...
start "前端 Vite" cmd /k "cd /d "%PROJECT%\frontend" && npm run dev"

echo.
echo ============================================================
echo  三个窗口已分别启动，稍等 10-20 秒即可访问：
echo    前端:   http://localhost:5173
echo    后端:   http://127.0.0.1:8080
echo    算法:   http://127.0.0.1:8000/health
echo.
echo  默认管理员账号: admin / 123456
echo ============================================================
echo.
echo 关闭本窗口不会停止服务，需手动关闭各子窗口或运行 04-停止服务.bat
pause
