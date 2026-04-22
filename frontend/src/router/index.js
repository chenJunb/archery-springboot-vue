import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'control',
    component: () => import('../views/EnhancedControlView.vue')
  },
  {
    path: '/display-a',
    name: 'display-a',
    component: () => import('../views/EnhancedDisplayViewA.vue')
  },
  {
    path: '/display-b',
    name: 'display-b',
    component: () => import('../views/EnhancedDisplayViewB.vue')
  },
  {
    path: '/display-a-full',
    name: 'display-a-full',
    component: () => import('../views/EnhancedDisplayViewA.vue')
  },
  {
    path: '/display-b-full',
    name: 'display-b-full',
    component: () => import('../views/EnhancedDisplayViewB.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：根据路由设置页面标题
router.beforeEach((to, from, next) => {
  if (to.name === 'control') {
    document.title = '射箭比赛计时控制系统 - 控制端'
  } else if (to.name === 'display-a' || to.name === 'display-a-full') {
    document.title = '射箭比赛计时系统 - A屏显示'
  } else if (to.name === 'display-b' || to.name === 'display-b-full') {
    document.title = '射箭比赛计时系统 - B屏显示'
  } else {
    document.title = '射箭比赛计时系统'
  }
  next()
})

export default router