# 综合代码审查问题修复计划 - 2026-04-24

**总问题数**: 73个 (严重31🔴 + 高等12🟠 + 中等30🟡)  
**修复状态**: 进行中  
**最后更新**: 2026-04-24

---

## 📊 问题分类统计

### 后端Java - 46个问题
- 🔴 严重: 31个
- 🟠 高等: 12个  
- 🟡 中等: 3个

**涉及文件**:
- TimerEngine.java (14个问题)
- EnhancedWebSocketController.java (7个问题)
- WebSocketService.java (7个问题)
- WebSocketEventListener.java (5个问题)
- MatchTypeConfigService.java (4个问题)
- LogFileManager.java (3个问题)
- 其他 (6个问题)

### 前端Vue/JS - 43个问题
- 🔴 严重: 26个
- 🟠 高等: 17个
- 🟡 中等: 0个

**涉及文件**:
- globalWebSocketService.js (9个问题)
- EnhancedControlView.vue (8个问题)
- enhancedTimer.js (7个问题)
- EnhancedDisplayViewA/B.vue (7个问题)
- useBuzzer.js (3个问题)
- 其他 (9个问题)

---

## 🔴 第一优先级 - 关键问题修复（5个）

### 1. enhancedTimer.js - isActiveScreen逻辑错误 ❌
**状态**: 待修复  
**问题**: 'only_a'/'only_b'模式返回true给所有屏幕  
**影响**: 核心功能完全失效  
**行号**: 259-277  

### 2. EnhancedWebSocketController.java - 直接修改活跃状态 ❌
**状态**: 待修复  
**问题**: 直接修改getState()返回的活跃对象  
**影响**: 倒计时时间完全错误  
**行号**: 190-210  

### 3. TimerEngine.java - 整数溢出 ❌
**状态**: 待修复  
**问题**: 24天后整数溢出导致时间计算错误  
**影响**: 长期运行系统故障  
**行号**: 619-631  

### 4. globalWebSocketService.js - 无界回调集合 ❌
**状态**: 待修复  
**问题**: messageCallbacks Set无清理机制  
**影响**: 内存泄漏，长期运行OOM  
**行号**: 24  

### 5. LogFileManager.java - 清理逻辑错误 ❌
**状态**: 待修复  
**问题**: limit()导致清理条件永不为真，文件永不清理  
**影响**: 磁盘爆满  
**行号**: 201-205  

---

## 🟠 第二优先级 - 高风险问题（8个）

### 6. WebSocketEventListener.java - Session/ClientId混淆 ❌
**状态**: 待修复  
**问题**: 使用sessionId作为clientId，客户端永不注销  
**行号**: 39, 52  

### 7. enhancedTimer.js - 保护字段备份时序错误 ❌
**状态**: 待修复  
**问题**: 先覆盖后备份，用户输入仍会丢失  
**行号**: 148-166  

### 8. EnhancedDisplayViewA/B.vue - Buzzer检测错误 ❌
**状态**: 待修复  
**问题**: prep→comp→prep会重复鸣笛  
**行号**: 282-292  

### 9. TimerEngine.java - 竞态条件（调度器重启） ❌
**状态**: 待修复  
**问题**: stopTimerTask()后立即创建新调度器，任务丢失  
**行号**: 556-565  

### 10. TimerEngine.java - 竞态条件（时间基准重置） ❌
**状态**: 待修复  
**问题**: timerStartedAt和lastUpdateAt重置未同步  
**行号**: 185-189  

---

## 🟡 第三优先级 - 内存泄漏（9个）

### 11. useBuzzer.js - 每次挂载创建新对象 ❌
**状态**: 待修复  
**问题**: 100+组件=100+份音频对象，内存爆炸  
**行号**: 14-39  

### 12. EnhancedControlView.vue - 订阅无清理 ❌
**状态**: 待修复  
**问题**: subscribeToTopics()订阅但无清理函数  
**行号**: 827-848  

### 13. enhancedTimer.js - interval无清理 ❌
**状态**: 待修复  
**问题**: connectionCheckInterval创建但组件卸载无清理  
**行号**: 215-236  

---

## ✅ 已修复的相关问题

从ISSUE-001到ISSUE-011的修复中，以下问题已经部分或完全解决：

- ✅ ISSUE-009: 保护用户编辑字段 → 改进了第7项问题的基础（但时序仍需修复）
- ✅ ISSUE-003: 自动注入clientId → 改进了消息完整性
- ✅ ISSUE-008: 权限检查 → 解决了权限相关问题
- ✅ ISSUE-007: 重连机制 → 改进了连接稳定性
- ✅ ISSUE-006: 控制端超时 → 减少了误判

**但这些修复不足以解决当前发现的73个问题**。

---

## 📋 修复计划

### 第一阶段：关键功能修复（优先级1-5）
- [ ] 修复enhancedTimer.js - isActiveScreen逻辑
- [ ] 修复EnhancedWebSocketController.java - 状态修改
- [ ] 修复TimerEngine.java - 整数溢出
- [ ] 修复globalWebSocketService.js - 回调管理
- [ ] 修复LogFileManager.java - 清理逻辑

**预计工作量**: 3-4小时

### 第二阶段：高风险问题修复（优先级6-10）
- [ ] 修复WebSocketEventListener.java - Session/ClientId
- [ ] 修复enhancedTimer.js - 备份时序
- [ ] 修复显示组件 - Buzzer检测
- [ ] 修复TimerEngine.java - 竞态条件（调度器）
- [ ] 修复TimerEngine.java - 竞态条件（时间基准）

**预计工作量**: 2-3小时

### 第三阶段：内存泄漏修复（优先级11-13+）
- [ ] 修复useBuzzer.js - 单例化
- [ ] 修复EnhancedControlView.vue - 订阅清理
- [ ] 修复enhancedTimer.js - interval清理
- [ ] 修复其他内存泄漏

**预计工作量**: 2-3小时

---

## 🎯 修复进度追踪

| # | 问题 | 文件 | 优先级 | 状态 | 预计 |
|---|------|------|--------|------|------|
| 1 | isActiveScreen逻辑 | enhancedTimer.js | P1 | ⏳ | 30min |
| 2 | 直接修改活跃状态 | EnhancedWebSocketController.java | P1 | ⏳ | 45min |
| 3 | 整数溢出 | TimerEngine.java | P1 | ⏳ | 20min |
| 4 | 无界回调集合 | globalWebSocketService.js | P1 | ⏳ | 40min |
| 5 | 清理逻辑 | LogFileManager.java | P1 | ⏳ | 20min |
| 6 | Session混淆 | WebSocketEventListener.java | P2 | ⏳ | 30min |
| 7 | 备份时序 | enhancedTimer.js | P2 | ⏳ | 25min |
| 8 | Buzzer检测 | EnhancedDisplayView.vue | P2 | ⏳ | 30min |
| 9 | 竞态(调度) | TimerEngine.java | P2 | ⏳ | 30min |
| 10 | 竞态(时间) | TimerEngine.java | P2 | ⏳ | 30min |
| 11 | Buzzer单例 | useBuzzer.js | P3 | ⏳ | 40min |
| 12 | 订阅清理 | EnhancedControlView.vue | P3 | ⏳ | 30min |
| 13 | interval清理 | enhancedTimer.js | P3 | ⏳ | 25min |

---

**总预计工作量**: 7-10小时  
**下一步**: 开始第一优先级修复

