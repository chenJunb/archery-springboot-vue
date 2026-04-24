# 时间配置数据持久化修复 - 完整方案

## 问题现象
修改左侧「时间配置」模块下的任意时间数值（准备/比赛/黄灯时长），当输入框失焦的瞬间，右侧「屏幕预览」区域的倒计时数字会短暂更新为新配置值，但随后立即自动变回修改前的默认初始时间。

## 根本原因分析

### 原始数据流（有缺陷）
```
用户修改输入框
  → preparationTime.value = 20

失焦 @blur
  → onTimeConfigBlur()
  → syncTimeConfigToPreview(20, ...)
  → timerState.currentStageRemaining = 20
  ✅ 预览短暂显示新值 20

同时发送 WebSocket 消息到服务器
  → 服务器处理并返回状态消息

WebSocket 处理器收到响应
  → 对 timerState 进行 Object.assign()
  → protectedFields 备份/恢复保护字段
  → timerState.currentStageRemaining 不变（被保护）✓

但是... watch 监听触发
  → watch(() => timerState, ...)
  → 检测 timerState.preparationTime !== preparationTime.value
  → 执行: preparationTime.value = timerState.preparationTime（旧值）
  ❌ 导致用户输入被覆盖

数据回退发生
```

### 关键问题：watch 反向同步
```javascript
// ❌ 旧代码
watch(() => timerState, (newState) => {
  if (newState.preparationTime !== undefined && newState.preparationTime !== preparationTime.value) {
    preparationTime.value = newState.preparationTime  // ❌ 允许反向覆盖
  }
})
```

## 修复方案

### 1. 移除 watch 反向同步（关键修复）
**原则**：本地变量应作为单一事实来源

```javascript
// ✅ 新代码
watch(() => timerState, (newState) => {
  // 其他字段继续同步...
  soundEnabled.value = newState.soundEnabled
  volume.value = newState.volume || 80
  
  // ✅ 不同步时间配置回本地变量
  // 原因：用户修改的本地变量应该驱动timerState，而不是反过来
})
```

### 2. 统一预览同步机制
使用 `syncTimeConfigToPreview()` 在关键点同步：

```javascript
// 在 Store 中定义
const syncTimeConfigToPreview = (prep, comp, yellow) => {
  timerState.preparationTime = prep || 10
  timerState.competitionTime = comp || 180
  timerState.yellowLightTime = yellow || 30
  
  const totalSeconds = (prep || 10) + (comp || 180)
  timerState.currentStageRemaining = totalSeconds
  timerState.localDisplayRemaining = totalSeconds
}
```

**调用时机**：
- ✅ `onTimeConfigBlur()` - 用户编辑后失焦
- ✅ `updateTimeConfig()` - 点击按钮或比赛类型变化
- ✅ `loadMatchTypeConfig()` - 初始化比赛类型

### 3. 关键调用点

#### 时间配置失焦处理
```javascript
const onTimeConfigBlur = () => {
  if (!timerStore.connectionState.isConnected) return
  
  // ✅ 立即同步到预览（本地状态，无延迟）
  timerStore.syncTimeConfigToPreview(
    preparationTime.value,
    competitionTime.value,
    yellowLightTime.value
  )
  
  // 发送到服务器保持同步
  timerStore.sendGlobalWebSocketMessage('timer/set-time-config', config)
}
```

#### 更新时间配置
```javascript
const updateTimeConfig = () => {
  if (!timerStore.connectionState.isConnected) return
  
  // ✅ 立即同步到预览
  timerStore.syncTimeConfigToPreview(
    preparationTime.value,
    competitionTime.value,
    yellowLightTime.value
  )
  
  // 发送到服务器
  timerStore.sendGlobalWebSocketMessage('timer/set-time-config', config)
}
```

#### 初始化比赛类型
```javascript
const loadMatchTypeConfig = (matchType) => {
  preparationTime.value = matchType.preparationTime || 10
  competitionTime.value = matchType.competitionTime || 180
  yellowLightTime.value = matchType.yellowLightTime || 30
  
  // ✅ 立即同步预览
  timerStore.syncTimeConfigToPreview(
    preparationTime.value,
    competitionTime.value,
    yellowLightTime.value
  )
  
  // 发送到服务器
  updateTimeConfig()
}
```

## 修复后的数据流

```
用户修改输入框
  → preparationTime.value = 20 (本地变量改变)

失焦 @blur
  → onTimeConfigBlur()
  → syncTimeConfigToPreview(20, 180, 30)
    → timerState.preparationTime = 20
    → timerState.currentStageRemaining = 200
    → timerState.localDisplayRemaining = 200
  ✅ 预览立即显示新值

同时发送 WebSocket 消息
  → 服务器接收并返回状态

WebSocket 处理器响应
  → protectedFields 保护时间配置字段
  → 服务器旧值不会覆盖本地值
  → timerState 保持最新的配置

watch 监听（无反向同步）
  → 只更新非时间配置字段
  → 不会覆盖本地输入框值
  
结果
  ✅ 预览保持稳定显示新值
  ✅ 本地变量保有用户最新输入
  ✅ 无数据回退现象
```

## 修复提交历史

| 提交 | 说明 |
|------|------|
| `e199d07` | 倒计时改为纯数字秒数 |
| `95423ee` | AB屏预览页倒计时纯数字化 |
| `3dbb678` | 添加重置计时器调用（初始版） |
| `149972e` | 添加失焦事件同步机制 |
| `6c18827` | ✅ **关键修复：移除watch反向同步** |
| `da0053c` | 初始化时同步预览 |

## 验证清单

- [ ] 修改准备时间 → 失焦 → 预览稳定显示新值
- [ ] 修改比赛时间 → 失焦 → 预览稳定显示新值  
- [ ] 修改黄灯时间 → 失焦 → 预览稳定显示新值
- [ ] 修改任意配置后 → A屏预览倒计时显示正确初值
- [ ] 修改任意配置后 → B屏预览倒计时显示正确初值
- [ ] 切换比赛类型 → 预览立即显示对应配置的倒计时
- [ ] 倒计时显示为纯数字秒数（无格式化）
- [ ] 无"短暂更新后又回退"的现象

## 技术要点

### 数据持久化保障
1. **本地变量** - 用户输入的最新值
2. **timerState** - 预览显示的最新值
3. **protectedFields** - WebSocket 层保护，防止服务器覆盖

### 预览显示逻辑
```javascript
getDisplayRemaining() {
  if (running && localDisplayRemaining > 0) {
    return localDisplayRemaining  // 实时倒计时
  }
  return currentStageRemaining  // 初值（非运行状态）
}
```

### 状态保护机制
- WebSocket 接收消息时备份 protectedFields
- 合并服务器状态到 timerState
- 恢复受保护的字段，防止被覆盖

## 未来改进空间

1. 可以添加用户修改指示符，显示有未保存的更改
2. 可以添加自动保存延迟，确保多个快速修改被合并为一次请求
3. 可以添加配置版本管理，支持撤销/重做
