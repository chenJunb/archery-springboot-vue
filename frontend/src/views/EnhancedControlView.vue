<template>
  <div class="enhanced-control-view">
    <!-- 主控控制台布局 -->
    <div class="control-container">
      <!-- 左侧控制面板 -->
      <div class="control-left-panel">
        <!-- 比赛类型选择器 -->
        <div class="control-section">
          <div class="section-header">
            <el-icon><Trophy /></el-icon>
            <span>比赛类型</span>
          </div>
          <div class="section-content">
            <el-select
              v-model="selectedMatchType"
              placeholder="请选择比赛类型"
              size="large"
              style="width: 100%"
              :disabled="!canModifyConfig"
              @change="handleMatchTypeChange"
            >
              <el-option
                v-for="matchType in enhancedMatchTypes"
                :key="matchType.id"
                :label="matchType.chineseName"
                :value="matchType.id"
              />
            </el-select>

            <!-- 比赛类型描述 -->
            <div class="match-type-desc" v-if="currentMatchType">
              {{ currentMatchType.description }}
            </div>

            <!-- 运行中禁用提示 -->
            <div v-if="!canModifyConfig" style="color: #ff9800; font-size: 12px; margin-top: 8px;">
              ⚠️ 计时进行中，无法修改配置。请暂停或重置后再修改。
            </div>
          </div>
        </div>

        <!-- 时间配置 -->
        <div class="control-section">
          <div class="section-header">
            <el-icon><Clock /></el-icon>
            <span>时间配置</span>
          </div>
          <div class="section-content time-config">
            <div class="time-config-item">
              <label>准备（红灯）</label>
              <el-input-number
                v-model="preparationTime"
                :min="0"
                :max="300"
                :step="1"
                :disabled="!canModifyConfig"
                size="small"
                placeholder="秒"
                @change="updateTimeConfig"
              />
              <span class="time-unit">秒</span>
            </div>
            <div class="time-config-item">
              <label>比赛（绿灯）</label>
              <el-input-number
                v-model="competitionTime"
                :min="0"
                :max="600"
                :step="1"
                :disabled="!canModifyConfig"
                size="small"
                placeholder="秒"
                @change="updateTimeConfig"
              />
              <span class="time-unit">秒</span>
            </div>
            <div class="time-config-item">
              <label>黄灯（最后N秒）</label>
              <el-input-number
                v-model="yellowLightTime"
                :min="0"
                :max="60"
                :step="1"
                :disabled="!canModifyConfig"
                size="small"
                placeholder="秒"
                @change="updateTimeConfig"
              />
              <span class="time-unit">秒</span>
            </div>
            <div class="time-summary">
              总时间: {{ totalTime }}秒
            </div>
          </div>
        </div>

        <!-- 屏幕控制模式 -->
        <div class="control-section">
          <div class="section-header">
            <el-icon><Monitor /></el-icon>
            <span>屏幕控制模式</span>
          </div>
          <div class="section-content">
            <el-radio-group v-model="screenMode" :disabled="!canModifyConfig" @change="handleScreenModeChange">
              <el-radio value="sync" border>同步模式</el-radio>
              <el-radio value="alternate" border>AB交替模式</el-radio>
              <el-radio value="only_a" border>仅A屏</el-radio>
              <el-radio value="only_b" border>仅B屏</el-radio>
            </el-radio-group>
          </div>
        </div>

        <!-- AB屏提示文案 -->
        <div class="control-section">
          <div class="section-header">
            <el-icon><Edit /></el-icon>
            <span>AB屏提示文案</span>
          </div>
          <div class="section-content">
            <div class="prompt-item">
              <label>A屏提示</label>
              <el-input
                v-model="aPrompt"
                placeholder="A屏提示文本"
                @change="updatePrompt('A', aPrompt)"
              />
            </div>
            <div class="prompt-item">
              <label>B屏提示</label>
              <el-input
                v-model="bPrompt"
                placeholder="B屏提示文本"
                @change="updatePrompt('B', bPrompt)"
              />
            </div>
          </div>
        </div>

        <!-- 控制按钮区 -->
        <div class="control-section">
          <div class="section-header">
            <el-icon><Setting /></el-icon>
            <span>控制操作</span>
          </div>
          <div class="section-content">
            <div class="control-buttons">
              <!-- 第一行：开始/暂停 -->
              <div class="button-row">
                <el-button
                  type="success"
                  size="large"
                  :icon="VideoPlay"
                  :disabled="!canStart"
                  @click="startTimer"
                >
                  {{ isTimerPaused ? '继续' : '开始' }}
                </el-button>
                <el-button
                  type="warning"
                  size="large"
                  :icon="VideoPause"
                  :disabled="!canPause"
                  @click="pauseTimer"
                >
                  暂停
                </el-button>
              </div>
              <!-- 第二行：重置/切换 -->
              <div class="button-row">
                <el-button
                  type="danger"
                  size="large"
                  :icon="RefreshRight"
                  :disabled="!canReset"
                  @click="resetTimer"
                >
                  重置
                </el-button>
                <el-button
                  type="primary"
                  size="large"
                  :icon="Switch"
                  :disabled="screenMode !== 'alternate'"
                  @click="toggleABScreen"
                >
                  屏幕切换
                </el-button>
              </div>
              <!-- 第三行：鸣笛/声音 -->
              <div class="button-row">
                <el-button
                  type="info"
                  size="large"
                  :icon="Bell"
                  @click="manualBuzzer"
                >
                  手动鸣笛
                </el-button>
                <el-button
                  :type="soundButtonType"
                  size="large"
                  :icon="Headset"
                  @click="toggleSound"
                >
                  {{ soundEnabled ? '消音' : '开音' }}
                </el-button>
              </div>
            </div>

            <!-- 声音设置 -->
            <div class="sound-control" v-if="soundEnabled">
              <label>音量</label>
              <el-slider
                v-model="volume"
                :max="100"
                :step="1"
                :show-stops="false"
                @change="updateVolume"
              />
              <span class="volume-text">{{ volume }}%</span>
            </div>

            <!-- 鸣笛说明 -->
            <div class="buzzer-info">
              <div class="buzzer-item">
                <span class="buzzer-count">1声</span>
                <span class="buzzer-desc">准备阶段</span>
              </div>
              <div class="buzzer-item">
                <span class="buzzer-count">2声</span>
                <span class="buzzer-desc">比赛阶段</span>
              </div>
              <div class="buzzer-item">
                <span class="buzzer-count">3声</span>
                <span class="buzzer-desc">黄灯阶段</span>
              </div>
            </div>
          </div>
        </div>

        <!-- AB交替规则说明 -->
        <div class="control-section" v-if="screenMode === 'alternate' && currentMatchType">
          <div class="section-header">
            <el-icon><InfoFilled /></el-icon>
            <span>AB交替规则</span>
          </div>
          <div class="section-content rule-info">
            <div class="rule-item">
              <el-icon><CircleCheckFilled /></el-icon>
              <span><strong>准备阶段（红灯）</strong>：AB屏强制同步倒计时</span>
            </div>
            <div class="rule-item">
              <el-icon><CircleCheckFilled /></el-icon>
              <span><strong>绿灯阶段初始状态</strong>：A屏开始倒计时，B屏暂停</span>
            </div>
            <div v-if="currentMatchType.category === 'individual'" class="rule-item">
              <el-icon><SwitchFilled /></el-icon>
              <span><strong>个人赛切换规则</strong>：切换时原屏清零，新屏从初始时间开始</span>
            </div>
            <div v-if="currentMatchType.category === 'team' || currentMatchType.category === 'mixed_team'" class="rule-item">
              <el-icon><SwitchFilled /></el-icon>
              <span><strong>团队赛切换规则</strong>：切换时原屏暂停保留时间，新屏继续</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧显示区域 -->
      <div class="control-right-panel">
        <!-- 屏幕预览标题 -->
        <div class="screen-preview-header">
          <div class="screen-preview-title">
            <el-icon><Monitor /></el-icon>
            <span>屏幕预览</span>
          </div>
          <div class="screen-count-info">
            <span class="current-screens">当前显示：{{ activeScreensCount }}个屏幕</span>
            <el-tag size="small" :type="canAddMoreScreens ? 'success' : 'warning'">
              最多支持{{ maxSupportedScreens }}屏
            </el-tag>
          </div>
        </div>

        <!-- 屏幕预览容器 - 自适应布局 -->
        <div class="screen-preview-container">
          <!-- A屏预览 -->
          <div class="screen-preview" :class="{ active: timerState.activeScreen === 'A' && screenMode === 'alternate' }">
            <div class="screen-header">
            <span class="screen-title">A屏预览</span>
            <el-button
              size="small"
              type="primary"
              :icon="CopyDocument"
              @click="copyScreenUrl('A')"
            >
              复制地址
            </el-button>
          </div>
          <div class="screen-content">
            <!-- 提示文案 -->
            <div class="screen-prompt">
              {{ timerState.aPrompt || 'A屏提示' }}
            </div>

            <!-- 屏幕状态 -->
            <div class="screen-status">
              <!-- 圆形状态灯 -->
              <div class="status-light" :style="{ backgroundColor: currentLightColor }">
                <div class="light-glow"></div>
              </div>

              <!-- 倒计时时间 -->
              <div class="screen-timer">
                <!-- AB交替模式下显示当前屏幕的剩余时间 -->
                <template v-if="screenMode === 'alternate'">
                  <div class="timer-label">A屏剩余</div>
                  <div class="timer-value">{{ formatAbTime(timerState.screenARemaining) }}</div>
                </template>
                <template v-else>
                  <div class="timer-label">剩余时间</div>
                  <div class="timer-value">{{ formatTime(displayRemaining) }}</div>
                </template>
              </div>

              <!-- 屏幕状态标签 -->
              <div class="screen-status-tag" :class="{
                running: timerState.screenAStatus === 'running',
                paused: timerState.screenAStatus !== 'running'
              }">
                {{ timerState.screenAStatus === 'running' ? '运行中' : '已暂停' }}
              </div>
            </div>

            <!-- 当前阶段信息 -->
            <div class="stage-info">
              <div class="stage-name">{{ timerState.currentStageName || '准备阶段' }}</div>
              <div class="stage-timer">{{ formatTime(displayRemaining) }}</div>
            </div>
          </div>
        </div>

        <!-- B屏预览 -->
        <div class="screen-preview" :class="{ active: timerState.activeScreen === 'B' && screenMode === 'alternate' }">
          <div class="screen-header">
            <span class="screen-title">B屏预览</span>
            <el-button
              size="small"
              type="success"
              :icon="CopyDocument"
              @click="copyScreenUrl('B')"
            >
              复制地址
            </el-button>
          </div>
          <div class="screen-content">
            <!-- 提示文案 -->
            <div class="screen-prompt">
              {{ timerState.bPrompt || 'B屏提示' }}
            </div>

            <!-- 屏幕状态 -->
            <div class="screen-status">
              <!-- 圆形状态灯 -->
              <div class="status-light" :style="{ backgroundColor: currentLightColor }">
                <div class="light-glow"></div>
              </div>

              <!-- 倒计时时间 -->
              <div class="screen-timer">
                <!-- AB交替模式下显示当前屏幕的剩余时间 -->
                <template v-if="screenMode === 'alternate'">
                  <div class="timer-label">B屏剩余</div>
                  <div class="timer-value">{{ formatAbTime(timerState.screenBRemaining) }}</div>
                </template>
                <template v-else>
                  <div class="timer-label">剩余时间</div>
                  <div class="timer-value">{{ formatTime(displayRemaining) }}</div>
                </template>
              </div>

              <!-- 屏幕状态标签 -->
              <div class="screen-status-tag" :class="{
                running: timerState.screenBStatus === 'running',
                paused: timerState.screenBStatus !== 'running'
              }">
                {{ timerState.screenBStatus === 'running' ? '运行中' : '已暂停' }}
              </div>
            </div>

            <!-- 当前阶段信息 -->
            <div class="stage-info">
              <div class="stage-name">{{ timerState.currentStageName || '准备阶段' }}</div>
              <div class="stage-timer">{{ formatTime(displayRemaining) }}</div>
            </div>
          </div>
        </div>

        </div> <!-- 关闭 screen-preview-container -->

        <!-- 额外屏幕容器占位符 - 预留增加屏幕展示位置 -->
        <div class="extra-screens-container">
          <div class="screen-preview placeholder-screen" v-if="showPlaceholderScreen">
            <div class="screen-header">
              <span class="screen-title">C屏预览 (占位符)</span>
              <el-button
                size="small"
                type="info"
                :icon="Switch"
                disabled
              >
                等待启用
              </el-button>
            </div>
            <div class="screen-content placeholder-content">
              <div class="screen-prompt">
                未来可扩展更多屏幕
              </div>
              <div class="screen-status">
                <div class="status-light" :style="{ backgroundColor: '#666666' }">
                  <div class="light-glow"></div>
                </div>
                <div class="screen-timer">
                  <div class="timer-label">等待配置</div>
                  <div class="timer-value">--:--</div>
                </div>
                <div class="screen-status-tag info">
                  待启用
                </div>
              </div>
              <div class="stage-info">
                <div class="stage-name">未配置阶段</div>
                <div class="stage-timer">--:--</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 系统状态信息 -->
        <div class="system-status">
          <div class="status-item">
            <el-icon><Connection /></el-icon>
            <span>连接状态:</span>
            <span :class="connectionStatusClass">{{ connectionStatusText }}</span>
          </div>
          <div class="status-item">
            <el-icon><User /></el-icon>
            <span>当前控制端:</span>
            <span class="control-client">{{ isControlClient ? '当前用户' : '其他用户' }}</span>
          </div>
          <div class="status-item">
            <el-icon><Clock /></el-icon>
            <span>服务器时间:</span>
            <span class="server-time">{{ formatServerTime(timerState.timestamp) }}</span>
          </div>
          <div class="status-item">
            <el-icon><SwitchFilled /></el-icon>
            <span>WebSocket:</span>
            <span :class="timerStore.connectionState.isConnected ? 'status-connected' : 'status-disconnected'">
              {{ timerStore.connectionState.isConnected ? '已连接' : '未连接' }}
            </span>
          </div>
          <div class="status-item" v-if="timerStore.connectionState.clientId">
            <el-icon><Key /></el-icon>
            <span>客户端ID:</span>
            <span class="client-id">{{ timerStore.connectionState.clientId }}</span>
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
import {
  Trophy, Clock, Monitor, Edit, Setting, VideoPlay, VideoPause,
  RefreshRight, Switch, Bell, Headset, CopyDocument, InfoFilled,
  Connection, User, CircleCheckFilled, SwitchFilled, Key
} from '@element-plus/icons-vue'

const timerStore = useEnhancedTimerStore()
const timerState = timerStore.timerState
const connectionState = timerStore.connectionState
const buzzer = useBuzzer()

// 初始化音频上下文
onMounted(() => {
  // ✅ 修复：检查初始化结果，如果失败则显示用户提示
  const audioInitialized = buzzer.initAudioContext()

  if (!audioInitialized) {
    ElMessage.warning({
      message: '⚠️ 音频初始化失败，声音功能可能不可用。请检查浏览器设置和音频文件。',
      duration: 5000
    })
    logService.warn('音频初始化失败，用户已通知')
  } else {
    logService.debug('音频初始化成功')
  }

  logService.event('CONTROL_VIEW_INITIALIZED', {
    soundEnabled: soundEnabled.value,
    audioInitialized: audioInitialized
  })
})

// 数据
const enhancedMatchTypes = ref([])
const selectedMatchType = ref('')
const currentMatchType = ref(null)

// 时间配置
const preparationTime = ref(10)
const competitionTime = ref(180)
const yellowLightTime = ref(30)

// 屏幕控制
const screenMode = ref('alternate')
const aPrompt = ref('A屏提示')
const bPrompt = ref('B屏提示')
const showPlaceholderScreen = ref(false) // 占位符屏幕显示控制 - 暂时隐藏

// 声音控制
const volume = ref(80)
const soundEnabled = ref(true)

// 计算属性
const totalTime = computed(() => {
  // ✅ 修复：使用 ?? 而非 || 来正确处理值为0的情况
  // 总时间 = 准备时间 + 比赛时间，黄灯时间不纳入总时间
  const prep = preparationTime.value ?? 0
  const comp = competitionTime.value ?? 0
  return prep + comp
})

const currentLightColor = computed(() => {
  // 根据当前阶段计算灯色
  const stageName = timerState.currentStageName
  if (!stageName) return '#FF0000'

  if (stageName.includes('准备')) return '#FF0000' // 红色
  if (stageName.includes('黄灯')) return '#FFFF00' // 黄色
  return '#00FF00' // 绿色
})

const canStart = computed(() => {
  return !timerStore.isTimerRunning() || timerStore.isTimerPaused()
})

const canPause = computed(() => {
  return timerStore.isTimerRunning() && !timerStore.isTimerPaused()
})

const canReset = computed(() => {
  return timerStore.isTimerRunning() || timerStore.isTimerFinished()
})

// 是否可以修改配置 - 只有在非运行状态下才能修改
const canModifyConfig = computed(() => {
  const status = timerState.status
  return status === 'idle' || status === 'paused' || status === 'finished'
})

const isTimerPaused = computed(() => timerStore.isTimerPaused())
const isControlClient = computed(() => timerStore.isControlClient())

// 获取显示的剩余时间 - 用于实时显示
const displayRemaining = computed(() => {
  return timerStore.getDisplayRemaining()
})

const soundButtonType = computed(() => {
  return timerState.soundEnabled ? 'success' : 'info'
})

const connectionStatusClass = computed(() => {
  if (!connectionState.isConnected) return 'status-disconnected'
  if (!timerStore.isControlClient()) return 'status-waiting'
  return 'status-connected'
})

const connectionStatusText = computed(() => {
  if (!connectionState.isConnected) return '离线'
  if (!timerStore.isControlClient()) return '等待控制权'
  return '已连接 (控制端)'
})

// 屏幕相关信息
const activeScreensCount = computed(() => {
  let count = 0
  if (timerState.screenAEnabled) count++
  if (timerState.screenBEnabled) count++
  return count
})

const maxSupportedScreens = ref(6) // 最大支持屏幕数量
const canAddMoreScreens = computed(() => {
  return activeScreensCount.value < maxSupportedScreens.value
})

// 方法
const fetchEnhancedMatchTypes = async () => {
  try {
    const response = await fetch('/api/match-types')
    if (response.ok) {
      const data = await response.json()
      if (data.success && data.data) {
        enhancedMatchTypes.value = data.data

        // 默认选择第一个比赛类型
        if (enhancedMatchTypes.value.length > 0) {
          selectedMatchType.value = enhancedMatchTypes.value[0].id
          currentMatchType.value = enhancedMatchTypes.value[0]
          loadMatchTypeConfig(enhancedMatchTypes.value[0])
        }
      }
    }
  } catch (error) {
    logService.error('获取比赛类型失败:', error)
  }
}

const loadMatchTypeConfig = (matchType) => {
  if (!matchType) return

  // 设置时间配置
  preparationTime.value = matchType.preparationTime || 10
  competitionTime.value = matchType.competitionTime || 180
  yellowLightTime.value = matchType.yellowLightTime || 30

  // 设置AB屏模式
  screenMode.value = matchType.defaultScreenMode || 'alternate'

  // 设置提示文案
  aPrompt.value = matchType.defaultAPrompt || 'A屏'
  bPrompt.value = matchType.defaultBPrompt || 'B屏'

  // 发送到服务器
  updatePrompt('A', aPrompt.value)
  updatePrompt('B', bPrompt.value)
  handleScreenModeChange(screenMode.value)

  // 选择比赛类型
  if (timerStore.connectionState.isConnected) {
    timerStore.selectMatchType(matchType.id)
    updateTimeConfig()
  }
}

const handleMatchTypeChange = (matchTypeId) => {
  const matchType = enhancedMatchTypes.value.find(m => m.id === matchTypeId)
  if (matchType) {
    currentMatchType.value = matchType
    loadMatchTypeConfig(matchType)

    // 重置AB屏和控制按钮到初始状态
    resetABScreenState()
  }
}

const handleScreenModeChange = (mode) => {
  // ✅ 修复：验证模式值在允许的列表中
  const allowedModes = ['sync', 'alternate', 'only_a', 'only_b']

  if (!allowedModes.includes(mode)) {
    logService.warn('⚠️ 无效的屏幕模式: ' + mode)
    ElMessage.error('无效的屏幕模式，请使用有效值')
    // 恢复为之前的有效值
    screenMode.value = timerState.abMode || 'alternate'
    return
  }

  if (timerStore.connectionState.isConnected) {
    try {
      timerStore.setABMode(mode)
      logService.event('SCREEN_MODE_CHANGED', { mode })

      // 重置AB屏和控制按钮到初始状态
      resetABScreenState()
    } catch (error) {
      logService.error('设置屏幕模式失败', { error: error.message })
      ElMessage.error('设置屏幕模式失败，请重试')
      // 恢复为之前的值
      screenMode.value = timerState.abMode || 'alternate'
    }
  } else {
    ElMessage.warning('未连接到服务器，无法更改屏幕模式')
    screenMode.value = timerState.abMode || 'alternate'
  }
}

// 重置AB屏和控制按钮到初始状态
const resetABScreenState = () => {
  // 当比赛类型或配置更改时，重置屏幕到初始状态
  // 这会让后端根据新的比赛类型配置重新初始化屏幕状态
  logService.debug('配置已更改，AB屏状态将根据新的比赛类型重新初始化')
}

const updateTimeConfig = () => {
  if (!timerStore.connectionState.isConnected) return

  const config = {
    preparation: preparationTime.value,
    competition: competitionTime.value,
    yellowLight: yellowLightTime.value
  }

  // 通过WebSocket发送时间配置更新
  timerStore.sendGlobalWebSocketMessage('timer/set-time-config', config)

  // 重置AB屏和控制按钮到初始状态
  resetABScreenState()
}

const updatePrompt = (screen, prompt) => {
  logService.debug(`更新${screen}屏提示文案: "${prompt}"，连接状态:`, timerStore.connectionState)
  if (timerStore.connectionState.isConnected && timerStore.connectionState.clientId) {
    timerStore.setPrompt(screen, prompt)
    logService.debug(`✅ 已发送${screen}屏提示文案到服务器`)
  } else {
    logService.debug(`⏳ ${screen}屏提示文案已保存，等待WebSocket连接后发送`)
    // 保存到本地状态，等连接后重新发送
    if (screen === 'A') {
      timerState.aPrompt = prompt
    } else {
      timerState.bPrompt = prompt
    }
  }
}

const startTimer = () => {
  logService.debug('点击开始按钮，连接状态:', timerStore.connectionState)
  if (timerStore.connectionState.isConnected) {
    logService.debug('发送开始计时消息')
    timerStore.startTimer()
  } else {
    logService.error('WebSocket未连接，无法发送开始计时消息')
  }
}

const pauseTimer = () => {
  logService.debug('点击暂停按钮，连接状态:', timerStore.connectionState)
  if (timerStore.connectionState.isConnected) {
    logService.debug('发送暂停计时消息')
    timerStore.pauseTimer()
  } else {
    logService.error('WebSocket未连接，无法发送暂停计时消息')
  }
}

const resetTimer = () => {
  if (timerStore.connectionState.isConnected) {
    timerStore.resetTimer()
  }
}

const toggleABScreen = () => {
  if (timerStore.connectionState.isConnected) {
    timerStore.toggleABScreen()
  }
}

const toggleSound = () => {
  if (timerStore.connectionState.isConnected) {
    const newSoundEnabled = !timerState.soundEnabled
    timerStore.setSoundEnabled(newSoundEnabled)
    soundEnabled.value = newSoundEnabled

    // 同时切换本地buzzer
    buzzer.isMuted.value = !newSoundEnabled
    logService.event('SOUND_TOGGLED', { enabled: newSoundEnabled })
  }
}

const updateVolume = (newVolume) => {
  if (timerStore.connectionState.isConnected) {
    timerStore.setVolume(newVolume)
    // 同时更新本地buzzer音量
    buzzer.setVolume(newVolume / 100)
    logService.event('VOLUME_UPDATED', { volume: newVolume })
  }
}

const manualBuzzer = () => {
  if (soundEnabled.value) {
    // 本地播放鸣笛声
    buzzer.buzz2() // 使用2声作为手动鸣笛的标准
    logService.event('MANUAL_BUZZER', { soundEnabled: true })
  }

  // 同时发送消息到所有屏幕
  if (timerStore.connectionState.isConnected) {
    timerStore.sendGlobalWebSocketMessage('timer/manual-buzzer', { type: 'manual' })
  }
}

const copyScreenUrl = (screen) => {
  const url = `${window.location.origin}${screen === 'A' ? '/display-a' : '/display-b'}`
  navigator.clipboard.writeText(url)
    .then(() => ElMessage.success(`${screen}屏地址已复制到剪贴板`))
    .catch(err => ElMessage.error('复制失败: ' + err))
}

const formatTime = (seconds) => {
  // ✅ 修复：统一返回 MM:SS 格式，与其他视图保持一致
  if (seconds == null || seconds < 0) return '00:00'
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const formatAbTime = (seconds) => {
  // ✅ 修复：AB交替模式下显示 HH:MM:SS 格式
  if (seconds == null || seconds < 0) return '00:00:00'
  const hours = Math.floor(seconds / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const formatServerTime = (timestamp) => {
  if (!timestamp) return '--:--:--'
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN')
}

// 监听timerState的变化
// ✅ 修复9.1: 移除deep: true，优化性能。timerState本身已是reactive，不需要深度监听
watch(() => timerState, (newState) => {
  // 更新本地状态以匹配服务器状态
  soundEnabled.value = newState.soundEnabled
  volume.value = newState.volume || 80

  if (newState.aPrompt && newState.aPrompt !== aPrompt.value) {
    aPrompt.value = newState.aPrompt
  }

  if (newState.bPrompt && newState.bPrompt !== bPrompt.value) {
    bPrompt.value = newState.bPrompt
  }

  if (newState.abMode && newState.abMode !== screenMode.value) {
    screenMode.value = newState.abMode
  }

  // 更新时间配置
  if (newState.preparationTime !== undefined && newState.preparationTime !== preparationTime.value) {
    preparationTime.value = newState.preparationTime
  }

  if (newState.competitionTime !== undefined && newState.competitionTime !== competitionTime.value) {
    competitionTime.value = newState.competitionTime
  }

  if (newState.yellowLightTime !== undefined && newState.yellowLightTime !== yellowLightTime.value) {
    yellowLightTime.value = newState.yellowLightTime
  }
})

// 生命周期
onMounted(() => {
  // 自动连接
  timerStore.autoConnect()

  // 获取比赛类型
  fetchEnhancedMatchTypes()

  // 订阅WebSocket主题
  subscribeToTopics()
})

onUnmounted(() => {
  // 清理资源
})

const subscribeToTopics = () => {
  if (!timerStore.connectionState.isConnected) {
    logService.debug('未连接到WebSocket，无法订阅主题，将在连接后自动订阅')
    return
  }

  // 订阅计时器状态更新
  if (timerStore.subscribe && typeof timerStore.subscribe === 'function') {
    timerStore.subscribe('timer_state', (data) => {
      logService.debug('收到计时器状态更新', data)
    })
  }

  logService.debug('✅ WebSocket主题订阅完成')
}
</script>

<style scoped>
.enhanced-control-view {
  height: 100vh;
  background-color: #f0f2f5;
  padding: 20px;
}

.control-container {
  display: flex;
  height: 100%;
  gap: 20px;
  overflow: hidden; /* 防止容器溢出 */
}

/* 左侧控制面板 */
.control-left-panel {
  flex: 0 0 auto;
  width: 450px; /* 自适应宽度，min/max可根据需要调整 */
  display: flex;
  flex-direction: column;
  gap: 16px;
  background-color: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  overflow-y: auto;
  max-height: 100%; /* 确保不超过容器高度 */
}

/* 右侧显示区域 */
.control-right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0; /* 防止内容溢出 */
  overflow-y: auto; /* 允许垂直滚动 */
  max-height: 100%; /* 限制为父容器高度 */
}

/* 控制区域通用样式 */
.control-section {
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background-color: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.section-header .el-icon {
  font-size: 16px;
  color: #409eff;
}

.section-content {
  padding: 16px;
}

/* 时间配置样式 */
.time-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.time-config-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.time-config-item label {
  flex: 0 0 80px;
  font-size: 14px;
  color: #606266;
}

.time-config-item .el-input-number {
  flex: 1;
}

.time-unit {
  flex: 0 0 24px;
  font-size: 14px;
  color: #909399;
}

.time-summary {
  margin-top: 8px;
  text-align: center;
  font-size: 14px;
  color: #409eff;
  font-weight: 500;
}

/* 提示文案样式 */
.prompt-item {
  margin-bottom: 12px;
}

.prompt-item label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: #606266;
}

.prompt-item:last-child {
  margin-bottom: 0;
}

/* 控制按钮样式 */
.control-buttons {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.button-row {
  display: flex;
  gap: 12px;
}

.button-row .el-button {
  flex: 1;
}

/* 声音控制样式 */
.sound-control {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed #e4e7ed;
}

.sound-control label {
  font-size: 14px;
  color: #606266;
  flex: 0 0 40px;
}

.sound-control .el-slider {
  flex: 1;
}

.volume-text {
  flex: 0 0 40px;
  font-size: 14px;
  color: #909399;
  text-align: right;
}

/* 鸣笛说明样式 */
.buzzer-info {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed #e4e7ed;
  display: flex;
  justify-content: space-around;
}

.buzzer-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.buzzer-count {
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
}

.buzzer-desc {
  font-size: 12px;
  color: #909399;
}

/* AB交替规则样式 */
.rule-info {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rule-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  line-height: 1.4;
  color: #606266;
}

.rule-item .el-icon {
  color: #67c23a;
  margin-top: 2px;
}

.rule-item strong {
  color: #303133;
}

/* 屏幕预览头部 */
.screen-preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.screen-preview-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.screen-preview-title .el-icon {
  color: #409eff;
  font-size: 18px;
}

.screen-count-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.current-screens {
  font-size: 14px;
  color: #606266;
}

/* 屏幕预览容器 - 自适应布局 */
.screen-preview-container {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 12px;
  min-height: 0; /* 允许flex缩小 */
}

/* 额外屏幕容器 */
.extra-screens-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 12px;
  opacity: 0.7;
  transition: opacity 0.3s ease;
}

.extra-screens-container:hover {
  opacity: 1;
}

/* 占位符屏幕 */
.placeholder-screen {
  border-style: dashed;
  border-color: #909399;
  background-color: #2a2a2a;
}

.placeholder-content {
  opacity: 0.7;
}

.placeholder-content .screen-prompt {
  font-size: 18px;
  opacity: 0.6;
}

/* 屏幕预览样式 */
.screen-preview {
  background-color: #1a1a1a;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid #333;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  min-height: 320px; /* 降低最小高度 */
  max-height: 450px; /* 设置最大高度，防止过大 */
}

.screen-preview.active {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.3);
}

.screen-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #262626;
  border-bottom: 1px solid #333;
}

.screen-title {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.screen-content {
  padding: 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.screen-prompt {
  font-size: 16px;  /* 从18px缩小到16px */
  font-weight: bold;
  text-align: center;
  color: #fff;
  margin-bottom: 12px; /* 从20px缩小到12px */
  min-height: 20px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.screen-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  position: relative;
  flex-shrink: 0;
}

.status-light {
  width: 80px;  /* 从90px缩小到80px */
  height: 80px; /* 从90px缩小到80px */
  border-radius: 50%;
  position: relative;
  box-shadow: 0 0 20px rgba(255, 255, 255, 0.1);
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.light-glow {
  position: absolute;
  top: 10px;
  left: 10px;
  width: 60px;    /* 从70px缩小到60px */
  height: 60px;   /* 从70px缩小到60px */
  border-radius: 50%;
  background: radial-gradient(circle at 18px 18px, rgba(255, 255, 255, 0.8), transparent);
  filter: blur(8px);
}

.screen-timer {
  text-align: center;
  flex-shrink: 0;
}

.timer-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.timer-value {
  font-size: 32px;  /* 从36px缩小到32px */
  font-weight: bold;
  font-family: 'Courier New', monospace;
  color: #fff;
  letter-spacing: 1px;
}

.screen-status-tag {
  position: absolute;
  top: 0;
  right: 0;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.screen-status-tag.running {
  background-color: rgba(103, 194, 58, 0.2);
  color: #67c23a;
  border: 1px solid rgba(103, 194, 58, 0.4);
}

.screen-status-tag.paused {
  background-color: rgba(230, 162, 60, 0.2);
  color: #e6a23c;
  border: 1px solid rgba(230, 162, 60, 0.4);
}

.screen-status-tag.info {
  background-color: rgba(144, 147, 153, 0.2);
  color: #909399;
  border: 1px solid rgba(144, 147, 153, 0.4);
}

/* 阶段信息 */
.stage-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #333;
  gap: 8px;
  flex-shrink: 0;
}

.stage-name {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-timer {
  font-size: 20px;
  font-weight: bold;
  font-family: 'Courier New', monospace;
  color: #fff;
  flex-shrink: 0;
}

/* 系统状态信息 */
.system-status {
  background-color: white;
  border-radius: 8px;
  padding: 12px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  display: flex;
  justify-content: space-around;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0; /* 防止被压缩 */
}

.status-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  flex: 0 1 auto;
  white-space: nowrap;
}

.status-item .el-icon {
  color: #409eff;
}

.status-item span:not(:first-child) {
  color: #606266;
}

.status-disconnected {
  color: #f56c6c;
}

.status-waiting {
  color: #e6a23c;
}

.status-connected {
  color: #67c23a;
}

.control-client {
  font-weight: 500;
}

.server-time {
  font-family: 'Courier New', monospace;
  font-weight: 500;
}

/* 比赛类型描述 */
.match-type-desc {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

/* 屏幕控制模式单选组特殊样式 */
.el-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.el-radio {
  margin: 0;
  flex: 1;
  min-width: 80px;
}

/* 响应式调整 */
@media (max-width: 1400px) {
  .control-left-panel {
    width: 380px;
  }
}

@media (max-width: 1200px) {
  .control-container {
    flex-direction: column;
  }

  .control-left-panel {
    flex: none;
    width: 100%;
    max-width: 100%;
    max-height: 40vh; /* 限制左侧面板的最大高度 */
  }

  .control-right-panel {
    flex: 1;
    max-height: 60vh; /* 右侧预览区约60% */
    overflow-y: auto;
  }

  /* 中等屏幕下的屏幕预览布局 */
  .screen-preview-container,
  .extra-screens-container {
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
    gap: 12px;
  }

  .screen-preview {
    min-height: 280px;
    max-height: 360px;
  }
}

@media (max-width: 768px) {
  .enhanced-control-view {
    padding: 10px;
  }

  .control-left-panel {
    padding: 12px;
    width: 100%;
    max-height: auto;
    flex: 0 0 auto;
  }

  .control-right-panel {
    flex: 1;
    max-height: auto;
  }

  .button-row {
    flex-direction: column;
  }

  .system-status {
    flex-direction: column;
    gap: 8px;
  }

  .timer-value {
    font-size: 36px;
  }

  /* 屏幕预览容器在平板/移动端的响应式 */
  .screen-preview-container,
  .extra-screens-container {
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: 12px;
  }

  .screen-preview-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .screen-count-info {
    align-self: flex-start;
  }

  .screen-preview {
    min-height: 260px;
    max-height: 340px;
  }
}
</style>