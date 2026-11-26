import { describe, expect, it } from 'vitest'
import { isValidDate, parsePositiveInt } from '../validation'

describe('parsePositiveInt', () => {
  it.each([
    ['1', 1],
    [' 200 ', 200],
    ['9007199254740991', 9007199254740991],
  ])('接受正整数 %s', (raw, expected) => {
    expect(parsePositiveInt(raw)).toBe(expected)
  })

  it.each(['', '0', '-1', '1.5', 'abc', '1e3', '9007199254740992'])('拒绝非法值 %s', (raw) => {
    expect(parsePositiveInt(raw)).toBeUndefined()
  })
})

describe('isValidDate', () => {
  it.each(['2026-11-26', '2024-02-29'])('接受合法日期 %s', (raw) => {
    expect(isValidDate(raw)).toBe(true)
  })

  it.each(['2026-99-99', '2026-02-30', '2026-2-3', '2026/11/26', 'abc', ''])('拒绝非法日期 %s', (raw) => {
    expect(isValidDate(raw)).toBe(false)
  })
})
