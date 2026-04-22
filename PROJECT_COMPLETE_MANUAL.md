# 🎯 射箭比赛计时系统 - 完整项目文档

**项目名称**: archery-springboot-vue  
**版本**: 1.0.1  
**最后更新**: 2026-04-23 (倒计时逻辑修复)
**完成度**: 65% （功能开发完成，集成测试待进行）  
**状态**: 🟡 可用，主要功能已实现

---

# 第一部分：完整需求说明

## 1.1 项目概述

### 目标
开发一个专业的**射箭比赛计时和控制系统**，支持现场实时计时、多屏显示、自动提示音和多种赛制规则。

### 核心需求
- ✅ **9种专业比赛类型**：涵盖所有常见射箭赛制
- ✅ **AB交替模式**：支持个人赛和团队赛的不同切屏规则
- ✅ **实时多屏同步**：通过WebSocket实现<100ms同步延迟
- ✅ **灯色提示系统**：红灯（准备）→ 绿灯（比赛）→ 黄灯（最后N秒，基于倒计时自动转换）
- ✅ **自动鸣笛系统**：阶段转换自动播放对应声音（1声/2声），灯色转换时播放3声
- ✅ **一键启动**：Electron打包，无需环境配置
- ✅ **日志记录**：完整的操作日志便于审计和排查

## 1.2 功能需求

### 1.2.1 9种比赛类型配置

#### 个人赛（4种）
| 类型 | 准备 | 比赛 | 黄灯 | 模式 | 说明 |
|------|------|------|------|------|------|
| 个人排名 | 10s | 180s | 30s | 同步 | 标准排名赛 |
| 个人排名对决 | 10s | 30s×2 | 30s | AB交替 | 对手轮流射击 |
| 个人淘汰（统一） | 10s | 90s | 30s | 同步 | 淘汰赛统一模式 |
| 个人淘汰（AB交替） | 10s | 20s×2 | 0s | AB交替 | 淘汰赛交替模式 |

#### 团队赛（3种）
| 类型 | 准备 | 比赛 | 黄灯 | 模式 | 说明 |
|------|------|------|------|------|------|
| 团队（统一） | 10s | 120s | 30s | 同步 | 团队统一计时 |
| 团队决赛（AB交替） | 10s | 120s | 30s | AB交替 | 团队决赛交替 |
| 团队对决（AB交替） | 10s | 60s | 30s | AB交替 | 团队对决交替 |

#### 混团赛（2种）
| 类型 | 准备 | 比赛 | 黄灯 | 模式 | 说明 |
|------|------|------|------|------|------|
| 混团（统一） | 10s | 80s | 30s | 同步 | 混合团队统一 |
| 混团决赛（AB交替） | 10s | 80s | 30s | AB交替 | 混合团队决赛 |

**⚠️ 时间计算方式（关键）**：
- **总时间** = 准备时间 + 比赛时间（**不含黄灯时间**）
- **黄灯时间** 是比赛倒计时的最后N秒，不单独加入总时间
- **示例**：个人排名赛 = 10(准备) + 180(比赛) = **190秒**（不是220秒）
  - 时间流程：0-10秒(RED) → 10-40秒(GREEN) → 40-190秒(YELLOW)
  - 注：绿灯显示150秒，黄灯显示30秒，都属于比赛的180秒

### 1.2.2 AB交替模式规则

#### 准备阶段（红灯）
- **规则**：AB屏强制同步倒计时
- **持续时间**：依据比赛类型配置（通常10秒）
- **视觉反馈**：圆形红灯 + "准备" 标签

#### 绿灯阶段初始状态
- **A屏状态**：开始倒计时（绿灯）
- **B屏状态**：暂停在初始时间（绿灯）
- **提示文案**：显示各屏的提示内容

#### 个人赛切屏规则（最关键）
```
第一次切屏：A屏清零 → B屏从初始时间开始
第二次切屏：B屏清零 → A屏从初始时间开始
（以此类推，每次切屏都清零原屏）
```

#### 团队赛切屏规则
```
第一次切屏：A屏暂停保留 → B屏从初始时间开始
第二次切屏：B屏暂停保留 → A屏从暂停点继续
（保留已运行的时间，形成接力制）
```

### 1.2.3 灯色系统

| 灯色 | 十六进制 | 场景 | 转换逻辑 |
|------|---------|------|---------|
| **红灯** | #FF0000 | 准备阶段 | 从比赛开始前到准备结束 |
| **绿灯** | #00FF00 | 比赛前期 | 从比赛开始到进入黄灯时间 |
| **黄灯** | #FFFF00 | 比赛最后阶段 | **当比赛倒计时 ≤ 黄灯时间时自动变黄**（不是独立阶段） |
| **待机** | #CCCCCC | 空闲/未选择 | 持续显示 |

**关键说明**：
- ✅ 黄灯时间是比赛时间的**一部分**，不单独加入总时间
- ✅ 总时间 = 准备时间 + 比赛时间（黄灯不单独计算）
- ✅ 灯色转换基于**剩余时间**而不是阶段切换
- ✅ 当比赛倒计时最后N秒时，灯色自动从GREEN变为YELLOW

**示例**：准备10秒 + 比赛90秒 + 黄灯30秒
- 总时间 = 10 + 90 = **100秒**（不是130秒）
- 时间流程：0-10秒(RED) → 10-60秒(GREEN) → 60-100秒(YELLOW)

### 1.2.4 鸣笛系统

| 触发条件 | 鸣笛方式 | 说明 |
|---------|---------|------|
| 进入准备阶段（红灯） | **1声** | 提示准备开始 |
| 进入比赛阶段（绿灯） | **2声** | 提示开始射击 |
| **灯色从GREEN变为YELLOW**（进入黄灯时间） | **3声** | 提示最后阶段，当比赛倒计时 ≤ 黄灯时间时触发 |
| 手动触发 | **2声** | 控制端手动鸣笛 |

**修复说明**：3声鸣笛的触发时机由"进入黄灯阶段"改为"灯色从GREEN变为YELLOW"，因为黄灯不再是独立的阶段，而是比赛时间的最后N秒。

### 1.2.5 控制台功能需求

#### 左侧控制面板
- **比赛类型选择**：下拉菜单选择9种类型，自动加载默认参数
- **时间配置**：三个独立输入框（准备/比赛/黄灯），可自定义
- **屏幕模式**：单选按钮组（同步/AB交替/仅A屏/仅B屏）
- **提示文案**：A屏和B屏各一个文本框，支持自定义内容
- **操作按钮**：
  - 开始/暂停/重置（状态联动）
  - 屏幕切换（仅AB交替模式可用）
  - 手动鸣笛
  - 消音/开音
- **音量控制**：0-100%滑块调节（消音时灰显）

#### 右侧预览区
- **A屏预览**：显示A屏当前状态，带"复制地址"按钮
- **B屏预览**：显示B屏当前状态，带"复制地址"按钮
- **系统状态**：显示WebSocket连接状态、当前控制端、服务器时间

### 1.2.6 显示页面需求

#### A屏显示（B屏相同）
- **顶部信息栏**：比赛类型、AB模式、控制端信息
- **提示文案区**：显示A屏配置的提示文字
- **中心显示区**：
  - **圆形灯牌**：直径180px，带glow效果
  - **倒计时显示**：大字体（100+px），时分秒格式
  - **屏幕状态**：当前屏幕状态标签（活跃/待机）
- **阶段信息**：当前阶段名称 + 阶段剩余时间
- **全屏支持**：F11或按钮切换全屏模式
- **声音状态**：麦克风图标显示当前音频状态

## 1.3 非功能需求

### 性能需求
- **同步延迟**：<100ms（三屏间消息同步）
- **计时精度**：±100ms（足以应对比赛场景）
- **页面加载**：<3s
- **WebSocket连接**：自动重连（最多5次，每次间隔3s）

### 可靠性需求
- **连接恢复**：自动重连机制
- **状态恢复**：刷新页面自动恢复计时器状态
- **日志记录**：完整的操作日志便于事后审查

### 用户体验需求
- **响应式设计**：支持不同屏幕尺寸
- **深色模式**：A/B屏采用深色背景（便于现场显示）
- **可访问性**：清晰的文本标签和视觉反馈

## 1.4 后续补充需求（可随时扩展）

### 未来可能的功能
- [ ] **多个比赛同时管理**：支持多个计时器实例
- [ ] **成绩记录功能**：记录每轮比赛的成绩
- [ ] **数据导出**：生成比赛报告（PDF/Excel）
- [ ] **性能统计**：显示运动员历史成绩对比
- [ ] **网络直播**：支持多地点同步显示
- [ ] **移动端控制**：支持手机/平板远程控制
- [ ] **自定义规则**：允许用户创建新的比赛类型
- [ ] **多语言支持**：国际化界面

---

# 第二部分：技术栈、工作原理、文件结构

## 2.1 技术栈

### 后端技术
| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 8+ | 编程语言 |
| Spring Boot | 2.7.17 | 应用框架 |
| Spring WebSocket | 2.7.17 | WebSocket支持 |
| Maven | 3.6+ | 项目管理和构建 |
| Lombok | 1.18.x | 代码生成 |
| SLF4J | 1.x | 日志框架 |

### 前端技术
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.3.4 | 前端框架 |
| Vue Router | 4.2.5 | 路由管理 |
| Vite | 4.5.14 | 构建工具 |
| Element Plus | 2.4.1 | UI组件库 |
| Element Plus Icons | 2.1.0 | 图标库 |
| STOMP.js | 7.0.0 | WebSocket协议 |
| SockJS | 1.6.1 | WebSocket降级方案 |
| Howler.js | 2.2.4 | 音频处理 |

### 桌面应用
| 技术 | 版本 | 用途 |
|------|------|------|
| Electron | latest | 应用打包 |
| Electron Builder | latest | 安装包构建 |
| Electron-is-dev | latest | 开发模式检测 |

### 通信协议
| 协议 | 说明 |
|------|------|
| **WebSocket** | 全双工通信 |
| **STOMP** | 消息格式协议 |
| **JSON** | 数据交换格式 |

## 2.2 工作原理

### 2.2.1 整体架构流程

```
┌─────────────────────────────────────────────────────────────┐
│                    Electron 应用启动                        │
│  ├─ 启动 Spring Boot 后端（端口 8080）                      │
│  ├─ 启动 Vite 开发服务器（端口 3000）                       │
│  └─ 创建应用窗口和日志窗口                                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              应用启动完成 - 显示日志窗口                     │
│  ├─ 3个访问地址：                                           │
│  │  ├─ http://localhost:3000 (控制端)                       │
│  │  ├─ http://localhost:3000/display-a (A屏显示)            │
│  │  └─ http://localhost:3000/display-b (B屏显示)            │
│  └─ 后端API：http://localhost:8080                          │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│            用户操作流程（以选择比赛类型为例）              │
│                                                             │
│  控制端操作                  WebSocket通信                  │
│  ┌──────────────────┐      ┌──────────────────┐           │
│  │ 选择比赛类型     │──→  │  发送消息        │           │
│  └──────────────────┘      │  /app/timer/     │           │
│                            │  select-match    │           │
│                            └────────┬─────────┘           │
│                                     ↓                      │
│                            ┌──────────────────┐           │
│                            │ 后端处理（BE）   │           │
│                            │ TimerEngine      │           │
│                            │ 更新计时器状态   │           │
│                            └────────┬─────────┘           │
│                                     ↓                      │
│                            ┌──────────────────┐           │
│                            │ 广播新状态       │           │
│                            │ /topic/timer     │           │
│                            │ -state           │           │
│                            └────────┬─────────┘           │
│                                     ↓                      │
│  所有客户端接收              ┌──────────────────┐         │
│  ┌──────────────────┐      │ A屏、B屏更新    │         │
│  │ 实时更新界面     │←──── │ 状态             │         │
│  │ 灯牌、时间等     │      │ 立即渲染         │         │
│  └──────────────────┘      └──────────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 2.2.2 后端处理流程

```
HTTP Request (控制台操作)
         ↓
┌─────────────────────┐
│ EnhancedWebSocket   │  接收并验证请求
│ Controller          │  记录操作日志
└────────┬────────────┘
         ↓
┌─────────────────────┐
│ TimerEngine         │  处理计时逻辑
│ - selectMatchType   │  - 更新计时器状态
│ - startTimer        │  - 计算剩余时间
│ - toggleABScreen    │  - 触发阶段转换
│ - handleLight       │  - 决定灯色显示
└────────┬────────────┘
         ↓
┌─────────────────────┐
│ LogFileManager      │  记录日志
│ LogService          │  到文件系统
└────────┬────────────┘
         ↓
┌─────────────────────┐
│ SimpMessaging       │  广播新状态
│ Template            │  通过 WebSocket
└────────┬────────────┘
         ↓
所有连接的客户端收到更新
```

### 2.2.3 计时运行流程

```
开始计时
    ↓
┌──────────────────────┐
│ 启动定时任务          │
│ (周期 = broadcast    │
│  -interval: 1000ms)  │
└────────┬─────────────┘
         ↓
┌──────────────────────┐
│ 每次触发（1000ms）    │
│ 计算当前时间          │
│ 更新各屏剩余时间      │
└────────┬─────────────┘
         ↓
┌──────────────────────┐
│ 判断阶段状态         │
│ 准备(RED) → 比赛     │
│ 比赛中：根据剩余时间 │
│ 动态判断GREEN→YELLOW │
│ 触发鸣笛系统         │
└────────┬─────────────┘
         ↓
┌──────────────────────┐
│ 广播状态更新         │
│ 前端实时渲染         │
│ (更新灯色、倒计时)    │
└────────┬─────────────┘
         ↓
┌──────────────────────┐
│ 是否完成？           │
│ NO → 继续循环        │
│ YES → 停止定时任务   │
└──────────────────────┘
```

### 2.2.4 AB交替模式切屏逻辑

```
AB交替模式启动
    ↓
┌─────────────────────────────────────────┐
│ 准备阶段                                 │
│ 状态：AB屏同步倒计时（红灯）            │
│ A剩余 = B剩余 = 准备时间                │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│ 绿灯初始状态                             │
│ A屏：开始倒计时（RUNNING）              │
│ B屏：暂停在初始时间（PAUSED）           │
│ 都是绿灯状态                            │
└─────────────────────────────────────────┘
    ↓
用户点击"屏幕切换"按钮
    ↓
┌─────────────────────────────────────────┐
│ 判断比赛类型                             │
└──────────────┬──────────────────┬────────┘
               ↓                  ↓
          ┌─────────┐      ┌─────────┐
          │ 个人赛  │      │ 团队赛  │
          └────┬────┘      └────┬────┘
               ↓                 ↓
        ┌──────────────┐   ┌──────────────┐
        │ 原屏清零     │   │ 原屏暂停保留 │
        │ 新屏重新开始 │   │ 新屏继续计时 │
        └──────────────┘   └──────────────┘
               ↓                 ↓
        A屏清零(0)         A屏暂停保留
        B屏开始(初值)       B屏继续运行
```

## 2.3 文件结构

### 2.3.1 项目总体结构

```
archery-springboot-vue/
│
├── 📁 backend/                           # Spring Boot后端应用
│   ├── src/main/java/com/archery/timer/
│   │   ├── controller/
│   │   │   ├── EnhancedWebSocketController.java    ✅ 主WebSocket端点
│   │   │   ├── MatchTypeController.java             🔄 比赛类型API（待实现）
│   │   │   └── WebSocketController.java             ✅ 原始控制器
│   │   │
│   │   ├── service/
│   │   │   ├── TimerEngine.java                     ✅ 核心计时引擎
│   │   │   ├── LogFileManager.java                  ✅ 日志管理（15文件轮转）
│   │   │   ├── ABAlternateService.java              ✅ AB交替逻辑（待测试）
│   │   │   ├── MatchTypeConfigService.java          ✅ 比赛类型管理
│   │   │   └── WebSocketService.java                ✅ WebSocket服务
│   │   │
│   │   ├── model/
│   │   │   ├── dto/
│   │   │   │   ├── MatchTypeDTO.java                ✅ 比赛类型数据对象
│   │   │   │   ├── TimerStateDTO.java               ✅ 计时器状态DTO
│   │   │   │   ├── WebSocketMessageDTO.java         ✅ WebSocket消息DTO
│   │   │   │   └── EnhancedMatchTypeDTO.java        ✅ 增强的比赛类型DTO
│   │   │   │
│   │   │   ├── enums/
│   │   │   │   ├── LightColor.java                  ✅ 灯色枚举
│   │   │   │   └── TimerStatus.java                 ✅ 计时器状态枚举
│   │   │   │
│   │   │   ├── entity/
│   │   │   │   └── Client.java                      ✅ 客户端实体
│   │   │   └── vo/
│   │   │       └── ClientResponse.java              ✅ 客户端响应VO
│   │   │
│   │   ├── config/
│   │   │   ├── WebSocketConfig.java                 ✅ WebSocket配置
│   │   │   ├── MatchTypeConfig.java                 ✅ 比赛类型配置
│   │   │   └── TimerConfig.java                     ✅ 计时器配置
│   │   │
│   │   ├── listener/
│   │   │   ├── WebSocketEventListener.java          ✅ WebSocket事件监听
│   │   │   └── TimerLifecycleListener.java          🔄 计时器生命周期（可选）
│   │   │
│   │   └── ArcheryTimerApplication.java             ✅ Spring Boot启动类
│   │
│   ├── src/main/resources/
│   │   ├── application.yml                          ✅ 应用配置
│   │   └── application-dev.yml                      🔄 开发环境配置（可选）
│   │
│   ├── target/
│   │   └── archery-timer-backend-1.0.0.jar         ✅ 构建后的JAR
│   │
│   └── pom.xml                                      ✅ Maven配置
│
├── 📁 frontend/                          # Vue 3前端应用
│   ├── src/
│   │   ├── views/
│   │   │   ├── EnhancedControlView.vue              ✅ 控制台（完整功能）
│   │   │   ├── EnhancedDisplayViewA.vue             ✅ A屏显示（完整功能）
│   │   │   ├── EnhancedDisplayViewB.vue             ✅ B屏显示（完整功能）
│   │   │   ├── ControlView.vue                      🗑️ 旧控制台（可删除）
│   │   │   ├── DisplayViewA.vue                     🗑️ 旧A屏（可删除）
│   │   │   └── DisplayViewB.vue                     🗑️ 旧B屏（可删除）
│   │   │
│   │   ├── components/
│   │   │   ├── LightIndicator.vue                   ✅ 灯牌组件
│   │   │   └── TimerDisplay.vue                     🔄 计时显示组件（可选）
│   │   │
│   │   ├── stores/
│   │   │   ├── enhancedTimer.js                     ✅ 状态管理（使用pinia）
│   │   │   ├── timer.js                             🗑️ 旧状态管理（可删除）
│   │   │   └── useWebSocket.js                      🗑️ 旧WebSocket钩子（可删除）
│   │   │
│   │   ├── services/
│   │   │   ├── globalWebSocketService.js            ✅ 全局WebSocket单例
│   │   │   ├── logService.js                        ✅ 前端日志服务
│   │   │   └── useWebSocket.js                      🗑️ 旧WebSocket（可删除）
│   │   │
│   │   ├── composables/
│   │   │   ├── useBuzzer.js                         ✅ 鸣笛系统（Howler集成）
│   │   │   └── useLocalStorage.js                   ✅ localStorage持久化
│   │   │
│   │   ├── data/
│   │   │   └── matchTypes.js                        ✅ 比赛类型定义
│   │   │
│   │   ├── router/
│   │   │   └── index.js                             ✅ 路由配置
│   │   │
│   │   ├── App.vue                                  ✅ 根组件
│   │   └── main.js                                  ✅ 入口文件
│   │
│   ├── public/
│   │   ├── sounds/
│   │   │   ├── 1-beep.mp3                          ❌ 缺失（需补充）
│   │   │   ├── 2-beep.mp3                          ❌ 缺失（需补充）
│   │   │   ├── 3-beep.mp3                          ❌ 缺失（需补充）
│   │   │   └── countdown_audio.mp3                  ✅ 已有（16KB）
│   │   └── index.html                               ✅ HTML入口
│   │
│   ├── dist/                                        📦 生产构建输出
│   ├── node_modules/                                📦 NPM依赖
│   ├── package.json                                 ✅ NPM配置
│   └── vite.config.js                               ✅ Vite配置
│
├── 📁 electron/                          # Electron应用
│   ├── main.js                                      ✅ 主进程
│   │   ├─ 启动Spring Boot后端
│   │   ├─ 创建应用窗口
│   │   ├─ 管理日志窗口
│   │   └─ 处理应用生命周期
│   │
│   ├── preload.js                                   ✅ 预加载脚本
│   │   └─ 暴露安全的IPC接口
│   │
│   └── log.html                                     ✅ 日志窗口界面
│       ├─ 实时日志显示
│       ├─ 日志清空/复制功能
│       └─ 系统启动信息展示
│
├── 📄 package.json (root)                          ✅ 根目录配置
│   ├─ Electron脚本
│   ├─ 打包配置（NSIS/DMG/AppImage）
│   └─ 所有依赖声明
│
├── 📄 README.md                                     ✅ 项目说明
├── 📄 STARTUP_GUIDE.md                              ✅ 启动指南
├── 📄 QUICK_REFERENCE.md                            ✅ 快速参考
├── 📄 REQUIREMENTS_VALIDATION_REPORT.md            ✅ 需求验证报告
├── 📄 VALIDATION_EXECUTIVE_SUMMARY.md              ✅ 验证摘要
├── 📄 DEVELOPMENT_PLAN.md                          ✅ 开发计划
├── 📄 COMPLETE_IMPLEMENTATION_GUIDE.md             ✅ 实现指南
└── 📄 WEBSOCKET_FIX_DOCUMENTATION.md               ✅ WebSocket文档
```

### 2.3.2 核心文件功能说明

#### 后端核心文件

**TimerEngine.java** - 计时引擎核心
```
职责：
- 管理计时器状态和生命周期
- 处理不同比赛类型的逻辑
- 实现AB交替模式的两套规则
- 驱动定时任务（周期1000ms）
- 触发状态变化事件
- 记录操作日志

关键方法：
- selectMatchType(matchTypeId)       选择比赛类型
- startTimer(clientId)               启动计时
- pauseTimer()                        暂停计时
- resetTimer()                        重置计时
- toggleABScreen()                    切换AB屏（AB交替模式）
- setABMode(mode)                     设置屏幕模式
```

**LogFileManager.java** - 日志管理
```
职责：
- 创建session级别日志文件（日期+时间戳）
- 自动轮转保留最多15个文件
- 记录所有操作和错误
- 线程安全的文件写入

配置项：
- archery.timer.log.directory        日志目录（默认：logs/）
- archery.timer.log.max-files         保留文件数（默认：15）
```

**EnhancedWebSocketController.java** - WebSocket端点
```
端点：
- /app/timer/select-match-type       选择比赛类型
- /app/timer/start                   开始计时
- /app/timer/pause                   暂停计时
- /app/timer/reset                   重置计时
- /app/timer/toggle-ab-screen        切换AB屏
- /app/timer/set-ab-mode             设置AB模式
- /app/timer/set-prompt              设置提示文案
- /app/timer/set-volume              设置音量
- /app/timer/set-sound-enabled       设置是否启用声音
- /app/timer/manual-buzzer           手动鸣笛

发布主题：
- /topic/timer-state                 计时器状态（所有客户端）
- /topic/clients                     客户端列表（所有客户端）
- /user/queue/messages               个人消息队列
```

#### 前端核心文件

**globalWebSocketService.js** - 全局WebSocket单例
```
职责：
- 维护全局唯一WebSocket连接
- 实现自动重连机制（5次，每次3s延迟）
- 消息回调注册和分发
- 心跳保活（10s一次）

导出函数：
- initGlobalWebSocket()               初始化连接
- disconnectGlobalWebSocket()          断开连接
- registerGlobalClient(clientType)    注册客户端
- sendGlobalWebSocketMessage(...)     发送消息
- onGlobalWebSocketMessage(callback)  注册消息回调
```

**enhancedTimer.js (Store)** - 状态管理
```
职责：
- 管理全局计时器状态
- 处理WebSocket消息
- 更新UI组件
- 触发本地事件

状态字段：
- timerState：当前计时器状态
- connectionState：连接状态
- clientId/clientType：客户端标识
- 所有时间配置和灯色状态
```

**useBuzzer.js** - 鸣笛系统
```
职责：
- 管理音频加载和播放
- 实现4种鸣笛声（1声/2声/3声/倒计时）
- 音量控制和消音开关

函数：
- buzz1()                            1声鸣笛
- buzz2()                            2声鸣笛
- buzz3()                            3声鸣笛
- buzzCountdown()                    倒计时声
- setVolume(0-1)                     设置音量
- toggleMute()                       切换消音
```

**useLocalStorage.js** - 持久化
```
职责：
- 保存和恢复计时器状态
- 保存用户偏好设置
- localStorage版本管理

函数：
- saveTimerState(state)              保存计时器状态
- restoreTimerState()                恢复计时器状态
- savePreferences(prefs)             保存偏好设置
- restorePreferences()               恢复偏好设置
```

**logService.js** - 前端日志
```
职责：
- 记录所有前端操作
- 保留最多1000条内存日志
- 支持导出为JSON/CSV

函数：
- info(message, data)                信息日志
- warn(message, data)                警告日志
- error(message, data)               错误日志
- event(eventName, data)             事件日志
- getLogs()                          获取所有日志
- exportLogs()                       导出JSON
- exportLogsAsCSV()                  导出CSV
```

### 2.3.3 功能实现完整性矩阵

| 功能 | 状态 | 位置 | 备注 |
|------|------|------|------|
| 比赛类型管理 | ✅ | backend/service, frontend/data | 9种类型全部配置 |
| 计时引擎 | ✅ | backend/service/TimerEngine.java | 核心逻辑完整 |
| AB交替逻辑 | ✅ | backend/service/TimerEngine.java | 个人赛+团队赛都支持 |
| 灯色系统 | ✅ | backend/model/enums/LightColor.java | 4种颜色完整 |
| WebSocket通信 | ✅ | backend/controller, frontend/services | 所有端点实现 |
| 日志系统 | ✅ | backend/LogFileManager.java, frontend/logService.js | 后端+前端双层 |
| 鸣笛系统 | ✅ | frontend/composables/useBuzzer.js | 音频集成完成 |
| 控制面板 | ✅ | frontend/views/EnhancedControlView.vue | 所有功能集成 |
| 显示页面 | ✅ | frontend/views/EnhancedDisplayViewA/B.vue | 灯牌+倒计时 |
| 持久化 | ✅ | frontend/composables/useLocalStorage.js | 代码就绪 |
| Electron应用 | ✅ | electron/main.js | 框架完成 |
| 后端编译 | ✅ | backend/target/archery-timer-*.jar | 19MB JAR文件 |
| 前端依赖 | ✅ | frontend/node_modules | npm install成功 |

### 2.3.4 未实现功能清单

| 功能 | 影响 | 优先级 | 预计工作量 |
|------|------|--------|-----------|
| 音频文件1-2-3声 | 鸣笛系统无法完全使用 | 高 | 1天 |
| 完整集成测试 | 功能验证不完整 | 高 | 3天 |
| Electron应用测试 | 无法打包发布 | 高 | 2天 |
| Vite生产构建修复 | 无法生产部署 | 中 | 1天 |
| 旧文件清理 | 项目整洁度 | 低 | 1小时 |
| 多语言支持 | 国际化 | 低 | 选项功能 |
| 成绩记录功能 | 扩展功能 | 低 | 选项功能 |
| API速率限制 | 安全加固 | 低 | 选项功能 |

---

# 第三部分：启动部署方式和注意事项

## 3.1 系统要求

### 硬件要求
- **CPU**：Intel Core i3 或同等 AMD 处理器
- **RAM**：4GB 最小（8GB 推荐）
- **存储**：500MB 可用空间（含依赖）
- **网络**：局域网或本地网络连接

### 软件要求
- **操作系统**：Windows 10+, macOS 10.13+, Linux (Ubuntu 18+)
- **Java**：JDK 8 或更高版本（运行后端）
- **Node.js**：14+ 或更高版本（开发和构建）
- **Maven**：3.6+ （后端构建）
- **浏览器**：Chrome/Firefox/Edge 最新版本

### 验证安装
```bash
# 检查 Java
java -version
# 输出：java version "1.8.0_xxx" 或更高

# 检查 Node.js 和 npm
node -v          # v14.0.0 或更高
npm -v            # 6.0.0 或更高

# 检查 Maven
mvn -version      # Apache Maven 3.6.0 或更高
```

## 3.2 开发模式启动

### 3.2.1 一键启动（推荐）

```bash
cd archery-springboot-vue

# 安装根目录依赖（首次）
npm install

# 启动开发环境
npm start

# 自动执行：
# 1. 后端编译和启动（Spring Boot 8080）
# 2. 前端开发服务器启动（Vite 3000）
# 3. Electron 应用窗口打开
# 4. 日志窗口显示启动日志
```

### 3.2.2 分步启动（调试用）

#### 步骤 1：启动后端
```bash
cd backend

# 方式A：使用 Maven 插件
mvn spring-boot:run -DskipTests

# 方式B：使用预编译的 JAR
java -jar target/archery-timer-backend-1.0.0.jar

# 成功标志：
# ✅ Tomcat started on port(s): 8080
# ✅ 加载比赛类型配置完成，共 X 种类型
# ✅ WebSocket 配置就绪
```

#### 步骤 2：启动前端（新终端窗口）
```bash
cd frontend

# 首次运行：安装依赖
npm install

# 启动开发服务器
npm run dev

# 成功标志：
# ✅ Vite dev server running at http://localhost:3000/
```

#### 步骤 3：访问应用
```
控制台：   http://localhost:3000/
A屏显示：  http://localhost:3000/display-a
B屏显示：  http://localhost:3000/display-b
后端API：  http://localhost:8080/api/match-types
WebSocket：ws://localhost:8080/ws-archery-timer
```

### 3.2.3 开发工具和调试

#### 前端开发工具
```bash
# 开发模式自动启用 DevTools
npm run dev

# 在浏览器中：
# - F12 打开开发者工具
# - 查看 Console 标签获取日志
# - 使用 Network 标签调试 WebSocket
```

#### 后端调试
```bash
# 查看后端日志
tail -f logs/session_*.log

# 查看 Spring Boot 日志
# 实时输出在控制台
```

## 3.3 生产部署方式

### 3.3.1 构建生产版本

```bash
# 在项目根目录

# 方式 1：自动构建所有
npm run build:all
# ├─ 构建后端 JAR
# └─ 构建前端 dist

# 方式 2：分别构建
# 构建后端
cd backend && mvn clean package -DskipTests && cd ..

# 构建前端
cd frontend && npm run build && cd ..
```

### 3.3.2 Electron 应用打包

```bash
# 确保已构建前端
npm run build:all

# 打包 Electron 应用
npm run dist

# 生成的安装包位置：
# Windows: dist/射箭比赛计时系统.exe
# macOS:   dist/射箭比赛计时系统.dmg
# Linux:   dist/射箭比赛计时系统.AppImage
```

### 3.3.3 运行生产应用

#### Windows
```bash
# 双击安装包
dist/射箭比赛计时系统.exe

# 或使用命令行安装
./dist/射箭比赛计时系统.exe

# 安装完成后：
# - 应用自动启动
# - 日志窗口显示启动信息
# - 自动打开主窗口
```

#### macOS
```bash
# 双击 DMG 文件
dist/射箭比赛计时系统.dmg

# 或从命令行
open dist/射箭比赛计时系统.dmg

# 将应用拖入 Applications 文件夹
# 从 Applications 启动
```

#### Linux
```bash
# 给予执行权限
chmod +x dist/射箭比赛计时系统.AppImage

# 运行应用
./dist/射箭比赛计时系统.AppImage

# 或使用 desktop 图标
# 应用会自动创建启动菜单项
```

## 3.4 重要注意事项

### 3.4.1 端口冲突

**问题**：如果 8080 或 3000 被占用

**解决方案**：
```bash
# 查找占用 8080 的进程（Windows）
netstat -ano | findstr :8080

# 查找占用 8080 的进程（Linux/Mac）
lsof -i :8080

# 杀死进程
taskkill /PID <PID> /F          # Windows
kill -9 <PID>                    # Linux/Mac

# 或修改端口：
# backend:     application.yml -> server.port
# frontend:    vite.config.js  -> server.port
# Electron:    electron/main.js -> 修改端口常量
```

### 3.4.2 音频文件缺失

**问题**：鸣笛声音无法播放

**解决方案**：
需要补充 3 个音频文件到 `frontend/public/sounds/`
```
1-beep.mp3      # 1声鸣笛 (建议 0.2 秒)
2-beep.mp3      # 2声鸣笛 (建议 0.4 秒)
3-beep.mp3      # 3声鸣笛 (建议 0.6 秒)
```

### 3.4.3 WebSocket 连接问题

**问题**：前端无法连接到后端 WebSocket

**排查步骤**：
```bash
# 1. 检查后端是否运行
curl http://localhost:8080/actuator/health
# 应该返回 {"status":"UP"}

# 2. 检查防火墙设置
# Windows: 允许 Java 和 Node 通过防火墙
# Linux/Mac: 检查防火墙规则

# 3. 查看浏览器控制台错误
# F12 -> Console 标签

# 4. 检查网络连接
# 确保控制端和显示端在同一网络
```

### 3.4.4 刷新页面后状态丢失

**问题**：页面刷新后计时器状态消失

**原因**：localStorage 未正确保存

**解决方案**：
```javascript
// 浏览器控制台检查
localStorage.getItem('archery-timer_timerState')
// 应该返回一个 JSON 对象

// 如果返回 null，尝试：
// 1. 清除浏览器缓存
// 2. 检查浏览器隐私模式是否开启
// 3. 检查存储空间是否足够
```

### 3.4.5 多屏同步延迟

**问题**：A屏和B屏显示不同步（超过100ms）

**排查步骤**：
```
1. 检查网络延迟
   - 相同局域网内应该 <10ms

2. 检查后端负载
   - 查看后端日志是否有性能警告
   - 使用 jps 命令检查 Java 进程状态

3. 检查浏览器性能
   - F12 -> Performance 标签分析
   - 关闭不必要的浏览器标签

4. 调整广播间隔
   - application.yml: archery.timer.broadcast-interval
   - 默认 1000ms（每秒更新一次）
```

### 3.4.6 后端日志文件堆积

**问题**：logs 目录占用过多空间

**说明**：系统自动保留最多 15 个 session 日志文件，应该无此问题

**手动清理**：
```bash
# 查看日志文件
ls -lh logs/

# 手动删除旧日志（保留最近的）
rm logs/session_*.log  # 谨慎操作

# 或清空所有日志
rm -rf logs/
# 系统会自动重新创建
```

### 3.4.7 Electron 应用黑屏

**问题**：Electron 窗口打开但显示黑屏

**原因**：前端加载失败或后端未启动

**解决方案**：
```bash
# 1. 查看日志窗口的错误信息

# 2. 检查后端是否启动
curl http://localhost:8080/api/match-types
# 应该返回比赛类型列表

# 3. 重新编译前端
npm run build

# 4. 重启 Electron 应用
npm start

# 5. 启用开发者工具调试
electron/main.js 中 mainWindow.webContents.openDevTools()
```

## 3.5 故障排除快速参考

| 问题 | 症状 | 快速修复 |
|------|------|---------|
| 端口占用 | 应用启动失败 | `netstat -ano / 修改application.yml` |
| 音频不播放 | 鸣笛无声 | 补充1-2-3-beep.mp3文件 |
| WebSocket未连 | 页面灰显 | 检查后端运行状态/防火墙 |
| 状态丢失 | 刷新后重置 | 检查localStorage权限 |
| 黑屏 | Electron黑屏 | 检查前端编译/后端启动 |
| 日志堆积 | 磁盘满 | 自动管理（保留15个）|

---

# 第四部分：配置性内容调整位置说明

## 4.1 后端配置

### 4.1.1 application.yml - Spring Boot主配置

**位置**：`backend/src/main/resources/application.yml`

**可配置项**：
```yaml
# 服务器配置
server:
  port: 8080                              # 修改：后端服务端口
  servlet:
    context-path: /                       # 修改：API前缀路径

# WebSocket 配置
websocket:
  allowed-origins: http://localhost:3000  # 修改：允许的前端地址（CORS）
  timeout: 86400000                       # 修改：连接超时时间（毫秒）
  heartbeat-timeout: 30000                # 修改：心跳超时时间（毫秒）

# 应用自定义配置
archery:
  timer:
    log:
      directory: logs                     # 修改：日志目录
      max-files: 15                       # 修改：保留日志文件数量
    broadcast-interval: 1000              # 修改：状态广播间隔（毫秒）
```

**修改说明**：
```
1. 改变后端端口
   server.port: 8080 → 8888
   
2. 改变日志目录
   log.directory: logs → /var/log/archery
   
3. 改变保留日志数
   max-files: 15 → 30
   
4. 调整广播频率
   broadcast-interval: 1000 → 500    # 更频繁的更新
   broadcast-interval: 1000 → 2000   # 更少的网络开销
```

### 4.1.2 WebSocketConfig.java - WebSocket详细配置

**位置**：`backend/src/main/java/com/archery/timer/config/WebSocketConfig.java`

**可配置项**：
```java
// CORS 设置
config.setAllowedOrigins("http://localhost:3000", "http://192.168.1.100:3000")

// STOMP 端点
config.addEndpoint("/ws-archery-timer")
    .setAllowedOrigins("*")  // 修改：允许所有源（生产不推荐）

// 心跳配置
config.setClientInboundChannel(...)
    .heartbeat(0, 25000)  // 修改：客户端心跳间隔

// 消息大小限制
config.setClientInboundChannel(...)
    .bufferSize(8192)     // 修改：缓冲区大小
```

**修改说明**：
```
1. 严格的 CORS（生产推荐）
   setAllowedOrigins("http://192.168.1.100:3000")

2. 宽松的 CORS（开发测试）
   setAllowedOrigins("*")

3. 多个源配置
   setAllowedOrigins(
     "http://localhost:3000",
     "http://192.168.1.100:3000",
     "http://192.168.1.101:3000"
   )
```

## 4.2 前端配置

### 4.2.1 vite.config.js - Vite构建配置

**位置**：`frontend/vite.config.js`

**可配置项**：
```javascript
// 开发服务器配置
server: {
  port: 3000,                            // 修改：前端开发服务端口
  host: 'localhost',                     // 修改：监听地址
  proxy: {
    '/api': {
      target: 'http://localhost:8080',   // 修改：后端API地址
      changeOrigin: true
    }
  }
},

// 构建优化
build: {
  target: 'esnext',                      // 修改：浏览器兼容性
  minify: 'terser',                      // 修改：压缩器
  sourcemap: false                       // 修改：是否生成source map
},

// 全局变量
define: {
  __API_BASE_URL__: '"http://localhost:8080"'  // 修改：API地址
}
```

**修改说明**：
```javascript
// 1. 改变开发服务端口
server: { port: 3000 } → { port: 5173 }

// 2. 改变后端地址
proxy: {
  '/api': {
    target: 'http://192.168.1.100:8080'  // IP地址
  }
}

// 3. 启用source map（调试）
build: { sourcemap: true }

// 4. 改变构建目标
build: { target: 'es2015' }  // 更好的兼容性
```

### 4.2.2 .env - 环境变量配置

**位置**：`frontend/.env`（需自己创建）

**可配置项**：
```env
# API配置
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080

# 应用配置
VITE_APP_TITLE=射箭比赛计时系统
VITE_APP_VERSION=1.0.0

# 功能开关
VITE_ENABLE_DARK_MODE=true
VITE_ENABLE_DEBUG=false
```

**使用方式**：
```javascript
// 在代码中使用
const apiUrl = import.meta.env.VITE_API_BASE_URL
const wsUrl = import.meta.env.VITE_WS_URL
```

### 4.2.3 matchTypes.js - 比赛类型定义

**位置**：`frontend/src/data/matchTypes.js`

**可配置项**：
```javascript
// 修改现有类型的时间配置
DEFAULT_PARAMS = {
  personal_ranking: { prep: 10, comp: 180, yellow: 30, mode: 'sync' }
  // 改为
  personal_ranking: { prep: 12, comp: 200, yellow: 40, mode: 'sync' }
}

// 添加新的比赛类型
export const MATCH_TYPES = [
  // ... 现有类型
  // 添加新类型
  {
    id: 'custom_type',
    name: '自定义类型',
    category: 'personal',
    order: 10
  }
]
```

### 4.2.4 useBuzzer.js - 音频配置

**位置**：`frontend/src/composables/useBuzzer.js`

**可配置项**：
```javascript
// 修改音频音量
const sounds = {
  beep1: new Howl({
    src: ['/sounds/1-beep.mp3'],
    volume: 0.5      // 修改：默认音量（0-1）
  })
}

// 修改音频文件路径
beep1: new Howl({
  src: ['/assets/sounds/1-beep.mp3']  // 修改：文件位置
})

// 启用/禁用音频
const sounds = {
  beep1: new Howl({
    preload: true,  // 修改：是否预加载
    loop: false     // 修改：是否循环播放
  })
}
```

### 4.2.5 router/index.js - 路由配置

**位置**：`frontend/src/router/index.js`

**可配置项**：
```javascript
// 修改路由定义
routes = [
  {
    path: '/',
    name: 'control',
    component: () => import('../views/EnhancedControlView.vue')
    // 修改：改变首页指向
    // component: () => import('../views/EnhancedDisplayViewA.vue')
  },
  // 修改：添加新路由
  {
    path: '/settings',
    name: 'settings',
    component: () => import('../views/SettingsView.vue')
  }
]
```

## 4.3 Electron 应用配置

### 4.3.1 electron/main.js - 主进程配置

**位置**：`electron/main.js`

**可配置项**：
```javascript
// 后端启动配置
const backendPort = 8080  // 修改：后端端口
const jarPath = path.join(__dirname, '../backend/target')

// 前端开发服务器配置
const frontendPort = 3000  // 修改：前端端口

// 应用窗口配置
const mainWindow = new BrowserWindow({
  width: 1400,            // 修改：窗口宽度
  height: 900             // 修改：窗口高度
})

// 开发工具
if (isDev) {
  mainWindow.webContents.openDevTools()  // 修改：是否打开开发工具
}
```

**修改说明**：
```javascript
// 1. 改变端口
const backendPort = 8080 → 8888
const frontendPort = 3000 → 5173

// 2. 改变窗口大小
width: 1400, height: 900 → width: 1920, height: 1080

// 3. 禁用开发工具
if (isDev) {
  // mainWindow.webContents.openDevTools()  // 注释掉
}

// 4. 改变启动页面
const url = isDev ? `http://localhost:${frontendPort}`
  : `file://${path.join(__dirname, '../frontend/dist/index.html')}`
// 可以改为特定路由
: `file://${path.join(__dirname, '../frontend/dist/index.html')}#/display-a`
```

### 4.3.2 package.json (root) - Electron打包配置

**位置**：`package.json` (项目根目录)

**可配置项**：
```json
{
  "build": {
    "appId": "com.archery.timer",
    "productName": "射箭比赛计时系统",
    "files": ["dist", "backend/target/*.jar", "electron/*"],
    
    "win": {
      "target": ["nsis"],           // 修改：Windows安装方式
      "arch": ["x64"]               // 修改：支持的CPU架构
    },
    
    "nsis": {
      "oneClick": false,            // 修改：是否一键安装
      "allowToChangeInstallationDirectory": true,
      "createDesktopShortcut": true // 修改：是否创建桌面快捷方式
    }
  }
}
```

**修改说明**：
```json
{
  "build": {
    // 1. 改变应用名称
    "productName": "射箭计时器"
    
    // 2. 添加 macOS 配置
    "mac": {
      "target": ["dmg", "zip"],
      "arch": ["x64", "arm64"]
    }
    
    // 3. 添加 Linux 配置
    "linux": {
      "target": ["AppImage"]
    }
    
    // 4. 改变应用ID（反向域名）
    "appId": "com.mycompany.archery"
  }
}
```

## 4.4 配置修改场景

### 场景 1：多地点部署

**需求**：在不同 IP 地址上部署多个实例

**修改步骤**：
```yaml
# 1. application.yml
websocket:
  allowed-origins: http://192.168.1.100:3000
  
# 2. vite.config.js
server: {
  host: '0.0.0.0',  // 监听所有网卡
  proxy: {
    '/api': {
      target: 'http://192.168.1.100:8080'  // 改为实际IP
    }
  }
}

# 3. frontend/.env
VITE_API_BASE_URL=http://192.168.1.100:8080
VITE_WS_URL=ws://192.168.1.100:8080
```

### 场景 2：性能优化

**需求**：优化计时精度和网络开销

**修改步骤**：
```yaml
# application.yml
archery:
  timer:
    broadcast-interval: 500  # 更频繁的更新（更精确）
    # 或
    broadcast-interval: 2000 # 更少的网络开销（更节省）
```

### 场景 3：添加新的比赛类型

**需求**：支持自定义的比赛规则

**修改步骤**：
```javascript
// frontend/src/data/matchTypes.js
export const MATCH_TYPES = [
  // ... 现有类型
  {
    id: 'indoor_3x30',
    name: '室内3×30',
    category: 'personal',
    order: 11,
    description: '室内3轮每轮30秒'
  }
]

export const DEFAULT_PARAMS = {
  // ... 现有配置
  indoor_3x30: { prep: 10, comp: 30, yellow: 0, mode: 'sync' }
}
```

### 场景 4：调整显示效果

**需求**：改变灯牌大小、字体大小、颜色

**修改位置**：`frontend/src/components/LightIndicator.vue`

```vue
<style scoped>
.light-indicator {
  width: 180px;  /* 修改：灯牌大小 */
  height: 180px;
}

/* 时间显示 */
.time-value {
  font-size: 120px;  /* 修改：字体大小 */
}
</style>
```

## 4.5 配置优先级

当存在多个配置时，优先级如下（从高到低）：

```
1. 环境变量 (.env 文件)
2. 命令行参数 (npm run dev -- --port 5173)
3. 配置文件 (application.yml / vite.config.js)
4. 代码内默认值
```

示例：
```bash
# 命令行参数优先级最高
npm run dev -- --port 5173  # 忽略 vite.config.js 中的端口设置
```

## 4.6 配置常见问题

| 问题 | 解决方案 |
|------|---------|
| 改了配置但没生效 | 重启应用，清除浏览器缓存，检查文件保存 |
| 多个实例端口冲突 | 为每个实例配置不同的端口号 |
| 改动导致应用启动失败 | 查看错误日志，恢复为默认值 |
| 生产和开发配置不同 | 使用 .env.production 和 .env.development |
| 不知道什么是最优配置 | 使用默认值，有问题时再调整 |

---

# 总结

本文档共4个部分，覆盖了射箭比赛计时系统的完整信息：

1. **第一部分**：详细的功能需求，包括9种比赛类型、AB交替规则、灯色系统等
2. **第二部分**：技术栈、工作原理和完整的文件结构，清晰标注了已实现和未实现的功能
3. **第三部分**：详细的启动部署方式和故障排除指南
4. **第四部分**：所有配置性内容的位置和修改说明，便于后期定制

该项目目前**功能开发完成度 65%**，主要功能已实现，主要待完成任务是集成测试、打包验证和音频文件补充。

