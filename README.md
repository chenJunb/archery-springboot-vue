# 🏹 射箭比赛专业计时控制系统（增强版）

## 📋 项目简介

**专业的射箭比赛计时系统，专为比赛现场设计，支持9种比赛类型、AB屏交替模式、实时同步和专业鸣笛提示**

- ✅ **纯本地运行**：无需网络，无需数据库，无需复杂环境
- ✅ **9种专业比赛类型**：覆盖所有射箭比赛赛制
- ✅ **AB屏交替模式**：详细的个人赛和团队赛切换规则
- ✅ **实时同步**：WebSocket确保<100ms同步延迟
- ✅ **专业鸣笛提示**：自动播放鸣笛声，符合比赛规范
- ✅ **现代化界面**：专业美观的控制台和显示界面

## 🎯 核心功能亮点

### **9种专业比赛类型支持**
| 比赛类型 | 准备时间 | 比赛时间 | 黄灯时间 | 默认模式 | 总时间 |
|----------|----------|----------|----------|----------|--------|
| 个人排名赛 | 10秒 | 180秒 | 30秒 | 同步 | **190秒** |
| 个人排名对决 | 10秒 | 30+30秒 | 30秒 | AB交替 | **40秒** |
| 个人淘汰赛（统一） | 10秒 | 90秒 | 30秒 | 同步 | **100秒** |
| 个人淘汰赛（AB交替） | 10秒 | 20秒 | 0秒 | AB交替 | **30秒** |
| 团队淘汰赛（统一） | 10秒 | 120秒 | 30秒 | 同步 | **130秒** |
| 团队淘汰赛决赛（AB交替） | 10秒 | 120秒 | 30秒 | AB交替 | **130秒** |
| 团队淘汰赛对决（AB交替） | 10秒 | 60秒 | 30秒 | AB交替 | **70秒** |
| 混团淘汰赛（统一） | 10秒 | 80秒 | 30秒 | 同步 | **90秒** |
| 混团淘汰赛决赛（AB交替） | 10秒 | 80秒 | 30秒 | AB交替 | **90秒** |

**⚠️ 关键说明**：
- ✅ 总时间 = 准备时间 + 比赛时间（**黄灯时间不单独加入**）
- ✅ 黄灯时间是比赛的最后N秒，当倒计时≤黄灯时间时自动变为黄灯显示
- ✅ 灯色转换：RED(准备) → GREEN(比赛) → YELLOW(最后N秒)

### **AB交替模式详细规则**
- **准备阶段（红灯）**: AB屏强制同步倒计时
- **绿灯初始状态**: A屏开始倒计时，B屏暂停在初始时间
- **个人赛切换**: 原屏清零，新屏从初始时间重新开始
- **团队赛切换**: 原屏暂停保留时间，新屏继续倒计时
- **黄灯自动转换**: 当比赛倒计时进入最后N秒时，灯色自动从GREEN变为YELLOW

### **专业音效系统**
- 🎵 **准备阶段**: 1声鸣笛
- 🎵🎵 **比赛阶段**: 2声鸣笛  
- 🎵🎵🎵 **黄灯阶段**: 3声鸣笛（**当灯色从GREEN变为YELLOW时触发**）
- 🔇 **消音控制**: 全局声音开关
- 🔊 **音量调节**: 0-100%精细控制

## 🚀 快速开始

### **Windows一键启动（推荐）**
```bash
# 方式1：Electron 应用（最简单）
npm run start        # 启动开发模式（包含热更新）
npm run dist         # 构建安装包

# 方式2：双击 start.bat 或运行
cd archery-springboot-vue
start.bat
```

系统将启动两个窗口：
1. **后端服务** (端口:8080) - Spring Boot应用
2. **前端服务** (端口:3000) - Vue开发服务器

### **手动启动（开发环境）**

#### **1. 启动后端服务**
```bash
cd backend
mvn spring-boot:run

# 成功标志：Tomcat started on port(s): 8080
# 加载成功：加载比赛类型配置完成，共 10 种类型
```

#### **2. 启动前端服务**
```bash
cd frontend
npm install      # 首次运行需安装依赖
npm run dev

# 成功标志：Vite dev server running at http://localhost:3000/
```

#### **3. 访问系统**
| 页面 | 地址 | 功能 |
|------|------|------|
| **增强控制台** | http://localhost:3000/ | 主要控制界面（推荐） |
| **增强A屏显示** | http://localhost:3000/display-a | A屏大屏显示 |
| **增强B屏显示** | http://localhost:3000/display-b | B屏大屏显示 |
| **后端API** | http://localhost:8080/api/match-types | 比赛类型API |

## 🖥️ 控制台界面布局

```
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
- 比赛类型：下拉选                                                      |
- 准备：可编辑输入框 | 比赛：可编辑输入框 | 黄灯：可编辑输入框           |   A屏预览区域 (带复制地址按钮)        B屏预览区域 (带复制地址按钮)
- 屏幕控制：单选按钮组 同步|AB交替|仅A屏|仅B屏                        |    ------------------------------------------        ------------------------------------------
- A屏提示：文本输入框                                                 |     -                                       -         -
- B屏提示：文本输入框                                                 |     -           A屏提示文案                 -         -           B屏提示文案   
- 开始按钮 | 暂停按钮                                                 |    -    -------------------------------    -         -    -------------------------------
- 重置按钮 | 屏幕切换按钮（仅AB交替时可用）                           |    -     -                            -    -         -    -                            -    -
- 鸣笛按钮 | 消音按钮                                                 |    -    -                             -    -         -    -                             -    -
-                                                                     |    -    -           --------------    -    -         -    -           --------------    -    -
-                                                                     |     -   -   圆形状态灯 -            -    -    -         -    -   圆形状态灯 -            -    -    -
-                                                                     |     -   -           -倒计时显示    -    -    -         -    -           -倒计时显示    -    -    -
-                                                                     |     -   -           -            -    -    -         -    -           -            -    -    -
-                                                                     |     -   -           --------------    -    -         -    -           --------------    -    - 
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
```

## 🔧 技术架构

### **技术栈**
| 组件 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **后端框架** | Spring Boot | 2.7.17 | 企业级Java应用框架 |
| **前端框架** | Vue 3 | 3.5.32 | 现代化的前端框架 |
| **UI组件库** | Element Plus | 2.13.7 | Vue专业UI组件库 |
| **实时通信** | WebSocket (STOMP) | - | 实时双向通信 |
| **构建工具** | Vite | 4.5.14 | 现代化前端构建工具 |
| **构建工具** | Maven | 3.6+ | Java项目管理工具 |
| **音频处理** | Howler.js | 2.2.4 | Web音频处理库 |

### **系统架构**
```
         ┌─────────────┐     WebSocket      ┌─────────────┐
         │   控制台     │◄─────────────────►│    A屏显示   │
         │ (端口:3000) │    实时同步        │ (端口:3000) │
         └─────────────┘                    └─────────────┘
                 ▲                                    ▲
                 │                                    │
                 │       ┌─────────────┐            │
                 │       │  Spring     │            │
                 └───────┤  Boot后端   ├────────────┘
                         │ (端口:8080) │
                         └─────────────┘
                                 ▲
                                 │ WebSocket
                         ┌─────────────┐
                         │    B屏显示   │
                         │ (端口:3000) │
                         └─────────────┘

  设计特点：控制台集中控制，A/B屏独立显示，通过WebSocket实时同步状态
```

## 📁 项目结构

```
archery-springboot-vue/
├── backend/                             # Spring Boot后端
│   ├── src/main/java/com/archery/timer/
│   │   ├── controller/                 # 控制器
│   │   │   ├── EnhancedWebSocketController.java   # 增强WebSocket控制器
│   │   │   ├── MatchTypeController.java           # 比赛类型API
│   │   │   └── WebSocketController.java           # 原始WebSocket
│   │   ├── model/dto/                  # 数据传输对象
│   │   │   ├── EnhancedMatchTypeDTO.java          # 增强比赛类型
│   │   │   ├── MatchTypeDTO.java                  # 比赛类型
│   │   │   └── TimerStateDTO.java                 # 计时器状态
│   │   ├── service/                    # 业务服务
│   │   │   ├── MatchTypeConfigService.java        # 比赛类型配置
│   │   │   └── TimerEngine.java                   # 计时引擎
│   │   └── resources/config/
│   │       └── match-types.json        # 比赛类型配置文件
│   └── pom.xml                        # Maven配置
│
├── frontend/                          # Vue前端
│   ├── src/
│   │   ├── views/                    # 页面视图
│   │   │   ├── EnhancedControlView.vue      # 增强控制台
│   │   │   ├── EnhancedDisplayViewA.vue     # 增强A屏
│   │   │   ├── EnhancedDisplayViewB.vue     # 增强B屏
│   │   │   ├── ControlView.vue              # 原始控制台
│   │   │   ├── DisplayViewA.vue             # 原始A屏
│   │   │   └── DisplayViewB.vue             # 原始B屏
│   │   ├── stores/                   # 状态管理
│   │   │   ├── enhancedTimer.js             # 增强计时器store
│   │   │   └── timer.js                     # 原始计时器store
│   │   └── composables/              # 组合式API
│   │       ├── useWebSocket.js              # WebSocket连接
│   │       └── useSound.js                  # 音频服务
│   ├── public/
│   │   └── sounds/
│   │       └── countdown_audio.mp3   # 鸣笛音频文件
│   └── vite.config.js                # Vite配置
│
├── start.bat                         # Windows启动脚本
├── README.md                         # 项目说明文档
└── SYSTEM_VALIDATION_REPORT.md      # 系统验证报告
```

## 💡 使用说明

### **首次使用四步法**
1. **启动服务**: 运行 `start.bat` 或分别启动前后端
2. **访问控制台**: 打开 http://localhost:3000/
3. **选择比赛类型**: 从下拉列表中选择比赛类型
4. **开始计时**: 点击开始按钮，系统自动应用对应规则

### **AB交替模式操作流程**
```mermaid
graph TD
    A[选择AB交替模式] --> B[准备阶段红灯]
    B --> C[AB屏同步倒计时10秒]
    C --> D[绿灯阶段开始]
    D --> E[自动播放2声鸣笛]
    E --> F[A屏开始倒计时 B屏暂停]
    F --> G[点击"屏幕切换"按钮]
    G --> H[根据比赛类型应用规则]
    H --> I[个人赛: 原屏清零 新屏重新开始]
    H --> J[团队赛: 原屏暂停 新屏继续]
    I --> K[继续计时]
    J --> K
```

### **控制按钮功能详解**
- **开始/暂停**: 控制计时器的启动和暂停
- **重置**: 重置所有计时器到初始状态
- **屏幕切换**: 在AB交替模式下切换活动屏幕
- **手动鸣笛**: 随时播放鸣笛声
- **消音开关**: 全局开启/关闭所有声音
- **音量调节**: 精细控制音量大小

### **显示端特点**
- **全屏显示**: F11进入全屏模式，适合比赛现场
- **状态指示**: 清晰显示屏幕状态（活动中/等待中）
- **阶段显示**: 实时显示当前比赛阶段和剩余时间
- **连接状态**: 顶部指示器显示WebSocket连接状态

## ⚠️ 故障排除

### **常见问题解决方案**

#### **1. 后端启动失败**
```bash
# 检查Java版本 (需要Java 17+)
java -version

# 检查端口占用
netstat -ano | findstr :8080

# 清理重新编译
cd backend && mvn clean compile

# 查看详细错误
mvn spring-boot:run -X
```

#### **2. 前端启动失败**
```bash
# 清理node_modules重新安装
cd frontend
rm -rf node_modules package-lock.json
npm install

# 检查Node.js版本 (需要Node.js 16+)
node --version
```

#### **3. WebSocket连接失败**
```
1. 确认后端服务正在运行 (端口8080)
2. 检查浏览器控制台(F12)错误信息
3. 确认防火墙未阻止WebSocket连接
4. 刷新页面重新连接
```

#### **4. 屏幕不同步**
```
1. 所有设备在同一局域网
2. 控制台获得控制权限
3. 检查控制台顶部连接状态
4. 刷新显示端页面重新连接
```

### **调试模式启动**
```bash
# 后端调试模式
cd backend && mvn spring-boot:run -Ddebug

# 前端调试模式
cd frontend && npm run dev -- --debug

# 日志查看
# 后端: backend/logs/application.log
# 前端: 浏览器F12 → Console
```

## 🛠️ 高级配置

### **自定义比赛类型**
```json
// backend/src/main/resources/config/match-types.json
{
  "your_custom_type": {
    "id": "your_custom_type",
    "name": "自定义比赛",
    "chineseName": "自定义比赛类型",
    "preparationTime": 15,
    "competitionTime": 120,
    "yellowLightTime": 15,
    "defaultScreenMode": "alternate",
    "supportABAlternate": true,
    "alternateType": "individual_alternate",
    "resetOnSwitchIndividual": true,
    "pauseOnSwitchTeam": false,
    "defaultAPrompt": "自定义A",
    "defaultBPrompt": "自定义B",
    "description": "自定义比赛类型配置"
  }
}
```

### **修改端口配置**
```yaml
# backend/src/main/resources/application.yml
server:
  port: 9090  # 修改后端端口

# frontend/vite.config.js
export default defineConfig({
  server: {
    port: 4000,  # 修改前端端口
    proxy: {
      '/api': 'http://localhost:9090',  # 更新API代理
      '/ws': { target: 'ws://localhost:9090', ws: true }
    }
  }
})
```

### **更换鸣笛声音**
1. 准备MP3格式音频文件
2. 替换 `frontend/public/sounds/countdown_audio.mp3`
3. 保持文件名一致，无需修改代码

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

```
MIT License

Copyright (c) 2024 射箭比赛计时控制系统

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 📞 技术支持

### **系统状态检查**
- **后端状态**: http://localhost:8080/api/timer/status
- **比赛类型API**: http://localhost:8080/api/match-types
- **连接状态**: 控制台顶部状态指示器
- **在线客户端**: 控制台显示已连接客户端数

### **验证报告**
详细系统验证和测试结果请查看：[SYSTEM_VALIDATION_REPORT.md](SYSTEM_VALIDATION_REPORT.md)

### **问题反馈**
- 部署问题: 检查环境配置和端口占用
- 功能问题: 查看控制台日志和错误信息
- 性能问题: 监控系统资源和网络状态

## 🎉 系统特点总结

### **技术优势**
- 🚀 **高性能**: WebSocket实时通信，<100ms同步延迟
- 🔒 **安全性**: 纯本地运行，无网络泄露风险
- 📱 **兼容性**: 支持主流浏览器和设备
- 🔧 **可维护**: 模块化设计，易于维护扩展

### **应用优势**
- 🎯 **专业性**: 专为射箭比赛设计，符合比赛规范
- ⏱️ **精准性**: 后端统一计时，避免前端时钟漂移
- 👥 **协作性**: 多人协作，各司其职
- 🎨 **美观性**: 现代化界面，符合现场显示要求

### **部署优势**
- 🖥️ **零依赖**: 无需数据库，无需复杂环境
- ⚡ **快速启动**: 一键启动，5分钟内可投入使用
- 📦 **便携性**: 单机部署，适合移动比赛现场

---

## ✅ 系统已通过全面验证，可直接在生产环境部署使用！

**使用场景**:
- 🏹 射箭比赛现场计时
- 🎯 训练和模拟比赛
- 🏆 赛事组织和裁判工作
- 📚 教学演示和培训

**系统准备就绪，祝您比赛顺利！** 🏹