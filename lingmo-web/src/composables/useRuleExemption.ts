import { ref } from 'vue'
import { addExemption, listDueExemptions, listExemptions, revokeExemption, reviewExemption } from '@/api/governance'
import type { ExemptRequest, NovelRuleExemption } from '@/api/types'

const CACHE_MS = 60_000

/** 规则豁免 / 叙诡保护区：清单、到期复核、新建、调整复核时间、撤销 */
export function useRuleExemption(bookId: number) {
  const items = ref<NovelRuleExemption[]>([])
  const due = ref<NovelRuleExemption[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  let cachedAt = 0

  async function load(force = false) {
    if (!force && cachedAt && Date.now() - cachedAt < CACHE_MS) return
    loading.value = true
    error.value = null
    try {
      const [all, dueList] = await Promise.all([listExemptions(bookId), listDueExemptions(bookId)])
      items.value = all ?? []
      due.value = dueList ?? []
      cachedAt = Date.now()
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err)
    } finally {
      loading.value = false
    }
  }

  async function exempt(request: Omit<ExemptRequest, 'bookId'>) {
    await addExemption({ ...request, bookId })
    await load(true)
  }

  async function review(ruleId: string, reviewAt?: string) {
    await reviewExemption(bookId, ruleId, reviewAt)
    await load(true)
  }

  async function revoke(ruleId: string) {
    await revokeExemption(bookId, ruleId)
    await load(true)
  }

  return { items, due, loading, error, load, exempt, review, revoke }
}
