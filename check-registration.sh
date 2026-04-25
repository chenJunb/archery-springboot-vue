#!/bin/bash
# 📱 WebSocket 客户端注册问题诊断脚本

echo "═══════════════════════════════════════════════════════════════"
echo "           WebSocket 客户端注册诊断工具"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}🔍 检查项1：后端修改是否已部署${NC}"
echo "─────────────────────────────────────────────────────────────"

# 检查后端 WebSocketConfig.java
if grep -q "CONNECT.equals" /d/code/ArcheryCompetition/archery-springboot-vue/backend/src/main/java/com/archery/timer/config/WebSocketConfig.java 2>/dev/null; then
    echo -e "${GREEN}✅ 后端 CONNECT 命令处理已添加${NC}"
else
    echo -e "${RED}❌ 后端 CONNECT 命令处理未找到${NC}"
    echo "   请执行：修改 WebSocketConfig.java，添加 CONNECT 命令处理"
fi

echo ""
echo -e "${BLUE}🔍 检查项2：前端修改是否已部署${NC}"
echo "─────────────────────────────────────────────────────────────"

if grep -q "Spring 将自动路由" /d/code/ArcheryCompetition/archery-springboot-vue/frontend/src/services/globalWebSocketService.js 2>/dev/null; then
    echo -e "${GREEN}✅ 前端注释已更新${NC}"
else
    echo -e "${YELLOW}⚠️  前端注释未更新（非关键）${NC}"
fi

echo ""
echo -e "${BLUE}🔍 检查项3：后端代码编译状态${NC}"
echo "─────────────────────────────────────────────────────────────"

cd /d/code/ArcheryCompetition/archery-springboot-vue/backend 2>/dev/null
if mvn compile -q 2>/dev/null; then
    echo -e "${GREEN}✅ 后端编译成功${NC}"
else
    echo -e "${RED}❌ 后端编译失败${NC}"
    echo "   请运行：cd backend && mvn compile"
    exit 1
fi

echo ""
echo -e "${BLUE}🔍 检查项4：关键类是否存在${NC}"
echo "─────────────────────────────────────────────────────────────"

if [ -f "/d/code/ArcheryCompetition/archery-springboot-vue/backend/src/main/java/com/archery/timer/config/WebSocketConfig.java" ]; then
    echo -e "${GREEN}✅ WebSocketConfig.java 存在${NC}"
else
    echo -e "${RED}❌ WebSocketConfig.java 未找到${NC}"
fi

if [ -f "/d/code/ArcheryCompetition/archery-springboot-vue/backend/src/main/java/com/archery/timer/listener/WebSocketEventListener.java" ]; then
    echo -e "${GREEN}✅ WebSocketEventListener.java 存在${NC}"
else
    echo -e "${RED}❌ WebSocketEventListener.java 未找到${NC}"
fi

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}📋 前端运行时检查${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

echo "以下信息需要在浏览器中手动验证："
echo ""
echo -e "${YELLOW}1️⃣  打开浏览器：http://localhost:3000/${NC}"
echo "   按 F12 打开 DevTools → Console 标签"
echo ""
echo -e "${YELLOW}2️⃣  搜索以下关键日志：${NC}"
echo "   在 Console 中输入：document.body.innerText.match(/客户端注册/)"
echo ""
echo "   预期看到的日志顺序："
echo "   ✅ 🔗 [入站] CONNECT 命令 - sessionId: xxx"
echo "   ✅ [入站] 用户队列订阅拦截处理 - sessionId: xxx"
echo "   ✅ 客户端注册请求已发送（立即）- clientType: control"
echo "   ✅ 收到注册成功消息 - clientId: yyy"
echo "   ✅ 客户端注册成功"
echo ""
echo -e "${YELLOW}3️⃣  验证连接状态：${NC}"
echo "   在 Console 中输入："
echo "   ${YELLOW}globalConnectionState${NC}"
echo ""
echo "   预期输出："
echo "   {"
echo "     isConnected: ${GREEN}true${NC}  ← 关键"
echo "     isRegistered: ${GREEN}true${NC}  ← 关键"
echo "     clientId: \"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"  ← 不能为 null"
echo "     clientType: \"control\" (或 display_a / display_b)"
echo "   }"
echo ""
echo -e "${YELLOW}4️⃣  检查页面右上角：${NC}"
echo "   应显示 ${GREEN}\"已连接\"${NC} （绿色指示灯）"
echo "   NOT ${RED}\"离线\"${NC} （红色指示灯）"
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✅ 诊断完成${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""

echo "🚀 后续步骤："
echo "  1. 确保后端正在运行（http://localhost:8080）"
echo "  2. 确保前端正在运行（http://localhost:3000）"
echo "  3. 在浏览器中验证上述检查点"
echo "  4. 如仍有问题，查看 CLIENT_REGISTRATION_FIX.md"
echo ""
