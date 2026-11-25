<script setup lang="ts">
/**
 * 我的书架（规范 4.2：藏书 + 阅读进度）
 * 书目来自后端 GET /bookshelf/list；阅读进度仍叠加 localStorage（novel_read_progress）。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshCw } from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Button, Skeleton } from '@/components/ui'
import BookScrollCard from '@/components/base/BookScrollCard.vue'
import { useToast } from '@/composables/useToast'
import { listShelfBooks } from '@/api/shelf'
import type { BookInfoVo } from '@/api/types'

const router = useRouter()
const toast = useToast()

const books = ref<BookInfoVo[]>([])
const loading = ref(false)

const readProgress = ref<Record<number, number>>({})

function loadProgress() {
  try {
    readProgress.value = JSON.parse(localStorage.getItem('novel_read_progress') || '{}')
  } catch {
    readProgress.value = {}
  }
}

async function loadBooks() {
  loading.value = true
  try {
    const page = await listShelfBooks(1, 24)
    books.value = page.records ?? []
  } catch (err) {
    toast.error('书架加载失败', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

function open(book: BookInfoVo) {
  if (book.id) router.push(`/book/${book.id}`)
}

const readingCount = computed(
  () => Object.values(readProgress.value).filter((id) => !!id).length,
)

onMounted(() => {
  loadProgress()
  loadBooks()
})
</script>

<template>
  <AppShell>
    <div class="flex items-center justify-between gap-3">
      <div>
        <h1 class="font-book text-xl font-semibold text-ink">我的书架</h1>
        <p class="mt-1 text-xs text-ink-secondary">
          {{ readingCount ? `在读 ${readingCount} 本 · ` : '' }}在读的书，会留下墨痕进度。
        </p>
      </div>
      <Button variant="ghost" size="sm" :loading="loading" @click="loadBooks">
        <RefreshCw class="size-3.5" />
        刷新
      </Button>
    </div>

    <section class="mt-6" aria-label="书架">
      <div v-if="loading" class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
        <div v-for="i in 8" :key="i" class="space-y-2">
          <Skeleton class="aspect-[3/4] w-full rounded-lg" />
          <Skeleton class="h-4 w-3/4" />
        </div>
      </div>
      <p v-else-if="books.length === 0" class="py-16 text-center text-sm text-ink-secondary">
        书架上还没有书，去发现页找一本吧。
      </p>
      <div v-else class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
        <BookScrollCard
          v-for="book in books"
          :key="book.id"
          :book="book"
          @open="open"
        />
      </div>
    </section>
  </AppShell>
</template>