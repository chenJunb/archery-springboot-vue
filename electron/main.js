const { app, BrowserWindow, Menu, ipcMain, dialog } = require('electron')
const { spawn } = require('child_process')
const path = require('path')
const fs = require('fs')
const http = require('http')
const url = require('url')
// mime 模块
const mime = require('mime').default;
// 检测是否是开发环境（替代 electron-is-dev）
const isDev = process.defaultApp || /[\\/]electron[\\/]/.test(process.execPath) || process.env.NODE_ENV === 'development'

// 应用目录配置 - 将所有数据放在安装目录下
const APP_NAME = '射箭比赛计时系统'
let appDataDir
if (isDev) {
  // 开发环境：使用项目目录
  appDataDir = path.join(__dirname, '../data')
} else {
  // 生产环境：使用可执行文件所在目录
  appDataDir = path.dirname(process.execPath)
}
const LOGS_DIR = path.join(appDataDir, 'logs')

let mainWindow
let logWindow
let backendProcess
const backendPort = 8080
const frontendPort = 3000
let frontendHttpServer

function getJavaExecutable() {
  if (isDev) {
    // 开发环境：检查系统是否安装了Java
    sendLog('[开发环境] 检查系统Java...')

    // 尝试查找Java可执行文件
    const javaCommands = ['java', 'java.exe']

    for (const javaCmd of javaCommands) {
      try {
        // 检查Java是否可通过PATH环境变量访问
        const { spawnSync } = require('child_process')
        const result = spawnSync(javaCmd, ['-version'], { encoding: 'utf8' })

        if (result.status === 0 || result.error === null) {
          sendLog(`[开发环境] 找到Java: ${javaCmd}`)

          // 检查Java版本
          if (result.stdout && result.stdout.includes('version')) {
            const versionMatch = result.stdout.match(/version\s+"([^"]+)"/) ||
                                 result.stderr.match(/version\s+"([^"]+)"/)
            if (versionMatch) {
              sendLog(`[开发环境] Java版本: ${versionMatch[1]}`)
            }
          }

          return javaCmd
        }
      } catch (error) {
        // 继续尝试下一个命令
        continue
      }
    }

    // 如果都没有找到，检查常见的Java安装路径
    const commonJavaPaths = [
      path.join('C:', 'Program Files', 'Java', 'jdk-*', 'bin', 'java.exe'),
      path.join('C:', 'Program Files', 'Java', 'jre-*', 'bin', 'java.exe'),
      path.join('C:', 'Program Files (x86)', 'Java', 'jre-*', 'bin', 'java.exe'),
    ]

    for (const javaPathPattern of commonJavaPaths) {
      // 处理通配符路径
      const dir = path.dirname(javaPathPattern)
      const basename = path.basename(javaPathPattern)

      if (fs.existsSync(path.dirname(dir))) {
        const javaDir = dir.replace('*', '')
        const exactPath = path.join(javaDir, basename)

        if (fs.existsSync(exactPath)) {
          sendLog(`[开发环境] 找到Java安装: ${exactPath}`)
          return exactPath
        }
      }
    }

    const errorMsg = '系统未安装Java或未配置PATH环境变量\n' +
                   '请安装Java并确保java命令可用\n' +
                   '下载地址: https://www.oracle.com/java/technologies/downloads/'
    sendLog(`[错误] ${errorMsg}`)
    throw new Error(errorMsg)
  }

  // 生产环境：使用内置JRE
  const jreExe = path.join(process.resourcesPath, 'jre', 'bin', 'java.exe')
  if (fs.existsSync(jreExe)) {
    sendLog(`[生产环境] 使用内置JRE: ${jreExe}`)
    return jreExe
  }

  const errorMsg = '内置 JRE 未找到: ' + jreExe
  sendLog(`[错误] ${errorMsg}`)
  throw new Error(errorMsg)
}

function getJarPath() {
  if (isDev) {
    const jarDir = path.join(__dirname, '../backend/target')
    if (!fs.existsSync(jarDir)) {
      throw new Error('后端构建目录不存在: ' + jarDir)
    }
    const jar = fs.readdirSync(jarDir).find(f => f.endsWith('.jar') && !f.includes('original'))
    if (!jar) throw new Error('未找到后端 JAR，请先执行 mvn package')
    const jarPath = path.join(jarDir, jar)
    sendLog(`[开发环境] JAR文件: ${jarPath}`)
    return jarPath
  }

  // 生产环境：查找 archery-timer-*.jar 文件
  const resourcesPath = process.resourcesPath
  const jarFiles = fs.readdirSync(resourcesPath).filter(f =>
    f.startsWith('archery-timer-') && f.endsWith('.jar') && !f.includes('original')
  )

  if (jarFiles.length === 0) {
    // 如果没找到 archery-timer-*.jar，尝试查找任意的 .jar 文件
    const allJars = fs.readdirSync(resourcesPath).filter(f =>
      f.endsWith('.jar') && !f.includes('original')
    )

    if (allJars.length === 0) {
      const files = fs.readdirSync(resourcesPath)
      throw new Error(`未找到后端 JAR，资源目录内容: ${files.join(', ')}`)
    }

    // 使用第一个找到的 JAR 文件
    const jarPath = path.join(resourcesPath, allJars[0])
    sendLog(`[生产环境] 使用JAR文件: ${jarPath}`)
    return jarPath
  }

  // 使用第一个匹配的 archery-timer-*.jar 文件
  const jarPath = path.join(resourcesPath, jarFiles[0])
  sendLog(`[生产环境] 使用JAR文件: ${jarPath}`)
  return jarPath
}

function sendLog(msg) {
  const timestamp = new Date().toISOString().replace('T', ' ').substring(0, 19)
  const logLine = `[${timestamp}] ${msg}`
  console.log(logLine)

  // 发送到日志窗口
  if (logWindow && !logWindow.isDestroyed()) {
    logWindow.webContents.send('log-message', msg)
  }

  // 写入日志文件
  try {
    // 确保日志目录存在
    if (!fs.existsSync(LOGS_DIR)) {
      fs.mkdirSync(LOGS_DIR, { recursive: true })
    }

    const logFile = path.join(LOGS_DIR, `app-${new Date().toISOString().substring(0, 10)}.log`)
    fs.appendFileSync(logFile, logLine + '\n', 'utf8')
  } catch (err) {
    console.error('写入日志文件失败:', err.message)
  }
}

function waitForBackend(port, maxWait) {
  return new Promise((resolve, reject) => {
    const start = Date.now()
    const check = () => {
      http.get(`http://localhost:${port}/`, res => resolve())
        .on('error', () => {
          if (Date.now() - start > maxWait) return reject(new Error('后端启动超时（60秒）'))
          setTimeout(check, 1000)
        })
    }
    check()
  })
}

function startBackend() {
  return new Promise((resolve, reject) => {
    try {
      const javaExe = getJavaExecutable()
      const jarPath = getJarPath()
      sendLog(`[启动] 启动后端 (Java: ${javaExe}, JAR: ${jarPath})`)

      // 检查 Java 可执行文件是否存在
      // 只在生产环境（有完整路径）或绝对路径时检查文件是否存在
      // 开发环境中，如果javaExe是命令名（如'java'），则跳过文件存在检查
      if (javaExe.includes(path.sep) && !fs.existsSync(javaExe)) {
        throw new Error(`Java 可执行文件不存在: ${javaExe}`)
      }

      // 检查 JAR 文件是否存在
      if (!fs.existsSync(jarPath)) {
        throw new Error(`JAR 文件不存在: ${jarPath}`)
      }

      sendLog(`[命令] 启动命令: "${javaExe}" -Dfile.encoding=UTF-8 -jar "${jarPath}" --server.port=${backendPort}`)

      backendProcess = spawn(javaExe, [
        '-Dfile.encoding=UTF-8',
        '-Dconsole.encoding=UTF-8',
        '-jar', jarPath,
        `--server.port=${backendPort}`
      ], {
        stdio: ['pipe', 'pipe', 'pipe'],
        windowsHide: false,
        env: {
          ...process.env,
          APP_INSTALL_DIR: appDataDir, // 传递安装目录给后端
          LOGGING_FILE_DIRECTORY: path.join(appDataDir, 'logs') // 指定日志目录
        }
      })

      backendProcess.stdout.on('data', d => {
        let output
        try {
          // 尝试多种编码
          output = d.toString('utf8').trim()
          // 如果包含乱码字符，尝试GBK解码
          if (/[\uFFFD\uFFFE]/.test(output)) {
            output = d.toString('binary').trim()
          }
        } catch (e) {
          output = d.toString().trim()
        }
        sendLog(`[后端] ${output}`)
      })

      backendProcess.stderr.on('data', d => {
        let output
        try {
          output = d.toString('utf8').trim()
          if (/[\uFFFD\uFFFE]/.test(output)) {
            output = d.toString('binary').trim()
          }
        } catch (e) {
          output = d.toString().trim()
        }
        sendLog(`[后端-错误] ${output}`)
        // 如果是关键错误，立即失败
        if (output.includes('ERROR') || output.includes('Exception') || output.includes('无法启动')) {
          reject(new Error(`后端启动失败: ${output}`))
        }
      })

      backendProcess.on('error', err => {
        sendLog(`[错误] 后端进程错误: ${err.message}`)
        reject(err)
      })

      backendProcess.on('exit', (code, signal) => {
        if (code !== null) {
          sendLog(`[警告] 后端进程退出，代码: ${code}`)
          if (code !== 0) {
            reject(new Error(`后端进程异常退出，代码: ${code}`))
          }
        } else if (signal) {
          sendLog(`[警告] 后端进程被信号终止: ${signal}`)
        }
      })

      // 等待后端就绪
      waitForBackend(backendPort, 60000).then(resolve).catch(reject)
    } catch (err) {
      sendLog(`[错误] 启动后端时捕获异常: ${err.message}`)
      reject(err)
    }
  })
}

function startFrontendHttpServer() {
  return new Promise((resolve, reject) => {
    try {
      sendLog(`[HTTP] 启动前端HTTP服务器 (端口: ${frontendPort})`)

      const frontendDistPath = isDev
        ? path.join(__dirname, '../frontend/dist')
        : path.join(process.resourcesPath, 'frontend/dist')

      if (!fs.existsSync(frontendDistPath)) {
        throw new Error(`前端构建目录不存在: ${frontendDistPath}`)
      }

      frontendHttpServer = http.createServer((req, res) => {
        try {
          const parsedUrl = url.parse(req.url)

          // 代理API请求到后端
          if (parsedUrl.pathname.startsWith('/api/') ||
              parsedUrl.pathname.startsWith('/ws-archery-timer')) {
            proxyToBackend(req, res)
            return
          }

          // 处理静态文件请求
          serveStaticFile(parsedUrl, req, res)

        } catch (error) {
          sendLog(`[错误] HTTP服务器处理请求出错: ${error.message}`)
          res.writeHead(500, { 'Content-Type': 'text/plain' })
          res.end('500 Internal Server Error')
        }
      })

      frontendHttpServer.on('error', (error) => {
        sendLog(`[错误] HTTP服务器出错: ${error.message}`)
        reject(error)
      })

      frontendHttpServer.listen(frontendPort, '127.0.0.1', () => {
        sendLog(`[成功] 前端HTTP服务器启动成功: http://localhost:${frontendPort}`)
        resolve()
      })

    } catch (error) {
      sendLog(`[错误] 启动前端HTTP服务器失败: ${error.message}`)
      reject(error)
    }
  })
}

function killBackend() {
  if (!backendProcess) return
  sendLog('[停止] 停止后端进程...')
  try {
    if (process.platform === 'win32') {
      spawn('taskkill', ['/F', '/T', '/PID', String(backendProcess.pid)], { detached: true, stdio: 'ignore' })
    } else {
      backendProcess.kill('SIGTERM')
    }
  } catch (e) {
    sendLog(`[警告] 停止后端时出错: ${e.message}`)
  }
  backendProcess = null
}

function serveStaticFile(parsedUrl, req, res) {
  let filePath = parsedUrl.pathname === '/' ? '/index.html' : parsedUrl.pathname

  // 防止路径遍历攻击
  filePath = decodeURIComponent(filePath).replace(/\.\./g, '')

  const frontendDistPath = isDev
    ? path.join(__dirname, '../frontend/dist')
    : path.join(process.resourcesPath, 'frontend/dist')

  const fullPath = path.join(frontendDistPath, filePath)

  // 检查文件是否存在
  if (!fs.existsSync(fullPath)) {
    // 对于单页应用，重定向到index.html处理路由
    const indexPath = path.join(frontendDistPath, 'index.html')
    if (fs.existsSync(indexPath)) {
      res.writeHead(200, {
        'Content-Type': 'text/html; charset=utf-8',
        'Cache-Control': 'no-cache'
      })
      res.end(fs.readFileSync(indexPath))
      return
    }
    res.writeHead(404, { 'Content-Type': 'text/plain' })
    res.end('404 Not Found')
    return
  }

  // 读取并发送文件
  const stats = fs.statSync(fullPath)
  if (stats.isDirectory()) {
    res.writeHead(403, { 'Content-Type': 'text/plain' })
    res.end('403 Forbidden')
    return
  }

  const contentType = mime.getType(fullPath) || 'application/octet-stream'
  const headers = {
    'Content-Type': contentType,
    'Content-Length': stats.size,
    'Cache-Control': 'no-cache'
  }

  res.writeHead(200, headers)
  const stream = fs.createReadStream(fullPath)
  stream.pipe(res)
}

function proxyToBackend(originalReq, originalRes) {
  const options = {
    hostname: '127.0.0.1',
    port: backendPort,
    path: originalReq.url,
    method: originalReq.method,
    headers: {
      ...originalReq.headers,
      host: '127.0.0.1:8080'
    }
  }

  const proxyReq = http.request(options, (proxyRes) => {
    originalRes.writeHead(proxyRes.statusCode, proxyRes.headers)
    proxyRes.pipe(originalRes)
  })

  proxyReq.on('error', (err) => {
    sendLog(`[错误] 代理请求到后端失败: ${err.message}`)
    originalRes.writeHead(502, { 'Content-Type': 'text/plain' })
    originalRes.end('Bad Gateway')
  })

  // 如果请求有body，则转发
  if (originalReq.method === 'POST' || originalReq.method === 'PUT' || originalReq.method === 'PATCH') {
    originalReq.pipe(proxyReq)
  } else {
    proxyReq.end()
  }
}

function killFrontendHttpServer() {
  if (!frontendHttpServer) return
  sendLog('[停止] 停止前端HTTP服务器...')
  try {
    frontendHttpServer.close()
  } catch (error) {
    sendLog(`[警告] 停止HTTP服务器时出错: ${error.message}`)
  }
  frontendHttpServer = null
}

function createLogWindow(showWindow = true) {
  logWindow = new BrowserWindow({
    width: 800, height: 600,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), nodeIntegration: false, contextIsolation: true },
    show: showWindow  // 默认不显示，只有需要时才显示
  })
  logWindow.loadFile(path.join(__dirname, 'log.html'))

  // 监听日志窗口关闭事件
  logWindow.on('close', (event) => {
    sendLog('[关闭] 日志窗口正在关闭，终止所有服务并退出应用...')

    // 用户关闭日志窗口时终止所有服务并退出整个应用
    killBackend()
    killFrontendHttpServer()

    // 强制退出整个应用
    setTimeout(() => {
      app.quit()
    }, 1000) // 延迟1秒确保清理完成
  })

  logWindow.on('closed', () => { logWindow = null })

  // 如果不需要显示，我们仍然要记录日志，但不会弹出窗口
  if (!showWindow) {
    sendLog('[系统] 日志窗口已创建但未显示')
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400, height: 900,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
      webSecurity: false // 允许加载本地文件
    },
    show: false // 先不显示，等页面加载完成
  })

  const url = `http://localhost:${frontendPort}`

  sendLog(`[前端] 加载前端: ${url} (通过HTTP)`)

  // 监听页面加载事件
  mainWindow.webContents.on('did-finish-load', () => {
    sendLog('[成功] 前端页面加载完成')
//    mainWindow.show()
    mainWindow.hide()
    mainWindow.focus()
  })

  mainWindow.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
    sendLog(`[错误] 前端页面加载失败: ${errorCode} - ${errorDescription}`)
    sendLog(`[URL] 尝试的URL: ${validatedURL}`)

    // 显示错误信息
    dialog.showErrorBox('前端加载失败',
      `无法加载前端页面: ${errorDescription}\n` +
      `URL: ${validatedURL}\n` +
      `请检查前端HTTP服务器是否启动在端口: ${frontendPort}\n` +
      `或检查前端文件是否存在:\n` +
      `${isDev ? path.join(__dirname, '../frontend/dist') : path.join(process.resourcesPath, 'frontend/dist')}`
    )
  })

  mainWindow.webContents.on('dom-ready', () => {
    sendLog('[成功] DOM已准备就绪')
  })

  // 加载页面
  mainWindow.loadURL(url)

  // 监听窗口关闭事件
  mainWindow.on('close', (event) => {
    // 在窗口关闭前终止后端进程和HTTP服务器并退出应用
    sendLog('[关闭] 主窗口正在关闭，停止服务并退出应用...')
    killBackend()
    killFrontendHttpServer()

    // 强制退出整个应用
    setTimeout(() => {
      app.quit()
    }, 1000) // 延迟1秒确保清理完成
  })

  mainWindow.on('closed', () => { mainWindow = null })
}

function createMenu() {
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    {
      label: '文件', submenu: [
        { label: '显示日志', accelerator: 'CmdOrCtrl+L', click: () => {
    if (logWindow) {
      logWindow.show()
      logWindow.focus()
    } else {
      createLogWindow(true)  // 显示窗口
    }
  }},
        { label: '退出', accelerator: 'CmdOrCtrl+Q', click: () => app.quit() }
      ]
    },
    {
      label: '帮助', submenu: [
        { label: '关于', click: () => dialog.showMessageBox(mainWindow, { type: 'info', title: '关于', message: '射箭比赛计时系统 v1.0.0' }) }
      ]
    }
  ]))
}

app.on('ready', async () => {
  // 创建日志窗口并在第一次启动时显示
  createLogWindow(true)
  createMenu()
  // 先检查资源文件
  checkResources()

  sendLog('[服务] 正在启动后端服务和前端HTTP服务器，请稍候...')
  try {
    // 启动后端服务
    await startBackend()
    sendLog(`[成功] 后端启动成功 http://localhost:${backendPort}`)

    // 启动前端HTTP服务器
    await startFrontendHttpServer()

    // 创建主窗口，现在可以通过HTTP访问前端
//    createWindow()

    // 成功启动后，可以最小化日志窗口但不关闭
    sendLog(`[成功] 系统启动完成，主窗口已打开`)
  } catch (err) {
    sendLog(`[错误] 启动失败: ${err.message}`)
    sendLog(`[提示] 日志文件位置: ${LOGS_DIR}`)

    // 启动失败时保持日志窗口显示（已经显示了）

    dialog.showErrorBox('启动失败',
      `${err.message}\n\n` +
      `详细日志已显示在日志窗口。\n` +
      `控制台页面: http://localhost:${frontendPort}\n` +
      `日志文件位置: ${LOGS_DIR}`)
  }
})

app.on('before-quit', () => { killBackend(); killFrontendHttpServer() })
app.on('window-all-closed', () => { killBackend(); killFrontendHttpServer(); if (process.platform !== 'darwin') app.quit() })
app.on('activate', () => { if (!mainWindow) createWindow() })

// 检查资源文件
function checkResources() {
  sendLog('[检查] 检查应用配置...')
  sendLog(`[目录] 应用安装目录: ${appDataDir}`)
  sendLog(`[目录] 日志目录: ${LOGS_DIR}`)
  sendLog(`[目录] 资源目录: ${process.resourcesPath}`)

  // 确保日志目录存在
  if (!fs.existsSync(LOGS_DIR)) {
    fs.mkdirSync(LOGS_DIR, { recursive: true })
    sendLog(`[目录] 已创建日志目录: ${LOGS_DIR}`)
  }

  try {
    const files = fs.readdirSync(process.resourcesPath)
    sendLog(`[目录] 资源文件列表: ${files.join(', ')}`)

    // 只在生产环境检查JAR和JRE文件
    if (!isDev) {
      // 检查关键文件
      const jrePath = path.join(process.resourcesPath, 'jre', 'bin', 'java.exe')

      // 使用与 getJarPath() 相同的逻辑查找 JAR 文件
      const jarFiles = fs.readdirSync(process.resourcesPath).filter(f =>
        f.startsWith('archery-timer-') && f.endsWith('.jar') && !f.includes('original')
      )

      if (jarFiles.length > 0) {
        const jarPath = path.join(process.resourcesPath, jarFiles[0])
        const stats = fs.statSync(jarPath)
        sendLog(`[JAR] JAR 文件: ${jarPath} (${stats.size} 字节)`)
      } else {
        // 如果没找到 archery-timer-*.jar，尝试查找任意的 .jar 文件
        const allJars = fs.readdirSync(process.resourcesPath).filter(f =>
          f.endsWith('.jar') && !f.includes('original')
        )

        if (allJars.length > 0) {
          const jarPath = path.join(process.resourcesPath, allJars[0])
          const stats = fs.statSync(jarPath)
          sendLog(`[JAR] JAR 文件: ${jarPath} (${stats.size} 字节)`)
        } else {
          const files = fs.readdirSync(process.resourcesPath).join(', ')
          sendLog(`[错误] 未找到 JAR 文件，资源目录内容: ${files}`)
          // 生产环境中抛出错误
          if (!isDev) {
            throw new Error(`未找到 JAR 文件，资源目录内容: ${files}`)
          }
        }
      }

      if (fs.existsSync(jrePath)) {
        sendLog(`[JRE] JRE 文件: ${jrePath}`)
      } else {
        sendLog(`[警告] JRE 文件不存在: ${jrePath}`)
        // 开发环境中这是正常的，不抛出错误
        if (!isDev) {
          throw new Error(`JRE文件不存在: ${jrePath}`)
        }
      }
    } else {
      sendLog('[检查] 开发环境：跳过JAR/JRE检查')
    }
  } catch (err) {
    sendLog(`[错误] 检查资源时出错: ${err.message}`)
  }
}

ipcMain.handle('get-app-info', () => ({ version: app.getVersion(), platform: process.platform, nodeVersion: process.versions.node }))

// 处理窗口控制
ipcMain.on('minimize-window', (event) => {
  if (logWindow) {
    logWindow.minimize()
  }
})

ipcMain.on('maximize-window', (event) => {
  if (logWindow) {
    if (logWindow.isMaximized()) {
      logWindow.unmaximize()
    } else {
      logWindow.maximize()
    }
  }
})

ipcMain.on('close-window', (event) => {
  if (logWindow) {
    logWindow.close()
  }
})

process.on('uncaughtException', err => sendLog(`[警告] 未捕获异常: ${err.message}`))
