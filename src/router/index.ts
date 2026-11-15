import HomeView from '@/views/HomeView.vue'
import LibraryView from '@/views/LibraryView.vue'
import CreatorView from '@/views/CreatorView.vue'
import ReaderView from '@/views/ReaderView.vue'
import {createWebHistory, createRouter} from "vue-router";
import WelcomeView from "@/views/WelcomeView.vue";
import Bookshelf from '@/views/Bookshelf.vue';
import LoginView from '@/views/LoginView.vue';
import {isAuthenticated} from "@/utils/token.ts";
import knowledgeBase from "@/views/KnowledgeBase.vue";
import knowledgeBaseList from "@/views/KnowledgeBaseList.vue";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'welcome',
      component: WelcomeView
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue')
    },
    {
      path: '/read/:id',
      name: 'Reader',
      component: () => import('../views/ReaderView.vue'),
      props: true
    },
    {
      path: '/library',
      name: 'library',
      component: LibraryView,
      meta: { requiresAuth: true }, // ← 加上这行
    },
    {
      path: '/discover',
      name: 'Discover',
      component: () => import('../views/Discover.vue')
    },
    {
      path: '/bookshelf',
      name: 'bookshelf',
      component: Bookshelf,
      meta: { requiresAuth: true }, // ← 加上这行
    },
    {
      path: '/creator/:id',
      name: 'creator',
      component: CreatorView,
      meta: { requiresAuth: true }, // ← 加上这行
    },
    {
      path: '/reader/:id',
      name: 'reader',
      component: ReaderView,
      props: true
    },
    {
      path: '/ranking',
      name: 'ranking',
      component: () => import('@/views/HomeView.vue') // 临时使用HomeView，后续可替换
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/UserProfile.vue') // 临时使用HomeView，后续可替换
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/HomeView.vue') // 临时使用HomeView，后续可替换
    },
    {
      path: '/home',
      name: 'home',
      component: HomeView
    },
    {
      path: '/knowledgeBase/:id',
      name: 'knowledgeBase',
      component: knowledgeBase
    },
    {
      path: '/knowledgeBaseList',
      name: 'knowledgeBaseList',
      component: knowledgeBaseList
    },
  ]
})

// 全局前置守卫
router.beforeEach((to, from, next) => {
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)

  if (requiresAuth && !isAuthenticated()) {
    next('/login')
  } else {
    next()
  }
})
export default router
