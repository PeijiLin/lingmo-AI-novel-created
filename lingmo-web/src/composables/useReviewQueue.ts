import { ref } from 'vue'
import { getReviewOverview, resolveReviewItem } from '@/api/governance'
import type { ReviewOverview, ReviewType } from '@/api/types'

const CACHE_MS = 60_000

/** 人审队列：四类 counts + 首屏数据，仲裁后自动刷新 */
export function useReviewQueue(bookId: number) {
  const overview = ref<ReviewOverview | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const deciding = ref(false)
  let cachedAt = 0

  async function load(force = false) {
    if (!force && cachedAt && Date.now() - cachedAt < CACHE_MS && overview.value) return
    loading.value = true
    error.value = null
    try {
      overview.value = await getReviewOverview(bookId)
      cachedAt = Date.now()
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err)
    } finally {
      loading.value = false
    }
  }

  /** accept=true 通过/确认，false 驳回；note 可空 */
  async function decide(type: ReviewType, id: number, accept: boolean, note?: string) {
    deciding.value = true
    try {
      await resolveReviewItem(bookId, type, id, { accept, note: note?.trim() || undefined })
      await load(true)
    } finally {
      deciding.value = false
    }
  }

  return { overview, loading, error, deciding, load, decide }
}
