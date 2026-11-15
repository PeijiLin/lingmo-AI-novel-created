/**
 * AI 多智能体创作（规范 3.1 / 阶段 3 核心对接）
 *
 * 同步：SSE stage 进度时间线 + content 流式正文 → 朱批卡实时生长
 * 异步：提交 taskId → 轮询 creation_task 至终态（整章生成完成后由视图重载章节）
 */
import { computed, reactive, readonly } from 'vue'
import { runPipeline, submitAsyncPipeline, queryCreationTask } from '@/api/pipeline'
import type { AiChatRequest, CreationMode } from '@/api/types'
import type { SseEvent, StageUsage } from '@/utils/sse'

export type AiAction = '续写' | '润色' | '审校'
export type RunChannel = 'sync' | 'async'

export interface StageProgress {
  code: string
  status: string
  index?: number
  total?: number
  round?: number
}

/** 朱批卡数据（稿纸边际旁注，规范签名元素） */
export interface VermilionNote {
  id: number
  kind: AiAction
  /** 锚定段落标题（章节题或「本章」） */
  label: string
  /** 流式累计正文 */
  text: string
  /** Tiptap 文档锚点（ProseMirror pos，由视图按策略计算） */
  anchorPos: number
  status: 'streaming' | 'ready' | 'adopted' | 'dismissed'
}

export interface AiRunState {
  running: boolean
  action: AiAction | null
  mode: CreationMode
  channel: RunChannel
  stages: StageProgress[]
  currentStage: StageProgress | null
  streamText: string
  usage: StageUsage | null
  degraded: boolean
  error: string | null
  taskId: number | null
  taskStatus: string | null
}

let noteSeq = 0

export interface RunOptions {
  request: Omit<AiChatRequest, 'mode' | 'maxRewriteRounds'> & { mode?: CreationMode; maxRewriteRounds?: number }
  action: AiAction
  /** 朱批锚点（ProseMirror pos）与标签 */
  anchorPos: number
  label: string
  channel?: RunChannel
  /** 异步完成后回调（重载章节等） */
  onAsyncCompleted?: (task: { usage?: StageUsage; degraded?: boolean }) => void
}

export function useAiCreation() {
  const state = reactive<AiRunState>({
    running: false,
    action: null,
    mode: 'FAST',
    channel: 'sync',
    stages: [],
    currentStage: null,
    streamText: '',
    usage: null,
    degraded: false,
    error: null,
    taskId: null,
    taskStatus: null,
  })

  const notes = reactive<VermilionNote[]>([])

  let activeNoteId: number | null = null
  let abortController: AbortController | null = null
  let pollTimer: ReturnType<typeof setTimeout> | null = null

  const isRunning = computed(() => state.running)

  function resetRun(action: AiAction, mode: CreationMode, channel: RunChannel) {
    state.running = true
    state.action = action
    state.mode = mode
    state.channel = channel
    state.stages = []
    state.currentStage = null
    state.streamText = ''
    state.usage = null
    state.degraded = false
    state.error = null
    state.taskId = null
    state.taskStatus = null
  }

  function openNote(action: AiAction, label: string, anchorPos: number): number {
    const id = ++noteSeq
    notes.unshift({
      id,
      kind: action,
      label,
      text: '',
      anchorPos,
      status: 'streaming',
    })
    activeNoteId = id
    return id
  }

  function patchNote(id: number, patch: Partial<VermilionNote>) {
    const note = notes.find((n) => n.id === id)
    if (note) Object.assign(note, patch)
  }

  function handleSseEvent(event: SseEvent, noteId: number) {
    if (event.type === 'stage') {
      const s = event.stage
      if (s.status === 'STARTED') {
        const progress: StageProgress = { code: s.code, status: s.status, index: s.index, total: s.total }
        state.stages.push(progress)
        state.currentStage = progress
      } else if (s.status === 'REVISION') {
        const progress: StageProgress = { code: s.code, status: s.status, round: s.round }
        state.stages.push(progress)
        state.currentStage = progress
      } else if (s.code === 'COMPLETED') {
        state.degraded = !!s.degraded
        state.usage = s.usage ?? null
        state.running = false
        patchNote(noteId, { status: 'ready' })
      } else if (s.code === 'FAILED') {
        state.error = s.error || '流水线执行失败'
        state.running = false
        patchNote(noteId, { status: 'dismissed' })
      }
      return
    }
    if (event.type === 'content') {
      state.streamText += event.text
      patchNote(noteId, { text: state.streamText })
    }
  }

  async function runSync(options: RunOptions): Promise<void> {
    const { request, action, anchorPos, label } = options
    const mode = request.mode ?? 'FAST'
    resetRun(action, mode, 'sync')
    const noteId = openNote(action, label, anchorPos)
    abortController = new AbortController()

    try {
      await runPipeline(
        { ...request, mode },
        (event) => handleSseEvent(event, noteId),
        { signal: abortController.signal },
      )
      // 流正常收尾但未见 COMPLETED（后端以流关闭代替事件）：置就绪
      if (state.running) {
        state.running = false
        if ((notes.find((n) => n.id === noteId)?.text ?? '').length > 0) {
          patchNote(noteId, { status: 'ready' })
        } else {
          patchNote(noteId, { status: 'dismissed' })
        }
      }
    } catch (err) {
      state.running = false
      state.error = err instanceof Error ? err.message : String(err)
      const note = notes.find((n) => n.id === noteId)
      if (note && note.status === 'streaming') {
        // 失败但已有部分产出：保留为 ready 供作者取舍，否则收回
        patchNote(noteId, { status: note.text.length > 0 ? 'ready' : 'dismissed' })
      }
      throw err
    } finally {
      abortController = null
    }
  }

  async function pollTask(taskId: number, onDone: RunOptions['onAsyncCompleted']): Promise<void> {
    const deadline = Date.now() + 10 * 60 * 1000
    const tick = async () => {
      if (Date.now() > deadline) {
        state.error = '异步任务超时未完成（10 分钟）'
        state.running = false
        return
      }
      try {
        const task = await queryCreationTask(taskId)
        state.taskStatus = task.status ?? null
        state.currentStage = task.currentStage
          ? { code: task.currentStage, status: 'RUNNING' }
          : state.currentStage
        if (task.status === 'COMPLETED') {
          state.running = false
          state.degraded = !!task.degraded
          state.usage = task.totalTokens
            ? { stages: 0, totalTokens: task.totalTokens, elapsedMs: task.elapsedMs ?? 0 }
            : null
          onDone?.({ usage: state.usage ?? undefined, degraded: state.degraded })
          return
        }
        if (task.status === 'FAILED') {
          state.running = false
          state.error = task.error || '异步任务失败'
          return
        }
      } catch (err) {
        // 轮询瞬时失败容忍，下轮重试
        console.warn('轮询任务进度失败，将重试', err)
      }
      pollTimer = setTimeout(tick, 3000)
    }
    await tick()
  }

  async function runAsync(options: RunOptions): Promise<void> {
    const { request, action } = options
    const mode = request.mode ?? 'FAST'
    resetRun(action, mode, 'async')
    state.taskStatus = 'PENDING'
    try {
      const taskId = await submitAsyncPipeline({ ...request, mode })
      state.taskId = taskId
      state.taskStatus = 'RUNNING'
      await pollTask(taskId, options.onAsyncCompleted)
    } catch (err) {
      state.running = false
      state.error = err instanceof Error ? err.message : String(err)
      throw err
    }
  }

  function run(options: RunOptions): Promise<void> {
    if (state.running) return Promise.reject(new Error('创作流水线正在执行中'))
    return (options.channel ?? 'sync') === 'async' ? runAsync(options) : runSync(options)
  }

  function abort() {
    abortController?.abort()
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
    state.running = false
    state.error = '已手动中止'
  }

  function dismissNote(id: number) {
    patchNote(id, { status: 'dismissed' })
  }

  function adoptNote(id: number) {
    patchNote(id, { status: 'adopted' })
  }

  function clearFinishedNotes() {
    for (let i = notes.length - 1; i >= 0; i -= 1) {
      if (notes[i].status === 'dismissed' || notes[i].status === 'adopted') notes.splice(i, 1)
    }
  }

  return {
    state: readonly(state) as unknown as AiRunState,
    notes,
    isRunning,
    run,
    abort,
    dismissNote,
    adoptNote,
    clearFinishedNotes,
  }
}
