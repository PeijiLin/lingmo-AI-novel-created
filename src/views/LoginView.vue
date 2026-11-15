<script setup lang="ts">
/**
 * 登录（文房表单 · 纸墨）
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Feather } from 'lucide-vue-next'
import { Button, Input } from '@/components/ui'
import { useAuth } from '@/composables/useAuth'
import { useToast } from '@/composables/useToast'
import { isAuthenticated } from '@/utils/token'

const router = useRouter()
const route = useRoute()
const toast = useToast()
const { login } = useAuth()

const account = ref('')
const password = ref('')
const submitting = ref(false)
const showPassword = ref(false)

async function handleLogin() {
  if (!account.value.trim()) {
    toast.warning('请输入账号')
    return
  }
  if (!password.value) {
    toast.warning('请输入密码')
    return
  }
  submitting.value = true
  try {
    await login(account.value.trim(), password.value)
    toast.success('欢迎回来')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/workbench'
    router.replace(redirect.startsWith('/') ? redirect : '/workbench')
  } catch (err) {
    toast.error('登录失败', err instanceof Error ? err.message : '请检查账号与密码')
  } finally {
    submitting.value = false
  }
}

// 已登录用户不留在登录页
onMounted(() => {
  if (isAuthenticated() && !route.query.redirect) router.replace('/workbench')
})
</script>

<template>
  <div class="flex min-h-screen flex-col items-center justify-center bg-paper px-4">
    <!-- 品牌 -->
    <div class="mb-8 flex flex-col items-center gap-3">
      <span class="flex size-14 items-center justify-center rounded-md bg-ink-indigo font-book text-2xl font-bold text-paper-surface shadow-card">墨</span>
      <div class="text-center">
        <h1 class="font-book text-2xl font-semibold text-ink">纸墨书卷</h1>
        <p class="mt-1 text-xs text-ink-secondary">AI 小说创作平台 · 落笔成书</p>
      </div>
    </div>

    <!-- 文房卡片 -->
    <div class="w-full max-w-sm rounded-lg border border-mist-line bg-paper-surface px-6 py-6 shadow-card">
      <form class="space-y-4" @submit.prevent="handleLogin">
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">账号</span>
          <Input v-model="account" placeholder="用户名或邮箱" autocomplete="username" />
        </label>
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">密码</span>
          <div class="relative">
            <Input v-model="password" :type="showPassword ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" />
            <button
              type="button"
              class="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-mist transition-colors hover:text-ink"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? '隐' : '显' }}
            </button>
          </div>
        </label>
        <Button type="submit" variant="cinnabar" class="w-full" size="lg" :loading="submitting">
          <template v-if="!submitting"><Feather class="size-4" />登录</template>
        </Button>
      </form>

      <div class="mt-5 border-t border-mist-line pt-4 text-center text-xs text-ink-secondary">
        还没有账号？
        <RouterLink to="/register" class="font-medium text-ink-indigo hover:text-cinnabar">读者转作者，立即注册</RouterLink>
      </div>
    </div>

    <p class="mt-6 text-[11px] text-mist">创作 / 阅读 / 展示，同一支笔，同一页纸。</p>
  </div>
</template>