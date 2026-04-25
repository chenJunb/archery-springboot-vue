# 射箭计时器系统 API 接口文档

本文档详细描述射箭计时器系统前后端接口，包括 WebSocket 消息接口和 HTTP REST API 接口。

**文档最后更新**：2026年4月25日

## 1. WebSocket 连接配置

### 1.1 连接端点
- **WebSocket 端点**：`/ws-archery-timer`
- **协议**：SockJS + STOMP
- **应用前缀**：`/app`
- **广播前缀**：`/topic`
- **用户队列前缀**：`/user`

### 1.2 连接建立流程
1. 前端通过 SockJS 连接到后端 WebSocket 端点
2. 建立 STOMP 连接并订阅相关主题
3. 发送客户端注册请求 (`/app/register`)
4. 接收注册响应 (`/user/queue/messages`)

## 2. WebSocket 消息接口（前端 → 后端）

前端通过 `sendGlobalWebSocketMessage(destination, message)` 发送消息到后端。

| 消息目的地 | 参数 | 功能描述 | 前端调用位置 |
|-----------|------|----------|-------------|
| `/app/register` | `clientType`, `clientName` | 客户端注册 | `globalWebSocketService.js:registerGlobalClient()` |
| `/app/timer/select-match-type` | `matchTypeId` | 选择比赛类型 | `enhancedTimer.js:selectMatchType()` |
| `/app/timer/start` | 无 | 开始计时 | `enhancedTimer.js:startTimer()` |
| `/app/timer/pause` | 无 | 暂停计时 | `enhancedTimer.js:pauseTimer()` |
| `/app/timer/reset` | 无 | 重置计时 | `enhancedTimer.js:resetTimer()` |
| `/app/timer/set-ab-mode` | `mode` | 设置AB屏模式 | `enhancedTimer.js:setABMode()` |
| `/app/timer/toggle-ab-screen` | 无 | 切换AB屏 | `enhancedTimer.js:toggleABScreen()` |
| `/app/timer/set-screen-enabled` | `screen`, `enabled` | 设置屏幕启用状态 | `enhancedTimer.js:setScreenEnabled()` |
| `/app/timer/set-prompt` | `screen`, `prompt` | 设置提示文案 | `enhancedTimer.js:setPrompt()` |
| `/app/timer/set-time-config` | `preparation`, `competition`, `yellowLight` | 设置时间配置 | `EnhancedControlView.vue:saveTimeConfig()` |
| `/app/timer/set-sound-enabled` | `enabled` | 设置声音开关 | `enhancedTimer.js:setSoundEnabled()` |
| `/app/timer/set-volume` | `volume` | 设置音量 | `enhancedTimer.js:setVolume()` |
| `/app/timer/manual-buzzer` | `type` | 手动鸣笛 | `enhancedTimer.js:manualBuzzer()` |
| `/app/match-types/get-all` | 无 | 获取所有比赛类型 | `enhancedTimer.js:requestMatchTypesViaWS()` |
| `/app/match-types/screen-mode` | `matchTypeId` | 获取AB屏模式配置 | `enhancedTimer.js:requestScreenModeConfig()` |
| `/app/match-types/time-config` | `matchTypeId` | 获取时间配置 | `enhancedTimer.js:requestTimeConfig()` |
| `/app/timer/broadcast-state` | 无 | 请求广播当前状态 | `enhancedTimer.js:requestBroadcastState()` |

## 3. WebSocket 订阅接口（后端 → 前端）

前端订阅以下主题接收后端推送的消息。

### 3.1 广播主题（所有客户端）

| 订阅路径 | 消息格式 | 功能描述 | 处理位置 |
|----------|----------|----------|----------|
| `/topic/timer-state` | `TimerStateDTO` | 计时器状态广播 | `globalWebSocketService.js:subscribeToAllTopics()` |
| `/topic/clients` | `{clients: []}` | 客户端状态广播 | `globalWebSocketService.js:subscribeToAllTopics()` |
| `/topic/debug` | `{type, sessionId, clientId, ...}` | 调试信息 | `globalWebSocketService.js:subscribeToAllTopics()` |
| `/topic/match-types` | `{type: "matchTypes", data: []}` | 比赛类型列表 | `enhancedTimer.js:subscribeToTopics()` |
| `/topic/match-type-screen-mode` | `{type: "screenModeConfig", data: {}}` | AB屏模式配置 | `enhancedTimer.js:subscribeToTopics()` |
| `/topic/match-type-details` | `{type: "matchTypeDetails", ...}` | 比赛类型详情 | `enhancedTimer.js:subscribeToTopics()` |
| `/topic/buzzer` | `{type: "manualBuzzer", ...}` | 鸣笛通知 | `enhancedTimer.js:subscribeToTopics()` |

### 3.2 用户专属队列（点对点）

| 订阅路径 | 消息类型 | 功能描述 | 处理位置 |
|----------|----------|----------|----------|
| `/user/queue/messages` | `client_registered` | 客户端注册响应 | `globalWebSocketService.js:subscribeToAllTopics()` |
| `/user/queue/messages` | `timer_state` | 个人计时器状态 | `globalWebSocketService.js:subscribeToAllTopics()` |
| `/user/queue/messages` | `error` | 错误消息 | `globalWebSocketService.js:subscribeToAllTopics()` |

## 4. HTTP REST API 接口

### 4.1 比赛类型相关 API

| 接口路径 | 方法 | 参数 | 返回值 | 功能描述 |
|----------|------|------|--------|----------|
| `/api/match-types` | GET | 无 | `{success, data[], count}` | 获取所有比赛类型 |
| `/api/match-types/{id}` | GET | `id` | `{success, data}` | 根据ID获取比赛类型 |
| `/api/match-types/category/{category}` | GET | `category` | `{success, data[], count}` | 根据类别获取比赛类型 |
| `/api/match-types/categories` | GET | 无 | `{success, data[], count}` | 获取所有类别 |
| `/api/match-types/{id}/legacy` | GET | `id` | `{success, data}` | 转换为兼容格式 |
| `/api/match-types/{id}/screen-mode` | GET | `id` | `{success, data}` | 获取AB屏模式配置 |
| `/api/match-types/{id}/default-prompts` | GET | `id` | `{success, data}` | 获取默认提示文案 |
| `/api/match-types/{id}/time-config` | GET | `id` | `{success, data}` | 获取时间配置 |

### 4.2 计时器相关 API

| 接口路径 | 方法 | 参数 | 返回值 | 功能描述 |
|----------|------|------|--------|----------|
| `/api/timer/status` | GET | 无 | `TimerStateDTO` | 获取当前计时器状态 |
| `/api/timer/match-types` | GET | 无 | `Map<String, MatchTypeDTO>` | 获取所有比赛类型（旧格式） |

## 5. 数据模型

### 5.1 TimerStateDTO（计时器状态）
```json
{
  "status": "idle|running|paused|finished",
  "totalRemaining": 0,
  "currentStageIndex": 0,
  "currentStageName": "准备",
  "currentStageColor": "#FF0000",
  "currentStageRemaining": 0,
  "activeScreen": "A",
  "abMode": "alternate|sync|only_a|only_b",
  "preparationTime": 10,
  "competitionTime": 180,
  "yellowLightTime": 30,
  "screenARemaining": 0,
  "screenBRemaining": 0,
  "screenAStatus": "paused|running",
  "screenBStatus": "paused|running",
  "timestamp": 1234567890
}
```

### 5.2 ClientInfo（客户端信息）
```json
{
  "clientId": "uuid",
  "clientType": "control|display_a|display_b",
  "clientName": "控制端|A屏显示端|B屏显示端",
  "registeredAt": "2026-04-25T19:11:54",
  "lastHeartbeat": "2026-04-25T19:12:00",
  "status": "connected|disconnected"
}
```

## 6. 接口一致性检查

### 6.1 WebSocket 消息路径一致性
| 前端发送端 | 后端接收端 | 是否一致 |
|------------|------------|----------|
| `/app/register` | `@MessageMapping("/register")` | ✅ 一致 |
| `/app/timer/select-match-type` | `@MessageMapping("/timer/select-match-type")` | ✅ 一致 |
| `/app/timer/start` | `@MessageMapping("/timer/start")` | ✅ 一致 |
| `/app/timer/pause` | `@MessageMapping("/timer/pause")` | ✅ 一致 |
| `/app/timer/reset` | `@MessageMapping("/timer/reset")` | ✅ 一致 |
| `/app/timer/set-ab-mode` | `@MessageMapping("/timer/set-ab-mode")` | ✅ 一致 |
| `/app/timer/toggle-ab-screen` | `@MessageMapping("/timer/toggle-ab-screen")` | ✅ 一致 |
| `/app/timer/set-screen-enabled` | `@MessageMapping("/timer/set-screen-enabled")` | ✅ 一致 |
| `/app/timer/set-prompt` | `@MessageMapping("/timer/set-prompt")` | ✅ 一致 |
| `/app/timer/set-time-config` | `@MessageMapping("/timer/set-time-config")` | ✅ 一致 |
| `/app/timer/set-sound-enabled` | `@MessageMapping("/timer/set-sound-enabled")` | ✅ 一致 |
| `/app/timer/set-volume` | `@MessageMapping("/timer/set-volume")` | ✅ 一致 |
| `/app/timer/manual-buzzer` | `@MessageMapping("/timer/manual-buzzer")` | ✅ 一致 |
| `/app/match-types/get-all` | `@MessageMapping("/match-types/get-all")` | ✅ 一致 |
| `/app/match-types/screen-mode` | `@MessageMapping("/match-types/screen-mode")` | ✅ 一致 |
| `/app/match-types/time-config` | `@MessageMapping("/match-types/time-config")` | ✅ 一致 |

### 6.2 订阅主题一致性
| 前端订阅端 | 后端发送端 | 是否一致 |
|------------|------------|----------|
| `/topic/timer-state` | `@SendTo("/topic/timer-state")` | ✅ 一致 |
| `/topic/clients` | `convertAndSend("/topic/clients", ...)` | ✅ 一致 |
| `/user/queue/messages` | `convertAndSendToUser(sessionId, "/queue/messages", ...)` | ✅ 一致 |

### 6.3 HTTP REST API 一致性
| 前端调用端 | 后端接口端 | 是否一致 |
|------------|------------|----------|
| `fetch('/api/match-types')` | `@GetMapping("/api/match-types")` | ✅ 一致 |
| `fetch('/api/match-types')` | `@GetMapping("/api/match-types/categories")` | ✅ 一致 |
| `fetch('/api/match-types/${id}/time-config')` | `@GetMapping("/api/match-types/{id}/time-config")` | ✅ 一致 |

## 7. 特殊注意事项

### 7.1 WebSocket 注册流程
1. 前端连接 WebSocket 并订阅所有主题
2. 等待 5 秒确保订阅生效
3. 发送 `/app/register` 消息
4. 等待后端通过 `/user/queue/messages` 返回 `client_registered` 响应
5. 更新 `globalConnectionState.isRegistered = true`

### 7.2 心跳机制
- STOMP 内置心跳：`heartbeatIncoming: 15000`, `heartbeatOutgoing: 15000`
- 前端每 15 秒发送一次心跳
- 后端心跳超时时间：60 秒

### 7.3 错误处理
- WebSocket 连接断开时自动重连（最多 5 次快速重连，之后每 30 秒重试）
- 前端收到 error 类型消息时广播给所有页面

## 8. 收费后端回调接口检查

**检查结果**：项目中未发现收费相关的接口或回调功能。

搜索关键词：`pay`、`payment`、`callback`、`charge`、`订单`、`收费` 均无匹配结果。

**结论**：
1. 当前系统为纯计时器功能，不涉及支付或收费功能
2. 所有 WebSocket 和 HTTP 接口均已在前端正确使用
3. 接口路径和参数完全一致，无未对齐的接口

## 9. 接口调用统计

### 9.1 最常用接口（按调用频率）
1. **计时器状态广播** (`/topic/timer-state`) - 实时更新，每 1 秒一次
2. **客户端注册** (`/app/register`) - 每个客户端连接时调用
3. **开始/暂停/重置计时** (`/app/timer/start|pause|reset`) - 用户操作时调用

### 9.2 重要接口（按业务重要性）
1. **客户端注册** - 连接建立基础
2. **计时器控制** - 核心业务功能
3. **比赛类型选择** - 业务配置

---

**文档维护说明**：
- 当新增 WebSocket MessageMapping 时，需同时更新前端发送代码
- 当新增订阅主题时，需同时更新前端订阅代码
- 接口变更时需确保前后端一致性