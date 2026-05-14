@echo off
chcp 65001 >/dev/null

echo ========================================
echo  Archery Timer - Build Script
echo ========================================

if not exist "jrein\java.exe" (
    echo [ERROR] jrein\java.exe not found
    echo Please extract JDK 11 zip to jre\ folder
    pause
    exit /b 1
)
echo [OK] JRE found

echo.
echo [1/3] Building backend...

:: 1. 确保没有Java进程占用文件
echo [INFO] Killing Java processes...
taskkill /F /IM java.exe /T 2>/dev/null
:: 使用ping代替timeout进行延时
ping -n 2 127.0.0.1 >/dev/null

:: 2. 清理target目录
if exist "backend	arget" (
    echo [INFO] Cleaning backend	arget...
    del /F /Q "backend	arget\*.jar" 2>/dev/null
    del /F /Q "backend	arget\*.class" 2>/dev/null
)

:: 3. 进入backend目录执行Maven构建
cd backend
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Backend build failed
    cd ..
    pause
    exit /b 1
)
if not exist "targetrchery-timer-backend-1.0.0.jar" (
    echo [ERROR] archery-timer-backend-1.0.0.jar not found
    cd ..
    pause
    exit /b 1
)
echo [OK] Backend built: backend	argetrchery-timer-backend-1.0.0.jar
cd ..

echo.
echo [2/3] Building frontend...
cd frontend
call npm install --silent
if errorlevel 1 ( echo [ERROR] npm install failed & cd .. & pause & exit /b 1 )
call npm run build
if errorlevel 1 ( echo [ERROR] Frontend build failed & cd .. & pause & exit /b 1 )
echo [OK] Frontend built: frontend\distcd ..

echo.
echo [3/3] Packaging Electron...
call npm install --silent
if errorlevel 1 ( echo [ERROR] npm install failed & pause & exit /b 1 )
call npx electron-builder --win
if errorlevel 1 ( echo [ERROR] Electron packaging failed & pause & exit /b 1 )

echo.
echo ========================================
echo  Done! Installer is in release\ folder
echo ========================================
pause
