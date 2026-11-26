import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { SseEvent } from '@/utils/sse'

/**
 * 事件脚本由 mock 的 runPipeline 依序回放，直接驱动 useAiCreation 的真实状态机。
 * 这里锁定的核心契约：REVISION 之后的 content 是整稿替换，不是追加。
 */
const script = vi.hoisted(() => ({ events: [] as SseEvent[] }))

vi.mock('@/api/pipeline', () => ({
  runPipeline: async (_request: unknown, onEvent: (event: SseEvent) => void) => {
    for (const event of script.events) onEvent(event)
  },
  submitAsyncPipeline: vi.fn(),
  queryCreationTask: vi.fn(),
}))

import { useAiCreation } from '../useAiCreation'

function playback(events: SseEvent[]) {
  script.events = events
  const ai = useAiCreation()
  return {
    ai,
    run: () =>
      ai.run({
        request: { bookId: 1, userInput: '续写' },
        action: '续写',
        anchorPos: 0,
        label: '本章末尾',
      }),
  }
}

describe('useAiCreation · SSE 缓冲语义', () => {
  beforeEach(() => {
    script.events = []
  })

  it('REVISION 清空缓冲，后续 content 为整稿替换而非追加', async () => {
    const { ai, run } = playback([
      { type: 'stage', stage: { code: 'WRITING', status: 'STARTED', index: 1, total: 2 } },
      { type: 'content', text: '旧稿开头' },
      { type: 'stage', stage: { code: 'CRITIC', status: 'STARTED', index: 2, total: 2 } },
      { type: 'stage', stage: { code: 'WRITING', status: 'REVISION', round: 1 } },
      { type: 'content', text: '新稿全文' },
    ])

    await run()

    expect(ai.state.streamText).toBe('新稿全文')
    expect(ai.notes[0]?.text).toBe('新稿全文')
  })

  it('连续多次 REVISION 只保留最后一次 content', async () => {
    const { ai, run } = playback([
      { type: 'stage', stage: { code: 'WRITING', status: 'STARTED', index: 1, total: 2 } },
      { type: 'content', text: '第一版' },
      { type: 'stage', stage: { code: 'WRITING', status: 'REVISION', round: 1 } },
      { type: 'content', text: '第二版' },
      { type: 'stage', stage: { code: 'WRITING', status: 'REVISION', round: 2 } },
      { type: 'content', text: '第三版' },
    ])

    await run()

    expect(ai.notes[0]?.text).toBe('第三版')
  })

  it('POLISHING 草稿态保持到终稿替换，COMPLETED 后清除', async () => {
    const { ai, run } = playback([
      { type: 'stage', stage: { code: 'POLISHING', status: 'STARTED', index: 4, total: 4, draft: true } },
      { type: 'content', text: '草稿正文' },
      { type: 'stage', stage: { code: 'POLISHING', status: 'REVISION', round: 1 } },
      { type: 'content', text: '终稿正文' },
      {
        type: 'stage',
        stage: {
          code: 'COMPLETED',
          status: 'COMPLETED',
          degraded: false,
          usage: { stages: 4, totalTokens: 100, elapsedMs: 200 },
        },
      },
    ])

    await run()

    expect(ai.notes[0]?.text).toBe('终稿正文')
    expect(ai.notes[0]?.draft).toBe(false)
    expect(ai.state.usage?.totalTokens).toBe(100)
  })

  it('仅收到草稿事件时保持 draft=true（禁止采纳）', async () => {
    const { ai, run } = playback([
      { type: 'stage', stage: { code: 'POLISHING', status: 'STARTED', index: 4, total: 4, draft: true } },
      { type: 'content', text: '草稿正文' },
    ])

    await run()

    expect(ai.notes[0]?.draft).toBe(true)
  })
})
