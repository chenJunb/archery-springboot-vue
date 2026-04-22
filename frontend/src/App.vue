<template>
  <div id="app" :class="themeClass">
    <div class="app-container">
      <div class="connection-status" :class="connectionStatus">
        <span class="status-indicator"></span>
        <span class="status-text">{{ connectionText }}</span>
      </div>

      <router-view />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useTimerStore } from './stores/timer'
import { initGlobalWebSocket, globalConnectionState } from './services/globalWebSocketService'

const route = useRoute()
const timerStore = useTimerStore()

// 在根组件挂载时初始化全局 WebSocket 连接（仅一次）
onMounted(() => {
  console.log('🌍 App 根组件挂载，初始化全局 WebSocket 连接...')
  initGlobalWebSocket()
})

// 根据路由设置主题
const themeClass = computed(() => {
  if (route.name === 'control') return 'control-theme'
  if (route.name === 'display-a') return 'display-a-theme'
  if (route.name === 'display-b') return 'display-b-theme'
  return 'control-theme'
})

// 连接状态（使用全局连接状态）
const connectionStatus = computed(() => {
  if (!globalConnectionState.isConnected) return 'disconnected'
  if (!globalConnectionState.isRegistered) return 'connecting'
  return 'connected'
})

const connectionText = computed(() => {
  if (!globalConnectionState.isConnected) return '离线'
  if (!globalConnectionState.isRegistered) return '连接中...'
  return '已连接'
})
</script>

<style scoped>
#app {
  height: 100vh;
  overflow: hidden;
  transition: background-color 0.3s;
}

.app-container {
  height: 100%;
  position: relative;
}

.connection-status {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 8px 16px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  z-index: 1000;
}

.status-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  transition: background-color 0.3s;
}

.connection-status.disconnected {
  background-color: rgba(255, 0, 0, 0.1);
  color: #ff4d4f;
}

.connection-status.disconnected .status-indicator {
  background-color: #ff4d4f;
  animation: blink 1s infinite;
}

.connection-status.connecting {
  background-color: rgba(255, 165, 0, 0.1);
  color: #fa8c16;
}

.connection-status.connecting .status-indicator {
  background-color: #fa8c16;
}

.connection-status.connected {
  background-color: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.connection-status.connected .status-indicator {
  background-color: #52c41a;
}

@keyframes blink {
  0% { opacity: 1; }
  50% { opacity: 0.3; }
  100% { opacity: 1; }
}
</style>

<style>
/* 全局样式 */
:root {
  --primary-color: #1890ff;
  --success-color: #52c41a;
  --warning-color: #fa8c16;
  --error-color: #ff4d4f;
  --text-color: #333;
  --bg-color: #f0f2f5;
  --border-color: #d9d9d9;
}

/* 控制端主题 */
.control-theme {
  --theme-primary: #1890ff;
  --theme-bg: #f0f2f5;
  --theme-card-bg: #ffffff;
  --theme-text: #333;
  background-color: var(--theme-bg);
  color: var(--theme-text);
}

/* 显示端A主题 */
.display-a-theme {
  --theme-primary: #1890ff;
  --theme-bg: #1a1a1a;
  --theme-card-bg: #262626;
  --theme-text: #ffffff;
  background-color: var(--theme-bg);
  color: var(--theme-text);
}

/* 显示端B主题 */
.display-b-theme {
  --theme-primary: #52c41a;
  --theme-bg: #001529;
  --theme-card-bg: #003a8c;
  --theme-text: #ffffff;
  background-color: var(--theme-bg);
  color: var(--theme-text);
}

body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

* {
  box-sizing: border-box;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style>