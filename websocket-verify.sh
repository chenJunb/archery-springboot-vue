#!/bin/bash
# WebSocket 连接快速验证脚本

echo "=========================================="
echo "WebSocket 连接诊断工具"
echo "=========================================="
echo ""

# 检查后端服务
echo "1️⃣  检查后端服务状态..."
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ 后端服务运行中 (localhost:8080)"
else
    echo "❌ 后端服务未响应"
    echo "   请运行: cd backend && mvn spring-boot:run"
    exit 1
fi

echo ""

# 检查前端服务
echo "2️⃣  检查前端服务状态..."
if curl -s http://localhost:3000/ > /dev/null 2>&1; then
    echo "✅ 前端服务运行中 (localhost:3000)"
else
    echo "❌ 前端服务未响应"
    echo "   请运行: cd frontend && npm run dev"
    exit 1
fi

echo ""

# 检查 WebSocket 端点
echo "3️⃣  检查 WebSocket 端点..."
if curl -s -i http://localhost:8080/ws-archery-timer 2>&1 | grep -q "404\|400"; then
    echo "⚠️  WebSocket 端点返回错误 (这是正常的，需要 WebSocket 升级)"
else
    echo "✅ WebSocket 端点可访问"
fi

echo ""

# 检查代理配置
echo "4️⃣  检查 Vite 代理配置..."
if grep -q "ws-archery-timer" /d/code/ArcheryCompetition/archery-springboot-vue/frontend/vite.config.js; then
    echo "✅ Vite 代理已配置 WebSocket"
else
    echo "❌ Vite 代理未配置 WebSocket"
fi

echo ""

# 检查前端 WebSocket URL
echo "5️⃣  检查前端 WebSocket URL 配置..."
if grep -q "const wsUrl = '/ws-archery-timer'" /d/code/ArcheryCompetition/archery-springboot-vue/frontend/src/services/globalWebSocketService.js; then
    echo "✅ 前端使用相对路径 (/ws-archery-timer)"
else
    echo "❌ 前端 WebSocket URL 配置错误"
fi

echo ""

# 检查注册延迟
echo "6️⃣  检查客户端注册延迟..."
if grep -q "setTimeout(checkAndRegister, 5000)" /d/code/ArcheryCompetition/archery-springboot-vue/frontend/src/services/globalWebSocketService.js; then
    echo "⚠️  仍使用 5 秒延迟（应该改为立即注册）"
else
    echo "✅ 已修改为立即注册（无延迟）"
fi

echo ""
echo "=========================================="
echo "诊断完成！"
echo "=========================================="
echo ""
echo "📝 后续步骤："
echo "1. 打开 http://localhost:3000/"
echo "2. 打开浏览器 DevTools (F12)"
echo "3. 查看 Console 日志"
echo "4. 搜索关键字："
echo "   - '✅ WebSocket 连接已建立'"
echo "   - '✅ 个人队列订阅已确认'"
echo "   - '✅ 客户端注册请求已发送'"
echo ""
echo "🎯 预期连接时间: 1-2 秒"
echo "🎯 消息同步延迟: <10ms"
