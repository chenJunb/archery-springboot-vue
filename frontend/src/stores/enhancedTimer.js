/**
 * 增强版计时器 Store
 * 使用全局 WebSocket 服务，避免多个连接
 */

import { reactive, readonly, ref } from 'vue'
import {
  globalConnectionState,
  onGlobalWebSocketMessage,
  registerGlobalClient,
  getClientTypeFromRoute,
  sendGlobalWebSocketMessage,
  subscribeToTopic,
  disconnectGlobalWebSocket,
  getGlobalStompClient
} from '../services/globalWebSocketService'
import { logService } from '../services/logService'
import { useBuzzer } from '../composables/useBuzzer'

// 计时器状态（增强版）
const timerState = reactive({
  // 基本状态
  status: 'idle',
  matchTypeId: null,
  matchTypeName: null,
  matchTypeCategory: null,
  matchTypeSubCategory: null,
  alternateType: null,

  // 当前阶段信息
  currentStageIndex: 0,
  currentStageName: '准备',
  currentStageColor: '#FF0000',
  currentStageDuration: 0,
  currentStageElapsed: 0,
  currentStageRemaining: 0,

  // 全局计时信息
  totalElapsed: 0,
  totalRemaining: 0,

  // AB屏模式和状态
  abMode: 'alternate',
  activeScreen: 'A',
  screenAEnabled: true,
  screenBEnabled: true,

  // AB屏详细计时信息
  screenARemaining: 0,
  screenBRemaining: 0,
  screenAStatus: 'paused',
  screenBStatus: 'paused',

  // ✅ 新增：AB屏独立的阶段信息（交替模式下使用）
  screenAStageIndex: 0,
  screenAStageName: '准备',
  screenAStageColor: '#FF0000',
  screenAStageDuration: 0,
  screenAStageElapsed: 0,
  screenAStageRemaining: 0,

  screenBStageIndex: 0,
  screenBStageName: '准备',
  screenBStageColor: '#FF0000',
  screenBStageDuration: 0,
  screenBStageElapsed: 0,
  screenBStageRemaining: 0,

  // 提示文案
  aPrompt: 'A屏',
  bPrompt: 'B屏',

  // 时间配置（可编辑）
  preparationTime: 10,
  competitionTime: 180,
  yellowLightTime: 30,

  // 控制信息
  controlClientId: null,
  connectedClients: 0,

  // 时间戳
  lastUpdateTime: null,
  timestamp: null,

  // 配置信息
  soundEnabled: true,
  volume: 80,

  // 本地计时辅助字段（用于前端实时显示，不从后端同步）
  localDisplayRemaining: 0,  // 本地显示的剩余时间
  localLastUpdateTime: 0      // 本地上次更新时间
})

// 比赛类型数据
const enhancedMatchTypes = ref([])

// 注销全局消息监听的函数集合（用于清理）
const unsubscribeCallbacks = []

// 本地计时器相关
let localCountdownInterval = null
let lastWebSocketUpdateTime = 0
let lastWebSocketRemaining = 0

// ✅ 新增：为AB屏跟踪各自的本地倒计时
let screenALastUpdateTime = 0
let screenALastRemaining = 0
let screenBLastUpdateTime = 0
let screenBLastRemaining = 0

// 连接状态检查定时器引用
let connectionCheckInterval = null

// 启动本地计时器 - 提供实时显示效果
function startLocalCountdown() {
  if (localCountdownInterval) {
    clearInterval(localCountdownInterval)
  }

  lastWebSocketUpdateTime = Date.now()
  lastWebSocketRemaining = timerState.currentStageRemaining

  localCountdownInterval = setInterval(() => {
    if (timerState.status === 'running') {
      const now = Date.now()
      const elapsed = (now - lastWebSocketUpdateTime) / 1000
      const newRemaining = Math.max(0, lastWebSocketRemaining - elapsed)
      timerState.localDisplayRemaining = Math.round(newRemaining * 10) / 10
    }
  }, 100)
}

// 停止本地计时器
function stopLocalCountdown() {
  if (localCountdownInterval) {
    clearInterval(localCountdownInterval)
    localCountdownInterval = null
  }
}

// 初始化消息监听
function initMessageListeners() {
  // 清理之前的监听器
  unsubscribeCallbacks.forEach(cb => cb())
  unsubscribeCallbacks.length = 0

  // 注册新的全局消息监听
  const unsubscribe = onGlobalWebSocketMessage((type, data) => {
    switch (type) {
      case 'connected':
        logService.debug('✅ 全局WebSocket已连接，等待主题订阅完成...')
        // 等待connection_ready消息再进行注册
        break

      case 'disconnected':
        logService.debug('❌ 全局WebSocket已断开')
        break

      case 'registered':
        logService.info('✅ 客户端注册成功:', data)

        // ✅ 关键修复：确保注册状态正确更新
        if (data?.success && data?.clientId) {
          logService.info('📝 更新store中的注册状态', {
            success: data.success,
            clientId: data.clientId,
            before: {
              isRegistered: globalConnectionState.isRegistered,
              clientId: globalConnectionState.clientId
            }
          })

          // 虽然globalConnectionState已经在globalWebSocketService.js中更新了
          // 但为了保险，这里也同步更新一下
          globalConnectionState.clientId = data.clientId
          globalConnectionState.isRegistered = true

          logService.info('✅ Store注册状态已同步', {
            after: {
              isRegistered: globalConnectionState.isRegistered,
              clientId: globalConnectionState.clientId
            }
          })
        }

        // 注册成功，重置注册状态
        if (registrationState.isRegistrationInProgress) {
          logService.debug('🔓 注册成功，重置注册状态')
          setRegistrationInProgress(false)
        }
        break

      case 'connection_ready':
        logService.debug('🔗 WebSocket连接就绪')
        // ✅ 注册请求现在由 globalWebSocketService 中的 subscribeToAllTopics() 负责
        // 这里只是记录连接已就绪，不需要再尝试注册
        if (globalConnectionState.isRegistered) {
          logService.debug('✅ 客户端已注册')
        }
        break

      case 'timer_state':
        logService.debug('📥 [Store] 收到计时器状态更新（通过广播）', {
          preparationTime: data.preparationTime,
          competitionTime: data.competitionTime,
          yellowLightTime: data.yellowLightTime,
          currentStageRemaining: data.currentStageRemaining,
          screenARemaining: data.screenARemaining,
          screenBRemaining: data.screenBRemaining
        })

        logService.event('TIMER_STATE_APPLIED', {
          source: 'broadcast_message',
          hasTimeConfig: data.preparationTime !== undefined && data.competitionTime !== undefined,
          hasCurrentStageRemaining: data.currentStageRemaining !== undefined,
          timestamp: new Date().toISOString()
        })

        // ✅ "后端单一数据源"原则：无条件接收后端状态
        // 前端不应该"保护"任何字段以阻止后端更新
        // 如果后端有新的配置值，应该立即应用（这意味着配置已被验证和处理）

        // ✅ 合并服务器状态到timerState（完全信任后端）
        Object.assign(timerState, data)

        logService.debug('✅ [Store] 已应用后端状态（包括所有时间配置）', {
          preparationTime: timerState.preparationTime,
          competitionTime: timerState.competitionTime,
          yellowLightTime: timerState.yellowLightTime,
          currentStageRemaining: timerState.currentStageRemaining,
          screenARemaining: timerState.screenARemaining,
          screenBRemaining: timerState.screenBRemaining
        })

        // 同步本地计时 - 更新基准时间和剩余时间
        lastWebSocketUpdateTime = Date.now()
        lastWebSocketRemaining = data.currentStageRemaining || 0
        timerState.localDisplayRemaining = lastWebSocketRemaining

        // 根据计时器状态管理本地计时器
        if (data.status === 'running') {
          startLocalCountdown()
        } else if (data.status === 'paused' || data.status === 'idle' || data.status === 'finished') {
          stopLocalCountdown()
        }
        break

      case 'matchTypes':
        if (data.success && data.data) {
          enhancedMatchTypes.value = data.data
        }
        break

      case 'screenModeConfig':
        if (data.success && data.data) {
          logService.debug('📋 收到AB屏模式配置:', data.data)
        }
        break

      case 'matchTypeDetails':
        logService.debug('📋 收到比赛类型详细信息:', data)
        break

      case 'debug':
        logService.debug('🔧 收到调试消息:', data)
        // 处理特定类型的调试消息
        if (data?.type === 'client_registered_debug' && data?.data?.clientId) {
          const clientId = data.data.clientId
          const sessionId = data.data.sessionId
          logService.info('🔧 从调试消息中提取clientId:', { clientId, sessionId })
          // 更新全局连接状态
          if (!globalConnectionState.isRegistered) {
            globalConnectionState.clientId = clientId
            globalConnectionState.isRegistered = true
            globalConnectionState.registerTime = new Date().toISOString()
            logService.info('✅ 通过调试消息更新客户端注册状态', {
              clientId: globalConnectionState.clientId,
              clientType: globalConnectionState.clientType,
              isRegistered: globalConnectionState.isRegistered
            })
            // 重置注册进度状态
            if (registrationState.isRegistrationInProgress) {
              logService.debug('🔓 调试消息：注册成功，重置注册状态')
              setRegistrationInProgress(false)
            }
          }
        } else if (data?.type === 'test_direct_message' || data?.type === 'test_global_message') {
          logService.debug('🔧 收到用户队列测试消息:', data)
        }
        break

      case 'error':
        logService.error('❌ 收到错误消息:', data)
        break
    }
  })

  unsubscribeCallbacks.push(unsubscribe)
}

// 导出方法
export function useEnhancedTimerStore() {
  logService.debug('🚀 使用 EnhancedTimerStore，初始化消息监听...')
  logService.debug('📍 页面URL:', window.location.href)

  // 用于控制注册状态的状态变量 - 现在在闭包作用域中
  let registrationState = {
    isRegistrationInProgress: false,
    lastRegistrationAttempt: 0
  }

  // 更新注册状态的函数
  const setRegistrationInProgress = (value) => {
    registrationState.isRegistrationInProgress = value
    if (value) {
      registrationState.lastRegistrationAttempt = Date.now()
    }
  }

  // 检查是否可以重试注册
  const canRetryRegistration = () => {
    const timeSinceLastAttempt = Date.now() - registrationState.lastRegistrationAttempt
    return !registrationState.isRegistrationInProgress && timeSinceLastAttempt > 5000
  }

  // 自动连接函数定义
  const autoConnect = () => {
    logService.debug('🔄 自动连接 - 注册客户端类型', {
      isConnected: globalConnectionState.isConnected,
      clientId: globalConnectionState.clientId,
      clientType: globalConnectionState.clientType,
      isRegistrationInProgress: registrationState.isRegistrationInProgress
    })

    const clientType = getClientTypeFromRoute()
    globalConnectionState.clientType = clientType
    logService.debug('📝 客户端类型设置为:', clientType)

    // 详细检查连接状态
    const globalStompClient = getGlobalStompClient()
    const stompConnected = globalStompClient?.connected
    logService.debug('📡 WebSocket连接状态详细检查', {
      globalConnectionState_isConnected: globalConnectionState.isConnected,
      globalStompClient_exists: !!globalStompClient,
      stompConnected: stompConnected,
      clientId: globalConnectionState.clientId
    })

    if (!globalConnectionState.isConnected) {
      logService.warn('⚠️ WebSocket未连接，跳过注册')
      return false
    }

    if (!stompConnected) {
      logService.warn('⚠️ STOMP客户端未就绪，等待connection_ready消息')
      return false
    }

    const success = registerGlobalClient(clientType)
    if (!success) {
      logService.warn('⚠️ 客户端注册消息发送失败，可能WebSocket未就绪')
      // 立即重置注册状态，允许下一次尝试
      setTimeout(() => {
        logService.debug('🔓 重置注册状态，允许下一次注册尝试')
        setRegistrationInProgress(false)
      }, 1000)
    } else {
      logService.debug('✅ 客户端注册请求已发送，等待后端响应...', {
          clientType,
          clientId: globalConnectionState.clientId,
          isConnected: globalConnectionState.isConnected,
          stompConnected: stompConnected,
          timestamp: new Date().toISOString()
        })
    }
    return success
  }

  // 初始化消息监听（每个页面使用 store 时初始化一次）
  initMessageListeners()

  // ✅ 延迟自动连接，确保 WebSocket 已连接
  // 不要在这里立即调用 autoConnect()，让 globalWebSocketService.js 中的 onConnect 处理
  // 这里只需要 initMessageListeners() 设置监听器即可

  // 添加连接状态变化监听器，持续检查连接状态
  let connectionCheckCount = 0
  const maxCheckCount = 30 // 最多检查30秒

  // 防止重复注册的标志
  let isRegistrationInProgress = false

  // 清理之前可能存在的定时器
  if (connectionCheckInterval) {
    clearInterval(connectionCheckInterval)
    connectionCheckInterval = null
  }

  connectionCheckInterval = setInterval(() => {
    connectionCheckCount++

    if (globalConnectionState.isConnected && globalConnectionState.isRegistered) {
      logService.debug('✅ WebSocket连接已建立并注册:', {
        clientId: globalConnectionState.clientId,
        clientType: globalConnectionState.clientType,
        isRegistered: globalConnectionState.isRegistered
      })
      clearInterval(connectionCheckInterval)
      connectionCheckInterval = null
    } else if (connectionCheckCount >= maxCheckCount) {
      logService.warn('⚠️ WebSocket连接仍未建立，请检查服务器连接', {
        isConnected: globalConnectionState.isConnected,
        isRegistered: globalConnectionState.isRegistered,
        clientId: globalConnectionState.clientId
      })
      clearInterval(connectionCheckInterval)
      connectionCheckInterval = null
      // 超时后重置注册状态
      isRegistrationInProgress = false
    } else {
      const stompClient = getGlobalStompClient()
      logService.debug('⏳ 等待WebSocket连接建立...', {
        isConnected: globalConnectionState.isConnected,
        clientId: globalConnectionState.clientId,
        isRegistered: globalConnectionState.isRegistered,
        checkCount: connectionCheckCount,
        stompConnected: stompClient?.connected || false,
        hasStompClient: !!stompClient,
        timestamp: new Date().toISOString()
      })

      // 如果已连接但没有注册，尝试重新注册
      // 添加防抖：避免频繁重新注册
      if (globalConnectionState.isConnected && !globalConnectionState.isRegistered && canRetryRegistration() && connectionCheckCount % 10 === 0) {
        const currentStompClient = getGlobalStompClient()
        logService.debug('🔄 已连接但未注册，尝试重新注册...', {
          isConnected: globalConnectionState.isConnected,
          clientId: globalConnectionState.clientId,
          isRegistered: globalConnectionState.isRegistered,
          stompConnected: currentStompClient?.connected || false,
          registrationInProgress: registrationState.isRegistrationInProgress,
          timestamp: new Date().toISOString()
        })
        setRegistrationInProgress(true)

        // 设置注册超时：如果在5秒内没有收到注册响应，允许下一次注册
        setTimeout(() => {
          if (!globalConnectionState.isRegistered) {
            const timeoutStompClient = getGlobalStompClient()
            logService.warn('⏳ 注册超时，允许下一次注册尝试', {
              isConnected: globalConnectionState.isConnected,
              clientId: globalConnectionState.clientId,
              isRegistered: globalConnectionState.isRegistered,
              stompConnected: timeoutStompClient?.connected || false,
              timeSinceLastAttempt: Date.now() - registrationState.lastRegistrationAttempt,
              timestamp: new Date().toISOString()
            })
            setRegistrationInProgress(false)
          }
        }, 5000)

        autoConnect()
      }
    }
  }, 1000)

  // 检查当前客户端是否是控制端
  const isControlClient = () => {
    return globalConnectionState.clientId === timerState.controlClientId
  }

  // 检查计时器是否正在运行
  const isTimerRunning = () => {
    return timerState.status === 'running'
  }

  // 检查计时器是否已暂停
  const isTimerPaused = () => {
    return timerState.status === 'paused'
  }

  // 检查计时器是否已结束
  const isTimerFinished = () => {
    return timerState.status === 'finished'
  }

  // 检查当前屏幕是否是活动屏幕
  const isActiveScreen = (screen = null) => {
    const state = timerState

    // ✅ 修复：不同模式的处理逻辑
    if (state.abMode === 'sync') {
      // 同步模式：所有屏幕都是活动的
      return true
    }

    if (state.abMode === 'only_a') {
      // 仅A屏模式：只有A屏是活动的
      return screen === 'A'
    }

    if (state.abMode === 'only_b') {
      // 仅B屏模式：只有B屏是活动的
      return screen === 'B'
    }

    // 交替模式：根据activeScreen判断
    if (screen) {
      return state.activeScreen === screen
    }

    if (globalConnectionState.clientType === 'display_a') {
      return state.activeScreen === 'A' && state.screenAEnabled
    }
    if (globalConnectionState.clientType === 'display_b') {
      return state.activeScreen === 'B' && state.screenBEnabled
    }

    return true // 控制端总是显示活动
  }

  // 获取当前屏幕剩余时间
  const getCurrentScreenRemaining = () => {
    const state = timerState

    if (state.abMode === 'alternate') {
      if (state.activeScreen === 'A') {
        return state.screenARemaining
      } else if (state.activeScreen === 'B') {
        return state.screenBRemaining
      }
    }

    return state.currentStageRemaining
  }

  
  // 获取所有增强版比赛类型
  const fetchEnhancedMatchTypes = async () => {
    try {
      const response = await fetch('/api/match-types')
      if (response.ok) {
        const data = await response.json()
        if (data.success && data.data) {
          enhancedMatchTypes.value = data.data
          return data.data
        }
      }
    } catch (error) {
      logService.error('获取增强版比赛类型失败:', error)
    }
    return []
  }

  // 通过WebSocket获取比赛类型
  const requestMatchTypesViaWS = () => {
    sendGlobalWebSocketMessage('match-types/getAll', {})
  }

  // 通过WebSocket获取AB屏模式配置
  const requestScreenModeConfig = (matchTypeId) => {
    sendGlobalWebSocketMessage('match-types/screen-mode', { matchTypeId })
  }

  // 通过WebSocket获取时间配置
  const requestTimeConfig = (matchTypeId) => {
    sendGlobalWebSocketMessage('match-types/time-config', { matchTypeId })
  }

  // 选择比赛类型
  const selectMatchType = (matchTypeId) => {
    logService.info('📤 发送选择比赛类型消息', { matchTypeId })
    sendGlobalWebSocketMessage('timer/select-match-type', { matchTypeId })
  }

  // 开始计时
  const startTimer = () => {
    sendGlobalWebSocketMessage('timer/start', {})
  }

  // 暂停计时
  const pauseTimer = () => {
    sendGlobalWebSocketMessage('timer/pause', {})
  }

  // 重置计时
  const resetTimer = () => {
    sendGlobalWebSocketMessage('timer/reset', {})
  }

  // 设置AB屏模式
  const setABMode = (mode) => {
    sendGlobalWebSocketMessage('timer/set-ab-mode', { mode })
  }

  // 切换AB屏
  const toggleABScreen = () => {
    sendGlobalWebSocketMessage('timer/toggle-ab-screen', {})
  }

  // 设置屏幕启用状态
  const setScreenEnabled = (screen, enabled) => {
    sendGlobalWebSocketMessage('timer/set-screen-enabled', { screen, enabled })
  }

  // 设置提示文案
  const setPrompt = (screen, prompt) => {
    sendGlobalWebSocketMessage('timer/set-prompt', { screen, prompt })
  }

  // 设置声音开关
  const setSoundEnabled = (enabled) => {
    sendGlobalWebSocketMessage('timer/set-sound-enabled', { enabled })
  }

  // 设置音量
  const setVolume = (volume) => {
    sendGlobalWebSocketMessage('timer/set-volume', { volume })
  }

  // 手动鸣笛
  const manualBuzzer = (type = 'manual') => {
    sendGlobalWebSocketMessage('timer/manual-buzzer', { type })
  }

  // 同步时间配置到预览（内部方法，仅更新配置字段，不计算任何值）
  const syncTimeConfigToPreview = (prep, comp, yellow) => {
    // ✅ 严格遵循"后端单一数据源"原则
    // 仅更新用户输入的配置字段，不进行任何计算
    // 所有计算值（如currentStageRemaining）由后端计算并广播

    timerState.preparationTime = prep
    timerState.competitionTime = comp
    timerState.yellowLightTime = yellow

    // ✅ 不计算 currentStageRemaining，等待后端广播
    // ❌ 移除：const totalSeconds = (prep || 10) + (comp || 180)
    // ❌ 移除：timerState.currentStageRemaining = totalSeconds
    // ❌ 移除：timerState.localDisplayRemaining = totalSeconds

    logService.debug('已同步时间配置字段（不计算任何值）', {
      preparation: timerState.preparationTime,
      competition: timerState.competitionTime,
      yellowLight: timerState.yellowLightTime,
      note: '计算值将由后端广播更新'
    })
  }

  // 订阅相关主题
  const subscribeToTopics = () => {
    // ✅ 修复：清理之前的订阅
    unsubscribeCallbacks.forEach(cb => cb?.unsubscribe?.())
    unsubscribeCallbacks.length = 0

    // ✅ 验证连接状态
    if (!globalConnectionState.isConnected) {
      logService.error('❌ 无法订阅主题：WebSocket 未连接', {
        isConnected: globalConnectionState.isConnected
      })
      return
    }

    // ✅ 重要修复：只订阅额外主题，不重复订阅 /topic/timer-state
    // /topic/timer-state 已在 globalWebSocketService.js 的 subscribeToAllTopics() 中全局订阅
    // 该消息会通过 broadcastMessage('timer_state', timerData) 广播到所有页面
    // 并通过 initMessageListeners() 中的 onGlobalWebSocketMessage 处理

    // 订阅比赛类型
    const sub1 = subscribeToTopic('/topic/match-types', (data) => {
      if (data.type === 'matchTypes' && data.success) {
        enhancedMatchTypes.value = data.data
      }
    })
    if (sub1) {
      unsubscribeCallbacks.push(sub1)
      logService.info('✅ 已订阅 /topic/match-types')
    }

    // 订阅AB屏模式配置
    const sub2 = subscribeToTopic('/topic/match-type-screen-mode', (data) => {
      logService.debug('📋 收到AB屏模式配置:', data)
    })
    if (sub2) {
      unsubscribeCallbacks.push(sub2)
      logService.info('✅ 已订阅 /topic/match-type-screen-mode')
    }

    // 订阅比赛类型详细信息
    const sub3 = subscribeToTopic('/topic/match-type-details', (data) => {
      logService.debug('📋 收到比赛类型详细信息:', data)
    })
    if (sub3) {
      unsubscribeCallbacks.push(sub3)
      logService.info('✅ 已订阅 /topic/match-type-details')
    }

    // ✅ 初始化鸣笛composable用于播放声音
    const buzzer = useBuzzer()

    // 订阅鸣笛
    const sub4 = subscribeToTopic('/topic/buzzer', (data) => {
      logService.debug('📢 收到鸣笛通知:', data)
      // ✅ 修复：根据鸣笛类型播放相应的声音
      if (data && data.buzzerType) {
        switch(data.buzzerType) {
          case 'buzz1':
            logService.info('🔔 播放1声鸣笛 - 进入准备阶段')
            buzzer.buzz1()
            break
          case 'buzz2':
            logService.info('🔔 播放2声鸣笛 - 进入比赛阶段')
            buzzer.buzz2()
            break
          case 'buzz3':
            logService.info('🔔 播放3声鸣笛 - 进入黄灯阶段')
            buzzer.buzz3()
            break
          default:
            logService.debug('⚠️ 未知的鸣笛类型:', data.buzzerType)
        }
      }
    })
    if (sub4) {
      unsubscribeCallbacks.push(sub4)
      logService.info('✅ 已订阅 /topic/buzzer')
    }

    logService.info(`✅ 已成功订阅 ${unsubscribeCallbacks.length} 个额外主题`, {
      successCount: unsubscribeCallbacks.length,
      note: '/topic/timer-state 通过全局广播处理，不在此重复订阅'
    })
  }

  // 广播状态请求（主动拉取最新状态）
  const requestBroadcastState = () => {
    if (!globalConnectionState.isConnected) {
      logService.warn('未连接，无法请求状态')
      return
    }
    logService.debug('📡 请求后端广播当前状态...')
    sendGlobalWebSocketMessage('timer/broadcast-state', {})
  }

  // 获取AB屏实时剩余时间（使用本地倒计时）
  // ✅ 修复：为AB屏提供流畅的秒级倒计时显示
  const getCurrentScreenRemainingWithLocalCountdown = (screenType) => {
    const isScreenA = screenType === 'A'
    const screenRemaining = isScreenA ? timerState.screenARemaining : timerState.screenBRemaining
    const screenStatus = isScreenA ? timerState.screenAStatus : timerState.screenBStatus

    // 如果屏幕暂停，直接返回秒数值
    if (screenStatus !== 'running') {
      return screenRemaining || 0
    }

    // 屏幕运行中：使用本地倒计时提供实时显示
    const now = Date.now()

    if (isScreenA) {
      // 如果A屏的值有更新，保存新的基准时间
      if (screenALastRemaining !== screenRemaining || screenALastRemaining === 0) {
        screenALastUpdateTime = now
        screenALastRemaining = screenRemaining
      }

      // 计算已经过去的时间
      const elapsed = (now - screenALastUpdateTime) / 1000
      const newRemaining = Math.max(0, screenALastRemaining - elapsed)
      return Math.round(newRemaining * 10) / 10  // 保留1位小数以获得更平滑的显示
    } else {
      // B屏逻辑相同
      if (screenBLastRemaining !== screenRemaining || screenBLastRemaining === 0) {
        screenBLastUpdateTime = now
        screenBLastRemaining = screenRemaining
      }

      const elapsed = (now - screenBLastUpdateTime) / 1000
      const newRemaining = Math.max(0, screenBLastRemaining - elapsed)
      return Math.round(newRemaining * 10) / 10
    }
  }

  // 获取显示用的剩余时间 - 用于前端实时显示
  const getDisplayRemaining = () => {
    if (timerState.status === 'running' && timerState.localDisplayRemaining > 0) {
      return timerState.localDisplayRemaining
    }
    return timerState.currentStageRemaining || 0
  }

  // 清理本地计时器
  const cleanup = () => {
    stopLocalCountdown()
    unsubscribeCallbacks.forEach(cb => cb())
    unsubscribeCallbacks.length = 0

    // 清理连接状态检查定时器
    if (connectionCheckInterval) {
      clearInterval(connectionCheckInterval)
      connectionCheckInterval = null
      logService.debug('🔧 连接状态检查定时器已清理')
    }
  }

  return {
    // 只读状态
    timerState: readonly(timerState),
    connectionState: readonly(globalConnectionState),
    enhancedMatchTypes: readonly(enhancedMatchTypes),

    // 计算属性
    isControlClient,
    isTimerRunning,
    isTimerPaused,
    isTimerFinished,
    isActiveScreen,
    getCurrentScreenRemaining,

    // 连接方法
    autoConnect,
    sendGlobalWebSocketMessage,
    subscribeToTopics,
    requestBroadcastState,

    // 比赛类型相关
    fetchEnhancedMatchTypes,
    requestMatchTypesViaWS,
    requestScreenModeConfig,
    requestTimeConfig,

    // 控制方法
    selectMatchType,
    startTimer,
    pauseTimer,
    resetTimer,
    setABMode,
    toggleABScreen,
    setScreenEnabled,
    setPrompt,
    setSoundEnabled,
    setVolume,
    manualBuzzer,

    // 显示相关方法
    getDisplayRemaining,
    getCurrentScreenRemainingWithLocalCountdown,
    syncTimeConfigToPreview,
    cleanup
  }
}

// 导出默认store，便于向后兼容
export default useEnhancedTimerStore
