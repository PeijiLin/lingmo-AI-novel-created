<!-- src/components/UserProfile.vue -->
<template>
  <div class="user-profile flex items-center gap-3 p-4 border-t border-gray-200">
    <!-- 头像占位（根据 userId 生成唯一字母头像） -->
    <div
      class="flex-shrink-0 w-10 h-10 rounded-full bg-blue-500 flex items-center justify-center text-white font-bold text-sm"
      :style="{ backgroundColor: userAvatarColor }"
    >
      {{ userInitials }}
    </div>

    <!-- 用户信息 -->
    <div class="flex-1 min-w-0">
      <p class="text-sm font-medium text-gray-900 truncate">
        {{ userName || '未登录' }}
      </p>
      <p class="text-xs text-gray-500 truncate">
        {{ isAuthed ? '已登录' : '请登录' }}
      </p>
    </div>

    <!-- 操作按钮 -->
    <div class="flex-shrink-0">
      <button
        v-if="isAuthed"
        @click="handleLogout"
        class="text-red-500 hover:text-red-700 text-xs font-medium transition-colors"
      >
        退出
      </button>
      <router-link
        v-else
        to="/login"
        class="text-blue-600 hover:text-blue-800 text-xs font-medium transition-colors"
      >
        登录
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getUserId, isAuthenticated } from '@/utils/token'
import {useUserStore} from "@/stores/user.ts";

// 引入 Pinia 用户状态
const userStore = useUserStore()

// 获取用户名（这里用 userId 代替，实际项目可从 API 获取）
const userName = computed(() => {
  const id = getUserId()
  return id ? `用户${id}` : ''
})

// 获取用户头像首字母（基于 userId）
const userInitials = computed(() => {
  const id = getUserId()
  if (!id) return ''
  return id.toString().charAt(0).toUpperCase()
})

// 根据 userId 生成背景色（避免所有用户都是蓝色）
const userAvatarColor = computed(() => {
  const id = getUserId()
  if (!id) return '#e5e7eb' // 灰色

  const colors = [
    '#3b82f6', // 蓝
    '#10b981', // 绿
    '#f59e0b', // 黄
    '#ef4444', // 红
    '#8b5cf6', // 紫
    '#ec4899', // 粉
    '#06b6d4', // 青
    '#f97316', // 橙
  ]
  return colors[id % colors.length]
})

// 是否已登录
const isAuthed = computed(() => isAuthenticated())

// 退出登录
const handleLogout = () => {
  userStore.logout()
  // 可选：跳转到首页或登录页
  // router.push('/login')
}
</script>
