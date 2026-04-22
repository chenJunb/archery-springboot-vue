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
  subscribeToTopic
} from '../services/globalWebSocketService'

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
  volume: 80
})

// 比赛类型数据
const enhancedMatchTypes = ref([])

// 注销全局消息监听的函数集合（用于清理）
const unsubscribeCallbacks = []

// 初始化消息监听
function initMessageListeners() {
  // 清理之前的监听器
  unsubscribeCallbacks.forEach(cb => cb())
  unsubscribeCallbacks.length = 0

  // 注册新的全局消息监听
  const unsubscribe = onGlobalWebSocketMessage((type, data) => {
    switch (type) {
      case 'connected':
        console.log('✅ 全局WebSocket已连接，准备注册客户端...')
        // 自动注册客户端
        const clientType = getClientTypeFromRoute()
        registerGlobalClient(clientType)
        break

      case 'disconnected':
        console.log('❌ 全局WebSocket已断开')
        break

      case 'registered':
        console.log('✅ 客户端注册成功:', data)
        break

      case 'connection_ready':
        console.log('🔗 WebSocket连接就绪')
        break

      case 'timer_state':
        console.log('📥 收到计时器状态更新')
        // 合并状态更新，保留已有的值
        Object.assign(timerState, data)
        break

      case 'matchTypes':
        if (data.success && data.data) {
          enhancedMatchTypes.value = data.data
        }
        break

      case 'screenModeConfig':
        if (data.success && data.data) {
          console.log('📋 收到AB屏模式配置:', data.data)
        }
        break

      case 'matchTypeDetails':
        console.log('📋 收到比赛类型详细信息:', data)
        break

      case 'error':
        console.error('❌ 收到错误消息:', data)
        break
    }
  })

  unsubscribeCallbacks.push(unsubscribe)
}

// 导出方法
export function useEnhancedTimerStore() {
  console.log('🚀 使用 EnhancedTimerStore，初始化消息监听...')
  console.log('📍 页面URL:', window.location.href)

  // 初始化消息监听（每个页面使用 store 时初始化一次）
  initMessageListeners()

  // 添加连接状态变化监听器，持续检查连接状态
  let connectionCheckCount = 0
  const maxCheckCount = 30 // 最多检查30秒
  const checkConnectionInterval = setInterval(() => {
    connectionCheckCount++

    if (globalConnectionState.isConnected && globalConnectionState.clientId) {
      console.log('✅ WebSocket连接已建立:', {
        clientId: globalConnectionState.clientId,
        clientType: globalConnectionState.clientType
      })
      clearInterval(checkConnectionInterval)
    } else if (connectionCheckCount >= maxCheckCount) {
      console.warn('⚠️ WebSocket连接仍未建立，请检查服务器连接')
      clearInterval(checkConnectionInterval)
    } else {
      console.log('⏳ 等待WebSocket连接建立...', {
        isConnected: globalConnectionState.isConnected,
        clientId: globalConnectionState.clientId,
        checkCount: connectionCheckCount
      })
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
    if (state.abMode === 'sync' || state.abMode === 'only_a' || state.abMode === 'only_b') {
      return true
    }

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

  // 自动连接（全局连接已在 App.vue 中初始化）
  const autoConnect = () => {
    console.log('🔄 自动连接 - 注册客户端类型')
    const clientType = getClientTypeFromRoute()
    globalConnectionState.clientType = clientType
    console.log('📝 客户端类型设置为:', clientType)
    registerGlobalClient(clientType)
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
      console.error('获取增强版比赛类型失败:', error)
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

  // 订阅相关主题
  const subscribeToTopics = () => {
    // 订阅计时器状态
    subscribeToTopic('/topic/timer-state', (data) => {
      Object.assign(timerState, data)
    })

    // 订阅比赛类型
    subscribeToTopic('/topic/match-types', (data) => {
      if (data.type === 'matchTypes' && data.success) {
        enhancedMatchTypes.value = data.data
      }
    })

    // 订阅AB屏模式配置
    subscribeToTopic('/topic/match-type-screen-mode', (data) => {
      console.log('📋 收到AB屏模式配置:', data)
    })

    // 订阅比赛类型详细信息
    subscribeToTopic('/topic/match-type-details', (data) => {
      console.log('📋 收到比赛类型详细信息:', data)
    })

    // 订阅鸣笛
    subscribeToTopic('/topic/buzzer', (data) => {
      console.log('📢 收到鸣笛通知:', data)
      // 这里可以播放鸣笛声音
    })
  }

  // 广播状态请求
  const requestBroadcastState = () => {
    sendGlobalWebSocketMessage('timer/broadcast-state', {})
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
    manualBuzzer
  }
}

// 导出默认store，便于向后兼容
export default useEnhancedTimerStore
