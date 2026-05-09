import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      'element-plus/es': 'element-plus/lib',
      'element-plus/es/components': 'element-plus/lib/components'
    }
  },
  server: {
    port: 3000, // 使用3000端口
    host: 'localhost',
    strictPort: true, // 强制使用3000端口，如果被占用则报错
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
  build: {
    base: './',
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      plugins: [{
        name: 'patch-element-plus',
        resolveId(source, importer) {
          // Patch all missing element-plus ES module imports
          if (importer && importer.includes('node_modules/element-plus/')) {
            // Fix missing window-node.mjs or global-node.mjs
            if (source.includes('window-node.mjs') || source.includes('global-node.mjs')) {
              return { id: '\0element-plus-global-node', external: false }
            }
            // Fix missing hooks
            if (source.includes('use-window-config') || source.includes('use-prevent-window')) {
              return { id: '\0element-plus-missing-hook', external: false }
            }
          }
        },
        load(id) {
          if (id === '\0element-plus-global-node') {
            return 'export const createGlobalNode = () => ({ remove: () => {} }); export const removeGlobalNode = () => {}'
          }
          if (id === '\0element-plus-missing-hook') {
            return 'export const useWindowConfig = () => ({}); export const usePreventWindow = () => ({})'
          }
        }
      }]
    }
  },
  // 定义全局变量以支持Node.js模块
  define: {
    global: 'window'
  }
})