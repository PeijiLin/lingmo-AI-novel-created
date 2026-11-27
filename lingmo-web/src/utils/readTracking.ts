/** 阅读量上报去重：同一章节在同一会话内只上报一次。 */

export const READ_REPORTED_KEY = 'novel_read_reported'

export type ReportedMap = Record<number, true>

export function parseReported(raw: string | null | undefined): ReportedMap {
  try {
    const parsed: unknown = JSON.parse(raw ?? '')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    const result: ReportedMap = {}
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      const chapterId = Number(key)
      if (Number.isInteger(chapterId) && chapterId > 0 && value === true) result[chapterId] = true
    }
    return result
  } catch {
    return {}
  }
}

export function shouldReport(reported: ReportedMap, chapterId: number): boolean {
  return Number.isInteger(chapterId) && chapterId > 0 && !reported[chapterId]
}

export function markReported(reported: ReportedMap, chapterId: number): ReportedMap {
  if (!shouldReport(reported, chapterId)) return reported
  return { ...reported, [chapterId]: true }
}
