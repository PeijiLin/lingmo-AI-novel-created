/**
 * 认证令牌存取（localStorage 持久化，key 与旧版一致保证已登录用户无缝迁移）
 */

const ACCESS_TOKEN_KEY = 'novel_immersion_access_token'
const REFRESH_TOKEN_KEY = 'novel_immersion_refresh_token'
const USER_ID_KEY = 'novel_immersion_user_id'
const AUTHOR_ID_KEY = 'novel_immersion_author_id'

export interface TokenPair {
  access_token: string
  refresh_token: string
  userId: number
}

/** 后端 refresh 接口缺陷防御：refresh_token 字段可能误带 "refresh_token:" 前缀（存的是 Redis key），剥掉再存 */
export function normalizeRefreshToken(raw: string | undefined | null): string {
  if (!raw) return ''
  const PREFIX = 'refresh_token:'
  return raw.startsWith(PREFIX) ? raw.slice(PREFIX.length) : raw
}

export function setTokens(data: TokenPair) {
  localStorage.setItem(ACCESS_TOKEN_KEY, data.access_token)
  localStorage.setItem(REFRESH_TOKEN_KEY, normalizeRefreshToken(data.refresh_token))
  localStorage.setItem(USER_ID_KEY, String(data.userId))
}

export function setAuthorId(authorId: number | null | undefined) {
  if (authorId == null) localStorage.removeItem(AUTHOR_ID_KEY)
  else localStorage.setItem(AUTHOR_ID_KEY, String(authorId))
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function getUserId(): number | null {
  const id = localStorage.getItem(USER_ID_KEY)
  if (!id) return null
  const parsed = Number.parseInt(id, 10)
  return Number.isFinite(parsed) ? parsed : null
}

export function getAuthorId(): number | null {
  const id = localStorage.getItem(AUTHOR_ID_KEY)
  if (!id) return null
  const parsed = Number.parseInt(id, 10)
  return Number.isFinite(parsed) ? parsed : null
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_ID_KEY)
  localStorage.removeItem(AUTHOR_ID_KEY)
}

export function isAuthenticated(): boolean {
  return !!getAccessToken()
}

/** @deprecated 旧命名兼容（stores/user.ts 过渡期引用，阶段 3 移除） */
export const getAuthor = getAuthorId
