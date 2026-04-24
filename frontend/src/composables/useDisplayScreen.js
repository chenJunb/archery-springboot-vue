/**
 * 显示屏幕通用逻辑
 * 用于A屏和B屏的共享logic
 */

import { ref, computed } from 'vue'
import { useEnhancedTimerStore } from '../stores/enhancedTimer'
import { useBuzzer } from './useBuzzer'
import { logService } from '../services/logService'

export function useDisplayScreen(screenType) {
  const timerStore = useEnhancedTimerStore()
  const timerState = timerStore.timerState
  const buzzer = useBuzzer()

  // 状态
  const isFullScreen = ref(false)
  const showAlternateInfo = ref(false)
  const previousLightColor = ref(null)
  const lastBuzzedPhase = ref(null)

  // 是否A屏
  const isScreenA = screenType === 'A'
  const screenPromptKey = isScreenA ? 'aPrompt' : 'bPrompt'
  const screenRemainingKey = isScreenA ? 'screenARemaining' : 'screenBRemaining'
  const screenStatusKey = isScreenA ? 'screenAStatus' : 'screenBStatus'

  // 计算属性
  const isActiveScreen = computed(() => {
    return timerStore.isActiveScreen(screenType)
  })

  const connectionClass = computed(() => {
    if (!timerStore.connectionState.isConnected) return 'disconnected'
    if (!isActiveScreen.value) return 'waiting'
    return 'active'
  })

  const connectionText = computed(() => {
    if (!timerStore.connectionState.isConnected) return '离线'
    if (!isActiveScreen.value) return '等待切换'
    return '活动屏幕'
  })

  const abModeText = computed(() => {
    const mode = timerState.abMode
    switch (mode) {
      case 'sync': return '同步模式'
      case 'alternate': return 'AB交替模式'
      case 'only_a': return '仅A屏模式'
      case 'only_b': return '仅B屏模式'
      default: return mode
    }
  })

  const currentLightColor = computed(() => {
    const stageName = timerState.currentStageName
    if (!stageName) return '#FF0000'
    if (stageName.includes('准备')) return '#FF0000'
    if (stageName.includes('黄灯')) return '#FFFF00'
    return '#00FF00'
  })

  const currentScreenRemaining = computed(() => {
    if (isScreenA) {
      return timerState.screenARemaining || timerState.currentStageRemaining
    } else {
      return timerState.screenBRemaining || timerState.currentStageRemaining
    }
  })

  const displayRemaining = computed(() => {
    return timerStore.getDisplayRemaining()
  })

  const screenStatus = computed(() => {
    if (timerState.abMode === 'alternate') {
      return isScreenA ? (timerState.screenAStatus || 'paused') : (timerState.screenBStatus || 'paused')
    }
    return timerState.status === 'running' ? 'running' : 'paused'
  })

  const screenStatusText = computed(() => {
    switch (screenStatus.value) {
      case 'running': return '运行中'
      case 'paused': return '已暂停'
      default: return '等待中'
    }
  })

  const currentTime = computed(() => {
    const now = new Date()
    return now.toLocaleTimeString('zh-CN', {
      hour12: false,
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  })

  // 方法
  const formatTime = (seconds) => {
    if (seconds == null || seconds < 0) return '00:00'
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }

  const formatAbTime = (seconds) => {
    if (seconds == null || seconds < 0) return '00:00:00'
    const hours = Math.floor(seconds / 3600)
    const mins = Math.floor((seconds % 3600) / 60)
    const secs = seconds % 60
    return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }

  const getControlClientName = () => {
    const clientId = timerState.controlClientId
    if (!clientId) return '未知'
    return clientId.substring(0, 8) + '...'
  }

  const toggleFullScreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen()
      isFullScreen.value = true
    } else {
      document.exitFullscreen()
      isFullScreen.value = false
    }
  }

  const playSound = () => {
    // ✅ 修复：调用正确的buzzer方法
    // buzz1用于准备阶段，buzz2用于比赛阶段，buzz3用于黄灯
    const stageName = timerState.currentStageName || ''
    if (stageName.includes('准备')) {
      buzzer.buzz1()
    } else if (stageName.includes('比赛') || stageName.includes('射击')) {
      buzzer.buzz2()
    } else if (stageName.includes('黄灯')) {
      buzzer.buzz3()
    } else {
      logService.warn('未知的阶段，无法播放鸣笛:', stageName)
    }
  }

  return {
    // 状态
    timerState,
    isFullScreen,
    showAlternateInfo,
    previousLightColor,
    lastBuzzedPhase,
    isScreenA,
    screenPromptKey,
    screenRemainingKey,
    screenStatusKey,

    // 计算属性
    isActiveScreen,
    connectionClass,
    connectionText,
    abModeText,
    currentLightColor,
    currentScreenRemaining,
    displayRemaining,
    screenStatus,
    screenStatusText,
    currentTime,

    // 方法
    formatTime,
    formatAbTime,
    getControlClientName,
    toggleFullScreen,
    playSound,

    // 工具
    buzzer
  }
}
