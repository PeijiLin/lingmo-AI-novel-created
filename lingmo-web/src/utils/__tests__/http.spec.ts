import { describe, expect, it, vi } from 'vitest'
import { ApiClientError, createHttpClient, type HttpClientDeps } from '../http'

/** 构造信封响应 */
function envelope(code: number, data?: unknown, msg?: string, status = 200) {
  return new Response(JSON.stringify({ code, data, msg }), { status })
}

interface TokenStoreState {
  access: string | null
  refresh: string | null
}

function makeDeps(state: TokenStoreState, calls: { url: string; init?: RequestInit }[], opts?: Partial<HttpClientDeps>) {
  const setTokens = vi.fn()
  const clearTokens = vi.fn()
  const onAuthExpired = vi.fn()
  const deps: HttpClientDeps = {
    baseUrl: '/api',
    fetchImpl: async (input, init) => {
      const url = String(input)
      calls.push({ url, init })
      const handler = handlers[callIndex++]
      if (!handler) throw new Error(`unexpected fetch #${callIndex}: ${url}`)
      return handler(url, init)
    },
    tokenStore: {
      getAccessToken: () => state.access,
      getRefreshToken: () => state.refresh,
      setTokens: (pair) => {
        setTokens(pair)
        state.access = pair.access_token
        state.refresh = pair.refresh_token
      },
      clearTokens: () => {
        clearTokens()
        state.access = null
        state.refresh = null
      },
    },
    onAuthExpired,
    ...opts,
  }
  let handlers: ((url: string, init?: RequestInit) => Response | Promise<Response>)[] = []
  let callIndex = 0
  return {
    deps,
    setHandlers(next: ((url: string, init?: RequestInit) => Response | Promise<Response>)[]) {
      handlers = next
      callIndex = 0
    },
    calls,
    setTokens,
    clearTokens,
    onAuthExpired,
  }
}

function bearerOf(call: { init?: RequestInit }) {
  const headers = call.init?.headers as Record<string, string>
  return headers?.Authorization ?? ''
}

describe('createHttpClient 信封解包', () => {
  it('code=200 返回 data', async () => {
    const kit = makeDeps({ access: 't0', refresh: 'r0' }, [])
    kit.setHandlers([() => envelope(200, { value: 42 })])
    const client = createHttpClient(kit.deps)
    await expect(client.get('/demo')).resolves.toEqual({ value: 42 })
  })

  it('业务码非 200 抛 ApiClientError（带 code 与 msg）', async () => {
    const kit = makeDeps({ access: 't0', refresh: 'r0' }, [])
    kit.setHandlers([() => envelope(40400, undefined, '请求数据不存在')])
    const client = createHttpClient(kit.deps)
    const err = (await client.get('/demo').catch((e: unknown) => e)) as ApiClientError
    expect(err).toBeInstanceOf(ApiClientError)
    expect(err.code).toBe(40400)
    expect(err.message).toBe('请求数据不存在')
  })
})

describe('单飞刷新队列（规范 5.2 核心验收）', () => {
  it('并发 401：仅刷新一次，双双重放且成功', async () => {
    const kit = makeDeps({ access: 'old', refresh: 'r1' }, [])
    kit.setHandlers([
      // 请求1 401
      () => envelope(40100, undefined, '未登录', 401),
      // 请求2 401（并发）
      () => envelope(40100, undefined, '未登录', 401),
      // 刷新（两个并发请求复用同一个 Promise，只应命中一次）
      (url) => {
        expect(url.endsWith('/user/refresh-token')).toBe(true)
        return envelope(200, { access_token: 'new', refresh_token: 'r2', userId: 1 })
      },
      // 重放1 成功
      () => envelope(200, { ok: 1 }),
      // 重放2 成功
      () => envelope(200, { ok: 2 }),
    ])
    const client = createHttpClient(kit.deps)

    const [a, b] = await Promise.all([client.get('/data'), client.get('/data')])
    expect(a).toEqual({ ok: 1 })
    expect(b).toEqual({ ok: 2 })

    // 刷新仅一次
    const refreshCalls = kit.calls.filter((c) => c.url.endsWith('/user/refresh-token'))
    expect(refreshCalls).toHaveLength(1)
    // 重放携带新令牌
    const replays = kit.calls.filter((c) => c.url.endsWith('/data') && bearerOf(c) === 'Bearer new')
    expect(replays).toHaveLength(2)
    expect(kit.setTokens).toHaveBeenCalledTimes(1)
    expect(kit.clearTokens).not.toHaveBeenCalled()
  })

  it('业务码 40101（无权限/令牌过期）同样触发单飞刷新', async () => {
    const kit = makeDeps({ access: 'old', refresh: 'r1' }, [])
    kit.setHandlers([
      () => envelope(40101, undefined, '无权限'), // HTTP 200 但业务码失效
      () => envelope(200, { access_token: 'new', refresh_token: 'r2', userId: 1 }),
      () => envelope(200, 'fine'),
    ])
    const client = createHttpClient(kit.deps)
    await expect(client.post('/data', {})).resolves.toBe('fine')
    expect(kit.calls.filter((c) => c.url.endsWith('/user/refresh-token'))).toHaveLength(1)
  })

  it('刷新失败：清令牌、广播登出、抛错', async () => {
    const kit = makeDeps({ access: 'old', refresh: 'bad' }, [])
    kit.setHandlers([
      () => envelope(40100, undefined, '未登录', 401),
      () => envelope(40400, undefined, 'refresh token 不存在'),
    ])
    const client = createHttpClient(kit.deps)
    await expect(client.get('/data')).rejects.toThrow('登录已过期')
    expect(kit.clearTokens).toHaveBeenCalled()
    expect(kit.onAuthExpired).toHaveBeenCalled()
  })

  it('刷新成功但重放仍 401：不再二次刷新（防循环）', async () => {
    const kit = makeDeps({ access: 'old', refresh: 'r1' }, [])
    kit.setHandlers([
      () => envelope(40100, undefined, '未登录', 401),
      () => envelope(200, { access_token: 'new', refresh_token: 'r2', userId: 1 }),
      () => envelope(40100, undefined, '未登录', 401), // 重放仍失败
    ])
    const client = createHttpClient(kit.deps)
    await expect(client.get('/data')).rejects.toThrow('登录已过期')
    expect(kit.calls.filter((c) => c.url.endsWith('/user/refresh-token'))).toHaveLength(1)
  })
})

describe('请求构造', () => {
  it('查询参数拼装 + Bearer 注入', async () => {
    const kit = makeDeps({ access: 't0', refresh: 'r0' }, [])
    kit.setHandlers([() => envelope(200, null)])
    const client = createHttpClient(kit.deps)
    await client.get('/demo', { params: { a: 1, b: 'x', skip: undefined, drop: null } })
    const call = kit.calls[0]
    expect(call.url).toBe('/api/demo?a=1&b=x')
    expect(bearerOf(call)).toBe('Bearer t0')
  })
})

describe('文件下载（responseType: blob）', () => {
  it('返回 Blob，跳过 JSON 信封解包', async () => {
    const kit = makeDeps({ access: 't0', refresh: 'r0' }, [])
    const csv = new Blob(['\ufeff模式,节点'], { type: 'text/csv' })
    kit.setHandlers([() => new Response(csv, { status: 200 })])
    const client = createHttpClient(kit.deps)

    const blob = await client.get<Blob>('/cost/1/export', { responseType: 'blob' })

    expect(blob).toBeInstanceOf(Blob)
    expect(blob.size).toBeGreaterThan(0)
    expect(bearerOf(kit.calls[0])).toBe('Bearer t0')
  })

  it('HTTP 401 时单飞刷新并携带新令牌重放', async () => {
    const kit = makeDeps({ access: 'old', refresh: 'r1' }, [])
    kit.setHandlers([
      () => new Response(null, { status: 401 }),
      () => envelope(200, { access_token: 'new', refresh_token: 'r2', userId: 1 }),
      () => new Response(new Blob(['ok']), { status: 200 }),
    ])
    const client = createHttpClient(kit.deps)

    const blob = await client.get<Blob>('/cost/1/export', { responseType: 'blob' })

    expect(blob.size).toBeGreaterThan(0)
    expect(kit.calls.filter((c) => c.url.endsWith('/user/refresh-token'))).toHaveLength(1)
    expect(bearerOf(kit.calls[2])).toBe('Bearer new')
  })
})
