/**
 * SSE 流式客户端（规范 5.2）
 *
 * 协议按后端 ProgressEmitter 实况实现：
 *   event: stage    data: {"code":"WRITING","status":"STARTED","index":2,"total":4}
 *   event: stage    data: {"code":"WRITING","status":"REVISION","round":1}
 *   event: content  data: {"text":"夜色渐沉"}
 *   event: stage    data: {"code":"COMPLETED","degraded":false,"usage":{...}}
 *   event: stage    data: {"code":"FAILED","error":"..."}
 *   （无独立 revision/error 事件类型；[DONE] 兼容处理）
 *
 * 机制：空闲超时熔断（默认 90s 无任何字节即断，而非全程超时——REFINED/PRECISE 可能数分钟）、
 * AbortController、401 单飞刷新后重连一次、解析器纯函数化便于单测。
 */
import { http, type HttpClient } from './http'
import { getAccessToken } from './token'

export interface StageUsage {
  stages: number
  totalTokens: number
  elapsedMs: number
}

export interface StageEventPayload {
  code: string
  status: string
  index?: number
  total?: number
  round?: number
  degraded?: boolean
  usage?: StageUsage
  error?: string
  /** POLISHING STARTED 携带：草稿态，随后 REVISION 整稿替换 */
  draft?: boolean
}

export type SseEvent =
  | { type: 'stage'; stage: StageEventPayload }
  | { type: 'content'; text: string }
  | { type: 'done' }
  | { type: 'raw'; event: string; data: string }

export interface SseParser {
  feed(chunk: string): void
  flush(): void
}

/** SSE 事件流解析器：处理 \n\n 与 \r\n\r\n 边界、多 data 行拼接、[DONE] */
export function createSseParser(onEvent: (event: SseEvent) => void): SseParser {
  let buffer = ''
  let doneEmitted = false

  function dispatch(eventLines: string[]) {
    let eventName = 'message'
    const dataLines: string[] = []
    for (const line of eventLines) {
      if (line.startsWith(':')) continue // 注释行
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        // 支持 "data:xxx" 与 "data: xxx"
        dataLines.push(line.slice(5).replace(/^\s/, ''))
      }
    }
    if (dataLines.length === 0) return
    const data = dataLines.join('\n')

    if (data === '[DONE]') {
      if (!doneEmitted) {
        doneEmitted = true
        onEvent({ type: 'done' })
      }
      return
    }
    if (eventName === 'stage') {
      try {
        onEvent({ type: 'stage', stage: JSON.parse(data) as StageEventPayload })
      } catch {
        onEvent({ type: 'raw', event: eventName, data })
      }
      return
    }
    if (eventName === 'content') {
      try {
        const parsed = JSON.parse(data) as { text?: string }
        if (typeof parsed.text === 'string' && parsed.text.length > 0) {
          onEvent({ type: 'content', text: parsed.text })
        }
      } catch {
        onEvent({ type: 'raw', event: eventName, data })
      }
      return
    }
    onEvent({ type: 'raw', event: eventName, data })
  }

  return {
    feed(chunk: string) {
      buffer += chunk
      // SSE 规范：事件以空行分隔（兼容 \n\n 与 \r\n\r\n）
      while (true) {
        const match = /\r?\n\r?\n/.exec(buffer)
        if (!match) break
        const eventText = buffer.slice(0, match.index)
        buffer = buffer.slice(match.index + match[0].length)
        dispatch(eventText.split(/\r?\n/).filter((line) => line.length > 0))
      }
    },
    flush() {
      const rest = buffer
      buffer = ''
      const lines = rest.split(/\r?\n/).filter((line) => line.length > 0)
      if (lines.length > 0) dispatch(lines)
    },
  }
}

export interface StreamSseOptions {
  body?: unknown
  signal?: AbortSignal
  /** 空闲熔断：连续无字节到达即断流（毫秒） */
  idleTimeoutMs?: number
  onEvent(event: SseEvent): void
  /** 注入用于测试 */
  fetchImpl?: typeof fetch
  client?: Pick<HttpClient, 'refreshOnce'>
}

export class SseIdleTimeoutError extends Error {
  constructor(idleMs: number) {
    super(`流式响应空闲超时（${idleMs}ms 无数据）`)
    this.name = 'SseIdleTimeoutError'
  }
}

/** 发起带认证的 SSE POST 流（401 时单飞刷新后自动重连一次） */
export async function streamSse(path: string, options: StreamSseOptions): Promise<void> {
  const {
    body,
    signal,
    idleTimeoutMs = 90_000,
    onEvent,
    fetchImpl = (input, init) => fetch(input, init),
    client = http,
  } = options

  const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) || '/api'
  const url = `${baseUrl}${path.startsWith('/') ? path : `/${path}`}`

  const parser = createSseParser(onEvent)

  async function openStream(retried: boolean): Promise<void> {
    const token = getAccessToken()
    const headers: Record<string, string> = {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    }
    if (token) headers.Authorization = `Bearer ${token}`

    const res = await fetchImpl(url, {
      method: 'POST',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })

    if (res.status === 401 && !retried) {
      const refreshed = await client.refreshOnce()
      if (refreshed) return openStream(true)
      throw new Error('登录已过期，请重新登录')
    }
    if (!res.ok || !res.body) {
      let detail = `HTTP ${res.status}`
      try {
        const text = await res.text()
        if (text) detail = text.slice(0, 200)
      } catch {
        /* ignore */
      }
      throw new Error(`流式请求失败：${detail}`)
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')

    // 空闲熔断：每收到字节重置计时；超时置标记并取消流
    let idleBroken = false
    let idleTimer: ReturnType<typeof setTimeout> | null = null
    const armIdleTimer = () => {
      if (idleTimer) clearTimeout(idleTimer)
      idleTimer = setTimeout(() => {
        idleBroken = true
        idleTimer = null
        reader.cancel().catch(() => undefined)
      }, idleTimeoutMs)
    }
    const disposeTimer = () => {
      if (idleTimer) clearTimeout(idleTimer)
      idleTimer = null
    }

    try {
      armIdleTimer()
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        if (value && value.length > 0) {
          armIdleTimer()
          parser.feed(decoder.decode(value, { stream: true }))
        }
      }
    } finally {
      disposeTimer()
      parser.flush()
    }

    if (idleBroken) throw new SseIdleTimeoutError(idleTimeoutMs)
  }

  await openStream(false)
}
