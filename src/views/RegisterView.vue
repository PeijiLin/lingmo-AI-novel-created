<!-- src/views/Register.vue -->
<template>
  <div class="register-container relative h-screen w-full overflow-hidden bg-gray-50">
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
        <form class="space-y-6" @submit.prevent="handleRegister">
          <!-- 用户名 -->
          <div>
            <label for="username" class="block text-sm font-medium text-gray-700 mb-1">用户名</label>
            <input
              id="username"
              v-model="form.username"
              type="text"
              autocomplete="username"
              class="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-colors text-gray-900 placeholder-gray-400"
              :class="{ 'border-red-500': errors.username }"
              placeholder="请输入您的用户名（2-16位字母、数字、下划线）"
            />
            <p v-if="errors.username" class="mt-1 text-sm text-red-600">{{ errors.username }}</p>
          </div>

          <!-- 邮箱 -->
          <div>
            <label for="email" class="block text-sm font-medium text-gray-700 mb-1">邮箱</label>
            <input
              id="email"
              v-model="form.email"
              type="email"
              autocomplete="email"
              class="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-colors text-gray-900 placeholder-gray-400"
              :class="{ 'border-red-500': errors.email }"
              placeholder="请输入您的常用邮箱"
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
                autocomplete="new-password"
                class="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-colors text-gray-900 placeholder-gray-400"
                :class="{ 'border-red-500': errors.password }"
                placeholder="请输入密码（至少8位，含大小写字母和数字）"
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
            <div class="mt-1">
              <div class="flex items-center justify-between text-xs">
                <span :class="passwordStrengthClass">{{ passwordStrengthText }}</span>
                <span class="text-gray-500">8位以上</span>
              </div>
              <div class="mt-1 h-1 bg-gray-200 rounded-full overflow-hidden">
                <div
                  class="h-full transition-all duration-300"
                  :style="{ width: `${passwordStrength * 100}%`, background: passwordStrengthColor }"
                ></div>
              </div>
            </div>
            <p v-if="errors.password" class="mt-1 text-sm text-red-600">{{ errors.password }}</p>
          </div>

          <!-- 确认密码 -->
          <div>
            <label for="confirmPassword" class="block text-sm font-medium text-gray-700 mb-1">确认密码</label>
            <div class="relative">
              <input
                id="confirmPassword"
                v-model="form.confirmPassword"
                :type="showConfirmPassword ? 'text' : 'password'"
                class="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-colors text-gray-900 placeholder-gray-400"
                :class="{ 'border-red-500': errors.confirmPassword }"
                placeholder="请再次输入密码"
              />
              <button
                type="button"
                @click="showConfirmPassword = !showConfirmPassword"
                class="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-500 hover:text-gray-700"
              >
                <svg v-if="!showConfirmPassword" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path>
                </svg>
                <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a3.04 3.04 0 01.252-1.145A10.05 10.05 0 0112 5c4.478 0 8.268 2.943 9.543 7a3.04 3.04 0 01-.252 1.145zM15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
                </svg>
              </button>
            </div>
            <p v-if="errors.confirmPassword" class="mt-1 text-sm text-red-600">{{ errors.confirmPassword }}</p>
          </div>

          <!-- 协议勾选 -->
          <div class="flex items-start">
            <div class="flex items-center h-5">
              <input
                id="terms"
                v-model="acceptedTerms"
                type="checkbox"
                class="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
              />
            </div>
            <div class="ml-3 text-sm">
              <label for="terms" class="text-gray-700">
                我同意
                <router-link to="/terms" class="font-medium text-blue-600 hover:text-blue-500">《用户协议》</router-link>
                和
                <router-link to="/privacy" class="font-medium text-blue-600 hover:text-blue-500">《隐私政策》</router-link>
              </label>
            </div>
          </div>
          <p v-if="errors.terms" class="mt-1 text-sm text-red-600">{{ errors.terms }}</p>

          <!-- 注册按钮 -->
          <div>
            <button
              type="submit"
              :disabled="isSubmitting || !acceptedTerms || !isPasswordStrong"
              class="w-full flex justify-center items-center py-3 px-4 border border-transparent rounded-lg shadow-sm text-base font-medium text-white bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <svg v-if="isSubmitting" class="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              {{ isSubmitting ? '注册中...' : '立 即 注 册' }}
            </button>
          </div>

          <!-- 全局错误提示 -->
          <div v-if="registerError" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
            {{ registerError }}
          </div>
        </form>

        <!-- 登录链接 -->
        <div class="text-center">
          <p class="text-sm text-gray-600">
            已有账号？
            <router-link
              to="/login"
              class="font-medium text-blue-600 hover:text-blue-500 transition-colors"
            >
              立即登录
            </router-link>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { UserControllerService } from '@/api' // 根据你的 API 模块调整

const router = useRouter()

// 表单数据
const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

// 表单验证错误
const errors = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  terms: ''
})

// 加载状态
const isSubmitting = ref(false)

// 是否显示密码
const showPassword = ref(false)
const showConfirmPassword = ref(false)

// 同意协议
const acceptedTerms = ref(false)

// 全局错误提示
const registerError = ref('')

// 密码强度检测
const passwordStrength = computed(() => {
  const pwd = form.password
  if (!pwd) return 0

  let score = 0
  if (pwd.length >= 8) score++
  if (/[a-z]/.test(pwd)) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^A-Za-z0-9]/.test(pwd)) score++

  return Math.min(score / 5, 1) // 最高5分，归一化到0-1
})

const passwordStrengthText = computed(() => {
  if (passwordStrength.value === 0) return '请输入密码'
  if (passwordStrength.value < 0.4) return '弱'
  if (passwordStrength.value < 0.8) return '中'
  return '强'
})

const passwordStrengthClass = computed(() => {
  if (passwordStrength.value === 0) return 'text-gray-400'
  if (passwordStrength.value < 0.4) return 'text-red-500'
  if (passwordStrength.value < 0.8) return 'text-yellow-600'
  return 'text-green-600'
})

const passwordStrengthColor = computed(() => {
  if (passwordStrength.value === 0) return '#e5e7eb'
  if (passwordStrength.value < 0.4) return '#ef4444'
  if (passwordStrength.value < 0.8) return '#f59e0b'
  return '#10b981'
})

const isPasswordStrong = computed(() => passwordStrength.value >= 0.8)

// 处理注册
const handleRegister = async () => {
  // 清空之前错误
  registerError.value = ''
  Object.keys(errors).forEach(key => {
    errors[key as keyof typeof errors] = ''
  })

  // 验证用户名
  if (!form.username.trim()) {
    errors.username = '用户名不能为空'
    return
  }
  if (form.username.length < 2 || form.username.length > 16) {
    errors.username = '用户名长度需在2-16个字符之间'
    return
  }
  if (!/^[a-zA-Z0-9_]+$/.test(form.username)) {
    errors.username = '用户名只能包含字母、数字和下划线'
    return
  }

  // 验证邮箱
  if (!form.email.trim()) {
    errors.email = '邮箱不能为空'
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    errors.email = '请输入有效的邮箱地址'
    return
  }

  // 验证密码
  if (!form.password) {
    errors.password = '密码不能为空'
    return
  }
  if (form.password.length < 8) {
    errors.password = '密码至少需要8位'
    return
  }
  if (!/[a-z]/.test(form.password) || !/[A-Z]/.test(form.password) || !/\d/.test(form.password)) {
    errors.password = '密码必须包含大小写字母和数字'
    return
  }

  // 验证确认密码
  if (form.password !== form.confirmPassword) {
    errors.confirmPassword = '两次输入的密码不一致'
    return
  }

  // 验证协议
  if (!acceptedTerms.value) {
    errors.terms = '请同意用户协议和隐私政策'
    return
  }

  isSubmitting.value = true

  try {
    const res = await UserControllerService.userRegister({
      account: form.username,
      checkPassword: form.confirmPassword,
      password: form.password
    })

    if (res.code === 200) {
      // 注册成功，跳转到登录页
      router.push('/login')
    } else {
      registerError.value = res.msg || '注册失败，请稍后再试'
    }
  } catch (error: any) {
    console.error('注册请求失败:', error)
    registerError.value = error.response?.data?.msg || '网络错误，请检查网络连接'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<style scoped>
/* 引入 Font Awesome 图标库 */
@import url('https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css');
</style>
