// src/utils/token.ts

const ACCESS_TOKEN_KEY = 'novel_immersion_access_token'
const REFRESH_TOKEN_KEY = 'novel_immersion_refresh_token'
const USER_ID_KEY = 'novel_immersion_user_id'
const AUTHOR_ID_KEY = 'novel_immersion_author_id'

/**
 * 存储 Token 和用户ID
 */
export const setTokens = (data: {
  access_token: string
  refresh_token: string
  userId: number
}) => {
  localStorage.setItem(ACCESS_TOKEN_KEY, data.access_token)
  localStorage.setItem(REFRESH_TOKEN_KEY, data.refresh_token)
  localStorage.setItem(USER_ID_KEY, String(data.userId))
}
export const setAuthorId = (authorId: number | undefined) => {
  localStorage.setItem(AUTHOR_ID_KEY, String(authorId))
}
/**
 * 获取 Token
 */
export const getAccessToken = (): string | null => {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export const getRefreshToken = (): string | null => {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export const getUserId = (): number | null => {
  const id = localStorage.getItem(USER_ID_KEY)
  return id ? parseInt(id, 10) : null
}

export const getAuthor = (): number | null => {
  const id = localStorage.getItem(AUTHOR_ID_KEY)
  return id ?  parseInt(id, 10) : null
}

/**
 * 清除所有 Token 和用户信息
 */
export const clearTokens = () => {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_ID_KEY)
}

/**
 * 是否已登录
 */
export const isAuthenticated = (): boolean => {
  return !!getAccessToken()
}
