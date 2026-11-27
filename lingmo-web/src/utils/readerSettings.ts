/** 阅读偏好：字号、行高、主题。所有字段读取时校验并回退默认值。 */

export type ReaderTheme = 'paper' | 'sepia' | 'night'

export interface ReaderSettings {
  fontSize: number
  lineHeight: number
  theme: ReaderTheme
}

export const READER_SETTINGS_KEY = 'novel_reader_settings'

export const DEFAULT_READER_SETTINGS: ReaderSettings = {
  fontSize: 17,
  lineHeight: 1.9,
  theme: 'paper',
}

const LINE_HEIGHTS = [1.6, 1.75, 1.9, 2.1]
const THEMES: ReaderTheme[] = ['paper', 'sepia', 'night']

export function parseReaderSettings(raw: string | null | undefined): ReaderSettings {
  try {
    const parsed: unknown = JSON.parse(raw ?? '')
    if (!parsed || typeof parsed !== 'object') return { ...DEFAULT_READER_SETTINGS }
    const obj = parsed as Record<string, unknown>
    const fontSize =
      typeof obj.fontSize === 'number' &&
      Number.isFinite(obj.fontSize) &&
      obj.fontSize >= 16 &&
      obj.fontSize <= 22
        ? Math.round(obj.fontSize)
        : DEFAULT_READER_SETTINGS.fontSize
    const lineHeight =
      typeof obj.lineHeight === 'number' && LINE_HEIGHTS.includes(obj.lineHeight)
        ? obj.lineHeight
        : DEFAULT_READER_SETTINGS.lineHeight
    const theme = THEMES.includes(obj.theme as ReaderTheme)
      ? (obj.theme as ReaderTheme)
      : DEFAULT_READER_SETTINGS.theme
    return { fontSize, lineHeight, theme }
  } catch {
    return { ...DEFAULT_READER_SETTINGS }
  }
}
