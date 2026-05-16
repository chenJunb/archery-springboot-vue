# 📦 射箭比赛计时系统 - 打包编译操作指南

## 📋 目录
1. [系统要求](#系统要求)
2. [快速开始](#快速开始)
3. [完整编译流程](#完整编译流程)
4. [输出文件说明](#输出文件说明)
5. [常见问题](#常见问题)
6. [文件大小分析](#文件大小分析)

---

## 🖥️ 系统要求

### 开发环境
- **操作系统**: Windows 10/11 Pro 或更高版本
- **Node.js**: v14 或更高版本
- **Java**: JDK 11 或更高版本
- **Maven**: v3.6 或更高版本
- **磁盘空间**: 至少 10GB（编译过程需要）

### 运行环境
- **操作系统**: Windows 7 SP1 或更高版本
- **内存**: 4GB 或更高
- **磁盘空间**: 1GB（安装后占用约 650MB）

---

## 🚀 快速开始

### 方式一：使用 npm 命令（推荐）

```bash
cd D:\code\ArcheryCompetition\archery-springboot-vue

# 完整编译并打包成 exe
npm run dist
```

**预期输出**：
- 位置：`dist/射箭比赛计时系统 Setup 1.0.0.exe`
- 大小：约 574 MB
- 编译时间：4-5 分钟

### 方式二：使用 build.bat 脚本

```bash
cmd /c build.bat
```

**功能**：
- 自动执行后端编译、前端编译、npm 依赖安装和打包
- 提供详细的编译进度日志
- 遇到错误会立即停止并显示错误信息

---

## 🔧 完整编译流程

### 步骤 1：环境验证

```bash
# 验证 Node.js 版本
node --version

# 验证 Java 版本
java -version

# 验证 Maven 版本
mvn --version
```

**要求**：
- Node.js: v14+
- Java: 11+
- Maven: 3.6+

### 步骤 2：清理旧文件（可选但推荐）

```powershell
cd D:\code\ArcheryCompetition\archery-springboot-vue

# 清理编译输出目录
Remove-Item -Path dist -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path backend/target -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path frontend/dist -Recurse -Force -ErrorAction SilentlyContinue
```

### 步骤 3：后端编译

```bash
cd backend

# 编译并打包 JAR 文件
mvn clean package -DskipTests

# 预期输出：
# - backend/target/archery-timer-1.0.0.jar (22MB)
```

**编译时间**：约 40-60 秒

### 步骤 4：前端编译

```bash
cd ../frontend

# 安装依赖
npm install

# 构建生产版本
npm run build

# 预期输出：
# - frontend/dist/ 目录（包含 index.html 和 assets/）
```

**编译时间**：约 15-20 秒

### 步骤 5：根目录依赖安装

```bash
cd ..

# 安装根目录依赖
npm install
```

**编译时间**：约 10-20 秒（首次可能更长）

### 步骤 6：打包成 exe

```bash
# 使用 electron-builder 打包
npx electron-builder --win --publish=never
```

**预期输出**：
- `dist/射箭比赛计时系统 Setup 1.0.0.exe` (574MB)
- `dist/latest.yml`（更新配置文件）
- `dist/win-unpacked/`（解包后的文件目录）

**编译时间**：约 1-2 分钟

---

## 📦 输出文件说明

### 主要输出文件

#### 1. 安装程序 exe
```
dist/射箭比赛计时系统 Setup 1.0.0.exe (574 MB)
```
- **类型**：NSIS 安装程序
- **大小**：约 574MB（压缩后）
- **安装位置**：`C:\Users\{username}\AppData\Local\Programs\archery-timer\`
- **功能**：包含完整的应用程序、运行环境和所有依赖

#### 2. 可执行文件目录
```
dist/win-unpacked/
├── 射箭比赛计时系统.exe (217 MB) - 主应用程序
├── resources/
│   ├── app.asar (1.3 GB 压缩)
│   ├── jre/ (307 MB) - Java 运行环境
│   ├── archery-timer-1.0.0.jar (22 MB)
│   ├── frontend/dist/ (1.6 MB)
│   └── elevate.exe
├── locales/
├── libGLESv2.dll
├── dxcompiler.dll
└── ... (其他 Electron 依赖)
```

#### 3. 更新配置文件
```
dist/latest.yml
dist/射箭比赛计时系统 Setup 1.0.0.exe.blockmap
```

---

## 📊 文件大小分析

### 最终 exe 大小构成

| 组件 | 原始大小 | 压缩后 | 说明 |
|------|--------|-------|------|
| app.asar | 1.3 GB | ~300 MB | Node.js + Electron + Vue 依赖 |
| jre | 307 MB | 307 MB | Java 运行环境（必需） |
| archery-timer.jar | 22 MB | 22 MB | 后端应用（必需） |
| frontend/dist | 1.6 MB | 1.6 MB | 前端应用（必需） |
| 其他（dll/pak等）| ~200 MB | ~44 MB | Chromium 相关文件 |
| **总计** | **~1.8 GB** | **~574 MB** | NSIS 安装程序 |

### 安装后磁盘占用

```
C:\Users\{username}\AppData\Local\Programs\archery-timer\
├── resources/
│   ├── app.asar: 1.3 GB
│   ├── jre/: 307 MB
│   ├── archery-timer-1.0.0.jar: 22 MB
│   └── frontend/dist/: 1.6 MB
├── 射箭比赛计时系统.exe: 217 MB
├── 其他依赖: ~150 MB
└── 日志文件: ~100 MB (动态增长)
───────────────────────
总计: 约 2.1 GB
```

---

## ⚠️ 常见问题

### Q1: 编译失败 - "后端构建目录不存在"

**原因**：后端未编译

**解决方案**：
```powershell
cd backend
mvn clean package -DskipTests
cd ..
```

### Q2: 编译失败 - "前端构建目录不存在"

**原因**：前端未编译

**解决方案**：
```bash
cd frontend
npm install
npm run build
cd ..
```

### Q3: 编译失败 - "Java 可执行文件不存在"

**原因**：Java 未安装或未配置到 PATH

**解决方案**：
1. 下载并安装 JDK 11+
2. 配置 JAVA_HOME 环境变量
3. 验证：`java -version`

### Q4: 编译失败 - "缺少 JRE"

**原因**：`jre` 目录未找到

**解决方案**：
1. 确保 `D:\code\ArcheryCompetition\archery-springboot-vue\jre\` 存在
2. 如果不存在，下载 OpenJDK 11 portable 版本
3. 将其放在 jre 目录下

### Q5: exe 文件异常大（1.2GB+）

**原因**：
- Electron 版本差异导致额外文件被打包
- 临时文件未清理

**解决方案**：
```powershell
# 完全清理
Remove-Item -Path dist -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path backend/target -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path frontend/dist -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path node_modules -Recurse -Force -ErrorAction SilentlyContinue

# 清理 npm 缓存
npm cache clean --force

# 重新开始
npm install
npm run dist
```

### Q6: 编译过程卡住

**原因**：
- 网络连接问题
- 磁盘满
- 进程被占用

**解决方案**：
```bash
# 强制停止所有 Node 进程
taskkill /F /IM node.exe

# 检查磁盘空间（需要至少 5GB 可用空间）
# 清理 npm 缓存
npm cache clean --force

# 重新开始
npm run dist
```

---

## 🔍 验证编译结果

### 1. 检查 exe 是否可执行

```powershell
# 检查文件大小
ls -lh dist/射箭比赛计时系统\ Setup\ 1.0.0.exe

# 预期大小：574 MB
```

### 2. 测试安装程序

```powershell
# 运行安装程序
& ".\dist\射箭比赛计时系统 Setup 1.0.0.exe"
```

### 3. 检查安装后的文件

```powershell
# 验证安装位置
ls "C:\Users\$env:USERNAME\AppData\Local\Programs\archery-timer\"

# 预期文件：
# - 射箭比赛计时系统.exe
# - resources/jre
# - resources/archery-timer-1.0.0.jar
# - resources/frontend/dist
```

### 4. 测试应用运行

```powershell
# 启动应用
& "C:\Users\$env:USERNAME\AppData\Local\Programs\archery-timer\射箭比赛计时系统.exe"

# 预期行为：
# - 启动日志窗口
# - 显示系统初始化日志
# - 后端在 http://localhost:8080 启动
# - 前端在 http://localhost:3000 启动
# - 应用主窗口打开
```

---

## 📝 编译日志检查

### 日志位置

编译后的应用日志保存在：
```
C:\Users\{username}\AppData\Local\Programs\archery-timer\logs\
```

### 查看日志

```powershell
# 查看最新日志文件
ls -lht "C:\Users\$env:USERNAME\AppData\Local\Programs\archery-timer\logs\" | head -5

# 查看启动日志
cat "C:\Users\$env:USERNAME\AppData\Local\Programs\archery-timer\logs\app-*.log"
```

### 常见日志错误

**错误**：`后端启动失败`
```
[错误] 启动后端时捕获异常: 未找到后端 JAR
```
**原因**：JAR 文件未正确打包
**解决**：重新编译后端

**错误**：`前端构建目录不存在`
```
[错误] 前端构建目录不存在: C:\...\resources\frontend\dist
```
**原因**：前端未正确打包
**解决**：重新编译前端

---

## 🔐 安全检查

### 签名验证

```powershell
# 检查 exe 是否已签名
signtool verify /v "dist\射箭比赛计时系统 Setup 1.0.0.exe"
```

### 病毒检查

```powershell
# 使用 Windows Defender 扫描
Start-MpScan -ScanType FullScan -ScanPath 'dist\'
```

---

## 📚 相关配置文件

### package.json - 打包配置

```json
{
  "build": {
    "appId": "com.archery.timer",
    "productName": "射箭比赛计时系统",
    "files": [
      "dist",
      "backend/target/archery-timer-*.jar",
      "electron/main.js",
      "electron/preload.js",
      "electron/log.html",
      "jre/**/*",
      "node_modules/**/*"
    ],
    "extraResources": [
      {
        "from": "backend/target/archery-timer-1.0.0.jar",
        "to": "archery-timer-1.0.0.jar"
      },
      {
        "from": "frontend/dist",
        "to": "frontend/dist"
      },
      {
        "from": "jre",
        "to": "jre"
      }
    ],
    "nsis": {
      "oneClick": false,
      "allowToChangeInstallationDirectory": true,
      "createDesktopShortcut": true,
      "createStartMenuShortcut": true
    }
  }
}
```

---

## 🎯 编译性能优化

### 首次编译

首次编译需要下载 Electron 等依赖，会比较慢（10-15 分钟）。

```bash
npm run dist
```

### 后续编译

后续编译会快得多（4-5 分钟），使用缓存的依赖。

```bash
npm run dist
```

### 快速重编译（仅前端/后端变化）

```bash
# 只重新编译后端
cd backend && mvn clean package -DskipTests && cd ..

# 只重新编译前端
cd frontend && npm run build && cd ..

# 然后重新打包
npx electron-builder --win --publish=never
```

---

## 💾 版本更新步骤

### 更新版本号

1. 编辑 `package.json`，修改 version：
   ```json
   "version": "1.0.1"
   ```

2. 编辑 `backend/pom.xml`，修改 version：
   ```xml
   <version>1.0.1</version>
   ```

3. 编辑 `frontend/package.json`，修改 version：
   ```json
   "version": "1.0.1"
   ```

4. 提交 git 变更
   ```bash
   git add .
   git commit -m "版本更新: 1.0.1"
   ```

5. 重新编译
   ```bash
   npm run dist
   ```

6. 输出文件会自动更新为新版本号
   ```
   dist/射箭比赛计时系统 Setup 1.0.1.exe
   ```

---

## 🚀 发布检查清单

在发布新版本前，请检查以下事项：

- [ ] 所有功能测试通过
- [ ] 后端 API 正常响应
- [ ] 前端界面正常显示
- [ ] WebSocket 连接正常
- [ ] 日志输出完整
- [ ] 没有 JavaScript 错误
- [ ] 没有 Java 异常
- [ ] 性能满足要求
- [ ] 安装程序大小合理（<1GB）
- [ ] 版本号已更新
- [ ] Git 提交已完成

---

## 📞 故障排查流程

### 如果编译失败

1. **检查错误信息**
   ```powershell
   # 查看完整错误日志
   npm run dist 2>&1 | tail -50
   ```

2. **验证环境**
   ```powershell
   node --version
   java -version
   mvn --version
   ```

3. **清理缓存**
   ```powershell
   npm cache clean --force
   Remove-Item -Path node_modules -Recurse -Force -ErrorAction SilentlyContinue
   npm install
   ```

4. **逐步编译测试**
   ```powershell
   cd backend
   mvn clean package -DskipTests
   cd ../frontend
   npm run build
   cd ..
   npm install
   npx electron-builder --win
   ```

5. **查看详细日志**
   - 后端编译日志：`backend/build.log`
   - 前端编译日志：终端输出
   - Electron 打包日志：终端输出

### 如果应用无法启动

1. **检查日志文件**
   ```
   C:\Users\{username}\AppData\Local\Programs\archery-timer\logs\
   ```

2. **检查 JAR 文件**
   ```bash
   jar -tf resources/archery-timer-1.0.0.jar | head
   ```

3. **检查前端文件**
   ```bash
   ls -la resources/frontend/dist/
   ```

4. **重新安装**
   - 卸载应用
   - 删除日志目录
   - 重新安装

---

## 📖 相关文档

- [项目 README](README.md)
- [开发指南](DEVELOPMENT.md)
- [API 文档](docs/API.md)
- [配置文档](docs/CONFIG.md)

---

**最后更新**：2026-05-15  
**维护者**：Claude Code  
**版本**：1.0.0

