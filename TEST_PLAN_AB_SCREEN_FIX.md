# AB 屏独立系统修复验证计划

**修复时间**: 2026-04-26
**状态**: ✅ 后端编译成功，测试中

## 核心修复内容

### 1️⃣ 修复1：交替模式下只计算活跃屏幕（非活跃屏幕保持初始状态）

**文件**: `TimerEngine.java` 行 744-761

**修复说明**:
```java
// 交替模式：只计算活跃屏幕，保护非活跃屏幕的状态
boolean isAlternateMode = "alternate".equals(currentState.getAbMode());
if (!isAlternateMode) {
    // 非交替模式：无条件计算两个屏幕
    calculateScreenStage("A", totalElapsedSecondsInt);
    calculateScreenStage("B", totalElapsedSecondsInt);
} else {
    // 交替模式：只计算活跃屏幕
    if (isAScreenActive) {
        calculateScreenStage("A", totalElapsedSecondsInt);
        // B屏不计算，保持其初始绿灯状态
    } else {
        calculateScreenStage("B", totalElapsedSecondsInt);
        // A屏不计算，保持其初始绿灯状态
    }
}
```

**预期效果**:
- A屏活跃时：A屏从绿灯→黄灯→完成，B屏保持绿灯初始时间不变
- B屏活跃时：B屏从绿灯→黄灯→完成，A屏保持绿灯初始时间不变

---

### 2️⃣ 修复2：计时结束后允许屏幕切换（Finished状态特殊处理）

**文件**: `TimerEngine.java` 行 526 和 560-599

**修复说明**:

**第一步：改进切换检查逻辑**
```java
// 原逻辑：if (!isTimerRunning) { return; }
// 新逻辑：允许在finished状态下切换
if (!isTimerRunning && !"finished".equals(currentState.getStatus())) {
    log.warn("计时器未运行且未结束，无法切换屏幕");
    return;
}
```

**第二步：Finished状态特殊处理**
```java
if ("finished".equals(currentState.getStatus())) {
    // 恢复计时器状态
    isTimerRunning = true;
    isTimerPaused = false;
    currentState.setStatus("running");
    timerStartedAt = now;
    lastUpdateAt = now;
    screenElapsedAtSwitch = now;
    
    // 重置屏幕初始状态
    if ("individual".equals(currentEnhancedMatchType.getCategory())) {
        // 个人赛：新屏从竞赛时间开始，原屏清零
        if ("A".equals(newScreen)) {
            screenATimerRemaining = currentState.getCompetitionTime();
            screenBTimerRemaining = 0;
        } else {
            screenBTimerRemaining = currentState.getCompetitionTime();
            screenATimerRemaining = 0;
        }
    } else {
        // 团队赛：两个屏幕都恢复为竞赛初始时间
        screenATimerRemaining = currentState.getCompetitionTime();
        screenBTimerRemaining = currentState.getCompetitionTime();
    }
    
    // 启动新的计时任务
    startTimerTask();
}
```

**预期效果**:
- ✅ A屏倒计时结束（0秒），计时器status = "finished"
- ✅ 点击屏幕切换按钮，成功切换到B屏
- ✅ B屏从绿灯初始时间开始倒计时
- ✅ 个人赛：A屏清零，B屏新开始
- ✅ 团队赛：两个屏幕都恢复初始时间

---

## 测试场景

### 场景1：交替模式 - A屏进入黄灯时B屏保持绿灯初始状态

**步骤**:
1. 访问 http://localhost:3003
2. 控制面板选择"AB交替模式"
3. 选择比赛类型（例如："110秒准备+60秒比赛"）
4. 启动计时器
5. 等待倒计时进入黄灯阶段（约50秒时）

**预期**:
- **A屏显示区**:
  - 灯色：🟨 黄灯 (#FFFF00)
  - 阶段名称：黄灯
  - 剩余时间：10-15秒
  
- **B屏显示区**:
  - 灯色：🟢 绿灯 (#00FF00)
  - 阶段名称：比赛/竞赛
  - 剩余时间：60秒（初始竞赛时间，未倒计时）
  
- **控制面板预览**:
  - A屏独立灯色：黄灯
  - B屏独立灯色：绿灯
  - 显示的阶段名称分别对应

**验证**:
```
✅ screenAStageColor == #FFFF00 (黄灯)
✅ screenBStageColor == #00FF00 (绿灯)
✅ screenARemaining ~= 10-15秒
✅ screenBRemaining == 60秒
```

---

### 场景2：个人赛 - A屏完成后切换到B屏

**步骤**:
1. 选择"AB交替模式"
2. 选择"个人赛"比赛类型
3. 启动计时器
4. 等待A屏倒计时完成（0秒，status变为"finished"）
5. 点击屏幕切换按钮

**预期**:
- ✅ 倒计时结束，前端显示"计时已结束"状态
- ✅ 后端响应切换请求（不再报"计时器未运行"错误）
- ✅ B屏显示绿灯初始时间（60秒）开始倒计时
- ✅ A屏显示清零状态（0秒）

**WebSocket验证消息**:
```json
{
  "status": "running",
  "abMode": "alternate",
  "activeScreen": "B",
  "screenAStageColor": "#FF0000",
  "screenARemaining": 0,
  "screenBStageColor": "#00FF00",
  "screenBRemaining": 60,
  "screenAStatus": "paused",
  "screenBStatus": "running"
}
```

---

### 场景3：团队赛 - A屏完成后切换到B屏

**步骤**:
1. 选择"AB交替模式"
2. 选择"团队赛"比赛类型
3. 启动计时器
4. 等待A屏倒计时完成（0秒）
5. 点击屏幕切换按钮

**预期**:
- ✅ 倒计时结束，计时器status = "finished"
- ✅ 点击切换成功，B屏从绿灯初始时间（60秒）开始倒计时
- ✅ **重要**：A屏也恢复为初始时间（60秒），不是清零
  - 原因：团队赛是"暂停保留，继续倒计时"，两个屏幕都恢复初始时间供下一轮使用

**WebSocket验证消息**:
```json
{
  "status": "running",
  "activeScreen": "B",
  "screenARemaining": 60,  // 团队赛：恢复初始，不是0
  "screenBRemaining": 60,
  "screenAStatus": "paused",  // A屏暂停（不活跃）
  "screenBStatus": "running"   // B屏运行（活跃）
}
```

---

### 场景4：同步模式 - 两个屏幕显示相同信息

**步骤**:
1. 切换到"同步模式"
2. 启动计时器
3. 观察A屏和B屏的灯色和阶段名称

**预期**:
- ✅ A屏和B屏灯色相同
- ✅ A屏和B屏阶段名称相同
- ✅ A屏和B屏倒计时时间相同

**验证**:
```
✅ screenAStageColor == screenBStageColor
✅ screenAStageName == screenBStageName
✅ screenARemaining == screenBRemaining
```

---

### 场景5：控制面板屏幕预览 - 独立显示A屏和B屏信息

**步骤**:
1. 交替模式，A屏活跃，倒计时进入黄灯
2. 观察控制面板的屏幕预览区

**预期**:
- ✅ **A屏预览**：显示黄灯颜色和对应的剩余时间
- ✅ **B屏预览**：显示绿灯颜色和对应的初始竞赛时间
- ✅ 预览中两个屏幕的灯色和时间独立显示，互不影响

---

## 关键验证点

### 后端数据验证

在浏览器控制台或WebSocket消息监听中，验证以下字段：

```javascript
// 每次状态更新都应包含这些字段
{
  // 全局字段（用于声音提示）
  currentStageName: "黄灯",
  currentStageColor: "#FFFF00",
  currentStageRemaining: 12,
  
  // A屏独立字段（A屏显示用）
  screenAStageColor: "#FFFF00",
  screenAStageName: "黄灯",
  screenAStageRemaining: 12,
  screenARemaining: 12,
  screenAStatus: "running",
  
  // B屏独立字段（B屏显示用）
  screenBStageColor: "#00FF00",
  screenBStageName: "比赛",
  screenBStageRemaining: 60,
  screenBRemaining: 60,
  screenBStatus: "paused",
  
  // 屏幕控制信息
  abMode: "alternate",
  activeScreen: "A",
  screenAEnabled: true,
  screenBEnabled: true
}
```

### 前端显示验证

**EnhancedDisplayViewA.vue**:
```javascript
// 应该显示A屏的独立数据
currentLightColor = screenAStageColor  // #FFFF00
displayRemaining = 12
stageName = screenAStageName           // "黄灯"
```

**EnhancedDisplayViewB.vue**:
```javascript
// 应该显示B屏的独立数据
currentLightColor = screenBStageColor  // #00FF00
displayRemaining = 60
stageName = screenBStageName           // "比赛"
```

**EnhancedControlView.vue（预览）**:
```javascript
screenALightColor = screenAStageColor  // #FFFF00
screenBLightColor = screenBStageColor  // #00FF00
```

---

## 常见问题排查

### Q1: B屏仍然跟随A屏变黄灯

**原因**: updateTimerTask()中的calculateScreenStage()仍在无条件调用
**解决**: 检查line 744-761是否正确实现了条件判断

### Q2: A屏完成后点击切换提示"计时器未运行"

**原因**: toggleABScreen()中的检查条件未更新
**解决**: 检查line 526是否使用了新的条件 `!isTimerRunning && !"finished".equals(currentState.getStatus())`

### Q3: 切换后B屏不倒计时，仍显示初始时间

**原因**: Finished状态特殊处理中isTimerRunning未恢复或startTimerTask()未调用
**解决**: 检查line 560-599的finished块中是否正确设置了isTimerRunning=true和调用startTimerTask()

### Q4: 控制面板预览中A屏和B屏灯色相同

**原因**: EnhancedControlView.vue的screenALightColor和screenBLightColor未使用独立字段
**解决**: 检查是否使用了screenAStageColor和screenBStageColor

---

## 编译和启动验证

✅ 后端编译成功（2026-04-26 18:57）
```
[INFO] BUILD SUCCESS
[INFO] Total time:  35.498 s
```

✅ 后端启动成功（port 8080）
```
$ curl http://localhost:8080/api/timer/status
{"status":"idle","abMode":"alternate",...}
```

✅ 前端启动成功（port 3003）
```
➜  Local:   http://localhost:3003/
```

---

## 测试完成清单

- [ ] 场景1：交替模式A屏黄灯时B屏保持绿灯
- [ ] 场景2：个人赛A屏完成后切换到B屏
- [ ] 场景3：团队赛A屏完成后切换到B屏
- [ ] 场景4：同步模式两屏显示相同
- [ ] 场景5：控制面板预览独立显示A屏和B屏
- [ ] 后端WebSocket消息包含完整的screenA*/screenB*字段
- [ ] 前端EnhancedDisplayViewA使用screenAStageColor
- [ ] 前端EnhancedDisplayViewB使用screenBStageColor
- [ ] 前端EnhancedControlView预览使用独立灯色
- [ ] 音频提示正常工作（基于全局字段）
- [ ] 屏幕模式切换（sync/alternate/only_a/only_b）正常
- [ ] 重置按钮恢复所有状态

---

## 修复提交信息

本轮修复涉及的关键commit：
- 条件calculateScreenStage调用实现
- Finished状态特殊处理实现
- 前端配套的显示逻辑验证
