/**
 * AI 创作流水线（/api/book/create/ai/*，多智能体四档管道）
 *
 * 路径说明：后端 context-path=/api 且控制器自带 /api 前缀，
 * 真实路径为 /api/api/book/create/ai/*（baseUrl '/api' + 下列 path）。
 *
 * 同步模式：SSE 混合事件流（stage/content），见 utils/sse.ts
 * 异步模式：提交返回 taskId → 轮询任务进度（usage/degraded/error）
 * 旧五端点（polish/create/continue/dialogue/activate）保留兼容。
 */
import { http } from '@/utils/http'
import { streamSse, type SseEvent } from '@/utils/sse'
import type { AiChatRequest, CreationTask } from './types'

const BASE = '/api/book/create/ai'

/** 同步运行流水线（SSE：stage 进度 + content 流式正文） */
export function runPipeline(
  request: AiChatRequest,
  onEvent: (event: SseEvent) => void,
  options?: { signal?: AbortSignal; idleTimeoutMs?: number },
): Promise<void> {
  return streamSse(`${BASE}/pipeline`, {
    body: request,
    onEvent,
    signal: options?.signal,
    idleTimeoutMs: options?.idleTimeoutMs,
  })
}

/** 提交异步创作任务（整章/批量），返回 taskId */
export function submitAsyncPipeline(request: AiChatRequest): Promise<number> {
  return http.post<number>(`${BASE}/pipeline/async`, request)
}

/** 查询异步任务进度与用量（轮询） */
export function queryCreationTask(taskId: number): Promise<CreationTask> {
  return http.get<CreationTask>(`${BASE}/pipeline/task/${taskId}`)
}

/* ---------------- 旧五端点（非流式，兼容保留） ---------------- */

export function polishContent(request: AiChatRequest): Promise<string[]> {
  return http.post<string[]>(`${BASE}/polish`, request)
}

export function generateDialogue(request: AiChatRequest): Promise<string[]> {
  return http.post<string[]>(`${BASE}/dialogue`, request)
}

export function createContent(request: AiChatRequest): Promise<string[]> {
  return http.post<string[]>(`${BASE}/create`, request)
}

export function continueWriting(request: AiChatRequest): Promise<string[]> {
  return http.post<string[]>(`${BASE}/continue`, request)
}

export function activateInspiration(request: AiChatRequest): Promise<string[]> {
  return http.post<string[]>(`${BASE}/activate`, request)
}
