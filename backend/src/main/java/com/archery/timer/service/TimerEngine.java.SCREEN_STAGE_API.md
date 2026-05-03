# TimerEngine AB 屏独立剩余时长计算 API 文档

## 新增方法概览

### 1. `getScreenStageRemainingTime(String targetScreen)`
获取指定屏幕当前运行阶段的独立剩余时长。

#### 方法签名
```java
public synchronized int getScreenStageRemainingTime(String targetScreen)
```

#### 参数说明
- **targetScreen**（String）：目标屏幕，取值为 `"A"` 或 `"B"`
  - `"A"`：A屏
  - `"B"`：B屏

#### 返回值
- **int**：当前阶段的独立剩余时长（单位：秒）
  - 最小值为 0（防止负数）
  - 在屏幕未初始化或比赛类型未选择时返回 0

#### 核心逻辑

##### 场景1：屏幕正在运行（screenTimerPausedAt == 0）
1. 计算屏幕从切换时起已经过的时间：`elapsedSinceScreenStart = (now - screenElapsedAtSwitch) / 1000`
2. 应用暂停补偿（如有多次暂停/继续）：`elapsedWithPauseCompensation = elapsedSinceScreenStart - (screenTotalPausedDuration / 1000)`
3. 根据当前是准备阶段还是比赛阶段分别处理：
   - **准备阶段**：`剩余 = prepTime - 已过秒数`
   - **比赛阶段**：直接返回屏幕的独立计时值 `screenXTimerRemaining`

##### 场景2：屏幕已暂停（screenTimerPausedAt != 0）
- 直接返回暂停时保存的剩余时长 `screenXTimerRemaining`
- 该值已包含暂停补偿

#### 使用示例
```java
// 获取 A 屏当前阶段的剩余时长
int remainingTimeA = timerEngine.getScreenStageRemainingTime("A");
System.out.println("A屏剩余: " + remainingTimeA + "秒");

// 获取 B 屏当前阶段的剩余时长
int remainingTimeB = timerEngine.getScreenStageRemainingTime("B");
System.out.println("B屏剩余: " + remainingTimeB + "秒");
```

---

### 2. `getScreenStageInfo(String targetScreen)`
获取指定屏幕的完整独立阶段信息。

#### 方法签名
```java
public synchronized Map<String, Object> getScreenStageInfo(String targetScreen)
```

#### 参数说明
- **targetScreen**（String）：目标屏幕，取值为 `"A"` 或 `"B"`

#### 返回值
- **Map<String, Object>**：包含以下字段的 Map 对象
  - `stageIndex`（Integer）：阶段索引（0=准备，1=比赛）
  - `stageName`（String）：阶段名称（"准备"、"比赛"、"黄灯" 等）
  - `stageColor`（String）：阶段颜色（"#FF0000"=红灯、"#00FF00"=绿灯、"#FFFF00"=黄灯）
  - `stageRemaining`（Integer）：当前阶段剩余时长（秒）（通过 `getScreenStageRemainingTime()` 计算）
  - `stageDuration`（Integer）：当前阶段总时长（秒）
  - `stageElapsed`（Integer）：当前阶段已过时长（秒）
  - `status`（String）：屏幕运行状态（"running"、"paused"、"finished" 等）

#### 返回 null 的情况
- 参数无效（不是 "A" 或 "B"）
- 比赛类型未选择

#### 使用示例
```java
// 获取 A 屏的完整阶段信息
Map<String, Object> stageInfoA = timerEngine.getScreenStageInfo("A");
if (stageInfoA != null) {
    System.out.println("A屏阶段: " + stageInfoA.get("stageName"));
    System.out.println("A屏剩余: " + stageInfoA.get("stageRemaining") + "秒");
    System.out.println("A屏颜色: " + stageInfoA.get("stageColor"));
}

// 获取 B 屏的完整阶段信息
Map<String, Object> stageInfoB = timerEngine.getScreenStageInfo("B");
if (stageInfoB != null) {
    System.out.println("B屏当前状态: " + stageInfoB);
}
```

---

## 实现细节

### 暂停补偿机制
- 当屏幕暂停时，系统记录暂停开始时刻：`screenAPauseStartTime` 或 `screenBPauseStartTime`
- 恢复时，计算暂停时长并累计到 `screenATotalPausedDuration` 或 `screenBTotalPausedDuration`
- 计算剩余时长时自动扣除累计暂停时长，确保时间流逝不会被计入暂停的时间

### 屏幕独立系统
- 每个屏幕维护独立的：
  - 剩余时长：`screenATimerRemaining`、`screenBTimerRemaining`
  - 暂停状态：`screenATimerPausedAt`、`screenBTimerPausedAt`
  - 累计暂停时长：`screenATotalPausedDuration`、`screenBTotalPausedDuration`
  - 阶段信息：`screenA/BCurrentStageIndex`、`screenA/BCurrentStageName` 等

### 线程安全
- 两个方法均使用 `synchronized` 关键字，确保多线程安全
- 与主计时逻辑（updateTimerState）互不干扰

---

## 场景应用

### 场景1：AB交替模式 - 屏幕切换时
```java
// 切换屏幕前获取新屏幕当前阶段的剩余时长
toggleABScreen();
int newScreenRemaining = timerEngine.getScreenStageRemainingTime("B"); // 切换到 B 屏
System.out.println("B屏准备开始计时，剩余: " + newScreenRemaining + "秒");
```

### 场景2：暂停/恢复时更新显示
```java
// 暂停时获取当前屏幕的剩余时长和颜色
pauseTimer();
Map<String, Object> info = timerEngine.getScreenStageInfo("A");
updateDisplayColor(info.get("stageColor")); // 更新前端显示颜色

// 恢复时，剩余时长自动应用暂停补偿
startTimer("controlClientId");
int remainingAfterPause = timerEngine.getScreenStageRemainingTime("A");
// 确保显示的时间是准确的（包含暂停补偿）
```

### 场景3：前端实时倒计时
```java
// 前端定时调用获取当前剩余时长（减轻后端广播压力）
setInterval(() => {
    const remainingA = timerEngine.getScreenStageRemainingTime("A");
    const remainingB = timerEngine.getScreenStageRemainingTime("B");
    updateDisplayCountdown(remainingA, remainingB);
}, 100); // 每 100ms 更新一次
```

---

## 日志输出示例

### 正常运行
```
[A屏独立计算] 比赛阶段，当前剩余: 45秒（无暂停）
[B屏独立计算] 屏幕已暂停，暂停时剩余: 60秒
[A屏阶段信息] {stageIndex=1, stageName=黄灯, stageColor=#FFFF00, stageRemaining=8, stageDuration=60, stageElapsed=52, status=running}
```

### 异常处理
```
❌ 无效的屏幕参数: C，必须是 'A' 或 'B'
❌ 比赛类型未选择，无法计算屏幕剩余时长
```

---

## 相关字段参考

| 字段 | 类型 | 说明 | 关键说明 |
|------|------|------|---------|
| `screenATimerRemaining` | long | A屏剩余时长 | 动态变化，受暂停影响 |
| `screenATimerPausedAt` | long | A屏暂停时刻 | 0=运行，非0=暂停时间戳 |
| `screenATotalPausedDuration` | long | A屏累计暂停 | 毫秒，自动应用补偿 |
| `screenElapsedAtSwitch` | long | 屏幕切换时刻 | 用于计算屏幕经过时间 |
| `prepTime` | Integer | 准备阶段时长 | 来自 currentState |
| `compTime` | Integer | 比赛阶段时长 | 来自 currentState |

