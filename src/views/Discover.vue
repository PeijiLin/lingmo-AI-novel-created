<script setup lang="ts">
/**
 * 发现（规范 4.2 展示：分类书目纸面陈列）
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshCw } from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Button, Skeleton } from '@/components/ui'
import BookScrollCard from '@/components/base/BookScrollCard.vue'
import { useToast } from '@/composables/useToast'
import { listCategories, pageBooks } from '@/api/books'
import type { BookCategoryVo, BookInfoVo } from '@/api/types'

const router = useRouter()
const toast = useToast()

const categories = ref<BookCategoryVo[]>([])
const activeCategory = ref<number | null>(null) // null = 全部
const books = ref<BookInfoVo[]>([])
const loading = ref(false)

async function loadCategories() {
  try {
    const map = await listCategories()
    const all: BookCategoryVo[] = Object.values(map ?? {}).flat()
    categories.value = all
  } catch {
    // 分类加载失败不阻塞书目
  }
}

async function loadBooks() {
  loading.value = true
  try {
    const page = await pageBooks({
      pageNum: 1,
      pageSize: 24,
      categoryId: activeCategory.value ?? undefined,
    })
    books.value = page.records ?? []
  } catch (err) {
    toast.error('书目加载失败', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

function pickCategory(id: number | null) {
  activeCategory.value = id
  loadBooks()
}

function tabClass(active: boolean): string {
  return [
    'inline-flex h-8 items-center rounded-md px-3 text-[13px] font-medium transition-colors duration-150 ease-[var(--ease-paper)]',
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40',
    active ? 'bg-ink-indigo/10 text-ink-indigo' : 'text-ink-secondary hover:bg-paper hover:text-ink',
  ].join(' ')
}

function openBook(book: BookInfoVo) {
  if (book.id) router.push(`/book/${book.id}`)
}

onMounted(async () => {
  await loadCategories()
  await loadBooks()
})
</script>

<template>
  <AppShell>
    <div class="flex items-center justify-between gap-3">
      <div>
        <h1 class="font-book text-xl font-semibold text-ink">发现</h1>
        <p class="mt-1 text-xs text-ink-secondary">在别人的作品里，找到下一部要写的理由。</p>
      </div>
      <Button variant="ghost" size="sm" :loading="loading" @click="loadBooks">
        <RefreshCw class="size-3.5" />
        刷新
      </Button>
    </div>

    <!-- 分类筛选 -->
    <nav class="mt-5 flex flex-wrap items-center gap-2" aria-label="分类筛选">
      <button
        type="button"
        :class="tabClass(activeCategory === null)"
        @click="pickCategory(null)"
      >
        全部
      </button>
      <button
        v-for="cat in categories"
        :key="cat.id"
        type="button"
        :class="tabClass(activeCategory === cat.id)"
        @click="pickCategory(cat.id ?? null)"
      >
        {{ cat.categoryName }}
      </button>
    </nav>

    <!-- 书卷陈列 -->
    <section class="mt-6" aria-label="书目">
      <div v-if="loading" class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
        <div v-for="i in 12" :key="i" class="space-y-2">
          <Skeleton class="aspect-[3/4] w-full rounded-lg" />
          <Skeleton class="h-4 w-3/4" />
        </div>
      </div>

      <p v-else-if="books.length === 0" class="py-16 text-center text-sm text-ink-secondary">
        暂无书目，快去创作一部吧。
      </p>

      <div v-else class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
        <BookScrollCard v-for="book in books" :key="book.id" :book="book" @open="openBook" />
      </div>
    </section>
  </AppShell>
</template>