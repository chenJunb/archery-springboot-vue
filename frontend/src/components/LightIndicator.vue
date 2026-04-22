<template>
  <div class="light-indicator-container">
    <div class="light-indicator" :style="lightStyle" :class="lightClass">
      <div class="light-shine"></div>
      <div class="light-border"></div>
    </div>
    <div class="light-label">{{ colorLabel }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  color: {
    type: String,
    required: true,
    validator: (value) => ['red', 'green', 'yellow', 'standby'].includes(value)
  }
})

const colorMap = {
  red: { bg: '#FF0000', shadow: '0 0 40px rgba(255, 0, 0, 0.8)', label: '准备' },
  green: { bg: '#00FF00', shadow: '0 0 40px rgba(0, 255, 0, 0.8)', label: '比赛' },
  yellow: { bg: '#FFFF00', shadow: '0 0 40px rgba(255, 255, 0, 0.8)', label: '黄灯' },
  standby: { bg: '#CCCCCC', shadow: 'none', label: '待机' }
}

const lightStyle = computed(() => ({
  backgroundColor: colorMap[props.color].bg,
  boxShadow: colorMap[props.color].shadow
}))

const lightClass = computed(() => props.color)

const colorLabel = computed(() => colorMap[props.color].label)
</script>

<style scoped>
.light-indicator-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.light-indicator {
  width: 180px;
  height: 180px;
  border-radius: 50%;
  position: relative;
  transition: all 0.3s ease;
  border: 4px solid rgba(0, 0, 0, 0.3);
}

.light-shine {
  position: absolute;
  width: 45%;
  height: 45%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.6), transparent);
  border-radius: 50%;
  top: 8%;
  left: 8%;
  pointer-events: none;
}

.light-border {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 2px solid rgba(0, 0, 0, 0.2);
}

.light-label {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  letter-spacing: 2px;
}

.light-indicator.red {
  border-color: #CC0000;
}

.light-indicator.green {
  border-color: #00CC00;
}

.light-indicator.yellow {
  border-color: #CCCC00;
}

.light-indicator.standby {
  border-color: #999999;
}

/* 动画效果 */
@keyframes pulse {
  0% {
    box-shadow: inset 0 0 0 0 rgba(0, 0, 0, 0);
  }
  50% {
    box-shadow: inset 0 0 0 4px rgba(0, 0, 0, 0.1);
  }
  100% {
    box-shadow: inset 0 0 0 0 rgba(0, 0, 0, 0);
  }
}

.light-indicator.red {
  animation: pulse 1.5s infinite;
}

.light-indicator.green {
  animation: pulse 0.5s infinite;
}
</style>
