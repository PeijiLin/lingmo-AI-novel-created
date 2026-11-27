import { describe, expect, it } from 'vitest'
import { DEFAULT_READER_SETTINGS, parseReaderSettings } from '../readerSettings'

describe('parseReaderSettings', () => {
  it('缺失或非法 JSON 回退默认值', () => {
    expect(parseReaderSettings(null)).toEqual(DEFAULT_READER_SETTINGS)
    expect(parseReaderSettings('{bad')).toEqual(DEFAULT_READER_SETTINGS)
  })

  it('钳制越界与非法字段', () => {
    expect(parseReaderSettings(JSON.stringify({ fontSize: 0, lineHeight: 99, theme: 'neon' })))
      .toEqual(DEFAULT_READER_SETTINGS)
    expect(parseReaderSettings(JSON.stringify({ fontSize: 999 }))).toEqual(DEFAULT_READER_SETTINGS)
    expect(parseReaderSettings(JSON.stringify({ fontSize: 'big' }))).toEqual(DEFAULT_READER_SETTINGS)
  })

  it('保留合法值', () => {
    expect(parseReaderSettings(JSON.stringify({ fontSize: 20, lineHeight: 1.75, theme: 'night' })))
      .toEqual({ fontSize: 20, lineHeight: 1.75, theme: 'night' })
  })
})
