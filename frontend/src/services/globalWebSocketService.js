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
let exponentialBackoffMultiplier = 1         // ✅ 新增：指数退避倍数
const maxBackoffDelay = 30000                // ✅ 新增：最大延迟30秒
let periodicReconnectInterval = null         // ✅ 新增：定期重连的间隔ID
let heartbeatInterval = null
const maxMessageCallbacks = 100              // ✅ 新增：回调集合最大数量限制

// 订阅状态跟踪
let userQueueSubscribed = false
let userQueueSubscription = null

/**
 * 注册消息回调
 * @param {Function} callback - 回调函数，接收 (type, data) 两个参数
 * @returns {Function} 注销函数
 */
export function onGlobalWebSocketMessage(callback) {
  // ✅ 防护：检查回调集合是否已满
  if (messageCallbacks.size >= maxMessageCallbacks) {
    logService.warn(`⚠️ 消息回调集合已达到上限(${maxMessageCallbacks})，忽略新回调注册`, {
      currentSize: messageCallbacks.size
    })
    return () => {}  // 返回空函数
  }

  messageCallbacks.add(callback)
  logService.debug(`📍 消息回调已注册，当前数量: ${messageCallbacks.size}`)

  // 返回注销函数
  return () => {
    const deleted = messageCallbacks.delete(callback)
    if (deleted) {
      logService.debug(`📍 消息回调已卸载，当前数量: ${messageCallbacks.size}`)
    }
  }
}

/**
 * 分发消息到所有回调
 * ✅ 改进：添加错误记录和空检查
 */
function broadcastMessage(type, data) {
  if (messageCallbacks.size === 0) {
    return  // 无回调，跳过处理
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
        error: error.message,
        stack: error.stack
      })
    }
  })

  // ✅ 如果回调失败率太高，发出警告
  if (failedCount > messageCallbacks.size * 0.5) {
    logService.warn(`⚠️ 消息回调失败率过高: ${failedCount}/${messageCallbacks.size}`)
  }
}

/**
 * 验证计时器状态数据完整性
 * ✅ 新增：确保数据有必要的字段
 */
function validateTimerState(data) {
  if (!data || typeof data !== 'object') {
    return false
  }

  // 验证必要字段（这些字段不能为null或undefined）
  const requiredFields = [
    'status',                // 状态：idle, running, paused, finished
    'totalRemaining',        // 总剩余时间
    'currentStageIndex',     // 当前阶段索引
    'currentStageName'       // 当前阶段名称
  ]

  return requiredFields.every(field => field in data && data[field] !== undefined)
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

  // 重置订阅状态
  userQueueSubscribed = false
  userQueueSubscription = null

  // ✅ 关键修复：订阅个人用户队列（必须先建立）
  const userQueuePath = '/user/queue/messages'
  logService.info('📡 订阅个人队列:', { path: userQueuePath })

  try {
    userQueueSubscription = globalStompClient.subscribe(userQueuePath, (message) => {
      try {
        logService.debug('📥 收到个人队列消息')
        const data = JSON.parse(message.body)

        // 如果是第一次收到个人队列消息，标记订阅成功
        if (!userQueueSubscribed) {
          userQueueSubscribed = true
          logService.info('✅ 个人队列订阅已确认')
        }

        // 处理不同类型的消息
        switch (data.type) {
          case 'client_registered':
          case 'client_registered_debug':
            logService.info('✅ 收到注册成功消息', {
              type: data.type,
              clientId: data.data?.clientId
            })
            logService.event('CLIENT_REGISTERED', data.data)

            // 更新全局连接状态
            if (data.data?.success && data.data?.clientId) {
              globalConnectionState.clientId = data.data.clientId
              globalConnectionState.isRegistered = true
              logService.info('✅ 客户端注册成功', { clientId: globalConnectionState.clientId })
            } else if (data.clientId) {
              // 调试消息可能直接包含clientId
              globalConnectionState.clientId = data.clientId
              globalConnectionState.isRegistered = true
              logService.info('✅ 客户端注册成功（调试消息）', { clientId: globalConnectionState.clientId })
            }

            // 广播注册消息
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
            logService.debug('收到消息', { type: data.type })
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

  // 订阅计时器状态（广播）
  globalStompClient.subscribe('/topic/timer-state', (message) => {
    try {
      const data = JSON.parse(message.body)
      const timerData = data.data || data

      logService.debug('📡 收到 /topic/timer-state 广播消息')
      broadcastMessage('timer_state', timerData)
    } catch (error) {
      logService.error('解析计时器状态失败', { error: error.message })
    }
  })

  // 订阅客户端状态
  globalStompClient.subscribe('/topic/clients', (message) => {
    try {
      const data = JSON.parse(message.body)
      logService.debug('收到客户端状态', { clientCount: data.clients?.length })
      broadcastMessage('client_status', data)
    } catch (error) {
      logService.error('解析客户端状态失败', { error: error.message })
    }
  })

  // 订阅调试主题
  globalStompClient.subscribe('/topic/debug', (message) => {
    try {
      const data = JSON.parse(message.body)
      logService.debug('🔧 收到调试消息', { type: data.type })
    } catch (error) {
      logService.error('解析调试消息失败', { error: error.message })
    }
  })

  logService.info('所有主题订阅完成')
  logService.event('WEB_SOCKET_SUBSCRIPTIONS_COMPLETE', {
    timestamp: new Date().toISOString()
  })

  // ✅ 关键：等待订阅完全生效后再注册
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
  }, 100)  // 等待100ms确保订阅已建立
}

/**
 * 启动心跳
 */
/**
 * 启动心跳
 * ✅ 改进：STOMP客户端已内置心跳机制（15秒），无需额外实现
 */
function startHeartbeat() {
  logService.info('STOMP心跳已启用（由STOMP客户端管理）')
  // STOMP客户端通过heartbeatIncoming和heartbeatOutgoing自动管理
  // 无需额外实现
}

/**
 * 停止心跳
 * ✅ 改进：由STOMP客户端自动管理
 */
function stopHeartbeat() {
  logService.info('STOMP心跳将随连接关闭而停止')
  // 不需要手动停止，连接关闭时自动停止
}

/**
 * 初始化全局 WebSocket 连接
 */
export function initGlobalWebSocket() {
  if (globalStompClient && globalStompClient.connected) {
    logService.info('WebSocket 已连接，跳过重复初始化')
    return
  }

  logService.info('初始化全局 WebSocket 连接', {
    endpoint: '/ws-archery-timer',
    currentURL: window.location.href,
    timestamp: new Date().toISOString()
  })

  // 显示详细的连接信息
  logService.event('WEBSOCKET_CONNECT_ATTEMPT', {
    endpoint: '/ws-archery-timer',
    origin: window.location.origin,
    protocol: window.location.protocol,
    host: window.location.host
  })

  // ✅ WebSocket URL 配置
  // 开发环境：使用相对路径走 Vite 代理，vite.config.js 中配置了 /ws-archery-timer 到 http://localhost:8080
  // 生产环境：直接使用相对路径
  const wsUrl = '/ws-archery-timer'
  logService.debug('WebSocket连接URL:', {
    wsUrl,
    currentHost: window.location.host,
    origin: window.location.origin,
    isDev: process.env.NODE_ENV === 'development'
  })

const socket = new SockJS(wsUrl)

  // 添加socket事件监听器用于调试
  socket.onopen = () => {
    logService.debug('📡 [WS] SockJS连接已建立')
  }
  socket.onclose = (event) => {
    logService.warn('📡 [WS] SockJS连接已关闭', {
      code: event.code,
      reason: event.reason,
      wasClean: event.wasClean
    })
  }
  socket.onerror = (error) => {
    logService.error('📡 [WS] SockJS连接错误', {
      error: error?.message || 'Unknown error',
      type: error?.type
    })
  }

  const client = new Client({
    webSocketFactory: () => socket,
    reconnectDelay: 5000,
    heartbeatIncoming: 15000,   // ✅ 改为15秒（从4秒）
    heartbeatOutgoing: 15000,   // ✅ 改为15秒（从4秒）
    heartbeatErrorMargin: 5000, // ✅ 添加5秒误差容限（新增）
    onConnect: () => {
      logService.event('WEBSOCKET_CONNECTED', { reconnectAttempts })
      reconnectAttempts = 0
      globalConnectionState.isConnected = true
      broadcastMessage('connected', null)

      // 订阅所有主题（包括发送注册请求）
      subscribeToAllTopics()

      // 启动心跳
      startHeartbeat()

      // 广播 connection_ready
      broadcastMessage('connection_ready', null)
    },
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
            // 重置计数器，重新开始快速重连阶段
            reconnectAttempts = 0
            exponentialBackoffMultiplier = 1
            clearInterval(periodicReconnectInterval)
            periodicReconnectInterval = null
            initGlobalWebSocket()
          }, 30000) // 每30秒尝试一次

          logService.info('定期重连机制已启用，每30秒尝试一次')
        }
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
    logService.info('断开 WebSocket 连接...')

    try {
      // ✅ 立即记录日志，因为后续操作可能被中断
      logService.event('WEBSOCKET_MANUAL_DISCONNECT', {
        wasConnected: globalStompClient.connected,
        clientId: globalConnectionState.clientId,
        timestamp: new Date().toISOString()
      })

      // ✅ 停止心跳
      stopHeartbeat()

      // ✅ 清理定期重连
      if (periodicReconnectInterval) {
        clearInterval(periodicReconnectInterval)
        periodicReconnectInterval = null
        logService.debug('✅ 清理了定期重连定时器')
      }

      // ✅ 重置指数退避
      reconnectAttempts = 0
      exponentialBackoffMultiplier = 1

      // ✅ 发送断开连接消息到日志（尽可能发送）
      try {
        if (globalStompClient.connected) {
          globalStompClient.publish({
            destination: '/app/disconnect',
            body: JSON.stringify({
              clientId: globalConnectionState.clientId,
              clientType: globalConnectionState.clientType,
              timestamp: Date.now(),
              reason: 'manual_disconnect'
            })
          })
          logService.debug('✅ 发送断开连接通知')
        }
      } catch (err) {
        // 忽略发送失败
      }

      // ✅ 停用STOMP客户端
      globalStompClient.deactivate()
      logService.debug('✅ STOMP客户端已停用')

      // ✅ 清理状态
      globalStompClient = null
      globalConnectionState.isConnected = false
      globalConnectionState.isRegistered = false
      globalConnectionState.clientId = null

      logService.info('✅ WebSocket连接已手动断开')
    } catch (error) {
      logService.error('断开WebSocket连接时发生错误', { error: error.message })
      // 即使出错，也强制清理
      globalStompClient = null
      globalConnectionState.isConnected = false
      globalConnectionState.isRegistered = false
    }
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
    logService.info('客户端注册消息已发送', {
      clientType,
      clientName,
      isConnected: globalStompClient.connected,
      hasClientId: !!globalConnectionState.clientId,
      isRegistered: globalConnectionState.isRegistered,
      timestamp: new Date().toISOString()
    })
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
 * 发送消息 - 自动注入clientId、clientType和timestamp
 */
export function sendGlobalWebSocketMessage(destination, message) {
  if (!globalStompClient || !globalStompClient.connected) {
    logService.warn('WebSocket 未连接，无法发送消息')
    return false
  }

  try {
    // 确保 destination 以 /app/ 开头
    const finalDestination = destination.startsWith('/app/')
      ? destination
      : `/app/${destination}`

    // ✅ 自动注入客户端信息
    const completeMessage = {
      ...message,
      clientId: globalConnectionState.clientId,
      clientType: globalConnectionState.clientType,
      timestamp: Date.now()
    }

    globalStompClient.publish({
      destination: finalDestination,
      body: JSON.stringify(completeMessage)
    })
    logService.debug(`发送消息到 ${finalDestination}`, completeMessage)
    return true
  } catch (error) {
    logService.error('发送消息失败', { destination, error: error.message })
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
