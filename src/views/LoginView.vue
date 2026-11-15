<!-- src/views/Login.vue -->
<template>
  <div class="login-container relative h-screen w-full overflow-hidden bg-gray-50">
    <!-- 背景图 -->
    <div class="absolute inset-0 z-0">
      <img
        src="https://picsum.photos/id/14/1920/1080"
        alt="Cozy Reading Room"
        class="h-full w-full object-cover opacity-20"
      />
      <div class="absolute inset-0 bg-gradient-to-br from-white via-transparent to-gray-100 opacity-60"></div>
    </div>

    <!-- 内容区 -->
    <div class="relative z-10 flex items-center justify-center min-h-screen p-6">
      <div class="w-full max-w-md space-y-8 bg-white/80 backdrop-blur-sm rounded-2xl shadow-xl p-10 border border-gray-100">
        <!-- Logo -->
        <div class="text-center">
          <h1 class="text-3xl font-bold text-gray-900">NovelImmersion</h1>
          <p class="mt-2 text-gray-600">沉浸在文字的世界</p>
        </div>

        <!-- 表单 -->
        <form class="space-y-6" @submit.prevent="handleLogin">
          <!-- 用户名 -->
          <div>
            <label for="email" class="block text-sm font-medium text-gray-700 mb-1">用户名 / 邮箱</label>
            <input
              id="email"
              v-model="form.email"
              type="text"
              autocomplete="username"
              class="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-colors text-gray-900 placeholder-gray-400"
              placeholder="请输入您的用户名或邮箱"
              :class="{ 'border-red-500': errors.email }"
            />
            <p v-if="errors.email" class="mt-1 text-sm text-red-600">{{ errors.email }}</p>
          </div>

          <!-- 密码 -->
          <div>
            <label for="password" class="block text-sm font-medium text-gray-700 mb-1">密码</label>
            <div class="relative">
              <input
                id="password"
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                class="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-colors text-gray-900 placeholder-gray-400"
                placeholder="请输入您的密码"
                :class="{ 'border-red-500': errors.password }"
              />
              <button
                type="button"
                @click="showPassword = !showPassword"
                class="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-500 hover:text-gray-700"
              >
                <svg v-if="!showPassword" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path>
                </svg>
                <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a3.04 3.04 0 01.252-1.145A10.05 10.05 0 0112 5c4.478 0 8.268 2.943 9.543 7a3.04 3.04 0 01-.252 1.145zM15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
                </svg>
              </button>
            </div>
            <p v-if="errors.password" class="mt-1 text-sm text-red-600">{{ errors.password }}</p>
          </div>

          <!-- 忘记密码 -->
          <div class="flex items-center justify-between">
            <router-link
              to="/forgot-password"
              class="text-sm text-blue-600 hover:text-blue-500 transition-colors"
            >
              忘记密码？
            </router-link>
          </div>

          <!-- 登录按钮 -->
          <div>
            <button
              type="submit"
              :disabled="isSubmitting"
              class="w-full flex justify-center items-center py-3 px-4 border border-transparent rounded-lg shadow-sm text-base font-medium text-white bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <svg v-if="isSubmitting" class="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              {{ isSubmitting ? '登录中...' : '登 录' }}
            </button>
          </div>

          <!-- 错误提示（全局） -->
          <div v-if="loginError" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
            {{ loginError }}
          </div>
        </form>

        <!-- 第三方登录 -->
        <div class="mt-6">
          <div class="relative">
            <div class="absolute inset-0 flex items-center">
              <div class="w-full border-t border-gray-300"></div>
            </div>
            <div class="relative flex justify-center text-sm">
              <span class="px-2 bg-white text-gray-500">或使用第三方账号登录</span>
            </div>
          </div>

          <div class="mt-6 grid grid-cols-3 gap-3">
            <button
              type="button"
              class="inline-flex justify-center items-center px-4 py-2 border border-gray-300 rounded-lg shadow-sm bg-white text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
            >
              <i class="fab fa-weixin text-green-500 text-lg"></i>
            </button>
            <button
              type="button"
              class="inline-flex justify-center items-center px-4 py-2 border border-gray-300 rounded-lg shadow-sm bg-white text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
            >
              <i class="fab fa-qq text-blue-500 text-lg"></i>
            </button>
            <button
              type="button"
              class="inline-flex justify-center items-center px-4 py-2 border border-gray-300 rounded-lg shadow-sm bg-white text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors"
            >
              <i class="fab fa-weibo text-red-500 text-lg"></i>
            </button>
          </div>
        </div>

        <!-- 注册链接 -->
        <div class="text-center">
          <p class="text-sm text-gray-600">
            还没有账号？
            <router-link
              to="/register"
              class="font-medium text-blue-600 hover:text-blue-500 transition-colors"
            >
              立即注册
            </router-link>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
// 或者直接用 API 调用
import { UserControllerService } from '@/api'
import {useUserStore} from "@/stores/user.ts";
import {setTokens} from "@/utils/token.ts"; // 根据你的 API 模块调整

const router = useRouter()

// 表单数据
const form = reactive({
  email: '',
  password: ''
})

// 表单验证错误
const errors = reactive({
  email: '',
  password: ''
})

// 加载状态
const isSubmitting = ref(false)

// 全局错误提示
const loginError = ref('')

// 是否显示密码
const showPassword = ref(false)

// 处理登录
const handleLogin = async () => {
  // 清空之前错误
  loginError.value = ''
  errors.email = ''
  errors.password = ''

  // 验证
  if (!form.email) {
    errors.email = '请输入用户名或邮箱'
    return
  }
  if (!form.password) {
    errors.password = '请输入密码'
    return
  }

  isSubmitting.value = true

  try {
    // 调用登录接口（示例）
    const res = await UserControllerService.userLogin({
      account: form.email,
      password: form.password
    })

    if (res.code === 200) {
      const { access_token, refresh_token, userId } = res.data;

      // ✅ 设置到 localStorage，供 OpenAPI.TOKEN 动态读取
      setTokens({ access_token, refresh_token, userId });
      // ✅ 同步到 Pinia（可选）
      useUserStore().login({ access_token, refresh_token, userId });
      await router.push('/') // 替换为你的首页路由
    } else {
      loginError.value = res.msg || '登录失败，请稍后再试'
    }
  } catch (error: any) {
    console.error('登录请求失败:', error)
    loginError.value = error.response?.data?.msg || '网络错误，请检查网络连接'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<style scoped>
/* 引入 Font Awesome 图标库 */
@import url('https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css');
</style>
