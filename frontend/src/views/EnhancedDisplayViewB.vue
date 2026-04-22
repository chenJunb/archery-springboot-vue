<template>
  <div class="enhanced-display-view display-b" :class="{ 'full-screen': isFullScreen }">
    <!-- 连接状态 -->
    <div class="connection-status" :class="connectionClass">
      <el-icon><Connection /></el-icon>
      <span>{{ connectionText }}</span>
      <span class="screen-label">B屏</span>
    </div>

    <!-- 主显示区 -->
    <div class="main-display" :class="{ active: isActiveScreen }">
      <!-- 顶部信息栏 -->
      <div class="top-info">
        <div class="match-type">{{ timerState.matchTypeName || '未选择比赛类型' }}</div>
        <div class="ab-mode">{{ abModeText }}</div>
        <div class="control-info" v-if="timerState.controlClientId">
          控制端: {{ getControlClientName() }}
        </div>
      </div>

      <!-- 中心显示区 -->
      <div class="center-display">
        <!-- 提示文案 -->
        <div class="screen-prompt">
          {{ timerState.bPrompt || 'B屏提示文案' }}
        </div>

        <!-- 圆形状态灯 -->
        <div class="center-content">
          <div
            class="status-light"
            :style="{
              backgroundColor: currentLightColor,
              boxShadow: `0 0 40px ${currentLightColor}50`
            }"
          >
            <div class="light-inner"></div>
          </div>

          <!-- 倒计时时间 -->
          <div class="time-display">
            <!-- AB交替模式下显示当前屏幕的剩余时间 -->
            <template v-if="timerState.abMode === 'alternate'">
              <div class="time-value">{{ formatAbTime(currentScreenRemaining) }}</div>
              <div class="time-label">B屏剩余时间</div>
            </template>
            <template v-else>
              <div class="time-value">{{ formatTime(timerState.currentStageRemaining) }}</div>
              <div class="time-label">剩余时间</div>
            </template>
          </div>

          <!-- 屏幕状态标签 -->
          <div class="screen-status-tag" :class="{
            running: screenStatus === 'running',
            paused: screenStatus !== 'running'
          }">
            {{ screenStatusText }}
          </div>
        </div>

        <!-- 当前阶段信息 -->
        <div class="stage-info">
          <div class="stage-label">当前阶段</div>
          <div class="stage-details">
            <div class="stage-name" :style="{ color: currentLightColor }">
              {{ timerState.currentStageName || '准备阶段' }}
            </div>
            <div class="stage-timer">{{ formatTime(timerState.currentStageRemaining) }}</div>
          </div>
        </div>
      </div>

      <!-- 底部状态栏 -->
      <div class="bottom-status">
        <div class="status-item">
          <el-icon><User /></el-icon>
          <span>在线: {{ timerState.connectedClients }}</span>
        </div>
        <div class="status-item">
          <el-icon><Timer /></el-icon>
          <span>总时间: {{ formatTime(timerState.totalRemaining) }}</span>
        </div>
        <div class="status-item">
          <el-icon><Clock /></el-icon>
          <span>{{ currentTime }}</span>
        </div>
      </div>
    </div>

    <!-- 全屏按钮 -->
    <div class="fullscreen-btn" @click="toggleFullScreen">
      <el-icon v-if="!isFullScreen"><FullScreen /></el-icon>
      <el-icon v-else><Close /></el-icon>
    </div>

    <!-- 声音状态指示 -->
    <div class="sound-indicator" :class="{ muted: !timerState.soundEnabled }">
      <el-icon><Headset /></el-icon>
      <span>{{ timerState.soundEnabled ? '声音开' : '声音关' }}</span>
    </div>

    <!-- AB交替模式说明（当需要时显示） -->
    <div class="alternate-info" v-if="showAlternateInfo">
      <div class="info-content">
        <div class="info-title">AB交替模式规则</div>
        <div class="info-rules">
          <div class="rule-item">
            <el-icon><CircleCheckFilled /></el-icon>
            准备阶段：AB屏同步倒计时
          </div>
          <div class="rule-item">
            <el-icon><CircleCheckFilled /></el-icon>
            绿灯阶段：A屏倒计时，B屏暂停
          </div>
          <div v-if="timerState.matchTypeCategory === 'individual'" class="rule-item">
            <el-icon><SwitchFilled /></el-icon>
            个人赛：切换时原屏清零，新屏重新开始
          </div>
          <div v-if="timerState.matchTypeCategory === 'team' || timerState.matchTypeCategory === 'mixed_team'" class="rule-item">
            <el-icon><SwitchFilled /></el-icon>
            团队赛：切换时原屏暂停保留，新屏继续
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useEnhancedTimerStore } from '../stores/enhancedTimer'
import { useBuzzer } from '../composables/useBuzzer'
import { logService } from '../services/logService'
import { Connection, User, Timer, Clock, Headset, FullScreen, Close, CircleCheckFilled, SwitchFilled } from '@element-plus/icons-vue'

const timerStore = useEnhancedTimerStore()
const timerState = timerStore.timerState
const buzzer = useBuzzer()

// 初始化音频上下文
onMounted(() => {
  buzzer.initAudioContext()
  logService.event('BUZZER_INITIALIZED', { isMuted: buzzer.isMuted.value })
})

// 状态
const isFullScreen = ref(false)
const showAlternateInfo = ref(false)
const previousLightColor = ref(null)
const lastBuzzedPhase = ref(null)

// 计算属性
const isActiveScreen = computed(() => {
  return timerStore.isActiveScreen('B')
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
  // 根据当前阶段计算灯色
  const stageName = timerState.currentStageName
  if (!stageName) return '#FF0000'

  if (stageName.includes('准备')) return '#FF0000' // 红色
  if (stageName.includes('黄灯')) return '#FFFF00' // 黄色
  return '#00FF00' // 绿色
})

const currentScreenRemaining = computed(() => {
  return timerState.screenBRemaining || timerState.currentStageRemaining
})

const screenStatus = computed(() => {
  // 判断当前屏幕状态
  if (timerState.abMode === 'alternate') {
    return timerState.screenBStatus || 'paused'
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
  // AB交替模式下显示完整的时分秒
  if (seconds == null || seconds < 0) return '00:00:00'
  const hours = Math.floor(seconds / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const getControlClientName = () => {
  // 简化的控制端名称获取
  return timerState.controlClientId ? '控制端' : '未知'
}

const toggleFullScreen = () => {
  isFullScreen.value = !isFullScreen.value
  if (isFullScreen.value) {
    document.documentElement.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

// 监听全屏变化
const handleFullscreenChange = () => {
  isFullScreen.value = !!document.fullscreenElement
}

// 连接和初始化
const initializeConnection = () => {
  // 显示连接状态
  setTimeout(() => {
    if (!timerStore.connectionState.isConnected) {
      ElMessage.warning('正在连接服务器...')
    }
  }, 1000)
}

// 声音播放与自动鸣笛系统
const playSound = () => {
  if (!timerState.soundEnabled) return

  const stageName = timerState.currentStageName || ''
  const currentPhase = stageName.includes('准备') ? 'prepare' :
                       stageName.includes('比赛') || stageName.includes('射击') ? 'competition' : null

  // 避免重复鸣笛同一阶段 - 仅1声和2声使用阶段名称
  if (currentPhase && currentPhase !== lastBuzzedPhase.value) {
    lastBuzzedPhase.value = currentPhase

    if (stageName.includes('准备')) {
      logService.event('STAGE_TRANSITION', { stage: '准备', action: '发出1声鸣笛' })
      buzzer.buzz1()
    } else if (stageName.includes('比赛') || stageName.includes('射击')) {
      logService.event('STAGE_TRANSITION', { stage: '比赛', action: '发出2声鸣笛' })
      buzzer.buzz2()
    }
  }
}

// 监听灯色变化以触发3声鸣笛（GREEN→YELLOW）
const checkLightColorTransition = () => {
  if (!timerState.soundEnabled) return

  const currentColor = currentLightColor.value

  // 检测GREEN→YELLOW转换
  if (previousLightColor.value === '#00FF00' && currentColor === '#FFFF00') {
    logService.event('LIGHT_TRANSITION', { from: 'GREEN', to: 'YELLOW', action: '发出3声鸣笛' })
    buzzer.buzz3()
  }

  previousLightColor.value = currentColor
}

// 观察阶段变化以自动播放声音
watch(() => timerState.currentStageName, () => {
  playSound()
})

// 监听灯色变化
watch(() => currentLightColor.value, () => {
  checkLightColorTransition()
})

// 生命周期
onMounted(() => {
  // 自动连接
  timerStore.autoConnect()

  // 订阅主题
  timerStore.subscribeToTopics()

  // 监听连接状态
  initializeConnection()

  // 添加事件监听
  document.addEventListener('fullscreenchange', handleFullscreenChange)

  // 添加键盘快捷键
  document.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  timerStore.disconnect()
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('keydown', handleKeyDown)
})

// 键盘快捷键
const handleKeyDown = (event) => {
  switch (event.key) {
    case 'F11':
      event.preventDefault()
      toggleFullScreen()
      break
    case 'Escape':
      if (isFullScreen.value) {
        isFullScreen.value = false
      }
      break
    case 'i':
    case 'I':
      // 切换信息显示
      showAlternateInfo.value = !showAlternateInfo.value
      break
  }
}

// 监听状态变化
watch(() => timerState.currentStageName, (newStage, oldStage) => {
  if (newStage && newStage !== oldStage) {
    // 显示阶段变化提示
    if (isActiveScreen.value) {
      logService.debug(`阶段变化: ${oldStage} -> ${newStage}`)
    }
  }
})

watch(() => timerState.screenBStatus, (newStatus, oldStatus) => {
  if (newStatus && newStatus !== oldStatus) {
    logService.debug(`B屏状态变化: ${oldStatus} -> ${newStatus}`)
  }
})

watch(() => timerState.status, (newStatus, oldStatus) => {
  if (newStatus && newStatus !== oldStatus) {
    logService.debug(`计时器状态变化: ${oldStatus} -> ${newStatus}`)

    // 计时结束提示
    if (newStatus === 'finished') {
      if (isActiveScreen.value) {
        logService.info('计时结束！')
      }
    }
  }
})
</script>

<style scoped>
.enhanced-display-view {
  height: 100vh;
  background-color: #000;
  color: #fff;
  position: relative;
  overflow: hidden;
  font-family: 'Microsoft YaHei', 'Segoe UI', sans-serif;
}

.enhanced-display-view.full-screen {
  background-color: #000;
}

/* 连接状态 */
.connection-status {
  position: absolute;
  top: 20px;
  left: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  z-index: 100;
  background-color: rgba(0, 0, 0, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.connection-status.disconnected {
  color: #ff4d4f;
  border-color: rgba(255, 77, 79, 0.4);
  background-color: rgba(255, 77, 79, 0.1);
}

.connection-status.waiting {
  color: #faad14;
  border-color: rgba(250, 173, 20, 0.4);
  background-color: rgba(250, 173, 20, 0.1);
}

.connection-status.active {
  color: #52c41a;
  border-color: rgba(82, 196, 26, 0.4);
  background-color: rgba(82, 196, 26, 0.1);
}

.screen-label {
  margin-left: 8px;
  padding: 2px 8px;
  background-color: #13ce66;
  border-radius: 4px;
  font-size: 12px;
  font-weight: bold;
}

/* 主显示区 */
.main-display {
  height: 100%;
  display: flex;
  flex-direction: column;
  opacity: 0.5;
  transition: opacity 0.5s ease;
}

.main-display.active {
  opacity: 1;
}

/* 顶部信息栏 */
.top-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 40px;
  background-color: rgba(0, 0, 0, 0.8);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.match-type {
  font-size: 20px;
  font-weight: 600;
  color: #fff;
}

.ab-mode {
  font-size: 18px;
  color: #13ce66;
  font-weight: 500;
  padding: 6px 16px;
  background-color: rgba(19, 206, 102, 0.2);
  border-radius: 20px;
  border: 1px solid rgba(19, 206, 102, 0.4);
}

.control-info {
  font-size: 14px;
  color: #aaa;
}

/* 中心显示区 */
.center-display {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.screen-prompt {
  font-size: 32px;
  font-weight: bold;
  text-align: center;
  margin-bottom: 60px;
  color: #fff;
  text-shadow: 0 0 10px rgba(255, 255, 255, 0.3);
}

.center-content {
  text-align: center;
  position: relative;
  margin-bottom: 60px;
}

/* 状态灯 */
.status-light {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  margin: 0 auto 40px;
  position: relative;
  transition: all 0.3s ease;
}

.light-inner {
  position: absolute;
  top: 20px;
  left: 20px;
  right: 20px;
  bottom: 20px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.1);
}

/* 时间显示 */
.time-display {
  text-align: center;
}

.time-value {
  font-size: 96px;
  font-weight: bold;
  font-family: 'Courier New', monospace;
  letter-spacing: 4px;
  margin-bottom: 16px;
  color: #fff;
  text-shadow: 0 0 20px rgba(255, 255, 255, 0.5);
}

.time-label {
  font-size: 20px;
  color: #aaa;
  letter-spacing: 2px;
}

/* 屏幕状态标签 */
.screen-status-tag {
  position: absolute;
  top: 0;
  right: 0;
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 16px;
  font-weight: 600;
}

.screen-status-tag.running {
  background-color: rgba(82, 196, 26, 0.3);
  color: #52c41a;
  border: 2px solid rgba(82, 196, 26, 0.5);
  animation: pulse 2s infinite;
}

.screen-status-tag.paused {
  background-color: rgba(250, 173, 20, 0.3);
  color: #faad14;
  border: 2px solid rgba(250, 173, 20, 0.5);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* 阶段信息 */
.stage-info {
  text-align: center;
  margin-top: 40px;
}

.stage-label {
  font-size: 18px;
  color: #aaa;
  margin-bottom: 8px;
}

.stage-details {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 30px;
}

.stage-name {
  font-size: 28px;
  font-weight: bold;
  text-transform: uppercase;
  letter-spacing: 2px;
}

.stage-timer {
  font-size: 36px;
  font-family: 'Courier New', monospace;
  font-weight: bold;
}

/* 底部状态栏 */
.bottom-status {
  display: flex;
  justify-content: space-around;
  padding: 20px 40px;
  background-color: rgba(0, 0, 0, 0.8);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.status-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  color: #ccc;
}

.status-item .el-icon {
  font-size: 20px;
}

/* 全屏按钮 */
.fullscreen-btn {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 0, 0, 0.7);
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  cursor: pointer;
  z-index: 100;
  transition: all 0.3s ease;
}

.fullscreen-btn:hover {
  background-color: rgba(19, 206, 102, 0.8);
  border-color: #13ce66;
  transform: scale(1.05);
}

.fullscreen-btn .el-icon {
  font-size: 24px;
  color: #fff;
}

/* 声音指示器 */
.sound-indicator {
  position: absolute;
  top: 90px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background-color: rgba(0, 0, 0, 0.7);
  border: 1px solid rgba(82, 196, 26, 0.4);
  border-radius: 16px;
  font-size: 14px;
  color: #52c41a;
}

.sound-indicator.muted {
  border-color: rgba(255, 77, 79, 0.4);
  color: #ff4d4f;
}

.sound-indicator .el-icon {
  font-size: 18px;
}

/* AB交替模式说明 */
.alternate-info {
  position: absolute;
  bottom: 100px;
  left: 50%;
  transform: translateX(-50%);
  background-color: rgba(0, 0, 0, 0.9);
  border: 2px solid rgba(19, 206, 102, 0.6);
  border-radius: 12px;
  padding: 20px;
  max-width: 600px;
  z-index: 100;
  animation: fadeIn 0.5s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateX(-50%) translateY(20px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}

.info-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-title {
  font-size: 18px;
  font-weight: bold;
  color: #13ce66;
  text-align: center;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(19, 206, 102, 0.3);
}

.info-rules {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rule-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  color: #ddd;
  line-height: 1.4;
}

.rule-item .el-icon {
  flex-shrink: 0;
  color: #52c41a;
  font-size: 18px;
}

/* 响应式调整 */
@media (max-width: 1024px) {
  .time-value {
    font-size: 72px;
  }

  .status-light {
    width: 160px;
    height: 160px;
  }

  .stage-name {
    font-size: 24px;
  }

  .stage-timer {
    font-size: 28px;
  }
}

@media (max-width: 768px) {
  .time-value {
    font-size: 64px;
  }

  .screen-prompt {
    font-size: 24px;
  }

  .status-light {
    width: 120px;
    height: 120px;
  }

  .center-display {
    padding: 20px;
  }

  .top-info {
    flex-direction: column;
    gap: 10px;
    padding: 15px 20px;
  }

  .alternate-info {
    left: 20px;
    right: 20px;
    transform: none;
    max-width: none;
  }
}

@media (max-width: 480px) {
  .time-value {
    font-size: 48px;
  }

  .screen-prompt {
    font-size: 20px;
    margin-bottom: 40px;
  }

  .status-light {
    width: 100px;
    height: 100px;
  }

  .stage-details {
    flex-direction: column;
    gap: 15px;
  }
}
</style>