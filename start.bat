@echo off
echo 射箭比赛计时控制系统启动脚本
echo.

echo 1. 启动后端服务...
echo    端口: 8080
echo.
echo 2. 启动前端服务...
echo    端口: 3000
echo.
echo 访问地址:
echo    - 控制端: http://localhost:3000/
echo    - 显示端A: http://localhost:3000/display-a
echo    - 显示端B: http://localhost:3000/display-b
echo.
echo 请按任意键开始启动...
pause >nul

echo 启动后端服务...
start cmd /k "cd backend && mvn spring-boot:run"

echo 启动前端服务...
start cmd /k "cd frontend && npm run dev"

echo 系统启动完成！
echo 请等待服务启动完成...