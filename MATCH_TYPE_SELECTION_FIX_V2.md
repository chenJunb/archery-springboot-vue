---
name: 比赛类型选择时序问题二次修复
description: 修复了watch条件判断导致selectMatchType消息未被发送的问题
type: feedback
---

## 问题现象

用户点击"开始计时"仍报错"比赛类型未选择"，虽然前端已执行了自动选择逻辑。

## 根本原因

前端的 watch 条件设置不当：

```javascript
// ❌ 原始条件
if (isConnected && enhancedMatchTypes.value.length > 0 && !selectedMatchType.value)
```

问题流程：
1. `fetchEnhancedMatchTypes()` 执行，立即设置 `selectedMatchType.value = enhancedMatchTypes.value[0].id`
2. 此时 WebSocket 还未连接，所以 `loadMatchTypeConfig()` 未被调用
3. 连接建立后，watch 触发
4. 但此时 `selectedMatchType.value` 已有值，条件 `!selectedMatchType.value` 为 false
5. **watch 不执行，selectMatchType 消息永不发送**

## 修复方案

### 修复1：添加状态追踪标记
**文件**: `frontend/src/views/EnhancedControlView.vue` (L909-910)

```javascript
// 跟踪是否已发送 selectMatchType 消息
let matchTypeConfigSent = false
```

### 修复2：改进 watch 条件判断
**文件**: `frontend/src/views/EnhancedControlView.vue` (L912-933)

```javascript
// ✅ 改进的条件
watch(() => timerStore.connectionState.isConnected, (isConnected) => {
  if (isConnected && enhancedMatchTypes.value.length > 0 && !matchTypeConfigSent) {
    // ... 现在基于是否已发送，而不是 selectedMatchType 是否为空
    loadMatchTypeConfig(currentMatchType.value)
    matchTypeConfigSent = true
  }
})
```

**核心改变**: 
- 从 `!selectedMatchType.value` 改为 `!matchTypeConfigSent`
- 这样即使比赛类型已选中，只要配置未发送就会执行 `loadMatchTypeConfig()`

### 修复3：前端添加日志
**文件**: `frontend/src/stores/enhancedTimer.js` (L556-558)

```javascript
const selectMatchType = (matchTypeId) => {
  logService.info('📤 发送选择比赛类型消息', { matchTypeId })
  sendGlobalWebSocketMessage('timer/select-match-type', { matchTypeId })
}
```

### 修复4：后端增强日志
**文件**: `backend/src/main/java/com/archery/timer/controller/EnhancedWebSocketController.java`

添加详细的日志追踪整个流程：
- 收到请求时的日志
- 调用 timerEngine.selectMatchType() 的日志
- 验证是否成功设置的日志

## 修复前后对比

### 修复前
```
页面加载
  ↓
比赛类型加载 → selectedMatchType 被设置
  ↓
WebSocket 连接
  ↓
watch 触发 → 检查 !selectedMatchType.value → false
  ↓
❌ loadMatchTypeConfig 不执行
  ↓
selectMatchType 消息未发送
  ↓
后端 currentMatchType 为 null
```

### 修复后
```
页面加载
  ↓
比赛类型加载 → selectedMatchType 被设置 → matchTypeConfigSent = false
  ↓
WebSocket 连接
  ↓
watch 触发 → 检查 !matchTypeConfigSent → true
  ↓
✅ loadMatchTypeConfig 执行
  ↓
selectMatchType 消息被发送
  ↓
后端 currentMatchType 被正确设置
  ↓
用户可以正常开始计时
```

## 关键改进

1. **独立的状态追踪**: `matchTypeConfigSent` 标记确保配置只发送一次
2. **更清晰的语义**: 检查"配置是否已发送"比检查"是否已选择"更准确
3. **完整的日志**: 前后端都添加了详细日志便于调试
4. **容错设计**: 即使前面的流程有问题，watch 仍能确保配置被发送

## 验证步骤

1. 打开浏览器 http://localhost:3000/
2. 等待 2-3 秒
3. 查看 Console 应显示:
   ```
   📤 发送选择比赛类型消息 {matchTypeId: "personal_ranking"}
   ✅ 连接已建立，发送比赛类型配置
   ```
4. 查看后端日志应显示:
   ```
   📨 收到选择比赛类型请求: matchTypeId=personal_ranking
   📋 调用 timerEngine.selectMatchType(personal_ranking)
   ✅ 比赛类型已成功设置: personal_ranking
   ```
5. 点击"开始计时" → 应正常启动，无错误

## 预期效果

- ✅ selectMatchType 消息正确发送到后端
- ✅ 后端成功设置比赛类型
- ✅ 用户可以正常开始计时
- ✅ 不再出现"比赛类型未选择"错误
