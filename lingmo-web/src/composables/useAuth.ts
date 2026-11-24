/**
 * 认证动作（登录 / 注册 / 登出），桥接 api 层与 user store
 */
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import * as authApi from '@/api/auth'

export function useAuth() {
  const store = useUserStore()
  const router = useRouter()

  async function login(account: string, password: string) {
    const result = await authApi.login({ account, password })
    store.login(result)
    return result
  }

  async function register(account: string, password: string, checkPassword: string) {
    return authApi.register({ account, password, checkPassword })
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      store.logout()
      router.push('/login')
    }
  }

  return { store, login, register, logout }
}
