import { describe, expect, it } from 'vitest'
import { markReported, parseReported, shouldReport } from '../readTracking'

describe('parseReported', () => {
  it('脏数据回退空表', () => {
    expect(parseReported('{bad')).toEqual({})
    expect(parseReported(null)).toEqual({})
  })

  it('解析合法记录', () => {
    expect(parseReported('{"3":true}')).toEqual({ 3: true })
  })
})

describe('read report dedupe', () => {
  it('同一章节只上报一次', () => {
    expect(shouldReport({}, 3)).toBe(true)
    const marked = markReported({}, 3)
    expect(shouldReport(marked, 3)).toBe(false)
    expect(shouldReport(marked, 4)).toBe(true)
  })

  it('忽略非法章节 id', () => {
    expect(shouldReport({}, 0)).toBe(false)
    expect(shouldReport({}, -1)).toBe(false)
  })
})
