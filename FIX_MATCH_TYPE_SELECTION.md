# 修复比赛类型自动选择问题

## 问题现象
- 前端成功连接 WebSocket (clientId 正确)
- 用户点击"开始计时"
- 后端报错："比赛类型未选择"
- 控制端无法开始计时

## 根本原因
前端有两个异步流程并行运行：
1. **WebSocket 连接建立** - 需要 1-2 秒
2. **REST API 获取比赛类型** - 需要 ~200ms

这两个流程的完成顺序不确定。当比赛类型加载完成时，WebSocket 可能还未连接，导致 `selectMatchType()` 消息未被发送到后端。

## 修复方案

### 修改1：优化 `fetchEnhancedMatchTypes()` 函数
**文件**: `frontend/src/views/EnhancedControlView.vue` (第609-628行)

```javascript
// ✅ 只有在连接已建立时才立即配置，否则等待watch触发
if (timerStore.connectionState.isConnected) {
  loadMatchTypeConfig(enhancedMatchTypes.value[0])
}
```

这样当比赛类型先加载完成时，不会立即尝试发送 selectMatchType 消息。

### 修改2：添加连接状态监听器
**文件**: `frontend/src/views/EnhancedControlView.vue` (新增 watch)

```javascript
// ✅ 监听连接状态，当连接建立时如果还未选择比赛类型则自动选择第一个
watch(() => timerStore.connectionState.isConnected, (isConnected) => {
  if (isConnected && enhancedMatchTypes.value.length > 0 && !selectedMatchType.value) {
    logService.debug('✅ 连接已建立，自动选择第一个比赛类型')
    selectedMatchType.value = enhancedMatchTypes.value[0].id
    currentMatchType.value = enhancedMatchTypes.value[0]
    loadMatchTypeConfig(enhancedMatchTypes.value[0])
  }
})
```

这个 watch 监听连接状态的变化。当从 `false` 变为 `true` 时，自动选择已加载的第一个比赛类型。

## 解决的场景

### 场景1：比赛类型先加载，WebSocket 后连接
```
t=0:   fetchEnhancedMatchTypes() 开始
t=200: 比赛类型加载完成 ← 不立即调用 loadMatchTypeConfig()
t=1000: WebSocket 连接完成 → watch 触发 → 调用 loadMatchTypeConfig()
✅ selectMatchType 消息被正确发送到后端
```

### 场景2：WebSocket 先连接，比赛类型后加载
```
t=0:   fetchEnhancedMatchTypes() 开始
t=1000: WebSocket 连接完成 → watch 触发但 enhancedMatchTypes 还为空
t=1200: 比赛类型加载完成 → 立即调用 loadMatchTypeConfig()（因为已连接）
✅ selectMatchType 消息被正确发送到后端
```

## 验证步骤

1. 打开浏览器访问 http://localhost:3000/
2. 按 F12 打开 Console
3. 等待日志显示：
   ```
   ✅ 连接已建立，自动选择第一个比赛类型
   ```
4. 页面的比赛类型下拉框应显示"个人排名赛"（默认第一个）
5. 点击"开始计时"按钮 - 应该可以正常开始，不再报"比赛类型未选择"错误

## 预期结果
- ✅ 页面加载时自动选择第一个比赛类型
- ✅ 后端接收到 selectMatchType 消息
- ✅ 用户可以正常开始计时
- ✅ 计时器状态实时同步到显示屏

## 技术说明
**为什么要用 watch 而不是 async/await？**

1. Vue 的响应式系统最擅长处理状态变化驱动的逻辑
2. 使用 watch 确保即使两个流程完成顺序改变，也能正确处理
3. 代码更清晰 - "当连接状态变为 true 时，执行..."
4. 避免额外的 Promise 链，更符合 Vue 3 Composition API 风格

## 影响范围
- 仅修改了前端的初始化逻辑
- 不影响后端任何代码
- 完全向后兼容 - 用户仍然可以手动改变比赛类型
