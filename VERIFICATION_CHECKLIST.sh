#!/bin/bash

echo "═══════════════════════════════════════════════════════════════"
echo "           射箭比赛计时系统 - 修复验证清单"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "✅ 已完成的修复："
echo ""

echo "1️⃣  WebSocket 客户端注册修复"
echo "   - 后端: 改用 convertAndSend() 发送注册响应"
echo "   - 前端: 添加自动选择比赛类型的 watch 监听器"
echo "   - 预期: clientId 不再为 null，页面显示已连接"
echo ""

echo "2️⃣  比赛类型自动选择修复"
echo "   - 前端: 条件判断确保连接建立后再配置"
echo "   - 前端: 添加 watch 监听器处理时序问题"
echo "   - 预期: 页面加载时自动选择第一个比赛类型"
echo ""

echo "3️⃣  后端空指针异常修复"
echo "   - 后端: setTimeConfig() 添加 state null 检查"
echo "   - 后端: notifyStateChange() 添加 callback 备用方案"
echo "   - 预期: 不再出现 NPE，系统更稳健"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "           服务状态检查"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# 检查后端
echo -n "后端服务 (http://localhost:8080/ws-archery-timer): "
if curl -s http://localhost:8080/ws-archery-timer 2>&1 | grep -q "SockJS"; then
    echo "✅ 运行中"
else
    echo "❌ 未运行"
fi

# 检查前端
echo -n "前端服务 (http://localhost:3000): "
if curl -s http://localhost:3000/ | grep -q "射箭"; then
    echo "✅ 运行中"
else
    echo "❌ 未运行"
fi

# 检查比赛类型API
echo -n "比赛类型 API (http://localhost:8080/api/match-types): "
COUNT=$(curl -s http://localhost:8080/api/match-types | grep -o '"count":[0-9]*' | cut -d: -f2)
if [ ! -z "$COUNT" ] && [ "$COUNT" -gt 0 ]; then
    echo "✅ 已加载 $COUNT 个类型"
else
    echo "❌ 无法获取"
fi

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "           功能测试步骤"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "📝 请按以下步骤进行测试："
echo ""

echo "1️⃣  打开浏览器访问: http://localhost:3000/"
echo ""

echo "2️⃣  按 F12 打开开发者工具，Console 标签"
echo ""

echo "3️⃣  等待 2-3 秒，查看 Console 中是否出现:"
echo "   ✅ 客户端注册成功"
echo "   ✅ 连接已建立，自动选择第一个比赛类型"
echo ""

echo "4️⃣  在 Console 中输入: globalConnectionState"
echo "   应显示: {isConnected: true, isRegistered: true, clientId: \"UUID\"}"
echo ""

echo "5️⃣  查看页面右上角"
echo "   应显示: \"已连接\" (绿色指示灯)"
echo "   不应显示: \"离线\" 或连接中..."
echo ""

echo "6️⃣  点击\"开始计时\"按钮"
echo "   应该成功启动计时，无错误"
echo "   A/B屏应显示实时计时数据"
echo ""

echo "7️⃣  修改时间配置 (准备、比赛、黄灯时间)"
echo "   应该成功更新，无错误"
echo "   A/B屏应显示新的时间"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "           日志检查"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "🔍 如果出现问题，请检查后端日志中是否有这些错误:"
echo ""

echo "❌ 不应该出现:"
echo "   - NullPointerException"
echo "   - stateChangeCallback为null"
echo "   - 比赛类型未选择"
echo ""

echo "✅ 应该出现:"
echo "   - 客户端注册成功"
echo "   - 时间配置已更新并广播"
echo "   - 通过messagingTemplate广播状态"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "✅ 验证清单完成"
echo "═══════════════════════════════════════════════════════════════"
echo ""
