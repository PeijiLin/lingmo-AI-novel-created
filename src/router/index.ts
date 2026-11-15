import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '@/utils/token'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'welcome',
      component: () => import('@/views/WelcomeView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
    },
    {
      path: '/reader/:id',
      name: 'reader',
      component: () => import('@/views/ReaderView.vue'),
      props: true,
    },
    {
      path: '/discover',
      name: 'discover',
      component: () => import('@/views/Discover.vue'),
    },
    {
      path: '/bookshelf',
      name: 'bookshelf',
      component: () => import('@/views/Bookshelf.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/creator/:id',
      name: 'creator',
      component: () => import('@/views/CreatorView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/knowledgeBase/:id',
      name: 'knowledgeBase',
      component: () => import('@/views/KnowledgeBase.vue'),
    },
    {
      path: '/knowledgeBaseList',
      name: 'knowledgeBaseList',
      component: () => import('@/views/KnowledgeBaseList.vue'),
    },
  ],
})

// 全局前置守卫：requiresAuth 路由未登录时跳登录页
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !isAuthenticated()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
