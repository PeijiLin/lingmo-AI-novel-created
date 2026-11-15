/**
 * HTTP 单实例（规范 5.2）
 *
 * - base：VITE_API_BASE_URL || '/api'（dev 走 Vite proxy，prod 同源/网关）
 * - fetch 实现，全仓库唯一 HTTP 通路；SSE 见 utils/sse.ts（共用 token 与刷新）
 * - {code,msg,data} 信封解包；40100/40101/HTTP 401 → 单飞刷新队列（并发复用同一 Promise）
 *   → 刷新成功后重放原请求（修复旧版刷新后丢请求缺陷）；失败则清令牌并广播登出
 * - createHttpClient 工厂注入 fetch/token 依赖，供单元测试
 */
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens,
  normalizeRefreshToken,
} from './token'

/** 业务错误：信封 code !== 200 */
export class ApiClientError extends Error {
  constructor(
    public code: number,
    message: string,
    public status?: number,
    public data?: unknown,
  ) {
    super(message)
    this.name = 'ApiClientError'
  }
}

interface Envelope<T> {
  code: number
  msg?: string
  data?: T
}

export interface TokenStore {
  getAccessToken(): string | null
  getRefreshToken(): string | null
  setTokens(pair: { access_token: string; refresh_token: string; userId: number }): void
  clearTokens(): void
}

export interface HttpClientDeps {
  baseUrl: string
  fetchImpl: typeof fetch
  tokenStore: TokenStore
  /** 登录态失效回调（默认由 runAuthExpiredHook 处理） */
  onAuthExpired?: () => void
}

export interface RequestOptions {
  body?: unknown
  signal?: AbortSignal
  /** 查询参数 */
  params?: Record<string, string | number | boolean | undefined | null>
  /** 内部：刷新后重放标记，防止无限循环 */
  retried?: boolean
}

export interface HttpClient {
  request<T>(method: string, path: string, options?: RequestOptions): Promise<T>
  get<T>(path: string, options?: RequestOptions): Promise<T>
  post<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T>
  put<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T>
  delete<T>(path: string, options?: RequestOptions): Promise<T>
  /** 单飞刷新（SSE 与外部复用） */
  refreshOnce(): Promise<boolean>
}

function buildUrl(baseUrl: string, path: string, params?: RequestOptions['params']) {
  let url = `${baseUrl}${path.startsWith('/') ? path : `/${path}`}`
  if (params) {
    const qs = new URLSearchParams()
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null) qs.append(key, String(value))
    }
    const query = qs.toString()
    if (query) url += `?${query}`
  }
  return url
}

async function parseEnvelope<T>(res: Response): Promise<Envelope<T> | null> {
  if (res.status === 204) return null
  const text = await res.text()
  if (!text) return null
  try {
    return JSON.parse(text) as Envelope<T>
  } catch {
    // 非 JSON 响应（网关错误页等）包装成系统错误
    return { code: 50000, msg: `响应解析失败（HTTP ${res.status}）` }
  }
}

export function createHttpClient(deps: HttpClientDeps): HttpClient {
  /** 单飞刷新队列：并发请求复用同一 Promise */
  let refreshing: Promise<boolean> | null = null

  async function doRefresh(): Promise<boolean> {
    const refreshToken = deps.tokenStore.getRefreshToken()
    if (!refreshToken) return false
    try {
      const res = await deps.fetchImpl(buildUrl(deps.baseUrl, '/user/refresh-token'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!res.ok) return false
      const body = await parseEnvelope<{ access_token: string; refresh_token: string; userId: string | number }>(res)
      if (!body || body.code !== 200 || !body.data?.access_token) return false
      deps.tokenStore.setTokens({
        access_token: body.data.access_token,
        refresh_token: normalizeRefreshToken(body.data.refresh_token),
        userId: Number(body.data.userId),
      })
      return true
    } catch {
      return false
    }
  }

  function refreshOnce(): Promise<boolean> {
    if (!refreshing) {
      refreshing = doRefresh().finally(() => {
        refreshing = null
      })
    }
    return refreshing
  }

  async function rawRequest(
    method: string,
    path: string,
    options: RequestOptions,
    token: string | null,
  ): Promise<Response> {
    const isForm = typeof FormData !== 'undefined' && options.body instanceof FormData
    const headers: Record<string, string> = {}
    if (options.body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
    if (token) headers.Authorization = `Bearer ${token}`
    return deps.fetchImpl(buildUrl(deps.baseUrl, path, options.params), {
      method,
      headers,
      body:
        options.body === undefined
          ? undefined
          : isForm
            ? (options.body as FormData)
            : JSON.stringify(options.body),
      signal: options.signal,
    })
  }

  async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
    const token = deps.tokenStore.getAccessToken()
    const res = await rawRequest(method, path, options, token)
    const payload = await parseEnvelope<T>(res)

    // 登录态失效：HTTP 401 或业务码 40100（未登录）/ 40101（无权限，网关令牌过期场景）
    const authExpired =
      res.status === 401 || payload?.code === 40100 || payload?.code === 40101

    if (authExpired) {
      if (!options.retried) {
        const refreshed = await refreshOnce()
        if (refreshed) {
          // 重放原请求（仅一次）
          return request<T>(method, path, { ...options, retried: true })
        }
      }
      // 刷新失败或重放仍失效：清令牌并广播登出
      deps.tokenStore.clearTokens()
      deps.onAuthExpired?.()
      throw new ApiClientError(40100, '登录已过期，请重新登录', res.status)
    }

    if (!res.ok && !payload) {
      throw new ApiClientError(50000, `服务异常（HTTP ${res.status}）`, res.status)
    }
    if (!payload) return undefined as T
    if (payload.code !== 200) {
      throw new ApiClientError(payload.code, payload.msg || '请求失败', res.status, payload.data)
    }
    return payload.data as T
  }

  return {
    request,
    get: <T>(path: string, options?: RequestOptions) => request<T>('GET', path, options),
    post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
      request<T>('POST', path, { ...options, body }),
    put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
      request<T>('PUT', path, { ...options, body }),
    delete: <T>(path: string, options?: RequestOptions) => request<T>('DELETE', path, options),
    refreshOnce,
  }
}

/** 登录态失效广播：router/main 监听后跳登录页（保留来源） */
export const AUTH_EXPIRED_EVENT = 'novel:auth-expired'

function defaultAuthExpiredHook() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
  }
}

/** 全站单例 */
export const http = createHttpClient({
  baseUrl: (import.meta.env.VITE_API_BASE_URL as string | undefined) || '/api',
  fetchImpl: (input, init) => fetch(input, init),
  tokenStore: {
    getAccessToken,
    getRefreshToken,
    setTokens,
    clearTokens,
  },
  onAuthExpired: defaultAuthExpiredHook,
})
