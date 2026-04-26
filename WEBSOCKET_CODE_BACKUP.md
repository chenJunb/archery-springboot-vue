# WebSocket关键代码备份 - 用于恢复

## 文件1: globalWebSocketService.js - 关键部分备份

```javascript
// ============ 核心初始化 ============
let globalStompClient = null

export const globalConnectionState = reactive({
  isConnected: false,
  isRegistered: false,
  clientId: null,
  clientType: 'control',
  registerTime: null
})

// ============ 重连配置 ============
const messageCallbacks = new Set()
let reconnectAttempts = 0
const maxReconnectAttempts = 5
const reconnectDelay = 3000
let exponentialBackoffMultiplier = 1
const maxBackoffDelay = 30000
let periodicReconnectInterval = null
const maxMessageCallbacks = 100

// ============ 订阅状态 ============
let userQueueSubscribed = false
let userQueueSubscription = null

// ============ 消息回调注册 ============
export function onGlobalWebSocketMessage(callback) {
  if (messageCallbacks.size >= maxMessageCallbacks) {
    logService.warn(`⚠️ 消息回调集合已达到上限(${maxMessageCallbacks})...`)
    return () => {}
  }

  messageCallbacks.add(callback)
  logService.debug(`📍 消息回调已注册，当前数量: ${messageCallbacks.size}`)

  return () => {
    const deleted = messageCallbacks.delete(callback)
    if (deleted) {
      logService.debug(`📍 消息回调已卸载，当前数量: ${messageCallbacks.size}`)
    }
  }
}

// ============ 消息分发 ============
function broadcastMessage(type, data) {
  if (messageCallbacks.size === 0) {
    return
  }

  logService.event('BROADCAST_MESSAGE', { type, callbackCount: messageCallbacks.size })

  let failedCount = 0
  messageCallbacks.forEach((callback, index) => {
    try {
      callback(type, data)
    } catch (error) {
      failedCount++
      logService.error('消息回调执行失败', {
        type,
        callbackIndex: index,
        error: error.message
      })
    }
  })

  if (failedCount > messageCallbacks.size * 0.5) {
    logService.warn(`⚠️ 消息回调失败率过高: ${failedCount}/${messageCallbacks.size}`)
  }
}

// ============ 订阅所有主题 ============
function subscribeToAllTopics() {
  if (!globalStompClient || !globalStompClient.connected) {
    logService.warn('STOMP 客户端未连接，无法订阅主题')
    return
  }

  logService.info('开始订阅所有主题')

  userQueueSubscribed = false
  userQueueSubscription = null

  // 订阅个人队列
  const userQueuePath = '/user/queue/messages'
  logService.info('📡 订阅个人队列:', { path: userQueuePath })

  try {
    userQueueSubscription = globalStompClient.subscribe(userQueuePath, (message) => {
      try {
        logService.debug('📥 收到个人队列消息')
        const data = JSON.parse(message.body)

        if (!userQueueSubscribed) {
          userQueueSubscribed = true
          logService.info('✅ 个人队列订阅已确认')
        }

        switch (data.type) {
          case 'client_registered':
          case 'client_registered_debug':
            logService.info('✅ 收到注册成功消息')
            if (data.data?.success && data.data?.clientId) {
              globalConnectionState.clientId = data.data.clientId
              globalConnectionState.isRegistered = true
            } else if (data.clientId) {
              globalConnectionState.clientId = data.clientId
              globalConnectionState.isRegistered = true
            }
            broadcastMessage('registered', data.data || data)
            break
          case 'timer_state':
            broadcastMessage('timer_state', data.data)
            break
          case 'error':
            logService.error('服务器返回错误', data.data)
            broadcastMessage('error', data.data)
            break
          default:
            broadcastMessage(data.type, data.data)
        }
      } catch (error) {
        logService.error('解析个人队列消息失败', { error: error.message })
      }
    })
    logService.info('✅ 个人队列订阅已创建')
  } catch (error) {
    logService.error('❌ 订阅个人队列失败', { error: error.message })
  }

  // 订阅计时器状态
  globalStompClient.subscribe('/topic/timer-state', (message) => {
    try {
      const data = JSON.parse(message.body)
      logService.debug('📡 收到 /topic/timer-state 广播消息')
      broadcastMessage('timer_state', data.data || data)
    } catch (error) {
      logService.error('解析计时器状态失败', { error: error.message })
    }
  })

  // 订阅客户端状态
  globalStompClient.subscribe('/topic/clients', (message) => {
    try {
      const data = JSON.parse(message.body)
      broadcastMessage('client_status', data)
    } catch (error) {
      logService.error('解析客户端状态失败', { error: error.message })
    }
  })

  // 订阅调试主题
  globalStompClient.subscribe('/topic/debug', (message) => {
    try {
      const data = JSON.parse(message.body)
      logService.debug('🔧 收到调试消息')
    } catch (error) {
      logService.error('解析调试消息失败', { error: error.message })
    }
  })

  logService.info('所有主题订阅完成')

  // 延迟后发送注册请求
  setTimeout(() => {
    if (globalStompClient && globalStompClient.connected) {
      logService.debug('📤 订阅已生效，现在发送注册请求')
      const clientType = getClientTypeFromRoute()
      const clientName = getClientName(clientType)

      try {
        globalStompClient.publish({
          destination: '/app/register',
          body: JSON.stringify({
            clientType,
            clientName
          })
        })
        logService.info('✅ 客户端注册请求已发送', { clientType })
      } catch (error) {
        logService.error('发送注册请求失败', { error: error.message })
      }
    }
  }, 100)
}

// ============ 初始化WebSocket ============
export function initGlobalWebSocket() {
  if (globalStompClient && globalStompClient.connected) {
    logService.info('WebSocket 已连接，跳过重复初始化')
    return
  }

  const wsUrl = '/ws-archery-timer'
  const socket = new SockJS(wsUrl)

  socket.onopen = () => {
    logService.debug('📡 [WS] SockJS连接已建立')
  }
  socket.onclose = (event) => {
    logService.warn('📡 [WS] SockJS连接已关闭')
  }
  socket.onerror = (error) => {
    logService.error('📡 [WS] SockJS连接错误', { error: error?.message })
  }

  const client = new Client({
    webSocketFactory: () => socket,
    reconnectDelay: 5000,
    heartbeatIncoming: 15000,
    heartbeatOutgoing: 15000,
    heartbeatErrorMargin: 5000,
    onConnect: () => {
      logService.event('WEBSOCKET_CONNECTED', { reconnectAttempts })
      reconnectAttempts = 0
      globalConnectionState.isConnected = true
      broadcastMessage('connected', null)

      subscribeToAllTopics()

      broadcastMessage('connection_ready', null)
    },
    onDisconnect: () => {
      logService.event('WEBSOCKET_DISCONNECTED', { reconnectAttempts })
      globalConnectionState.isConnected = false
      globalConnectionState.isRegistered = false
      broadcastMessage('disconnected', null)

      if (reconnectAttempts < maxReconnectAttempts) {
        reconnectAttempts++

        const backoffDelay = Math.min(
          reconnectDelay * exponentialBackoffMultiplier,
          maxBackoffDelay
        )
        exponentialBackoffMultiplier *= 2

        logService.warn(`尝试快速重连 (${reconnectAttempts}/${maxReconnectAttempts})`)
        setTimeout(() => {
          initGlobalWebSocket()
        }, backoffDelay)
      } else {
        logService.warn('已达快速重连上限，启用定期重连机制')

        if (!periodicReconnectInterval) {
          periodicReconnectInterval = setInterval(() => {
            logService.info('执行定期重连尝试...')
            reconnectAttempts = 0
            exponentialBackoffMultiplier = 1
            clearInterval(periodicReconnectInterval)
            periodicReconnectInterval = null
            initGlobalWebSocket()
          }, 30000)
        }
      }
    },
    onStompError: (error) => {
      logService.error('WebSocket 错误', { message: error.message })
      broadcastMessage('error', error)
    }
  })

  globalStompClient = client
  client.activate()
}

// ============ 断开连接 ============
export function disconnectGlobalWebSocket() {
  if (globalStompClient) {
    try {
      if (periodicReconnectInterval) {
        clearInterval(periodicReconnectInterval)
        periodicReconnectInterval = null
      }

      reconnectAttempts = 0
      exponentialBackoffMultiplier = 1

      globalStompClient.deactivate()

      globalStompClient = null
      globalConnectionState.isConnected = false
      globalConnectionState.isRegistered = false
      globalConnectionState.clientId = null
    } catch (error) {
      logService.error('断开WebSocket连接时发生错误', { error: error.message })
      globalStompClient = null
      globalConnectionState.isConnected = false
      globalConnectionState.isRegistered = false
    }
  }
}
```

---

## 文件2: WebSocketConfig.java - 关键部分备份

```java
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue", "/user");
        config.setUserDestinationPrefix("/user");
        config.setApplicationDestinationPrefixes("/app");
        log.info("✅ WebSocket MessageBroker 配置完成");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 入站拦截器 - 关键！
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    String sessionId = accessor.getSessionId();

                    log.debug("📡 [入站] SUBSCRIBE 命令 - sessionId: {}, destination: {}",
                             sessionId, destination);

                    // ✅ 关键：设置User Principal
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

                // ✅ CONNECT命令也要设置
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String sessionId = accessor.getSessionId();
                    log.debug("🔗 [入站] CONNECT 命令 - sessionId: {}", sessionId);
                    accessor.setUser(() -> sessionId);
                }

                return message;
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // 出站拦截器
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                String destination = accessor.getDestination();

                if (SimpMessageType.MESSAGE.equals(accessor.getMessageType())) {
                    if (destination != null && destination.startsWith("/user/")) {
                        log.debug("📤 [出站] 用户队列消息 - destination: {}", destination);

                        if (accessor.getUser() == null && destination.contains("/")) {
                            try {
                                String[] parts = destination.split("/");
                                if (parts.length > 2) {
                                    String sessionId = parts[2];
                                    accessor.setUser(() -> sessionId);
                                    log.info("🔧 [出站] 设置sessionId: {}", sessionId);
                                }
                            } catch (Exception e) {
                                log.debug("⚠️ [出站] 提取sessionId失败");
                            }
                        }
                    }

                    if (destination != null && destination.startsWith("/topic/")) {
                        log.debug("📤 [出站] 广播消息 - destination: {}", destination);
                    }
                }

                return message;
            }
        });
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-archery-timer")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        log.info("✅ WebSocket 端点配置完成 - /ws-archery-timer");
    }
}
```

---

## 文件3: WebSocketService.java - 关键部分备份

```java
@Service
@Slf4j
public class WebSocketService {

    // ✅ 线程安全的客户端存储
    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
    
    // ✅ sessionId到clientId的映射
    private final Map<String, String> sessionIdToClientId = new ConcurrentHashMap<>();
    
    private String currentControlClientId = null;
    private long lastControlHeartbeat = 0;

    // ✅ 注册客户端
    public synchronized void registerClient(String clientId, String clientType, String clientName) {
        ClientInfo existingClient = clients.get(clientId);
        if (existingClient != null) {
            log.info("客户端已注册，更新信息: {}", clientId);
            existingClient.setClientType(clientType);
            existingClient.setClientName(clientName);
            existingClient.setLastHeartbeat(LocalDateTime.now());
            existingClient.setStatus("connected");
            return;
        }

        ClientInfo client = new ClientInfo();
        client.setClientId(clientId);
        client.setClientType(clientType);
        client.setClientName(clientName);
        client.setRegisteredAt(LocalDateTime.now());
        client.setLastHeartbeat(LocalDateTime.now());
        client.setStatus("connected");

        clients.put(clientId, client);

        if ("control".equals(clientType) && currentControlClientId == null) {
            currentControlClientId = clientId;
            lastControlHeartbeat = System.currentTimeMillis();
        }

        log.info("新客户端注册成功 - ID: {}, 类型: {}, 名称: {}", clientId, clientType, clientName);
    }

    // ✅ 映射sessionId到clientId
    public synchronized void registerSessionIdMapping(String sessionId, String clientId) {
        sessionIdToClientId.put(sessionId, clientId);
        log.debug("映射会话到客户端 - 会话ID: {}, 客户端ID: {}", sessionId, clientId);
    }

    // ✅ 获取sessionId对应的clientId
    public String getClientIdBySessionId(String sessionId) {
        return sessionIdToClientId.get(sessionId);
    }

    // ✅ 注销客户端
    public synchronized void unregisterClient(String clientId) {
        ClientInfo client = clients.remove(clientId);
        if (client != null) {
            log.info("客户端注销 - ID: {}, 类型: {}", clientId, client.getClientType());

            // ✅ 清理sessionId映射
            sessionIdToClientId.values().removeIf(value -> value.equals(clientId));

            if (clientId.equals(currentControlClientId)) {
                selectNewControlClient();
            }
        }

        log.info("当前连接客户端数: {}", clients.size());
    }

    // ✅ 更新心跳
    public void updateHeartbeat(String clientId) {
        ClientInfo client = clients.get(clientId);
        if (client != null) {
            client.setLastHeartbeat(LocalDateTime.now());

            if (clientId.equals(currentControlClientId)) {
                lastControlHeartbeat = System.currentTimeMillis();
            }
        }
    }

    // ✅ 检查是否为控制端
    public boolean isControlClient(String clientId) {
        return clientId != null && clientId.equals(currentControlClientId);
    }

    // ✅ ClientInfo类
    @Getter
    @Setter
    public static class ClientInfo {
        private String clientId;
        private String clientType;
        private String clientName;
        private LocalDateTime registeredAt;  // ❌ 注册时不能改
        private LocalDateTime lastHeartbeat; // ⚠️ 可更新
        private String status;
    }
}
```

---

## 使用这个备份

如果WebSocket功能出现问题：

1. **对比代码**: 使用上面的代码与当前文件逐行对比
2. **查找差异**: 特别关注：
   - 是否删除了关键函数
   - 是否改动了函数签名
   - 是否删除了拦截器
   - 是否改动了数据结构
3. **恢复**: 用备份代码覆盖有问题的部分

---

**记住**: 如果完全修坏了，可以用这个备份完整恢复！
