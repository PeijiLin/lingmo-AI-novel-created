import { describe, expect, it } from 'vitest'
import { clampRankLimit, formatVisitCount, sanitizeRanking } from '../ranking'

describe('sanitizeRanking', () => {
  it('非数组响应返回空榜', () => {
    expect(sanitizeRanking(null)).toEqual([])
    expect(sanitizeRanking({})).toEqual([])
    expect(sanitizeRanking('x')).toEqual([])
  })

  it('过滤空项并保留有效书籍', () => {
    expect(
      sanitizeRanking([null, { id: 1, bookName: 'A' }, undefined, { bookName: 'no id' }]),
    ).toEqual([{ id: 1, bookName: 'A' }])
  })
})

describe('formatVisitCount', () => {
  it('格式化阅读量', () => {
    expect(formatVisitCount(12345)).toBe('1.2万')
    expect(formatVisitCount(10000)).toBe('1万')
    expect(formatVisitCount(0)).toBe('0')
    expect(formatVisitCount(null)).toBe('0')
  })
})

describe('clampRankLimit', () => {
  it('仅接受 20 或 50', () => {
    expect(clampRankLimit(50)).toBe(50)
    expect(clampRankLimit(20)).toBe(20)
    expect(clampRankLimit(7)).toBe(20)
  })
})
