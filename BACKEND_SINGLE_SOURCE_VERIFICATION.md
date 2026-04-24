# 后端单一数据源强同步 - 实现验证指南

## 当前状态（提交 c72d2eb）

✅ **前端修复已完成**
- 所有时间计算逻辑已移除
- protectedFields 保护机制已移除
- 前端现在只发送原始值，无条件接收后端状态

## 后端改进清单（需要后端团队实施）

### 必须改进的项目

#### 1. 时间配置更新处理

**当前状态**：需要确认后端如何处理 `timer/set-time-config` 消息

**改进要求**：
```java
@MessageMapping("/app/timer/set-time-config")
public void handleTimeConfig(TimeConfigDTO config) {
    // 步骤1：接收原始用户输入
    int prep = config.getPreparation();      // 例：20
    int comp = config.getCompetition();      // 例：180
    int yellow = config.getYellowLight();    // 例：30
    
    // 步骤2：更新后端状态
    timerState.setPreparationTime(prep);
    timerState.setCompetitionTime(comp);
    timerState.setYellowLightTime(yellow);
    
    // 步骤3：✅ 后端计算初始倒计时
    int totalSeconds = prep + comp;  // 20 + 180 = 200
    timerState.setCurrentStageRemaining(totalSeconds);
    timerState.setTotalRemaining(totalSeconds);
    
    // 步骤4：✅ 立即广播完整状态给所有客户端
    broadcastCompleteState();
    
    logService.info("已更新时间配置并广播", {
        prep: prep,
        comp: comp,
        yellow: yellow,
        calculated_total: totalSeconds
    });
}

private void broadcastCompleteState() {
    // 发送包含所有计算字段的完整状态
    TimerStateDTO fullState = buildCompleteState();
    webSocketService.broadcast("/topic/timer-state", fullState);
}

private TimerStateDTO buildCompleteState() {
    TimerStateDTO state = new TimerStateDTO();
    // ✅ 确保包含所有字段
    state.setPreparationTime(timerState.getPreparationTime());
    state.setCompetitionTime(timerState.getCompetitionTime());
    state.setYellowLightTime(timerState.getYellowLightTime());
    state.setCurrentStageRemaining(timerState.getCurrentStageRemaining());
    state.setTotalRemaining(timerState.getTotalRemaining());
    state.setStatus(timerState.getStatus());
    // ... 其他字段
    return state;
}
```

#### 2. 广播机制验证

**要求**：
- [ ] 每次接收 `timer/set-time-config` 后，立即广播到 `/topic/timer-state`
- [ ] 广播包含完整的 TimerStateDTO（所有计算字段都已填充）
- [ ] 没有条件判断或过滤（前端需要完整状态）
- [ ] 服务器日志记录：接收值、计算值、广播内容

#### 3. 状态初始化

**要求**：
- [ ] 应用启动时，设置默认配置（准备10秒，比赛180秒，黄灯30秒）
- [ ] 第一次客户端连接时，广播当前状态
- [ ] 任何客户端请求状态时，立即返回最新的完整状态

## 前端测试验证（开发阶段）

### 测试 1：配置修改同步

**步骤**：
1. 打开控制台（localhost:5173/control）
2. 修改"准备"时间为 **25 秒**
3. 修改"比赛"时间为 **185 秒**
4. 修改"黄灯"时间为 **35 秒**
5. 点击输入框外区域（失焦）

**验证**：
- [ ] 浏览器 DevTools → Network → WS 标签
  - 发送消息应该只包含：`{prep: 25, comp: 185, yellow: 35}`
  - **不包含** `currentStageRemaining` 或任何计算字段
  
- [ ] 浏览器 DevTools → Console（过滤 "已应用后端状态"）
  - 应该看到日志：`已应用后端状态 (prep: 25, comp: 185, yellow: 35, remaining: 210)`

- [ ] 屏幕预览区
  - A屏预览倒计时显示：**210**（25+185）
  - B屏预览倒计时显示：**210**
  - 标签显示"剩余秒数"

### 测试 2：三屏一致性

**步骤**：
1. 修改配置（如上）
2. 打开浏览器三个标签页：
   - 标签页A：控制端（localhost:5173/control）
   - 标签页B：A屏显示（localhost:5173/display-a）
   - 标签页C：B屏显示（localhost:5173/display-b）

**验证**：
- [ ] 在标签页A修改配置失焦
- [ ] 标签页B的倒计时立即显示 210
- [ ] 标签页C的倒计时立即显示 210
- [ ] 标签页A预览也显示 210
- [ ] 三处显示完全相同 ✅

### 测试 3：无计算验证

**步骤**：
1. 打开浏览器 DevTools → Sources 标签
2. 在 `enhancedTimer.js` 第150行附近设置断点（WebSocket 处理）
3. 修改配置并失焦
4. 步过代码，查看 timerState 的值

**验证**：
- [ ] 在 `Object.assign(timerState, data)` 后
- [ ] `timerState.preparationTime` = 用户输入的值
- [ ] `timerState.currentStageRemaining` = 后端发送的值
- [ ] 没有看到 `prep + comp` 的计算操作

### 测试 4：极端情况

**测试用例**：

| 准备(秒) | 比赛(秒) | 黄灯(秒) | 预期倒计时 |
|---------|---------|---------|----------|
| 1 | 1 | 1 | 2 |
| 0 | 0 | 0 | 0 |
| 300 | 600 | 60 | 900 |
| 10 | 180 | 30 | 190 |

**验证步骤**：
- [ ] 修改配置为上表中的值
- [ ] 失焦
- [ ] 检查预览倒计时是否显示预期值
- [ ] 重复所有用例

## 集成测试清单（完整验证）

### 前端集成
- [ ] 修改任意配置 → 预览稳定显示新值
- [ ] 无"短暂更新后回退"现象
- [ ] A屏、B屏、预览显示一致
- [ ] 倒计时显示为纯数字秒数
- [ ] 浏览器控制台无错误

### 后端集成
- [ ] 服务器启动时输出时间配置初值
- [ ] 接收配置消息时输出日志
- [ ] 广播前验证计算的 currentStageRemaining
- [ ] 日志显示：接收值 → 计算值 → 广播值

### 通信验证
- [ ] 发送消息只包含原始值（无计算字段）
- [ ] 接收消息包含完整计算结果
- [ ] 无任何字段被过滤或修改

## 故障排查

### 问题1：预览仍显示旧值

**原因排查**：
1. 后端是否收到消息？ → 检查服务器日志
2. 后端是否计算了新值？ → 检查日志中的计算结果
3. 后端是否广播了？ → 检查 WebSocket 消息
4. 前端是否接收了？ → 检查浏览器 Console 的"已应用后端状态"日志

### 问题2：预览显示不一致（A屏 vs B屏）

**原因排查**：
1. 所有客户端是否订阅了 `/topic/timer-state`？
2. 后端是否向所有订阅者广播？
3. 是否存在客户端级别的消息过滤？

### 问题3：发送消息包含计算字段

**原因排查**：
1. 代码中是否仍有对 `prep + comp` 的计算？
   ```bash
   grep -r "preparationTime.*competitionTime\|competitionTime.*preparationTime" frontend/src
   ```
2. 是否调用了 `syncTimeConfigToPreview`？
3. 检查 `onTimeConfigBlur` 和 `updateTimeConfig` 的实现

## 性能考虑

**优化建议**：
- [ ] 后端可以对频繁的配置更新进行防抖（如果需要）
- [ ] 广播时只包含有变化的字段（可选优化）
- [ ] 添加配置变更的版本号，避免重复处理

## 审计追踪

**需要记录的日志**：
```
[配置更新]
时间: 2026-04-25 10:30:45
接收值: {prep: 25, comp: 185, yellow: 35}
计算值: totalSeconds = 210
广播目标: /topic/timer-state
广播内容: {preparationTime: 25, competitionTime: 185, yellowLightTime: 35, currentStageRemaining: 210, ...}
受影响客户端: 4个

[显示同步]
客户端: control (127.0.0.1:12345)
接收消息: {prep: 25, comp: 185, ..., remaining: 210}
显示更新: 预览倒计时 = 210
```

## 完成标志

当以下所有条件都满足时，修复完成 ✅：

- [ ] 修改配置不再出现"短暂更新后回退"
- [ ] 所有屏幕显示的数值完全一致
- [ ] 前端代码中找不到任何时间计算逻辑
- [ ] WebSocket 消息中发送值不包含计算结果
- [ ] 后端日志明确显示计算和广播过程
- [ ] 用户信任度恢复 ✨

## 下一步

1. **后端改进**：实施上述 Java 代码改进
2. **集成测试**：运行上述所有测试用例
3. **监控验证**：在生产环境中监控相关日志
4. **文档更新**：将"后端单一数据源"原则纳入开发文档
