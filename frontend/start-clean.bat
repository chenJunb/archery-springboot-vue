@echo off
echo ========================================
echo     前端开发服务器清理和启动脚本
echo ========================================
echo.

echo [1/5] 停止所有Node.js相关进程...
taskkill /F /IM node.exe 2>nul
taskkill /F /IM npm.exe 2>nul
echo ✓ 进程清理完成
echo.

echo [2/5] 等待进程完全退出...
timeout /t 2 /nobreak >nul
echo.

echo [3/5] 检查端口占用情况...
echo 检查端口 3000-3010...
for /l %%i in (3000,1,3010) do (
  netstat -ano | findstr ":%%i" >nul
  if not errorlevel 1 (
    echo ! 端口 %%i 被占用，请手动解决
  ) else (
    echo ✓ 端口 %%i 可用
  )
)
echo.

echo [4/5] 清理node_modules缓存...
if exist node_modules\.vite rmdir /s /q node_modules\.vite 2>nul
echo ✓ 缓存清理完成
echo.

echo [5/5] 启动Vite开发服务器...
echo 将在端口3000启动，如果端口被占用会报错
echo 按任意键继续...
pause >nul

call npm run dev
if errorlevel 1 (
  echo.
  echo ❌ Vite启动失败！
  echo 可能原因：
  echo 1. 端口3000被其他程序占用
  echo 2. node_modules有问题
  echo 3. 依赖未安装
  echo.
  echo 解决方案：
  echo 1. 检查端口占用: netstat -ano | findstr :3000
  echo 2. 重新安装依赖: npm install
  echo 3. 使用其他端口: 修改vite.config.js中的port设置
)
pause