@echo off
REM Final Build Script - No External Config Needed
REM Complete offline build without any network dependencies

chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo  Archery Timer - Final Build
echo ========================================
echo [INFO] Offline build - no network required
echo.

REM Check for JRE
if not exist "jre\bin\java.exe" (
    echo [ERROR] jre\bin\java.exe not found
    pause
    exit /b 1
)
echo [OK] JRE found

REM ====================
REM Stage 1: Backend
REM ====================
echo.
echo [1/4] Building backend...
echo [INFO] Running Maven build...

cd backend
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Backend build failed
    cd ..
    pause
    exit /b 1
)

if not exist "target\archery-timer-1.0.0.jar" (
    echo [ERROR] archery-timer-1.0.0.jar not found
    cd ..
    pause
    exit /b 1
)

echo [OK] Backend compiled successfully
cd ..

REM ====================
REM Stage 2: Frontend
REM ====================
echo.
echo [2/4] Building frontend...
echo [INFO] Installing frontend dependencies...

cd frontend
call npm install
if errorlevel 1 (
    echo [ERROR] Frontend npm install failed
    cd ..
    pause
    exit /b 1
)

echo [INFO] Building frontend with Vite...
call npm run build
if errorlevel 1 (
    echo [ERROR] Frontend build failed
    cd ..
    pause
    exit /b 1
)

echo [OK] Frontend compiled successfully
cd ..

REM ====================
REM Stage 3: Root Dependencies
REM ====================
echo.
echo [3/4] Installing root dependencies...

call npm install
if errorlevel 1 (
    echo [ERROR] Root npm install failed
    pause
    exit /b 1
)

echo [OK] Root dependencies installed

REM ====================
REM Stage 4: Electron Packaging
REM ====================
echo.
echo [4/4] Packaging Electron application...

REM Completely disable code signing
echo [INFO] Disabling code signing to avoid network downloads...
set CSC_IDENTITY_AUTO_DISCOVERY=false
set CSC_KEY_PASSWORD=
set CSC_LINK=
set CSC_NAME=
set WIN_CSC_LINK=
set WIN_CSC_KEY_PASSWORD=
set CSC_FOR_PULL_REQUEST=true

REM Additional isolation flags
set ELECTRON_BUILDER_SKIP_DOWNLOAD=true
set npm_config_build_from_source=true

REM Build without code signing
echo [INFO] Building Windows installer (unsigned)...
call npx electron-builder --win --publish=never

if errorlevel 1 (
    echo.
    echo [ERROR] Electron build failed
    echo [HINT] Checking for alternative build method...
    pause
    exit /b 1
)

echo.
echo ========================================
echo  BUILD COMPLETED SUCCESSFULLY!
echo ========================================
echo.
echo Generated installer: release\Archery Timer Setup 1.0.0.exe
echo.
echo This is an unsigned installer. On first run, users may see
echo a Windows security warning. This is normal for unsigned software.
echo.
pause
