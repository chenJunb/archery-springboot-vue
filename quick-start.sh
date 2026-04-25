#!/bin/bash
# 📱 WebSocket 快速启动与验证指南

set -e

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║         WebSocket 连接修复 - 快速启动验证指南               ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查操作系统
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
    WINDOWS=true
    echo -e "${YELLOW}⚠️  Windows 环境检测到，请使用 WSL 或手动执行命令${NC}"
    WINDOWS_MODE=true
else
    WINDOWS=false
    WINDOWS_MODE=false
fi

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}第1步：启动后端服务${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

if [ "$WINDOWS_MODE" = true ]; then
    echo "Windows 用户请手动执行以下命令："
    echo ""
    echo "  cd backend"
    echo "  mvn clean compile"
    echo "  mvn spring-boot:run"
    echo ""
else
    echo "执行后端启动命令..."
    cd backend
    mvn clean compile > /dev/null 2>&1 &
    PID=$!
    echo -e "${GREEN}✅ 后端编译中 (PID: $PID)...${NC}"
    wait $PID
    echo -e "${GREEN}✅ 后端编译完成${NC}"
    mvn spring-boot:run &
    SERVER_PID=$!
    echo -e "${GREEN}✅ 后端启动中 (PID: $SERVER_PID)...${NC}"
    sleep 3
    cd ..
fi

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}第2步：启动前端服务${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

if [ "$WINDOWS_MODE" = true ]; then
    echo "Windows 用户请在新终端执行以下命令："
    echo ""
    echo "  cd frontend"
    echo "  npm run dev"
    echo ""
else
    echo "执行前端启动命令..."
    cd frontend
    npm run dev &
    FRONTEND_PID=$!
    echo -e "${GREEN}✅ 前端启动中 (PID: $FRONTEND_PID)...${NC}"
    sleep 5
    cd ..
fi

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}第3步：打开浏览器验证${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

echo "请在浏览器中打开以下三个链接："
echo ""
echo -e "${GREEN}  1. 控制端${NC}   → http://localhost:3000/"
echo -e "${GREEN}  2. A屏${NC}     → http://localhost:3000/display-a"
echo -e "${GREEN}  3. B屏${NC}     → http://localhost:3000/display-b"
echo ""

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}第4步：验证连接状态${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

echo "检查清单（每个页面都要检查）："
echo ""
echo "  ✅ 检查项1：右上角显示\"已连接\"（绿色）"
echo "  ✅ 检查项2：按 F12 打开 DevTools → Console"
echo "  ✅ 检查项3：搜索 \"✅ 客户端注册请求已发送（立即）\""
echo "  ✅ 检查项4：搜索 \"✅ 个人队列订阅已确认\""
echo ""

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}第5步：测试功能同步${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

echo "功能测试步骤："
echo ""
echo "  1️⃣  在控制端点击 \"开始\" 按钮"
echo "  2️⃣  观察 A屏和 B屏是否同时更新计时"
echo "  3️⃣  打开 DevTools → Network → WebSocket 标签"
echo "  4️⃣  观察消息是否实时发送"
echo ""

echo -e "${YELLOW}⏱️  预期连接时间：1-2 秒${NC}"
echo -e "${YELLOW}⏱️  预期消息延迟：<10ms${NC}"
echo ""

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}故障排查${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

echo "如果页面显示 \"离线\"："
echo ""
echo "  1. 检查后端是否运行"
echo "     curl http://localhost:8080/actuator/health"
echo ""
echo "  2. 检查前端是否运行"
echo "     curl http://localhost:3000/"
echo ""
echo "  3. 查看浏览器 Console 错误信息"
echo ""
echo "  4. 查看文档："
echo "     WEBSOCKET_CONNECTION_TROUBLESHOOTING.md"
echo ""

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✅ 所有步骤已完成！${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

echo "📝 推荐阅读："
echo "  - WEBSOCKET_FIX_FINAL_REPORT.md (最终报告)"
echo "  - WEBSOCKET_CONNECTION_TROUBLESHOOTING.md (详细排查)"
echo "  - WEBSOCKET_FIX_VERIFICATION.md (验证清单)"
echo ""

echo "🎯 修复状态："
echo "  ✅ WebSocket URL 已修复"
echo "  ✅ 客户端注册已优化"
echo "  ✅ 后端配置已简化"
echo ""

echo "按 Ctrl+C 停止服务"
echo ""

# 等待用户退出
wait
