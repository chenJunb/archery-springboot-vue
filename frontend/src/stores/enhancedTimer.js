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
  disconnectGlobalWebSocket
} from '../services/globalWebSocketService'
import { logService } from '../services/logService'

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
        logService.debug('✅ 全局WebSocket已连接，准备注册客户端...')
        // 自动注册客户端
        const clientType = getClientTypeFromRoute()
        registerGlobalClient(clientType)
        break

      case 'disconnected':
        logService.debug('❌ 全局WebSocket已断开')
        break

      case 'registered':
        logService.debug('✅ 客户端注册成功:', data)
        break

      case 'connection_ready':
        logService.debug('🔗 WebSocket连接就绪')
        break

      case 'timer_state':
        logService.debug('📥 收到计时器状态更新')

        // ✅ "后端单一数据源"原则：无条件接收后端状态
        // 前端不应该"保护"任何字段以阻止后端更新
        // 如果后端有新的配置值，应该立即应用（这意味着配置已被验证和处理）

        // ✅ 合并服务器状态到timerState（完全信任后端）
        Object.assign(timerState, data)

        logService.debug('✅ 已应用后端状态（包括所有时间配置）', {
          preparationTime: timerState.preparationTime,
          competitionTime: timerState.competitionTime,
          yellowLightTime: timerState.yellowLightTime,
          currentStageRemaining: timerState.currentStageRemaining
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

  // 初始化消息监听（每个页面使用 store 时初始化一次）
  initMessageListeners()

  // 添加连接状态变化监听器，持续检查连接状态
  let connectionCheckCount = 0
  const maxCheckCount = 30 // 最多检查30秒
  const checkConnectionInterval = setInterval(() => {
    connectionCheckCount++

    if (globalConnectionState.isConnected && globalConnectionState.clientId) {
      logService.debug('✅ WebSocket连接已建立:', {
        clientId: globalConnectionState.clientId,
        clientType: globalConnectionState.clientType
      })
      clearInterval(checkConnectionInterval)
    } else if (connectionCheckCount >= maxCheckCount) {
      logService.warn('⚠️ WebSocket连接仍未建立，请检查服务器连接')
      clearInterval(checkConnectionInterval)
    } else {
      logService.debug('⏳ 等待WebSocket连接建立...', {
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

  // 自动连接（全局连接已在 App.vue 中初始化）
  const autoConnect = () => {
    logService.debug('🔄 自动连接 - 注册客户端类型')
    const clientType = getClientTypeFromRoute()
    globalConnectionState.clientType = clientType
    logService.debug('📝 客户端类型设置为:', clientType)
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

    // ✅ 修复：订阅并存储unsubscribe函数
    // 订阅计时器状态
    const sub1 = subscribeToTopic('/topic/timer-state', (data) => {
      Object.assign(timerState, data)
    })
    if (sub1) unsubscribeCallbacks.push(sub1)

    // 订阅比赛类型
    const sub2 = subscribeToTopic('/topic/match-types', (data) => {
      if (data.type === 'matchTypes' && data.success) {
        enhancedMatchTypes.value = data.data
      }
    })
    if (sub2) unsubscribeCallbacks.push(sub2)

    // 订阅AB屏模式配置
    const sub3 = subscribeToTopic('/topic/match-type-screen-mode', (data) => {
      logService.debug('📋 收到AB屏模式配置:', data)
    })
    if (sub3) unsubscribeCallbacks.push(sub3)

    // 订阅比赛类型详细信息
    const sub4 = subscribeToTopic('/topic/match-type-details', (data) => {
      logService.debug('📋 收到比赛类型详细信息:', data)
    })
    if (sub4) unsubscribeCallbacks.push(sub4)

    // 订阅鸣笛
    const sub5 = subscribeToTopic('/topic/buzzer', (data) => {
      logService.debug('📢 收到鸣笛通知:', data)
      // 这里可以播放鸣笛声音
    })
    if (sub5) unsubscribeCallbacks.push(sub5)

    logService.debug(`✅ 已订阅 ${unsubscribeCallbacks.length} 个主题`)
  }

  // 广播状态请求
  const requestBroadcastState = () => {
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
