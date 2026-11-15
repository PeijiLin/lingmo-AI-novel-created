import { describe, expect, it } from 'vitest'
import { createSseParser, type SseEvent } from '../sse'

function collect() {
  const events: SseEvent[] = []
  const parser = createSseParser((e) => events.push(e))
  return { parser, events }
}

describe('createSseParser 事件解析（后端 ProgressEmitter 协议）', () => {
  it('stage / content / [DONE] 完整序列', () => {
    const { parser, events } = collect()
    parser.feed('event: stage\ndata: {"code":"PLANNING","status":"STARTED","index":1,"total":4}\n\n')
    parser.feed('event: content\ndata: {"text":"夜色渐沉"}\n\n')
    parser.feed('event: stage\ndata: {"code":"WRITING","status":"REVISION","round":1}\n\n')
    parser.feed('data: [DONE]\n\n')

    expect(events).toEqual([
      { type: 'stage', stage: { code: 'PLANNING', status: 'STARTED', index: 1, total: 4 } },
      { type: 'content', text: '夜色渐沉' },
      { type: 'stage', stage: { code: 'WRITING', status: 'REVISION', round: 1 } },
      { type: 'done' },
    ])
  })

  it('跨 chunk 半包拼接：事件在任意字节边界被切断也不丢', () => {
    const { parser, events } = collect()
    // 把一个事件切成三段喂入
    parser.feed('event: conten')
    parser.feed('t\ndata: {"text":"第一段')
    parser.feed('开头"}\n\n')

    expect(events).toEqual([{ type: 'content', text: '第一段开头' }])
  })

  it('CRLF 边界（\\r\\n\\r\\n）与 data 紧凑写法（data:xxx）', () => {
    const { parser, events } = collect()
    parser.feed('event: content\r\ndata:{"text":"雨声"}\r\n\r\n')
    expect(events).toEqual([{ type: 'content', text: '雨声' }])
  })

  it('COMPLETED 事件携带 usage 与 degraded', () => {
    const { parser, events } = collect()
    parser.feed(
      'event: stage\ndata: {"code":"COMPLETED","status":"COMPLETED","degraded":false,"usage":{"stages":4,"totalTokens":8200,"elapsedMs":54000}}\n\n',
    )
    expect(events).toEqual([
      {
        type: 'stage',
        stage: {
          code: 'COMPLETED',
          status: 'COMPLETED',
          degraded: false,
          usage: { stages: 4, totalTokens: 8200, elapsedMs: 54000 },
        },
      },
    ])
  })

  it('无结尾空行的残留由 flush 兜底', () => {
    const { parser, events } = collect()
    parser.feed('event: stage\ndata: {"code":"FAILED","status":"FAILED","error":"模型超时"}')
    parser.flush()
    expect(events).toEqual([
      { type: 'stage', stage: { code: 'FAILED', status: 'FAILED', error: '模型超时' } },
    ])
  })

  it('多 data 行按 SSE 规范以 \\n 拼接；[DONE] 只发一次', () => {
    const { parser, events } = collect()
    parser.feed('data: [DONE]\n\ndata: [DONE]\n\n')
    expect(events).toEqual([{ type: 'done' }])
  })

  it('未知事件透传 raw；非法 JSON 容错不抛', () => {
    const { parser, events } = collect()
    parser.feed('event: keepalive\ndata: ping\n\n')
    parser.feed('event: stage\ndata: not-json\n\n')
    expect(events).toEqual([
      { type: 'raw', event: 'keepalive', data: 'ping' },
      { type: 'raw', event: 'stage', data: 'not-json' },
    ])
  })

  it('注释行（: heartbeat）忽略', () => {
    const { parser, events } = collect()
    parser.feed(': heartbeat\n\nevent: content\ndata: {"text":"续"}\n\n')
    expect(events).toEqual([{ type: 'content', text: '续' }])
  })
})
