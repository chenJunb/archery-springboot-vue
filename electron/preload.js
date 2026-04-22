const { contextBridge, ipcRenderer } = require('electron');

/**
 * 暴露给渲染进程的安全API
 */
contextBridge.exposeInMainWorld('electronAPI', {
  /**
   * 获取应用信息
   */
  getAppInfo: () => ipcRenderer.invoke('get-app-info'),

  /**
   * 监听日志消息
   */
  onLogMessage: (callback) => {
    ipcRenderer.on('log-message', (event, message) => {
      callback(message);
    });

    // 返回取消监听函数
    return () => {
      ipcRenderer.removeListener('log-message', callback);
    };
  },

  /**
   * 最小化窗口
   */
  minimizeWindow: () => ipcRenderer.send('minimize-window'),

  /**
   * 最大化窗口
   */
  maximizeWindow: () => ipcRenderer.send('maximize-window'),

  /**
   * 关闭窗口
   */
  closeWindow: () => ipcRenderer.send('close-window'),

  /**
   * 打开开发工具
   */
  openDevTools: () => ipcRenderer.send('open-devtools'),

  /**
   * 获取系统信息
   */
  getSystemInfo: () => {
    return {
      platform: process.platform,
      arch: process.arch,
      cpus: require('os').cpus().length,
      totalMemory: require('os').totalmem(),
      freeMemory: require('os').freemem()
    };
  }
});
