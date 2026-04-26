# WebSocket连接状态管理 - 完整文档

## 📋 目录
1. [核心架构](#核心架构)
2. [关键代码模块](#关键代码模块)
3. [连接状态流程](#连接状态流程)
4. [误修改恢复指南](#误修改恢复指南)
5. [不可变代码保护](#不可变代码保护)
6. [常见问题排查](#常见问题排查)

---

## 核心架构

### 系统组成

```
┌─────────────────────────────────────────────────────────────┐
│                    前端 Vue 3 + Vite                         │
├─────────────────────────────────────────────────────────────┤
│  globalWebSocketService.js                                  │
│  ├─ globalStompClient (STOMP客户端)                         │
│  ├─ globalConnectionState (连接状态管理)                     │
│  ├─ messageCallbacks (消息回调集合)                          │
│  ├─ reconnectAttempts (重连次数)                            │
│  └─ exponentialBackoffMultiplier (指数退避倍数)             │
└─────────────────────────────────────────────────────────────┘
                          ↕ WebSocket
                    (SockJS + STOMP)
┌─────────────────────────────────────────────────────────────┐
│                Spring Boot 后端 8080                         │
├─────────────────────────────────────────────────────────────┤
│  WebSocketConfig.java                                       │
│  ├─ ChannelInterceptor (入站/出站拦截)                      │
│  ├─ SimpleBroker (/topic, /queue, /user)                   │
│  └─ STOMP端点 (/ws-archery-timer)                           │
│                                                              │
│  EnhancedWebSocketController.java                           │
│  ├─ @SubscribeMapping("/topic/timer-state")                │
│  ├─ @MessageMapping("/register")                           │
│  └─ @MessageMapping("/timer/*")                            │
│                                                              │
│  WebSocketService.java                                      │
│  ├─ clients (ConcurrentHashMap<clientId, ClientInfo>)      │
│  ├─ sessionIdToClientId (会话到客户端映射)                  │
│  ├─ currentControlClientId (当前控制端)                     │
│  └─ 清理/心跳任务                                           │
└─────────────────────────────────────────────────────────────┘
```

### 连接状态对象

#### 前端: globalConnectionState
```javascript
{
  isConnected: boolean,      // WebSocket连接状态 (true/false)
  isRegistered: boolean,     // 客户端是否已在后端注册 (true/false)
  clientId: string | null,   // 后端分配的唯一客户端ID
  clientType: string,        // 'control' | 'display_a' | 'display_b'
  registerTime: timestamp    // 注册时间戳
}
```

#### 后端: ClientInfo
```java
public static class ClientInfo {
    String clientId;          // UUID，唯一标识
    String clientType;        // control, display_a, display_b
    String clientName;        // 客户端名称
    LocalDateTime registeredAt;  // 首次注册时间（不变）
    LocalDateTime lastHeartbeat; // 最后心跳时间（更新）
    String status;            // connected, disconnected
}
```

---

## 关键代码模块

### 1️⃣ 前端: globalWebSocketService.js

#### ✅ 必须保持的核心常量

```javascript
// 行1-21: 核心初始化代码
let globalStompClient = null                              // ❌ 不能删除

export const globalConnectionState = reactive({
  isConnected: false,                                     // ❌ 不能删除
  isRegistered: false,                                    // ❌ 不能删除
  clientId: null,                                         // ❌ 不能删除
  clientType: 'control',                                  // ❌ 不能删除
  registerTime: null                                      // ❌ 不能删除
})

// 重连配置（✅ 可修改值，但不能删除变量）
const messageCallbacks = new Set()                        // ❌ 不能删除
const maxReconnectAttempts = 5                            // ⚠️ 可修改(1-10)
const reconnectDelay = 3000                               // ⚠️ 可修改(1000-10000)ms
const maxBackoffDelay = 30000                             // ⚠️ 可修改(10000-60000)ms
const maxMessageCallbacks = 100                           // ⚠️ 可修改(50-200)
```

#### ✅ 关键函数（不能修改签名或删除）

```javascript
// ❌ 签名不能改，返回值不能改
export function onGlobalWebSocketMessage(callback)
  // 返回: () => {} (注销函数)

export function initGlobalWebSocket()
  // 职责: 初始化STOMP客户端，配置onConnect/onDisconnect

export function disconnectGlobalWebSocket()
  // 职责: 安全断开连接，清理资源

export function getGlobalStompClient()
  // 返回: globalStompClient (STOMP客户端实例)

export function registerGlobalClient(clientType)
  // 返回: boolean

export function sendGlobalWebSocketMessage(destination, message)
  // 返回: boolean

export function subscribeToTopic(topic, callback)
  // 返回: Subscription或null
```

#### ⚠️ 关键配置（修改需谨慎）

| 配置项 | 当前值 | 说明 | 修改范围 |
|-------|--------|------|----------|
| maxReconnectAttempts | 5 | 快速重连次数 | 3-10 |
| reconnectDelay | 3000ms | 基础重连延迟 | 1000-10000ms |
| maxBackoffDelay | 30000ms | 最大退避延迟 | 10000-60000ms |
| heartbeatIncoming | 15000ms | 心跳超时 | 10000-30000ms |
| heartbeatOutgoing | 15000ms | 心跳发送间隔 | 10000-30000ms |

---

### 2️⃣ 后端: WebSocketConfig.java

#### ✅ 不能改动的配置

```java
// 行29-32: MessageBroker配置 - ❌ 严禁修改
config.enableSimpleBroker("/topic", "/queue", "/user");
config.setUserDestinationPrefix("/user");
config.setApplicationDestinationPrefixes("/app");

// 行41-78: ChannelInterceptor - ❌ 入站拦截逻辑不能改
// 关键修复:
//   1. SUBSCRIBE命令时设置User Principal (L57)
//   2. CONNECT命令时设置User Principal (L73)
//   3. 检查/user/*/queue路由 (L54-55)

// 行84-126: 出站拦截 - ❌ 逻辑不能改
// 关键修复:
//   1. 监控/user/前缀消息 (L94-115)
//   2. 从路径提取sessionId (L103-109)
//   3. 设置User Principal (L106)

// 行133-135: STOMP端点配置 - ⚠️ 可改origin但要谨慎
registry.addEndpoint("/ws-archery-timer")              // ❌ 路径不能改
        .setAllowedOriginPatterns("*")                 // ⚠️ 生产环境改为具体域名
        .withSockJS();
```

#### 🔴 禁止删除的拦截代码

```java
// ❌ 这些代码必须存在，否则用户队列消息无法路由
// 行54-60: 用户队列识别
if (destination != null && destination.contains("/user/")
    && destination.contains("/queue")) {
    accessor.setUser(() -> sessionId);  // ← 关键！
}

// 行70-74: CONNECT命令处理
if (StompCommand.CONNECT.equals(accessor.getCommand())) {
    accessor.setUser(() -> sessionId);  // ← 关键！
}

// 行100-109: 出站User设置
if (accessor.getUser() == null && destination.contains("/")) {
    String[] parts = destination.split("/");
    if (parts.length > 2) {
        String sessionId = parts[2];
        accessor.setUser(() -> sessionId);  // ← 关键！
    }
}
```

---

### 3️⃣ 后端: WebSocketService.java

#### ✅ 核心数据结构（不能删除或改签名）

```java
// ❌ 线程安全的客户端存储，不能改为普通Map
private final Map<String, ClientInfo> clients 
    = new ConcurrentHashMap<>();

// ❌ 会话到客户端的映射，必须保留
private final Map<String, String> sessionIdToClientId 
    = new ConcurrentHashMap<>();

// ❌ 当前控制端ID，不能删除
private String currentControlClientId = null;

// ❌ 控制端心跳时间戳
private long lastControlHeartbeat = 0;
```

#### ✅ 关键方法（不能改签名）

```java
// ❌ 签名不能改
public synchronized void registerClient(
    String clientId, String clientType, String clientName)

public synchronized void registerSessionIdMapping(
    String sessionId, String clientId)

public String getClientIdBySessionId(String sessionId)

public synchronized void unregisterClient(String clientId)

public void updateHeartbeat(String clientId)

public boolean isControlClient(String clientId)

public String getCurrentControlClientId()

public synchronized boolean setControlClient(String clientId)

// ❌ ClientInfo类不能改
public static class ClientInfo {
    String clientId;
    String clientType;
    String clientName;
    LocalDateTime registeredAt;    // ❌ 注册时不能改，之后也不能改
    LocalDateTime lastHeartbeat;   // ⚠️ 可更新
    String status;
}
```

#### 🔴 禁止修改的逻辑

```java
// 行80-92: 防止重复注册 - ❌ 逻辑不能改
if (existingClient != null) {
    // 只更新，不创建新对象
    return;  // ← 必须early return
}

// 行106-110: 控制端选择 - ❌ 逻辑不能改
if ("control".equals(clientType) && currentControlClientId == null) {
    currentControlClientId = clientId;
    lastControlHeartbeat = System.currentTimeMillis();
}

// 行146-147: sessionId清理 - ❌ 不能改
sessionIdToClientId.values().removeIf(value -> value.equals(clientId));

// 行149-151: 控制端重选 - ❌ 不能改
if (clientId.equals(currentControlClientId)) {
    selectNewControlClient();
}
```

---

### 4️⃣ 后端: EnhancedWebSocketController.java

#### ✅ 关键端点（不能改路由）

```java
// ❌ 路由不能改
@SubscribeMapping("/topic/timer-state")
public TimerStateDTO handleSubscribe()

@MessageMapping("/register")
public void registerClient(
    Map<String, Object> payload, 
    org.springframework.messaging.Message<?> message)

// 其他端点...
@MessageMapping("/timer/start")    // ❌ /timer前缀不能改
@MessageMapping("/timer/pause")    // ❌ /timer前缀不能改
// ... 等等
```

#### 🔴 禁止修改的注册逻辑

```java
// 行66-68: 参数验证 - ❌ 不能删除
String clientType = validateAndGetString(payload, "clientType", "control");
String clientName = validateAndGetString(payload, "clientName", "unknown");

// 行76-85: sessionId和clientType验证 - ❌ 不能删除
if (sessionId == null || sessionId.isEmpty() || sessionId.length() > 256) {
    return;
}
if (!isValidClientType(clientType)) {
    return;
}

// 行88-99: sessionId映射检查 - ❌ 逻辑不能改
String existingClientId = webSocketService.getClientIdBySessionId(sessionId);
if (existingClientId != null) {
    clientId = existingClientId;  // 复用现有ID
} else {
    clientId = UUID.randomUUID().toString();  // 生成新ID
}

// 行103-127: 重复注册防护 - ❌ 逻辑不能改
boolean alreadyRegistered = false;
if (existingClientId != null) {
    var existingClient = webSocketService.getClientInfo(existingClientId);
    alreadyRegistered = (existingClient != null);
    if (alreadyRegistered) {
        webSocketService.updateHeartbeat(existingClientId);
    }
}
```

---

## 连接状态流程

### 完整的连接生命周期

```
┌──────────────────────────────────────────────────────────────┐
│                 初始状态                                      │
│  isConnected: false                                          │
│  isRegistered: false                                         │
│  clientId: null                                              │
└──────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  Step 1: initGlobalWebSocket()                               │
│  ├─ new SockJS('/ws-archery-timer')                          │
│  ├─ new Client({ webSocketFactory, ...})                    │
│  ├─ client.activate()                                       │
│  └─ 监听onConnect回调                                        │
└──────────────────────────────────────────────────────────────┘
                          ↓
                   SockJS连接成功
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  Step 2: onConnect() 触发                                    │
│  ├─ globalConnectionState.isConnected = true  ← ✅ 更新     │
│  ├─ subscribeToAllTopics()                                  │
│  │   ├─ subscribe('/user/queue/messages')                   │
│  │   ├─ subscribe('/topic/timer-state')                    │
│  │   ├─ subscribe('/topic/clients')                        │
│  │   ├─ subscribe('/topic/debug')                          │
│  │   └─ 延迟100ms后发送 /app/register                       │
│  └─ broadcastMessage('connected')                           │
└──────────────────────────────────────────────────────────────┘
                          ↓
              100ms延迟 (/app/register)
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  Step 3: 后端收到 /app/register                              │
│  ├─ EnhancedWebSocketController.registerClient()            │
│  ├─ 验证clientType和sessionId                               │
│  ├─ WebSocketService.registerSessionIdMapping(sessionId)    │
│  ├─ WebSocketService.registerClient(clientId)               │
│  ├─ 发送响应到 /user/{sessionId}/queue/messages             │
│  └─ 类型: 'client_registered'                               │
└──────────────────────────────────────────────────────────────┘
                          ↓
         后端消息通过/user队列路由到前端
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  Step 4: 前端收到注册响应                                    │
│  ├─ /user/queue/messages 消息处理器                         │
│  ├─ data.type === 'client_registered'                      │
│  ├─ globalConnectionState.clientId = data.data.clientId    │
│  ├─ globalConnectionState.isRegistered = true  ← ✅ 更新   │
│  ├─ broadcastMessage('registered', data.data)              │
│  └─ 所有订阅者收到'registered'事件                          │
└──────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  已连接、已注册状态 ✅✅                                      │
│  isConnected: true                                           │
│  isRegistered: true                                          │
│  clientId: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'            │
│  可以发送/接收消息                                           │
└──────────────────────────────────────────────────────────────┘
```

### 意外断开场景

```
┌──────────────────────────────────────────────────────────────┐
│  WebSocket连接断开                                           │
│  (网络中断、服务器重启、浏览器关闭等)                         │
└──────────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  onDisconnect() 触发                                         │
│  ├─ globalConnectionState.isConnected = false ← 更新        │
│  ├─ globalConnectionState.isRegistered = false              │
│  ├─ broadcastMessage('disconnected')                        │
│  ├─ reconnectAttempts = 0                                  │
│  ├─ exponentialBackoffMultiplier = 1                        │
│  └─ 开始快速重连(第1次重连)                                 │
└──────────────────────────────────────────────────────────────┘
                          ↓
           延迟 3000ms * 1 = 3秒后
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  快速重连 (Attempt 1/5)                                      │
│  ├─ reconnectAttempts = 1                                  │
│  ├─ exponentialBackoffMultiplier = 2                        │
│  ├─ initGlobalWebSocket()  ← 重新连接                       │
│  └─ 重复Step 1-4                                           │
└──────────────────────────────────────────────────────────────┘
                          ↓
           延迟 3000ms * 2 = 6秒后
                          ↓
│  快速重连 (Attempt 2/5)
│  ├─ 延迟 6秒
│  └─ ...
│
│  快速重连 (Attempt 3/5)
│  ├─ 延迟 12秒
│  └─ ...
│
│  快速重连 (Attempt 4/5)
│  ├─ 延迟 24秒
│  └─ ...
│
│  快速重连 (Attempt 5/5)
│  ├─ 延迟 30秒 (min达到maxBackoffDelay)
│  └─ ...
                          ↓
     如果快速重连5次都失败
                          ↓
┌──────────────────────────────────────────────────────────────┐
│  启用定期重连机制                                            │
│  ├─ 每30秒尝试一次重连                                      │
│  ├─ 重置计数器: reconnectAttempts = 0                      │
│  └─ 重新开始快速重连阶段                                    │
└──────────────────────────────────────────────────────────────┘
```

---

## 误修改恢复指南

### 场景 1️⃣: 删除了 globalConnectionState

**❌ 错误代码**:
```javascript
// 错误：删除了状态对象
// export const globalConnectionState = reactive({...})
```

**✅ 恢复代码**:
```javascript
export const globalConnectionState = reactive({
  isConnected: false,
  isRegistered: false,
  clientId: null,
  clientType: 'control',
  registerTime: null
})
```

**验证方法**:
```bash
grep -n "globalConnectionState" frontend/src/services/globalWebSocketService.js
# 应该能找到: 行15-21
```

---

### 场景 2️⃣: 修改了消息回调集合为数组

**❌ 错误代码**:
```javascript
// 错误：改成数组
const messageCallbacks = []  // ← 错误！Set才能防重复注册

export function onGlobalWebSocketMessage(callback) {
  messageCallbacks.push(callback)  // 会导致重复注册
  // ...
}
```

**✅ 恢复代码**:
```javascript
// 必须是Set
const messageCallbacks = new Set()

export function onGlobalWebSocketMessage(callback) {
  // ✅ 防护：检查回调集合是否已满
  if (messageCallbacks.size >= maxMessageCallbacks) {
    logService.warn(`⚠️ 消息回调集合已达到上限(${maxMessageCallbacks})...`)
    return () => {}
  }

  messageCallbacks.add(callback)  // ← Set.add()
  logService.debug(`📍 消息回调已注册，当前数量: ${messageCallbacks.size}`)

  return () => {
    const deleted = messageCallbacks.delete(callback)
    if (deleted) {
      logService.debug(`📍 消息回调已卸载，当前数量: ${messageCallbacks.size}`)
    }
  }
}
```

**验证方法**:
```javascript
// 在浏览器控制台测试
console.log(globalConnectionState)  // 检查是否是Set
```

---

### 场景 3️⃣: 删除了ChannelInterceptor入站拦截

**❌ 错误代码**:
```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    // 错误：完全删除了拦截器
    // registration.interceptors(...)
}
```

**❌ 后果**:
- /user/queue/messages 消息无法路由到具体客户端
- 所有客户端都收不到个人消息
- clientId注册消息无法送达
- 系统陷入"已连接但未注册"状态

**✅ 恢复代码**:
```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                String destination = accessor.getDestination();
                String sessionId = accessor.getSessionId();

                log.debug("📡 [入站] SUBSCRIBE 命令 - sessionId: {}, destination: {}",
                         sessionId, destination);

                // ✅ 关键：设置User Principal，用于/user队列路由
                if (destination != null && destination.contains("/user/")
                    && destination.contains("/queue")) {
                    accessor.setUser(() -> sessionId);
                    log.info("✅ [入站] 用户队列订阅拦截处理 - sessionId: {}, destination: {}",
                             sessionId, destination);
                }

                if (destination != null && destination.startsWith("/topic/")) {
                    log.debug("📡 [入站] 主题订阅 - sessionId: {}, destination: {}",
                             sessionId, destination);
                }
            }

            // ✅ 连接命令时也设置 Principal
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String sessionId = accessor.getSessionId();
                log.debug("🔗 [入站] CONNECT 命令 - sessionId: {}", sessionId);
                accessor.setUser(() -> sessionId);
            }

            return message;
        }
    });
}
```

**验证方法**:
```bash
# 启动后端，查看日志是否有"[入站] SUBSCRIBE"输出
grep "\[入站\]" /path/to/backend.log
# 应该能找到多条[入站]日志
```

---

### 场景 4️⃣: 删除了sessionIdToClientId映射

**❌ 错误代码**:
```java
// 错误：删除了这个映射
// private final Map<String, String> sessionIdToClientId = new ConcurrentHashMap<>();
```

**❌ 后果**:
- 无法追踪sessionId到clientId的对应关系
- 断开连接时无法清理对应的sessionId映射
- 多次连接同一个sessionId会创建多个ClientInfo对象
- 内存泄漏

**✅ 恢复代码**:
```java
private final Map<String, String> sessionIdToClientId = new ConcurrentHashMap<>();

// 添加这些方法
public synchronized void registerSessionIdMapping(String sessionId, String clientId) {
    sessionIdToClientId.put(sessionId, clientId);
    log.debug("映射会话到客户端 - 会话ID: {}, 客户端ID: {}", sessionId, clientId);
}

public String getClientIdBySessionId(String sessionId) {
    return sessionIdToClientId.get(sessionId);
}

// 在unregisterClient中清理
public synchronized void unregisterClient(String clientId) {
    ClientInfo client = clients.remove(clientId);
    if (client != null) {
        // ✅ 清理sessionId映射
        sessionIdToClientId.values().removeIf(value -> value.equals(clientId));
        // ...
    }
}
```

**验证方法**:
```bash
# 查看WebSocketService.java中是否有sessionIdToClientId
grep "sessionIdToClientId" backend/src/main/java/com/archery/timer/service/WebSocketService.java
# 应该能找到该字段的定义
```

---

### 场景 5️⃣: 修改了重连逻辑

**❌ 错误代码**:
```javascript
// 错误：修改了onDisconnect处理
onDisconnect: () => {
    globalConnectionState.isConnected = false
    // 错误：没有启用重连
    // 应该有重连逻辑，但被删除了
},
```

**✅ 恢复代码**:
```javascript
onDisconnect: () => {
    logService.event('WEBSOCKET_DISCONNECTED', { reconnectAttempts })
    globalConnectionState.isConnected = false
    globalConnectionState.isRegistered = false
    broadcastMessage('disconnected', null)
    stopHeartbeat()

    // ✅ 快速重连阶段（前5次）
    if (reconnectAttempts < maxReconnectAttempts) {
        reconnectAttempts++

        // ✅ 使用指数退避算法
        const backoffDelay = Math.min(
            reconnectDelay * exponentialBackoffMultiplier,
            maxBackoffDelay
        )
        exponentialBackoffMultiplier *= 2

        logService.warn(`尝试快速重连 (${reconnectAttempts}/${maxReconnectAttempts})，延迟${backoffDelay}ms`)
        setTimeout(() => {
            initGlobalWebSocket()
        }, backoffDelay)
    } else {
        // ✅ 快速重连失败，启用定期重连
        logService.warn('已达快速重连上限(5次)，启用定期重连机制(每30秒重试一次)')

        if (!periodicReconnectInterval) {
            periodicReconnectInterval = setInterval(() => {
                logService.info('执行定期重连尝试...')
                reconnectAttempts = 0
                exponentialBackoffMultiplier = 1
                clearInterval(periodicReconnectInterval)
                periodicReconnectInterval = null
                initGlobalWebSocket()
            }, 30000)

            logService.info('定期重连机制已启用，每30秒尝试一次')
        }
    }
},
```

**验证方法**:
```javascript
// 在浏览器控制台监控断开和重连
globalConnectionState  // 应该是 isConnected: false
// 等待应该能看到日志"尝试快速重连"
```

---

## 不可变代码保护

### 📌 红线代码 (绝对不能改)

| 代码位置 | 代码片段 | 原因 |
|---------|--------|------|
| globalWebSocketService.js L15-21 | globalConnectionState定义 | 所有UI依赖这个状态对象 |
| globalWebSocketService.js L24 | messageCallbacks = new Set() | Set才能防重复回调 |
| globalWebSocketService.js L41-56 | onGlobalWebSocketMessage回调注册 | 所有消息分发机制的基础 |
| globalWebSocketService.js L119-252 | subscribeToAllTopics()函数 | 订阅4个关键主题 |
| globalWebSocketService.js L330-396 | STOMP客户端配置 | onConnect/onDisconnect生命周期 |
| WebSocketConfig.java L41-78 | 入站拦截器 | 用户队列路由的关键 |
| WebSocketConfig.java L84-126 | 出站拦截器 | 确保出站消息有User信息 |
| WebSocketService.java L28-33 | 客户端数据结构 | ConcurrentHashMap + sessionId映射 |
| WebSocketService.java L80-92 | 防重复注册逻辑 | 防止多个ClientInfo对象 |
| EnhancedWebSocketController.java L88-99 | sessionId映射检查 | 复用现有clientId |

### 🟡 黄线代码 (可改但有风险)

| 代码位置 | 参数 | 建议值 | 改动影响 |
|---------|------|-------|--------|
| globalWebSocketService.js L26 | maxReconnectAttempts | 3-10 | 快速重连尝试次数 |
| globalWebSocketService.js L27 | reconnectDelay | 1000-10000ms | 基础重连延迟 |
| globalWebSocketService.js L29 | maxBackoffDelay | 10000-60000ms | 最大退避延迟 |
| globalWebSocketService.js L333 | heartbeatIncoming | 10000-30000ms | 服务器发心跳超时 |
| globalWebSocketService.js L334 | heartbeatOutgoing | 10000-30000ms | 客户端发心跳频率 |
| WebSocketConfig.java L134 | setAllowedOriginPatterns | 改为具体域名 | 仅在生产环境 |

### ✅ 绿线代码 (可以改)

- 日志级别 (debug/info/warn/error)
- 日志信息内容
- 代码注释
- 变量名 (只要不改数据结构)

---

## 常见问题排查

### 问题 1️⃣: 页面显示"WebSocket: 已连接"，但"isRegistered: false"

**症状**:
```
isConnected: true
isRegistered: false
clientId: null
```

**原因排查**:
1. ❌ 订阅 `/user/queue/messages` 失败
2. ❌ 后端拦截器未设置User Principal
3. ❌ sessionId为null

**诊断步骤**:
```bash
# 1. 后端日志检查
grep "📡 \[入站\]" backend.log | grep SUBSCRIBE
# 应该看到: "[入站] SUBSCRIBE 命令 - sessionId: xxx, destination: /user/queue/messages"

# 2. 前端日志检查
# F12 → Console，搜索"个人队列"
# 应该看到: "✅ 个人队列订阅已创建"

# 3. 注册请求检查
grep "📨 收到客户端注册请求" backend.log
# 应该看到该日志

# 4. 注册响应检查
grep "📤 发送注册成功响应" backend.log
# 应该看到该日志
```

**恢复方法**:
1. 检查 WebSocketConfig.java 中的入站拦截器是否存在
2. 检查 line 54-60 是否有 `accessor.setUser(() -> sessionId)`
3. 确认后端已重新启动

---

### 问题 2️⃣: 页面显示"WebSocket 连接不上"

**症状**:
```
isConnected: false
isRegistered: false
clientId: null
```

**原因排查**:
1. ❌ 后端未启动或监听端口错误
2. ❌ Vite 代理配置错误
3. ❌ STOMP 端点配置错误
4. ❌ WebSocket URL错误

**诊断步骤**:
```bash
# 1. 后端检查
netstat -an | grep 8080
# 应该看到: LISTENING ... :8080

# 2. 检查 Vite 代理
cat vite.config.js | grep -A5 "ws-archery-timer"
# 应该看到: '^/ws-archery-timer(/.*)?$'

# 3. WebSocket 连接检查
# F12 → Network → WS 过滤
# 应该看到: 
#   ws://localhost:3000/ws-archery-timer/...
#   Status: 101 Switching Protocols

# 4. 检查前端URL
grep "const wsUrl" frontend/src/services/globalWebSocketService.js
# 应该看到: "/ws-archery-timer"
```

**恢复方法**:
1. 确认后端启动: `mvn spring-boot:run`
2. 确认前端启动: `npm run dev`
3. 查看浏览器控制台错误
4. 检查网络请求状态

---

### 问题 3️⃣: 接收不到计时器状态更新

**症状**:
```
isConnected: true
isRegistered: true
clientId: 'xxx'
# 但屏幕上的倒计时不更新
```

**原因排查**:
1. ❌ 没有订阅 `/topic/timer-state`
2. ❌ enhancedTimer.js 中的消息处理器未注册
3. ❌ 后端未发送状态更新

**诊断步骤**:
```bash
# 1. 前端日志
grep "📡 收到 /topic/timer-state" browser-console.log
# 应该看到频繁的消息

# 2. 后端日志
grep "广播计时器状态" backend.log | head -10
# 应该看到计时器状态广播日志

# 3. 检查enhancedTimer.js
grep "subscribeToTopic.*timer-state" frontend/src/stores/enhancedTimer.js
# 应该找到订阅代码
```

**恢复方法**:
1. 确认 globalWebSocketService.js L193 有订阅 `/topic/timer-state`
2. 确认 enhancedTimer.js 有监听器
3. 检查消息回调是否超过上限(100)

---

### 问题 4️⃣: WebSocket 频繁断开重连

**症状**:
```
Console: 尝试快速重连 (1/5)
Console: 尝试快速重连 (2/5)
# 不断重复
```

**原因排查**:
1. ❌ 网络不稳定
2. ❌ 后端心跳超时设置过短
3. ❌ 客户端心跳发送间隔过长

**诊断步骤**:
```bash
# 1. 网络状态检查
# F12 → Network → WS → 监控连接
# 检查是否有频繁的"断开"

# 2. 心跳配置检查
grep "heartbeat" frontend/src/services/globalWebSocketService.js
# 应该看到:
#   heartbeatIncoming: 15000
#   heartbeatOutgoing: 15000

# 3. 后端控制端心跳
grep "checkControlHeartbeat" backend.log
```

**恢复方法**:
1. 检查网络稳定性
2. 调整心跳参数到 15000ms
3. 检查后端 controlHeartbeatTimeout 是否过小

---

### 问题 5️⃣: 某些客户端收不到消息

**症状**:
```
# 控制端可以收到消息
# 但显示端A或B收不到
```

**原因排查**:
1. ❌ clientType 注册错误
2. ❌ clientId为null
3. ❌ /user队列路由失败

**诊断步骤**:
```bash
# 1. 检查客户端注册情况
# F12 → Console，搜索"客户端注册成功"
# 检查clientId是否为UUID格式

# 2. 后端查看客户端列表
grep "新客户端注册成功" backend.log
# 应该看到三个不同的clientId和clientType

# 3. 检查消息路由
grep "用户队列消息 - destination:" backend.log
# 应该看到消息被正确路由

# 4. 清理浏览器缓存
# F12 → Application → Clear Site Data
# 重新刷新
```

**恢复方法**:
1. 确保不同浏览器标签页注册时clientType正确
2. 查看 globalConnectionState.clientType 是否为 'control', 'display_a', 或 'display_b'
3. 重新启动后端和前端

---

## 总结

### 🔐 必须保护的三大件

1. **前端状态对象** `globalConnectionState`
   - 所有UI组件依赖这个响应式对象
   - 任何删除都会导致系统无法工作

2. **后端拦截器** ChannelInterceptor
   - 入站拦截: 设置User Principal用于用户队列路由
   - 出站拦截: 确保消息有正确的用户信息
   - 删除会导致消息无法送达

3. **客户端-会话映射** sessionIdToClientId
   - 追踪哪个会话对应哪个客户端
   - 删除会导致内存泄漏和多重注册

### ✅ 验证清单

修改WebSocket代码后，必须验证:
- [ ] 前端可以连接到后端
- [ ] 页面显示"WebSocket: 已连接"
- [ ] globalConnectionState.isConnected === true
- [ ] 接收到注册成功消息
- [ ] globalConnectionState.isRegistered === true
- [ ] globalConnectionState.clientId 不为null
- [ ] 接收计时器状态更新
- [ ] 倒计时正常显示和运行

