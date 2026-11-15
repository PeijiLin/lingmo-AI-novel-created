<script setup lang="ts">
/**
 * 排行榜（规范 4.2：按阅读量轻量榜单）
 * 后端榜单接口未就绪时，客户端拉取书目按 visitCount 降序兜底（规范 6）。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshCw } from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Button, Skeleton } from '@/components/ui'
import { useToast } from '@/composables/useToast'
import { listRanking, pageBooks } from '@/api/books'
import type { BookInfoVo } from '@/api/types'

const router = useRouter()
const toast = useToast()

const rankList = ref<BookInfoVo[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    // 直连预留榜单端点；失败回退分页 + 客户端按阅读量排序
    try {
      const data = await listRanking(20)
      if (Array.isArray(data) && data.length > 0) {
        rankList.value = data
        return
      }
    } catch {
      /* 回退 */
    }
    const page = await pageBooks({ pageNum: 1, pageSize: 50 })
    rankList.value = (page.records ?? [])
      .filter((b) => (b.visitCount ?? 0) > 0)
      .sort((a, b) => (b.visitCount ?? 0) - (a.visitCount ?? 0))
      .slice(0, 20)
  } catch (err) {
    toast.error('榜单加载失败', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

function open(book: BookInfoVo) {
  if (book.id) router.push(`/book/${book.id}`)
}

onMounted(load)

const medals = ['text-cinnabar', 'text-ink-indigo', 'text-mist']
function rankColor(i: number) {
  return i < 3 ? medals[i] : 'text-ink-secondary'
}
</script>

<template>
  <AppShell>
    <div class="flex items-center justify-between gap-3">
      <div>
        <h1 class="font-book text-xl font-semibold text-ink">排行</h1>
        <p class="mt-1 text-xs text-ink-secondary">按阅读量排名的热度榜。</p>
      </div>
      <Button variant="ghost" size="sm" :loading="loading" @click="load">
        <RefreshCw class="size-3.5" />
        刷新
      </Button>
    </div>

    <section class="mt-6" aria-label="排行榜">
      <div v-if="loading" class="space-y-3">
        <Skeleton v-for="i in 8" :key="i" class="h-16 rounded-md" />
      </div>
      <p v-else-if="rankList.length === 0" class="py-16 text-center text-sm text-ink-secondary">
        暂无上榜作品。
      </p>

      <ol v-else class="divide-y divide-mist-line rounded-lg border border-mist-line bg-paper-surface shadow-card">
        <li v-for="(book, i) in rankList" :key="book.id">
          <button
            type="button"
            class="flex w-full items-center gap-4 px-4 py-3 text-left transition-colors duration-150 ease-[var(--ease-paper)] hover:bg-paper focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
            @click="open(book)"
          >
            <span
              class="w-6 shrink-0 text-center font-book text-lg font-bold"
              :class="rankColor(i)"
            >
              {{ i + 1 }}
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate font-book text-sm font-semibold text-ink">{{ book.bookName }}</span>
              <span class="block truncate text-[11px] text-ink-secondary">
                {{ book.authorName || '佚名' }}{{ book.categoryName ? ` · ${book.categoryName}` : '' }}
              </span>
            </span>
            <span class="shrink-0 text-right">
              <span class="block text-xs font-medium text-cinnabar">{{ (book.visitCount ?? 0).toLocaleString() }}</span>
              <span class="block text-[10px] text-mist">阅读</span>
            </span>
          </button>
        </li>
      </ol>
    </section>
  </AppShell>
</template>