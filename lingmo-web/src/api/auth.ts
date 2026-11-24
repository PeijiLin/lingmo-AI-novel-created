/**
 * 认证（/user/*）
 * 刷新逻辑在 utils/http.ts（单飞队列），此处仅登录/注册/登出。
 */
import { http } from '@/utils/http'
import { setTokens, setAuthorId, clearTokens, getAccessToken } from '@/utils/token'
import type { LoginResult } from './types'

export interface LoginPayload {
  account: string
  password: string
}

export async function login(payload: LoginPayload): Promise<LoginResult> {
  const data = await http.post<LoginResult & Record<string, unknown>>('/user/login', payload)
  const result: LoginResult = {
    access_token: String(data.access_token ?? ''),
    refresh_token: String(data.refresh_token ?? ''),
    userId: Number(data.userId),
  }
  setTokens(result)
  return result
}

export async function register(payload: LoginPayload & { checkPassword: string }): Promise<number> {
  return http.post<number>('/user/register', payload)
}

export async function logout(): Promise<void> {
  const token = getAccessToken()
  if (!token) return
  try {
    // 后端 @RequestHeader("Authorization")：http 层已自动注入 Bearer 头
    await http.post<unknown>('/user/logout')
  } finally {
    clearTokens()
    setAuthorId(null)
  }
}
