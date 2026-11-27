import { describe, expect, it } from 'vitest'
import {
  clampScrollY,
  makeReadingEntry,
  parseReadingProgress,
  resolveChapterId,
} from '../readingProgress'

describe('parseReadingProgress', () => {
  it('合法条目原样返回', () => {
    const entry = { chapterId: 7, scrollY: 320, updatedAt: 123 }
    expect(parseReadingProgress(JSON.stringify({ 4: entry }))).toEqual({ 4: entry })
  })

  it('读取旧数字格式为章节 id', () => {
    expect(parseReadingProgress('{"1":5}')).toEqual({
      1: { chapterId: 5, scrollY: 0, updatedAt: 0 },
    })
  })

  it('脏 JSON 与非法条目回退空表', () => {
    expect(parseReadingProgress('{bad')).toEqual({})
    expect(parseReadingProgress(JSON.stringify({ 1: 'x', 2: null }))).toEqual({})
  })

  it('负滚动位置钳制为 0', () => {
    expect(parseReadingProgress(JSON.stringify({ 1: { chapterId: 5, scrollY: -10 } }))).toEqual({
      1: { chapterId: 5, scrollY: 0, updatedAt: 0 },
    })
  })
})

describe('clampScrollY', () => {
  it('不超过正文最大高度', () => {
    expect(clampScrollY(999, 400)).toBe(400)
    expect(clampScrollY(120, 400)).toBe(120)
    expect(clampScrollY(-5, 400)).toBe(0)
  })
})

describe('resolveChapterId', () => {
  it('章节存在时返回进度章节', () => {
    expect(resolveChapterId({ chapterId: 2, scrollY: 0, updatedAt: 0 }, [1, 2, 3])).toBe(2)
  })

  it('章节失效或缺失时返回 undefined', () => {
    expect(resolveChapterId({ chapterId: 99, scrollY: 0, updatedAt: 0 }, [1, 2])).toBeUndefined()
    expect(resolveChapterId(undefined, [1, 2])).toBeUndefined()
  })
})

describe('makeReadingEntry', () => {
  it('生成带时间戳的条目', () => {
    expect(makeReadingEntry(3, 10, 999)).toEqual({ chapterId: 3, scrollY: 10, updatedAt: 999 })
  })
})
