<template>
  <div class="enhanced-display-b">
    <!-- B屏全屏沉浸式展示 -->
    <div class="full-screen-container">
      <!-- 提示文案 -->
      <div class="screen-prompt">
        {{ displayBPrompt }}
      </div>

      <!-- 屏幕状态 -->
      <div class="screen-status">
        <!-- 倒计时时间 -->
        <div class="screen-timer">
          <div class="timer-value">{{ timerState.screenBRemaining }}</div>
        </div>

        <!-- 圆形状态灯 -->
        <div class="status-light" :style="{ backgroundColor: screenBLightColor }">
          <div class="light-glow"></div>
        </div>

        <!-- 屏幕状态标签 -->
        <div class="screen-status-tag" :class="{
          running: timerState.screenBStatus === 'running',
          paused: timerState.screenBStatus !== 'running'
        }">
          {{ timerState.screenBStatus === 'running' ? '运行中' : '已暂停' }}
        </div>
      </div>
    </div>

    <!-- 连接状态指示（仅在未连接时显示） -->
    <div class="connection-status" :class="connectionClass" v-if="showConnectionStatus">
      <el-icon><Connection /></el-icon>
      <span>{{ connectionText }}</span>
    </div>

    <!-- 全屏按钮 -->
    <div class="fullscreen-btn" @click="toggleFullScreen">
      <el-icon v-if="!isFullScreen"><FullScreen /></el-icon>
      <el-icon v-else><Close /></el-icon>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useEnhancedTimerStore } from '../stores/enhancedTimer'
import { subscribeToTimerState } from '../services/globalWebSocketService'
import { useBuzzer } from '../composables/useBuzzer'
import { logService } from '../services/logService'
import { Connection, FullScreen, Close } from '@element-plus/icons-vue'

const timerStore = useEnhancedTimerStore()
const timerState = timerStore.timerState
const buzzer = useBuzzer()

// 状态
const isFullScreen = ref(false)
const showConnectionStatus = ref(true)
const screenMode = ref('alternate') // 默认交替模式，从timerState获取更准确

// 初始化音频上下文
onMounted(() => {
  const audioInitialized = buzzer.initAudioContext()
  if (!audioInitialized) {
    logService.warn('B屏: 音频初始化失败')
  } else {
    logService.debug('B屏: 音频初始化成功')
  }

  // 自动连接
  timerStore.autoConnect()

  // 等待连接建立后订阅主题
  const checkAndSubscribe = () => {
    if (timerStore.connectionState.isConnected) {
      logService.debug('✅ WebSocket 已连接，立即订阅主题')
      timerStore.subscribeToTopics()
      subscribeToTimerState()
      timerStore.requestBroadcastState()

      // 连接成功后隐藏连接状态
      setTimeout(() => {
        showConnectionStatus.value = false
      }, 2000)
    } else {
      logService.debug('⏳ 等待 WebSocket 连接建立...')
      setTimeout(checkAndSubscribe, 500)
    }
  }
  checkAndSubscribe()

  // 添加全屏变化监听
  document.addEventListener('fullscreenchange', handleFullscreenChange)
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
})

// 计算属性（与控制台保持一致）
const displayBPrompt = computed(() => {
  // 只有在当前比赛类型支持轮次模式且确实处于轮次模式时，才显示轮次内容
  // 注意：这里需要获取currentMatchType，但在独立页面中可能没有这个数据
  // 暂时使用与控制台相同的逻辑
  if (timerState.isRound && timerState.roundRecord?.launchRoundCurrentKey) {
    return timerState.roundRecord.launchRoundCurrentKey
  }
  return timerState.bPrompt || '选手B准备'
})

const screenBLightColor = computed(() => {
  // 优先使用B屏独立的阶段颜色
  if (timerState.screenBStageColor) {
    return timerState.screenBStageColor
  }

  // 备选：根据当前阶段名称计算灯色
  const stageName = timerState.currentStageName
  if (!stageName) return '#FF0000'

  if (stageName.includes('准备')) return '#FF0000' // 红色
  if (stageName.includes('黄灯')) return '#FFFF00' // 黄色
  return '#00FF00' // 绿色
})

// 连接状态
const connectionClass = computed(() => {
  if (!timerStore.connectionState.isConnected) return 'disconnected'
  if (!timerStore.isActiveScreen('B')) return 'waiting'
  return 'connected'
})

const connectionText = computed(() => {
  if (!timerStore.connectionState.isConnected) return '离线'
  if (!timerStore.isActiveScreen('B')) return '等待切换'
  return '已连接'
})

// 方法
const toggleFullScreen = () => {
  isFullScreen.value = !isFullScreen.value
  if (isFullScreen.value) {
    document.documentElement.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

const handleFullscreenChange = () => {
  isFullScreen.value = !!document.fullscreenElement
}
</script>

<style scoped>
.enhanced-display-b {
  height: 100vh;
  width: 100vw;
  background-color: #000000;
  position: relative;
  overflow: hidden;
}

/* 全屏容器 */
.full-screen-container {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

/* 提示文案 */
.screen-prompt {
  font-size: 36px;
  font-weight: bold;
  text-align: center;
  color: #fff;
  margin-bottom: 20px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
  padding: 0 20px;
  text-shadow: 0 0 10px rgba(255, 255, 255, 0.3);
}

/* 屏幕状态 */
.screen-status {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  flex: 1;
}

/* 倒计时时间 */
.screen-timer {
  text-align: center;
  z-index: 1;
}

.timer-value {
  font-size: 280px;
  font-weight: bold;
  font-family: 'Courier New', monospace;
  color: #fff;
  letter-spacing: 1px;
  line-height: 1;
  text-shadow: 0 0 20px rgba(255, 255, 255, 0.5);
}

/* 圆形状态灯 */
.status-light {
  width: 150px;
  height: 150px;
  border-radius: 50%;
  position: absolute;
  right: 10%;
  top: 50%;
  transform: translateY(-50%);
  box-shadow: 0 0 40px rgba(255, 255, 255, 0.2);
  transition: all 0.3s ease;
  z-index: 2;
}

.light-glow {
  position: absolute;
  top: 10px;
  left: 10px;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: radial-gradient(circle at 20px 20px, rgba(255, 255, 255, 0.8), transparent);
  filter: blur(12px);
}

/* 屏幕状态标签 */
.screen-status-tag {
  position: absolute;
  top: 20px;
  left: 20px;
  padding: 6px 16px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  z-index: 100;
}

.screen-status-tag.running {
  background-color: rgba(103, 194, 58, 0.3);
  color: #67c23a;
  border: 2px solid rgba(103, 194, 58, 0.5);
  animation: pulse 2s infinite;
}

.screen-status-tag.paused {
  background-color: rgba(230, 162, 60, 0.3);
  color: #e6a23c;
  border: 2px solid rgba(230, 162, 60, 0.5);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* 连接状态 */
.connection-status {
  position: absolute;
  top: 20px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  background-color: rgba(0, 0, 0, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.2);
  z-index: 100;
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

.connection-status.connected {
  color: #52c41a;
  border-color: rgba(82, 196, 26, 0.4);
  background-color: rgba(82, 196, 26, 0.1);
}

/* 全屏按钮 */
.fullscreen-btn {
  position: absolute;
  bottom: 20px;
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

/* 响应式调整 */
@media (max-width: 1200px) {
  .timer-value {
    font-size: 200px;
  }

  .screen-prompt {
    font-size: 28px;
  }

  .status-light {
    width: 120px;
    height: 120px;
    right: 8%;
  }

  .light-glow {
    width: 60px;
    height: 60px;
    background: radial-gradient(circle at 15px 15px, rgba(255, 255, 255, 0.8), transparent);
  }
}

@media (max-width: 768px) {
  .timer-value {
    font-size: 140px;
  }

  .screen-prompt {
    font-size: 24px;
    margin-bottom: 30px;
  }

  .status-light {
    width: 80px;
    height: 80px;
    right: 5%;
  }

  .light-glow {
    width: 40px;
    height: 40px;
    background: radial-gradient(circle at 10px 10px, rgba(255, 255, 255, 0.8), transparent);
  }

  .screen-status-tag {
    top: 10px;
    left: 10px;
    font-size: 12px;
    padding: 4px 12px;
  }

  .connection-status {
    top: 10px;
    right: 10px;
    font-size: 12px;
    padding: 6px 12px;
  }

  .fullscreen-btn {
    bottom: 10px;
    right: 10px;
    width: 40px;
    height: 40px;
  }

  .fullscreen-btn .el-icon {
    font-size: 20px;
  }
}

@media (max-width: 480px) {
  .timer-value {
    font-size: 100px;
  }

  .screen-prompt {
    font-size: 20px;
    margin-bottom: 40px;
  }

  .status-light {
    width: 60px;
    height: 60px;
    right: 3%;
  }

  .light-glow {
    width: 30px;
    height: 30px;
    background: radial-gradient(circle at 8px 8px, rgba(255, 255, 255, 0.8), transparent);
  }
}
</style>