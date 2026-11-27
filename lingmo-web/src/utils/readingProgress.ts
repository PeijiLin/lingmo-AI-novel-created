/** 阅读进度：按 bookId 记录最近章节与章节内滚动位置，兼容旧的数字格式。 */

export interface ReadingEntry {
  chapterId: number
  scrollY: number
  updatedAt: number
}

export type ReadingProgressMap = Record<number, ReadingEntry>

export const READ_PROGRESS_KEY = 'novel_read_progress'

export function parseReadingProgress(raw: string | null | undefined): ReadingProgressMap {
  try {
    const parsed: unknown = JSON.parse(raw ?? '')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    const result: ReadingProgressMap = {}
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      const bookId = Number(key)
      if (!Number.isInteger(bookId) || bookId <= 0) continue
      // 旧格式：值直接是章节 id
      if (typeof value === 'number' && Number.isInteger(value) && value > 0) {
        result[bookId] = { chapterId: value, scrollY: 0, updatedAt: 0 }
        continue
      }
      if (!value || typeof value !== 'object') continue
      const entry = value as Record<string, unknown>
      const chapterId =
        typeof entry.chapterId === 'number' && Number.isFinite(entry.chapterId) && entry.chapterId > 0
          ? entry.chapterId
          : 0
      if (!chapterId) continue
      const scrollY =
        typeof entry.scrollY === 'number' && Number.isFinite(entry.scrollY) && entry.scrollY > 0
          ? entry.scrollY
          : 0
      const updatedAt =
        typeof entry.updatedAt === 'number' && Number.isFinite(entry.updatedAt)
          ? entry.updatedAt
          : 0
      result[bookId] = { chapterId, scrollY, updatedAt }
    }
    return result
  } catch {
    return {}
  }
}

export function clampScrollY(scrollY: number, max: number): number {
  if (!Number.isFinite(scrollY) || scrollY <= 0) return 0
  if (!Number.isFinite(max) || max <= 0) return 0
  return Math.min(scrollY, max)
}

export function makeReadingEntry(chapterId: number, scrollY: number, now = Date.now()): ReadingEntry {
  return {
    chapterId,
    scrollY: clampScrollY(scrollY, Number.MAX_SAFE_INTEGER),
    updatedAt: now,
  }
}

export function resolveChapterId(
  entry: ReadingEntry | undefined,
  chapterIds: number[],
): number | undefined {
  if (!entry) return undefined
  return chapterIds.includes(entry.chapterId) ? entry.chapterId : undefined
}
