@echo off
chcp 65001 >nul

echo ========================================
echo  Archery Timer - Build Script
echo ========================================

if not exist "jre\bin\java.exe" (
    echo [ERROR] jre\bin\java.exe not found
    echo Please extract JDK 11 zip to jre\ folder
    pause
    exit /b 1
)
echo [OK] JRE found

echo.
echo [1/3] Building backend...

:: 1. 确保没有Java进程占用文件
echo [INFO] Killing Java processes...
taskkill /F /IM java.exe /T 2>nul
timeout /t 1 /nobreak >nul

:: 2. 清理target目录
if exist "backend\target" (
    echo [INFO] Cleaning backend\target...
    del /F /Q "backend\target\*.jar" 2>nul
    del /F /Q "backend\target\*.class" 2>nul
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
if not exist "target\archery-timer.jar" (
    echo [ERROR] archery-timer.jar not found
    cd ..
    pause
    exit /b 1
)
echo [OK] Backend built: backend\target\archery-timer.jar
cd ..

echo.
echo [2/3] Building frontend...
cd frontend
call npm install --silent
if errorlevel 1 ( echo [ERROR] npm install failed & cd .. & pause & exit /b 1 )
call npm run build
if errorlevel 1 ( echo [ERROR] Frontend build failed & cd .. & pause & exit /b 1 )
echo [OK] Frontend built: frontend\dist\
cd ..

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
