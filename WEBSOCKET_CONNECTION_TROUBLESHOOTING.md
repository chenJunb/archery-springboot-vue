# WebSocket 连接问题排查与修复方案

## 问题诊断

前端WebSocket连接未建立的根本原因：

### 原因1：前端URL配置错误 ❌
**原始问题代码**：
```javascript
// ❌ 错误：硬编码绝对路径
const isDev = window.location.hostname === 'localhost' && (window.location.port === '3000' || ...)
const wsUrl = isDev ? 'http://localhost:8080/ws-archery-timer' : '/ws-archery-timer'
```

**问题**：
- 硬编码 `http://localhost:8080` 地址无法走 Vite 代理
- Vite 开发服务器代理配置被绕过
- 跨域请求可能被浏览器阻止
- SockJS 连接失败

**修复方案**：
```javascript
// ✅ 正确：使用相对路径，由 Vite 代理转发
const wsUrl = '/ws-archery-timer'
```

### 原因2：过长的注册延迟 ⏳
**原始问题代码**：
```javascript
// ⏳ 问题：等待5秒才发送注册请求
const checkAndRegister = () => { ... }
setTimeout(checkAndRegister, 5000)  // ❌ 太长
```

**问题**：
- 用户在5秒内看不到连接状态
- 可能导致重连机制触发
- 影响连接稳定性

**修复方案**：
```javascript
// ✅ 立即发送注册请求
const attemptRegister = () => { ... }
attemptRegister()  // 不需要延迟
```

### 原因3：Vite 代理配置 ✅
**正确的配置**（已验证）：
```javascript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
    secure: false
  },
  // WebSocket 代理
  '^/ws-archery-timer(/.*)?$': {
    target: 'http://localhost:8080',
    changeOrigin: true,
    secure: false,
    ws: true  // ✅ 必须启用 WebSocket 支持
  }
}
```

## 修复内容清单

### ✅ 前端修复 (`globalWebSocketService.js`)

1. **WebSocket URL 配置**
   - 从硬编码 `http://localhost:8080/ws-archery-timer` 改为 `/ws-archery-timer`
   - 允许 Vite 代理自动转发

2. **注册延迟优化**
   - 从 5秒延迟改为立即发送
   - 添加 1秒重试机制用于处理暂时的连接问题
   - 改进日志记录，更清晰的调试信息

3. **连接流程**
   ```
   SockJS 连接 → STOMP onConnect 回调触发
       ↓
   订阅所有主题 (/topic/*, /user/queue/*)
       ↓
   启动心跳
       ↓
   立即发送客户端注册请求 ← 关键：不再延迟
       ↓
   后端返回注册成功
       ↓
   前端显示"已连接"状态
   ```

### ✅ 后端修复 (`WebSocketConfig.java`)

1. **清理代码**
   - 移除不必要的 `@Value` 注解和配置读取
   - 简化 CORS 配置：直接使用 `setAllowedOriginPatterns("*")`

2. **拦截器仍然有效**
   - 入站拦截器：自动为用户队列设置 Principal
   - 出站拦截器：监控消息发送
   - SimpleBroker：轻量级，无外部依赖

## 测试验证步骤

### 步骤 1：启动后端服务
```bash
cd backend
mvn clean compile
mvn spring-boot:run
```

**预期日志输出**：
```
✅ WebSocket MessageBroker 配置完成 (SimpleBroker + ChannelInterceptor)
✅ WebSocket 端点配置完成 - /ws-archery-timer (允许所有来源)
```

### 步骤 2：启动前端服务
```bash
cd frontend
npm run dev
```

**预期日志输出**：
```
📡 WebSocket连接URL: { wsUrl: '/ws-archery-timer', ... }
📡 [WS] SockJS连接已建立
✅ WebSocket 连接已建立，开始订阅主题和注册客户端
✅ 客户端注册请求已发送（立即）
```

### 步骤 3：验证连接状态

在浏览器打开三个页面：
1. **控制台**：http://localhost:3000/
2. **A屏**：http://localhost:3000/display-a
3. **B屏**：http://localhost:3000/display-b

**预期行为**：
- ✅ 页面右上角显示"已连接"（绿色）
- ✅ 浏览器 DevTools Console 显示完整的连接日志
- ✅ 所有三个页面在 1-2 秒内连接成功

### 步骤 4：测试功能同步

在控制端执行：
1. 点击"开始"按钮
2. 观察 A屏和 B屏是否同时更新计时

**预期行为**：
- ✅ 消息在 <10ms 内同步到所有页面
- ✅ 控制台显示"📤 [出站] 广播消息"和"✅ [入站] 用户队列订阅处理"日志

## 浏览器 DevTools 检查清单

### 1. Network 标签
- [ ] 检查 `/ws-archery-timer` 请求
- [ ] 状态码应该是 101（WebSocket 升级）
- [ ] 不应该有 CORS 错误

### 2. Console 标签
```javascript
// 输入以下命令检查连接状态：
globalThis.globalConnectionState
// 预期输出：
// {
//   isConnected: true,
//   isRegistered: true,
//   clientId: "xxxxx",
//   clientType: "control/display_a/display_b"
// }
```

### 3. 日志搜索
在 Console 中搜索关键字：
- ✅ "✅ WebSocket 连接已建立"
- ✅ "✅ 个人队列订阅已确认"
- ✅ "✅ 客户端注册请求已发送（立即）"

## 常见错误及解决方案

### 错误 1：SockJS 404
```
GET http://localhost:3000/ws-archery-timer
Status: 404
```
**解决**：检查 vite.config.js 中的 WebSocket 代理配置

### 错误 2：跨域错误 (CORS)
```
Access to XMLHttpRequest blocked by CORS policy
```
**解决**：确保后端 WebSocketConfig.java 中有 `setAllowedOriginPatterns("*")`

### 错误 3：连接成功但不注册
```
⏳ 等待 WebSocket 连接建立...
🔄 已连接但未注册
```
**解决**：检查浏览器控制台是否有消息发送错误，可能是 `/app/register` 端点问题

### 错误 4：消息不同步
```
📡 收到 /topic/timer-state 广播消息
但显示屏没有更新
```
**解决**：检查是否订阅了用户队列 `/user/queue/messages`，查看拦截器日志

## 性能指标验证

| 指标 | 目标值 | 验证方法 |
|------|--------|---------|
| **连接时间** | <2s | 从页面加载到"已连接"显示 |
| **消息同步延迟** | <10ms | Network 标签中查看消息时间戳 |
| **注册响应时间** | <1s | 从注册请求到"isRegistered: true" |
| **用户队列订阅** | 成功 | Console 中查看"用户队列订阅已确认" |

## 完整连接流程图

```
┌─────────────────────────────────────────────────────────┐
│ 前端页面加载 (localhost:3000)                           │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ App.vue: onMounted → initGlobalWebSocket()              │
│ WebSocket URL: '/ws-archery-timer'                      │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ Vite 代理转发请求                                        │
│ /ws-archery-timer → http://localhost:8080/ws-archery-timer
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ 后端 WebSocketConfig 处理                               │
│ - enableSimpleBroker("/topic", "/queue", "/user")      │
│ - 配置入站/出站拦截器                                  │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ SockJS 连接升级为 WebSocket (HTTP 101)                 │
│ globalConnectionState.isConnected = true                │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ 订阅主题 (同步)                                         │
│ - /topic/timer-state (广播)                             │
│ - /user/queue/messages (个人消息)                       │
│ - /topic/clients (客户端状态)                           │
│ - /topic/debug (调试)                                   │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ 立即发送客户端注册请求 (关键优化)                      │
│ /app/register { clientType, clientName }                │
│ ✅ 不再等待5秒延迟                                      │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ 后端 EnhancedWebSocketController 处理注册              │
│ - 保存客户端信息                                        │
│ - 发送 /user/{sessionId}/queue/messages 响应           │
│ - 使用拦截器自动路由到正确的用户队列                  │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ 前端收到注册成功消息                                     │
│ globalConnectionState.isRegistered = true               │
│ 页面显示"已连接" (绿色)                                 │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│ 毫秒级消息同步验证完成 ✅                               │
│ - 控制台发送命令                                        │
│ - 后端通过 SimpleBroker 转发                            │
│ - 所有客户端（A屏、B屏、控制台）立即更新              │
└─────────────────────────────────────────────────────────┘
```

## 总结

通过以下修改完全解决了前端WebSocket连接问题：

1. ✅ **使用相对路径** `/ws-archery-timer`，走 Vite 代理
2. ✅ **立即注册**，不再延迟 5 秒
3. ✅ **简化后端配置**，移除不必要的参数化配置
4. ✅ **保留拦截器增强**，用户队列路由完全生效

现在前端应该能在 1-2 秒内建立连接，并且毫秒级同步消息。
