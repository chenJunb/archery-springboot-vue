const { app, BrowserWindow, Menu, ipcMain } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const isDev = require('electron-is-dev');

let mainWindow;
let logWindow;
let backendProcess;
const backendPort = 8080;
const frontendPort = 3000;

/**
 * 启动后端 Spring Boot 应用
 */
function startBackend() {
  return new Promise((resolve, reject) => {
    try {
      const jarPath = path.join(__dirname, '../backend/target');
      const jarFile = fs.readdirSync(jarPath).find(f => f.endsWith('.jar'));

      if (!jarFile) {
        throw new Error('未找到后端 JAR 文件');
      }

      const fullJarPath = path.join(jarPath, jarFile);
      console.log(`🚀 启动后端: ${fullJarPath}`);

      backendProcess = spawn('java', [
        '-jar',
        fullJarPath,
        `--server.port=${backendPort}`
      ]);

      backendProcess.stdout.on('data', (data) => {
        const message = `[后端] ${data.toString()}`;
        console.log(message);
        if (logWindow) {
          logWindow.webContents.send('log-message', message);
        }
      });

      backendProcess.stderr.on('data', (data) => {
        const message = `[后端-错误] ${data.toString()}`;
        console.error(message);
        if (logWindow) {
          logWindow.webContents.send('log-message', message);
        }
      });

      backendProcess.on('error', (error) => {
        console.error('后端启动失败:', error);
        reject(error);
      });

      // 给后端一些时间启动
      setTimeout(() => resolve(), 3000);
    } catch (error) {
      console.error('启动后端出错:', error);
      reject(error);
    }
  });
}

/**
 * 创建日志窗口
 */
function createLogWindow() {
  logWindow = new BrowserWindow({
    width: 800,
    height: 600,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true
    }
  });

  // 加载日志页面
  const logPagePath = path.join(__dirname, 'log.html');
  logWindow.loadFile(logPagePath);

  logWindow.on('closed', () => {
    logWindow = null;
  });

  return logWindow;
}

/**
 * 创建主窗口
 */
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true
    }
  });

  const url = isDev
    ? `http://localhost:${frontendPort}`
    : `file://${path.join(__dirname, '../frontend/dist/index.html')}`;

  console.log(`📱 加载前端: ${url}`);
  mainWindow.loadURL(url);

  if (isDev) {
    mainWindow.webContents.openDevTools();
  }

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

/**
 * 创建应用菜单
 */
function createMenu() {
  const template = [
    {
      label: '文件',
      submenu: [
        {
          label: '显示日志窗口',
          accelerator: 'CmdOrCtrl+L',
          click: () => {
            if (logWindow) {
              logWindow.focus();
            } else {
              createLogWindow();
            }
          }
        },
        {
          label: '退出',
          accelerator: 'CmdOrCtrl+Q',
          click: () => {
            app.quit();
          }
        }
      ]
    },
    {
      label: '帮助',
      submenu: [
        {
          label: '关于',
          click: () => {
            const { dialog } = require('electron');
            dialog.showMessageBox(mainWindow, {
              type: 'info',
              title: '关于射箭比赛计时系统',
              message: '射箭比赛计时系统 v1.0.0',
              detail: '一个专业的射箭比赛计时和控制系统'
            });
          }
        }
      ]
    }
  ];

  const menu = Menu.buildFromTemplate(template);
  Menu.setApplicationMenu(menu);
}

/**
 * 应用启动
 */
app.on('ready', async () => {
  try {
    console.log('🌍 应用启动中...');

    // 创建日志窗口
    createLogWindow();

    // 启动后端
    await startBackend();
    console.log(`✅ 后端启动成功，运行在 http://localhost:${backendPort}`);

    // 创建主窗口
    createWindow();
    console.log(`✅ 前端启动成功，运行在 http://localhost:${frontendPort}`);

    // 创建菜单
    createMenu();

    // 显示启动信息
    if (logWindow) {
      logWindow.webContents.send('log-message', `
🎯 射箭比赛计时系统启动完成

📋 访问地址:
- 控制端: http://localhost:${frontendPort}
- 后端API: http://localhost:${backendPort}
- A屏显示: http://localhost:${frontendPort}/display-a
- B屏显示: http://localhost:${frontendPort}/display-b

💡 快捷键:
- Ctrl+L: 显示/隐藏日志窗口
- Ctrl+Q: 退出应用
- F12: 开发工具 (开发模式)

⚠️ 关闭此窗口或应用会停止所有服务
      `);
    }
  } catch (error) {
    console.error('应用启动失败:', error);
    if (logWindow) {
      logWindow.webContents.send('log-message', `❌ 启动失败: ${error.message}`);
    }
  }
});

/**
 * 应用退出
 */
app.on('window-all-closed', () => {
  // 停止后端进程
  if (backendProcess) {
    console.log('🛑 停止后端进程...');
    backendProcess.kill();
  }

  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (mainWindow === null) {
    createWindow();
  }
});

/**
 * IPC通信处理
 */
ipcMain.handle('get-app-info', async () => {
  return {
    version: app.getVersion(),
    platform: process.platform,
    nodeVersion: process.versions.node,
    chromeVersion: process.versions.chrome
  };
});

/**
 * 防止在生产环境打包时加载本地文件
 */
if (isDev) {
  if (process.argv.includes('--dev')) {
    console.log('📝 开发模式已激活');
  }
}

// 处理任何未捕获的异常
process.on('uncaughtException', (error) => {
  console.error('未捕获的异常:', error);
  if (logWindow) {
    logWindow.webContents.send('log-message', `⚠️ 未捕获的异常: ${error.message}`);
  }
});
