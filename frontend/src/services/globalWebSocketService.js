/**
 * 全局单例 WebSocket 服务
 * 所有页面共享同一个 WebSocket 连接
 */

import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import { reactive } from 'vue'
import { logService } from './logService'

// 全局 STOMP 客户端
let globalStompClient = null

// 全局连接状态
export const globalConnectionState = reactive({
  isConnected: false,
  isRegistered: false,
  clientId: null,
  clientType: 'control',
  registerTime: null
})

// 消息回调函数集合
const messageCallbacks = new Set()
let reconnectAttempts = 0
const maxReconnectAttempts = 5
const reconnectDelay = 3000
let heartbeatInterval = null

/**
 * 注册消息回调
 * @param {Function} callback - 回调函数，接收 (type, data) 两个参数
 * @returns {Function} 注销函数
 */
export function onGlobalWebSocketMessage(callback) {
  messageCallbacks.add(callback)

  // 返回注销函数
  return () => {
    messageCallbacks.delete(callback)
  }
}

/**
 * 分发消息到所有回调
 */
function broadcastMessage(type, data) {
  logService.event('BROADCAST_MESSAGE', { type, callbackCount: messageCallbacks.size })
  messageCallbacks.forEach(callback => {
    try {
      callback(type, data)
    } catch (error) {
      logService.error('消息回调执行失败', { type, error: error.message })
    }
  })
}

/**
 * 订阅所有主题
 */
function subscribeToAllTopics() {
  if (!globalStompClient || !globalStompClient.connected) {
    logService.warn('STOMP 客户端未连接，无法订阅主题')
    return
  }

  logService.info('开始订阅所有主题')

  // 订阅计时器状态（广播）
  globalStompClient.subscribe('/topic/timer-state', (message) => {
    try {
      const data = JSON.parse(message.body)
      logService.debug('收到计时器状态', { stageIndex: data.data?.currentStageIndex })
      broadcastMessage('timer_state', data.data || data)
    } catch (error) {
      logService.error('解析计时器状态失败', { error: error.message })
    }
  })

  // 订阅个人队列消息
  const userQueuePath = '/user/queue/messages'
  globalStompClient.subscribe(userQueuePath, (message) => {
    try {
      const data = JSON.parse(message.body)
      logService.debug('收到个人队列消息', { type: data.type })

      // 处理不同类型的消息
      switch (data.type) {
        case 'client_registered':
          logService.event('CLIENT_REGISTERED', data.data)
          broadcastMessage('registered', data.data)
          break
        case 'timer_state':
          broadcastMessage('timer_state', data.data)
          break
        case 'error':
          logService.error('服务器返回错误', data.data)
          broadcastMessage('error', data.data)
          break
        default:
          logService.debug('收到消息', { type: data.type })
          broadcastMessage(data.type, data.data)
      }
    } catch (error) {
      logService.error('解析个人队列消息失败', { error: error.message })
    }
  })

  // 订阅客户端状态
  globalStompClient.subscribe('/topic/clients', (message) => {
    try {
      const data = JSON.parse(message.body)
      logService.debug('收到客户端状态', { clientCount: data?.clients?.length })
      broadcastMessage('client_status', data)
    } catch (error) {
      logService.error('解析客户端状态失败', { error: error.message })
    }
  })

  logService.info('所有主题订阅完成', { topicsCount: 3 })
}

/**
 * 启动心跳
 */
function startHeartbeat() {
  stopHeartbeat()

  heartbeatInterval = setInterval(() => {
    if (globalStompClient && globalStompClient.connected) {
      try {
        globalStompClient.publish({
          destination: '/app/heartbeat',
          body: JSON.stringify({ timestamp: Date.now() })
        })
        console.log('💓 发送心跳')
      } catch (error) {
        console.error('发送心跳失败:', error)
      }
    }
  }, 10000) // 10秒发送一次
}

/**
 * 停止心跳
 */
function stopHeartbeat() {
  if (heartbeatInterval) {
    clearInterval(heartbeatInterval)
    heartbeatInterval = null
  }
}

/**
 * 初始化全局 WebSocket 连接
 */
export function initGlobalWebSocket() {
  if (globalStompClient && globalStompClient.connected) {
    logService.info('WebSocket 已连接，跳过重复初始化')
    return
  }

  logService.info('初始化全局 WebSocket 连接')

  const socket = new SockJS('/ws-archery-timer')
  const client = new Client({
    webSocketFactory: () => socket,
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    onConnect: () => {
      logService.event('WEBSOCKET_CONNECTED', { reconnectAttempts })
      reconnectAttempts = 0
      globalConnectionState.isConnected = true
      broadcastMessage('connected', null)

      // 订阅所有主题
      subscribeToAllTopics()

      // 启动心跳
      startHeartbeat()

      logService.info('WebSocket 连接就绪，开始心跳和广播')
      broadcastMessage('connection_ready', null)
    },
    onDisconnect: () => {
      logService.event('WEBSOCKET_DISCONNECTED', { reconnectAttempts })
      globalConnectionState.isConnected = false
      globalConnectionState.isRegistered = false
      broadcastMessage('disconnected', null)
      stopHeartbeat()

      // 尝试重连
      if (reconnectAttempts < maxReconnectAttempts) {
        reconnectAttempts++
        logService.warn(`尝试重连 (${reconnectAttempts}/${maxReconnectAttempts})`)
        setTimeout(() => {
          initGlobalWebSocket()
        }, reconnectDelay)
      } else {
        logService.error('达到最大重连次数，放弃连接', { maxAttempts: maxReconnectAttempts })
      }
    },
    onStompError: (error) => {
      logService.error('WebSocket 错误', { message: error.message })
      broadcastMessage('error', error)
    }
  })

  globalStompClient = client
  logService.debug('激活 STOMP 客户端')
  client.activate()
}

/**
 * 断开连接
 */
export function disconnectGlobalWebSocket() {
  if (globalStompClient) {
    console.log('断开 WebSocket 连接...')
    stopHeartbeat()
    globalStompClient.deactivate()
    globalStompClient = null
    globalConnectionState.isConnected = false
    globalConnectionState.isRegistered = false
  }
}

/**
 * 获取全局 STOMP 客户端
 */
export function getGlobalStompClient() {
  return globalStompClient
}

/**
 * 注册客户端
 */
export function registerGlobalClient(clientType) {
  if (!globalStompClient || !globalStompClient.connected) {
    logService.warn('WebSocket 未连接，无法注册客户端', { clientType })
    return false
  }

  const clientName = getClientName(clientType)
  logService.event('CLIENT_REGISTER_ATTEMPT', { clientType, clientName })

  try {
    globalStompClient.publish({
      destination: '/app/register',
      body: JSON.stringify({
        clientType,
        clientName
      })
    })
    globalConnectionState.clientType = clientType
    logService.info('客户端注册消息已发送', { clientType })
    return true
  } catch (error) {
    logService.error('注册客户端失败', { clientType, error: error.message })
    return false
  }
}

/**
 * 获取客户端名称
 */
function getClientName(clientType) {
  switch (clientType) {
    case 'display_a':
      return 'A屏显示端'
    case 'display_b':
      return 'B屏显示端'
    default:
      return '控制端'
  }
}

/**
 * 根据路由获取客户端类型
 */
export function getClientTypeFromRoute() {
  const path = window.location.pathname
  if (path.includes('display-a')) return 'display_a'
  if (path.includes('display-b')) return 'display_b'
  return 'control'
}

/**
 * 发送消息
 */
export function sendGlobalWebSocketMessage(destination, message) {
  if (!globalStompClient || !globalStompClient.connected) {
    console.warn('⚠️ WebSocket 未连接，无法发送消息')
    return false
  }

  try {
    // 确保 destination 以 /app/ 开头
    const finalDestination = destination.startsWith('/app/')
      ? destination
      : `/app/${destination}`

    globalStompClient.publish({
      destination: finalDestination,
      body: JSON.stringify(message)
    })
    console.log(`✅ 发送消息到 ${finalDestination}:`, message)
    return true
  } catch (error) {
    console.error('发送消息失败:', error)
    return false
  }
}

/**
 * 订阅自定义主题
 */
export function subscribeToTopic(topic, callback) {
  if (!globalStompClient || !globalStompClient.connected) {
    console.warn('⚠️ WebSocket 未连接，无法订阅主题')
    return null
  }

  return globalStompClient.subscribe(topic, (message) => {
    if (message.body) {
      try {
        const data = JSON.parse(message.body)
        callback(data)
      } catch (error) {
        console.error('解析消息失败:', error)
      }
    }
  })
}
