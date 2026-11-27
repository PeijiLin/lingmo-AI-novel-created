import { describe, expect, it } from 'vitest'
import {
  clampScrollY,
  getEntry,
  getLatestEntry,
  makeReadingEntry,
  parseReadingProgress,
  progressKey,
  resolveChapterId,
  setEntry,
} from '../readingProgress'

describe('parseReadingProgress', () => {
  it('合法条目按书与章节复合键返回', () => {
    const entry = { chapterId: 7, scrollY: 320, updatedAt: 123 }
    expect(parseReadingProgress(JSON.stringify({ '4:7': entry }))).toEqual({ '4:7': entry })
  })

  it('兼容旧单条格式', () => {
    expect(
      parseReadingProgress(
        JSON.stringify({ 4: { chapterId: 7, scrollY: 320, updatedAt: 123 } }),
      ),
    ).toEqual({ '4:7': { chapterId: 7, scrollY: 320, updatedAt: 123 } })
  })

  it('读取更旧的纯数字格式为章节 id', () => {
    expect(parseReadingProgress('{"1":5}')).toEqual({
      '1:5': { chapterId: 5, scrollY: 0, updatedAt: 0 },
    })
  })

  it('脏 JSON 与非法条目回退空表', () => {
    expect(parseReadingProgress('{bad')).toEqual({})
    expect(parseReadingProgress(JSON.stringify({ 1: 'x', 2: null }))).toEqual({})
  })

  it('负滚动位置钳制为 0', () => {
    expect(
      parseReadingProgress(JSON.stringify({ '4:5': { chapterId: 5, scrollY: -10 } })),
    ).toEqual({ '4:5': { chapterId: 5, scrollY: 0, updatedAt: 0 } })
  })
})

describe('chapter-scoped accessors', () => {
  const first = makeReadingEntry(1, 100, 1000)
  const second = makeReadingEntry(2, 200, 2000)

  it('按书与章节读取条目', () => {
    const map = setEntry(setEntry({}, 3, first), 3, second)
    expect(progressKey(3, 1)).toBe('3:1')
    expect(getEntry(map, 3, 1)).toEqual(first)
    expect(getEntry(map, 3, 2)).toEqual(second)
    expect(getEntry(map, 4, 1)).toBeUndefined()
  })

  it('取该书最近阅读的章节', () => {
    const map = setEntry(setEntry({}, 3, first), 3, second)
    expect(getLatestEntry(map, 3)).toEqual(second)
    expect(getLatestEntry(map, 4)).toBeUndefined()
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
