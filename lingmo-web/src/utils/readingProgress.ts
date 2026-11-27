/** 阅读进度：按 bookId + chapterId 记录章节内滚动位置，兼容旧格式。 */

export interface ReadingEntry {
  chapterId: number
  scrollY: number
  updatedAt: number
}

/** key 形如 `${bookId}:${chapterId}`，保留每章各自的位置。 */
export type ReadingProgressMap = Record<string, ReadingEntry>

export const READ_PROGRESS_KEY = 'novel_read_progress'

export function progressKey(bookId: number, chapterId: number): string {
  return `${bookId}:${chapterId}`
}

function parseEntry(value: unknown, fallbackChapterId: number): ReadingEntry | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const entry = value as Record<string, unknown>
  const chapterId =
    typeof entry.chapterId === 'number' && Number.isFinite(entry.chapterId) && entry.chapterId > 0
      ? entry.chapterId
      : fallbackChapterId
  if (!chapterId) return null
  const scrollY =
    typeof entry.scrollY === 'number' && Number.isFinite(entry.scrollY) && entry.scrollY > 0
      ? entry.scrollY
      : 0
  const updatedAt =
    typeof entry.updatedAt === 'number' && Number.isFinite(entry.updatedAt) ? entry.updatedAt : 0
  return { chapterId, scrollY, updatedAt }
}

export function parseReadingProgress(raw: string | null | undefined): ReadingProgressMap {
  try {
    const parsed: unknown = JSON.parse(raw ?? '')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    const result: ReadingProgressMap = {}
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      const parts = key.split(':')
      const keyBookId = Number(parts[0])
      if (!Number.isInteger(keyBookId) || keyBookId <= 0) continue
      if (parts.length === 2) {
        const keyChapterId = Number(parts[1])
        if (!Number.isInteger(keyChapterId) || keyChapterId <= 0) continue
        const entry = parseEntry(value, keyChapterId)
        if (entry) result[key] = entry
        continue
      }
      // 旧格式 1：bookId -> chapterId
      if (typeof value === 'number' && Number.isInteger(value) && value > 0) {
        result[progressKey(keyBookId, value)] = { chapterId: value, scrollY: 0, updatedAt: 0 }
        continue
      }
      // 旧格式 2：bookId -> { chapterId, scrollY, updatedAt }
      const entry = parseEntry(value, 0)
      if (entry) result[progressKey(keyBookId, entry.chapterId)] = entry
    }
    return result
  } catch {
    return {}
  }
}

export function getEntry(
  map: ReadingProgressMap,
  bookId: number,
  chapterId: number,
): ReadingEntry | undefined {
  return map[progressKey(bookId, chapterId)]
}

export function getLatestEntry(
  map: ReadingProgressMap,
  bookId: number,
): ReadingEntry | undefined {
  const prefix = `${bookId}:`
  const entries = Object.entries(map)
    .filter(([key]) => key.startsWith(prefix))
    .map(([, entry]) => entry)
  if (!entries.length) return undefined
  return entries.reduce((latest, entry) => (entry.updatedAt > latest.updatedAt ? entry : latest))
}

export function setEntry(
  map: ReadingProgressMap,
  bookId: number,
  entry: ReadingEntry,
): ReadingProgressMap {
  return { ...map, [progressKey(bookId, entry.chapterId)]: entry }
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
