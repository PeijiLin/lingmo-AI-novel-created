import type { BookInfoVo } from '@/api/types'

/** 榜单响应清洗与阅读量展示格式化。 */

export function clampRankLimit(value: number): 20 | 50 {
  return value === 50 ? 50 : 20
}

export function sanitizeRanking(data: unknown): BookInfoVo[] {
  if (!Array.isArray(data)) return []
  return data.filter(
    (item): item is BookInfoVo =>
      !!item && typeof item === 'object' && typeof (item as BookInfoVo).id === 'number',
  )
}

export function formatVisitCount(value: number | null | undefined): string {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return '0'
  if (value < 10000) return String(value)
  return `${(value / 10000).toFixed(1).replace(/\.0$/, '')}万`
}
