import { ref } from 'vue'
import { exportCostCsv, getCostSummary } from '@/api/governance'
import type { CostRow } from '@/api/types'

const CACHE_MS = 60_000

/** 成本账本：汇总表 + CSV 导出（按需加载，短时缓存避免 Tab 来回切重复请求） */
export function useCost(bookId: number) {
  const rows = ref<CostRow[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const exporting = ref(false)
  let cachedAt = 0

  async function load(force = false) {
    if (!force && cachedAt && Date.now() - cachedAt < CACHE_MS && rows.value.length) return
    loading.value = true
    error.value = null
    try {
      rows.value = (await getCostSummary(bookId)) ?? []
      cachedAt = Date.now()
    } catch (err) {
      error.value = err instanceof Error ? err.message : String(err)
    } finally {
      loading.value = false
    }
  }

  async function download() {
    exporting.value = true
    try {
      const blob = await exportCostCsv(bookId)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `cost-${bookId}.csv`
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      // 延迟释放，避免 Safari 等在下载尚未开始时 URL 就失效
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } finally {
      exporting.value = false
    }
  }

  return { rows, loading, error, exporting, load, download }
}
