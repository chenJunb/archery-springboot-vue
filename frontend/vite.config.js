import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 3000, // 使用3000端口
    host: 'localhost',
    strictPort: false, // 如果端口被占用，自动使用其他端口
    open: false, // 不自动打开浏览器
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      },
      // WebSocket代理 - 匹配所有以 /ws-archery-timer 开头的路径
      '^/ws-archery-timer(/.*)?$': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        ws: true
      }
    },
    cors: true // 允许CORS
  },
  // 定义全局变量以支持Node.js模块
  define: {
    global: 'window'
  }
})