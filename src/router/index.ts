import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '@/utils/token'
import { AUTH_EXPIRED_EVENT } from '@/utils/http'

/**
 * 信息架构（规范 4.1 创作第一）：
 * - 登录后默认进入创作工作台；/ 仅为未登录落地页
 * - 除 login / register / welcome 外全部需登录（不做未登录公开浏览）
 * - 旧门户视图暂以 shell: 'portal' 挂旧壳，随阶段 5 逐一迁移至 AppShell
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'welcome',
      component: () => import('@/views/WelcomeView.vue'),
      meta: { shell: 'portal', public: true },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/workbench',
      name: 'workbench',
      component: () => import('@/views/WorkbenchView.vue'),
    },
    {
      path: '/creator/:id',
      name: 'creator',
      component: () => import('@/views/CreatorView.vue'),
      props: true,
    },
    {
      path: '/reader/:id',
      name: 'reader',
      component: () => import('@/views/ReaderView.vue'),
      props: true,
      meta: { shell: 'portal' },
    },
    {
      path: '/discover',
      name: 'discover',
      component: () => import('@/views/Discover.vue'),
      meta: { shell: 'portal' },
    },
    {
      path: '/bookshelf',
      name: 'bookshelf',
      component: () => import('@/views/Bookshelf.vue'),
      meta: { shell: 'portal' },
    },
    {
      path: '/knowledgeBase/:id',
      name: 'knowledgeBase',
      component: () => import('@/views/KnowledgeBase.vue'),
      meta: { shell: 'portal' },
    },
    {
      path: '/knowledgeBaseList',
      name: 'knowledgeBaseList',
      component: () => import('@/views/KnowledgeBaseList.vue'),
      meta: { shell: 'portal' },
    },
  ],
})

// 全局前置守卫：除公开页外全部需登录
router.beforeEach((to) => {
  if (to.path === '/' && isAuthenticated()) {
    return { path: '/workbench' }
  }
  if (!to.meta.public && !isAuthenticated()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

// 登录态失效（http 单飞刷新失败）→ 回登录页，保留来源
if (typeof window !== 'undefined') {
  window.addEventListener(AUTH_EXPIRED_EVENT, () => {
    const current = router.currentRoute.value
    if (current.name !== 'login') {
      router.push({ path: '/login', query: { redirect: current.fullPath } })
    }
  })
}

export default router
