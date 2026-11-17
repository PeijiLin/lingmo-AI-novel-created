import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '@/utils/token'
import { AUTH_EXPIRED_EVENT } from '@/utils/http'

/**
 * 信息架构（规范 4.1 创作第一）：
 * - 登录后默认进入创作工作台；/ 为未登录落地页
 * - 除 login / register / welcome 外全部需登录
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'welcome',
      component: () => import('@/views/WelcomeView.vue'),
      meta: { public: true },
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
    },
    {
      path: '/book/:id',
      name: 'bookDetail',
      component: () => import('@/views/BookDetailView.vue'),
      props: true,
    },
    {
      path: '/discover',
      name: 'discover',
      component: () => import('@/views/Discover.vue'),
    },
    {
      path: '/ranking',
      name: 'ranking',
      component: () => import('@/views/RankingView.vue'),
    },
    {
      path: '/bookshelf',
      name: 'bookshelf',
      component: () => import('@/views/Bookshelf.vue'),
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
    },
    {
      path: '/knowledgeBase/:id',
      name: 'knowledgeBase',
      component: () => import('@/views/KnowledgeBaseView.vue'),
      props: true,
    },
    {
      path: '/knowledgeBaseList',
      name: 'knowledgeBaseList',
      component: () => import('@/views/KnowledgeBaseListView.vue'),
    },
  ],
})

// 全局前置守卫：除公开页外全部需登录；已登录访问 / 直进创作台
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