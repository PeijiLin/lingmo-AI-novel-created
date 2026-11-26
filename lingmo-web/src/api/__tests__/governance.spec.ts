import { beforeEach, describe, expect, it, vi } from 'vitest'

const put = vi.fn()
const del = vi.fn()

vi.mock('@/utils/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
    put: (...args: unknown[]) => put(...args),
    delete: (...args: unknown[]) => del(...args),
  },
}))

import { revokeExemption, reviewExemption } from '../governance'

describe('规则豁免的 ruleId 传参', () => {
  beforeEach(() => {
    put.mockClear()
    del.mockClear()
  })

  it('ruleId 含 / ? & 时撤销走 query 参数，不拼进路径', async () => {
    const ruleId = 'R/测试&?=1'
    await revokeExemption(2, ruleId)

    expect(del).toHaveBeenCalledTimes(1)
    const [path, options] = del.mock.calls[0] as [string, { params: Record<string, string> }]
    expect(path).toBe('/api/book/rule-exemption/2')
    expect(options.params).toEqual({ ruleId })
    expect(path).not.toContain(encodeURIComponent(ruleId))
  })

  it('复核时间为空时不发送 body，ruleId 仍在 query 参数', async () => {
    await reviewExemption(2, 'R/测试')

    expect(put).toHaveBeenCalledTimes(1)
    const [path, body, options] = put.mock.calls[0] as [string, unknown, { params: Record<string, string> }]
    expect(path).toBe('/api/book/rule-exemption/2/review')
    expect(body).toBeUndefined()
    expect(options.params).toEqual({ ruleId: 'R/测试' })
  })

  it('有复核时间时携带 body', async () => {
    await reviewExemption(2, 'R1', '2026-12-01')

    const [, body] = put.mock.calls[0] as [string, unknown]
    expect(body).toEqual({ reviewAt: '2026-12-01' })
  })
})
