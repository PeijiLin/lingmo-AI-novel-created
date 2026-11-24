<script setup lang="ts">
/**
 * 注册（文房表单 · 纸墨 · 读者转作者）
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Feather } from 'lucide-vue-next'
import { Button, Input } from '@/components/ui'
import { useAuth } from '@/composables/useAuth'
import { useToast } from '@/composables/useToast'

const router = useRouter()
const toast = useToast()
const { register } = useAuth()

const account = ref('')
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const showPassword = ref(false)

const strength = computed(() => {
  const p = password.value
  if (!p) return { score: 0, label: '', color: '' }
  let s = 0
  if (p.length >= 8) s += 1
  if (/[a-z]/.test(p) && /[A-Z]/.test(p)) s += 1
  if (/\d/.test(p)) s += 1
  if (/[^a-zA-Z0-9]/.test(p)) s += 1
  s = Math.min(s, 4)
  if (s <= 1) return { score: s, label: '较弱', color: 'var(--cinnabar)' }
  if (s === 2) return { score: s, label: '尚可', color: 'var(--mist)' }
  if (s === 3) return { score: s, label: '良好', color: 'var(--ink-indigo)' }
  return { score: s, label: '强', color: 'var(--ink-indigo)' }
})

async function handleRegister() {
  const name = account.value.trim()
  if (!name) {
    toast.warning('请输入用户名')
    return
  }
  if (password.value.length < 6) {
    toast.warning('密码至少 6 位')
    return
  }
  if (password.value !== confirmPassword.value) {
    toast.warning('两次输入的密码不一致')
    return
  }
  submitting.value = true
  try {
    await register(name, password.value, confirmPassword.value)
    toast.success('注册成功，请登录')
    router.replace('/login')
  } catch (err) {
    toast.error('注册失败', err instanceof Error ? err.message : undefined)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen flex-col items-center justify-center bg-paper px-4">
    <div class="mb-8 flex flex-col items-center gap-3">
      <span class="flex size-14 items-center justify-center rounded-md bg-ink-indigo font-book text-2xl font-bold text-paper-surface shadow-card">墨</span>
      <div class="text-center">
        <h1 class="font-book text-2xl font-semibold text-ink">成为作者</h1>
        <p class="mt-1 text-xs text-ink-secondary">从读者到作者，一支笔的距离。</p>
      </div>
    </div>

    <div class="w-full max-w-sm rounded-lg border border-mist-line bg-paper-surface px-6 py-6 shadow-card">
      <form class="space-y-4" @submit.prevent="handleRegister">
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">用户名</span>
          <Input v-model="account" placeholder="2-16 位字母、数字、下划线" autocomplete="username" maxlength="16" />
        </label>
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">密码</span>
          <Input v-model="password" :type="showPassword ? 'text' : 'password'" placeholder="至少 6 位" autocomplete="new-password" />
          <span v-if="password" class="mt-1 flex items-center gap-2 text-[11px]" :style="{ color: strength.color }">
            <span class="h-1 w-12 overflow-hidden rounded-full bg-mist-line">
              <span class="block h-full transition-all duration-300" :style="{ width: `${strength.score * 25}%`, background: strength.color }" />
            </span>
            {{ strength.label }}
          </span>
        </label>
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">确认密码</span>
          <div class="relative">
            <Input v-model="confirmPassword" :type="showPassword ? 'text' : 'password'" placeholder="再次输入密码" autocomplete="new-password" />
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
          <template v-if="!submitting"><Feather class="size-4" />注册并开笔</template>
        </Button>
      </form>

      <div class="mt-5 border-t border-mist-line pt-4 text-center text-xs text-ink-secondary">
        已有账号？
        <RouterLink to="/login" class="font-medium text-ink-indigo hover:text-cinnabar">返回登录</RouterLink>
      </div>
    </div>
  </div>
</template>