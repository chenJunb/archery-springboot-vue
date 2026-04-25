#!/bin/bash

# WebSocket连接诊断脚本

echo "=========================================="
echo "WebSocket 连接诊断"
echo "=========================================="
echo ""

# 检查后端是否运行
echo "1️⃣  检查后端服务..."
if curl -s http://localhost:8080/ws-archery-timer | grep -q "SockJS"; then
    echo "✅ 后端服务运行中"
else
    echo "❌ 后端服务未运行"
    exit 1
fi

echo ""

# 检查前端是否运行
echo "2️⃣  检查前端服务..."
if curl -s http://localhost:3000/ | grep -q "射箭"; then
    echo "✅ 前端服务运行中"
else
    echo "❌ 前端服务未运行"
    exit 1
fi

echo ""
echo "=========================================="
echo "🔍 打开浏览器进行手动检查"
echo "=========================================="
echo ""
echo "📍 请打开浏览器访问："
echo "   http://localhost:3000/"
echo ""
echo "📍 按 F12 打开开发者工具，Console 标签"
echo ""
echo "📍 等待 2-3 秒，然后查看 Console 中是否看到："
echo "   ✅ 客户端注册请求已发送"
echo "   ✅ 客户端注册成功"
echo ""
echo "📍 验证连接状态，在 Console 中输入："
echo "   globalConnectionState"
echo ""
echo "   预期看到："
echo "   {"
echo "     isConnected: true"
echo "     isRegistered: true"
echo "     clientId: \"xxx...\""
echo "     clientType: \"control\""
echo "   }"
echo ""
echo "=========================================="
echo "如果 clientId 仍为 null，请检查后端日志"
echo "=========================================="
echo ""
