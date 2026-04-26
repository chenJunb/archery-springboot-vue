# WebSocket连接 - 快速参考卡

## 核心状态对象

### 前端状态 (globalConnectionState)
```javascript
{
  isConnected: boolean,       // WebSocket连接状态
  isRegistered: boolean,      // 是否已注册（收到clientId）
  clientId: string | null,    // 唯一客户端ID（从后端）
  clientType: string,         // 'control' | 'display_a' | 'display_b'
  registerTime: timestamp     // 注册时间
}
```

## 连接流程

```
initGlobalWebSocket()
  ↓ SockJS连接 ✅
onConnect()
  ├─ isConnected = true
  ├─ subscribeToAllTopics()
  │  ├─ /user/queue/messages (个人队列)
  │  ├─ /topic/timer-state (计时器状态)
  │  ├─ /topic/clients (客户端列表)
  │  └─ /topic/debug (调试)
  └─ 100ms后发送 /app/register
     ↓
后端收到 /app/register
  ├─ 验证clientType和sessionId
  ├─ 生成/查找clientId
  ├─ WebSocketService.registerClient()
  └─ 发送响应到 /user/{sessionId}/queue/messages
     ↓
前端收到注册响应
  ├─ globalConnectionState.clientId = xxx
  ├─ globalConnectionState.isRegistered = true
  └─ ✅ 已连接且已注册
```

## 关键变量位置

| 变量 | 文件 | 行 | 说明 |
|------|------|-----|------|
| globalStompClient | globalWebSocketService.js | 12 | STOMP客户端实例 |
| globalConnectionState | globalWebSocketService.js | 15-21 | 连接状态对象 |
| messageCallbacks | globalWebSocketService.js | 24 | 消息回调Set集合 |
| clients | WebSocketService.java | 28 | ConcurrentHashMap<clientId, ClientInfo> |
| sessionIdToClientId | WebSocketService.java | 30 | 会话到客户端映射 |
| currentControlClientId | WebSocketService.java | 32 | 当前控制端ID |

## 关键函数

| 函数 | 文件 | 说明 |
|------|------|------|
| initGlobalWebSocket() | globalWebSocketService.js | 初始化WebSocket连接 |
| onGlobalWebSocketMessage() | globalWebSocketService.js | 注册消息回调 |
| subscribeToTopic() | globalWebSocketService.js | 订阅自定义主题 |
| sendGlobalWebSocketMessage() | globalWebSocketService.js | 发送WebSocket消息 |
| registerClient() | WebSocketService.java | 后端注册客户端 |
| getClientIdBySessionId() | WebSocketService.java | 根据sessionId查clientId |
| unregisterClient() | WebSocketService.java | 注销客户端 |

## 重连策略

```
第1次重连: 等待 3s   (3000 * 1)
第2次重连: 等待 6s   (3000 * 2)
第3次重连: 等待 12s  (3000 * 4)
第4次重连: 等待 24s  (3000 * 8)
第5次重连: 等待 30s  (3000 * 16, 但capped at 30s)
          ↓
  快速重连失败
          ↓
启用定期重连: 每30s重试一次
```

## 绝对不能删除的代码

1. **前端** globalWebSocketService.js
   - 行12: `let globalStompClient = null`
   - 行15-21: `globalConnectionState` 对象
   - 行24: `messageCallbacks = new Set()`
   - 行43-62: `onGlobalWebSocketMessage()` 函数
   - 行119-252: `subscribeToAllTopics()` 函数
   - 行330-396: STOMP客户端配置

2. **后端** WebSocketConfig.java
   - 行41-78: 入站拦截器 (设置User Principal)
   - 行84-126: 出站拦截器 (检查User信息)
   - 行54-60: `/user/*/queue` 的User设置
   - 行70-74: CONNECT命令的User设置

3. **后端** WebSocketService.java
   - 行28: `clients = new ConcurrentHashMap<>()`
   - 行30: `sessionIdToClientId = new ConcurrentHashMap<>()`
   - 行80-92: 防重复注册逻辑
   - 行123-126: `registerSessionIdMapping()` 方法
   - 行132-134: `getClientIdBySessionId()` 方法

## 诊断命令

```bash
# 检查后端是否监听8080
netstat -an | grep 8080

# 查看后端连接日志
grep "新客户端注册成功\|📡 \[入站\]" backend.log | head -20

# 查看重连日志
grep "尝试快速重连\|定期重连" browser-console.log

# 检查前端配置
grep "wsUrl\|heartbeat" frontend/src/services/globalWebSocketService.js

# 检查后端拦截器
grep "accessor.setUser" backend/src/main/java/com/archery/timer/config/WebSocketConfig.java
```

## 常见错误及修复

| 错误现象 | 原因 | 修复 |
|---------|------|------|
| isConnected: false | 后端未启动或URL错误 | 检查后端监听、Vite代理配置 |
| isRegistered: false | /user/queue/messages未订阅或拦截器缺失 | 检查入站拦截器是否设置User |
| 消息收不到 | 客户端消息回调未注册 | 调用 onGlobalWebSocketMessage() 注册回调 |
| 频繁断开 | 心跳设置过短或网络不稳定 | 改heartbeat到15000ms |
| 多个clientId | 同一sessionId重复注册 | 检查sessionIdToClientId映射 |

## 测试步骤

1. ✅ 打开浏览器F12 → Console
2. ✅ 观察第一行日志: "初始化全局 WebSocket 连接"
3. ✅ 等待2秒，看到: "✅ 个人队列订阅已创建"
4. ✅ 看到: "✅ 客户端注册成功"
5. ✅ 检查 `globalConnectionState`
   - isConnected: true ✅
   - isRegistered: true ✅
   - clientId: 'xxxxxxxx-...' ✅
6. ✅ 启动计时，看到消息更新

## 重要提醒

🔴 **绝对不能做**:
- 删除 globalConnectionState
- 删除 messageCallbacks Set
- 删除 ChannelInterceptor
- 删除 sessionIdToClientId 映射
- 改 /topic 和 /queue 路由前缀
- 改 /app 应用前缀
- 改 /ws-archery-timer STOMP端点

🟡 **谨慎修改**:
- 重连参数（影响网络稳定性体验）
- 心跳参数（影响连接检测灵敏度）
- 消息回调上限（影响内存占用）
- origin限制（生产环境必须改为具体域名）

✅ **可以改**:
- 日志级别
- 日志消息内容
- 注释
- 局部变量名（不涉及数据结构）
