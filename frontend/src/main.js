import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'

// Polyfill for global in browser environment
if (typeof window.global === 'undefined') {
  window.global = window
}

// Polyfill for process if needed by dependencies
if (typeof window.process === 'undefined') {
  window.process = { env: {} }
}

const app = createApp(App)

app.use(router)
app.use(ElementPlus)

app.mount('#app')