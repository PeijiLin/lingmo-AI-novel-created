<script setup lang="ts">
/**
 * 作品详情（规范 4.2 阅读入口：封面 + 目录 + 开始阅读 + 参与创作）
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOpen, Feather, Plus } from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Button, Skeleton } from '@/components/ui'
import { useToast } from '@/composables/useToast'
import { addToBookshelf } from '@/api/shelf'
import { getBookInfo, pageChapters } from '@/api/books'
import type { BookChapterVo, BookInfoVo } from '@/api/types'
import {
  getLatestEntry,
  parseReadingProgress,
  READ_PROGRESS_KEY,
  resolveChapterId,
} from '@/utils/readingProgress'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const bookId = Number(route.params.id)
const book = ref<BookInfoVo | null>(null)
const chapters = ref<BookChapterVo[]>([])
const loading = ref(true)
const resumeChapterId = ref<number | undefined>(undefined)

function readProgress(): string | null {
  try {
    return localStorage.getItem(READ_PROGRESS_KEY)
  } catch {
    return null
  }
}

async function load() {
  loading.value = true
  try {
    book.value = await getBookInfo(bookId)
    const page = await pageChapters({ pageNum: 1, pageSize: 200, bookInfoId: bookId })
    chapters.value = page.records ?? []
    const ids = chapters.value.map((c) => c.id).filter((id): id is number => !!id)
    resumeChapterId.value = resolveChapterId(
      getLatestEntry(parseReadingProgress(readProgress()), bookId),
      ids,
    )
  } catch (err) {
    toast.error('作品加载失败', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

function startReading(chapterId?: number) {
  router.push(chapterId ? `/reader/${bookId}?chapterId=${chapterId}` : `/reader/${bookId}`)
}

async function joinShelf() {
  try {
    await addToBookshelf({ bookId, userId: undefined })
    toast.success('已加入书架')
  } catch (err) {
    toast.error('加入书架失败', err instanceof Error ? err.message : undefined)
  }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <div v-if="loading" class="space-y-4">
      <Skeleton class="h-56 w-full rounded-lg" />
      <Skeleton class="h-8 w-1/3" />
      <Skeleton class="h-4 w-2/3" />
    </div>

    <div v-else-if="book" class="flex flex-col gap-8 md:flex-row">
      <!-- 封面 -->
      <div class="shrink-0">
        <img
          v-if="book.picUrl"
          :src="book.picUrl"
          :alt="book.bookName"
          class="aspect-[3/4] w-44 rounded-lg border border-mist-line object-cover shadow-card md:w-52"
        />
        <div v-else class="flex aspect-[3/4] w-44 flex-col items-center justify-center gap-3 rounded-lg border border-mist-line bg-paper-surface shadow-card md:w-52">
          <span class="flex size-9 items-center justify-center rounded-sm bg-cinnabar/90 font-book text-sm font-bold text-paper-surface">书</span>
          <span class="vertical-title px-2 text-center font-book text-sm leading-5 font-semibold text-ink">{{ book.bookName }}</span>
        </div>
      </div>

      <!-- 信息 -->
      <div class="min-w-0 flex-1">
        <h1 class="font-book text-2xl font-semibold text-ink">{{ book.bookName }}</h1>
        <p class="mt-1 text-sm text-ink-secondary">{{ book.authorName || '佚名' }}</p>
        <p class="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-mist">
          <span v-if="book.workDirection?.trim()">{{ book.workDirection.trim() }}</span>
          <span v-if="book.categoryName">{{ book.categoryName }}</span>
          <span v-if="book.wordCount">{{ book.wordCount }} 字</span>
          <span v-if="book.visitCount">{{ book.visitCount }} 阅读</span>
          <span v-if="book.bookStatus === 1">已完结</span>
        </p>
        <p v-if="book.bookDesc" class="mt-4 max-w-xl text-sm leading-relaxed text-ink-secondary">{{ book.bookDesc }}</p>

        <div class="mt-6 flex flex-wrap items-center gap-3">
          <Button
            variant="cinnabar"
            size="lg"
            @click="startReading(resumeChapterId && resumeChapterId !== chapters[0]?.id ? resumeChapterId : chapters[0]?.id)"
          >
            <BookOpen class="size-4" />
            {{ chapters.length ? (resumeChapterId && resumeChapterId !== chapters[0]?.id ? '继续阅读' : '开始阅读') : '尚无章节' }}
          </Button>
          <Button variant="outline" @click="joinShelf">
            <Plus class="size-4" />
            加入书架
          </Button>
          <RouterLink :to="`/creator/${bookId}`">
            <Button variant="ghost">
              <Feather class="size-4 text-cinnabar" />
              参与创作
            </Button>
          </RouterLink>
        </div>
      </div>
    </div>

    <div v-else class="py-16 text-center text-sm text-ink-secondary">作品不存在或已被下架。</div>

    <!-- 目录 -->
    <section v-if="chapters.length" class="mt-10" aria-label="目录">
      <h2 class="font-book text-base font-semibold text-ink">目录</h2>
      <ul class="mt-3 grid gap-px overflow-hidden rounded-lg border border-mist-line bg-mist-line">
        <li v-for="ch in chapters" :key="ch.id" class="bg-paper-surface">
          <button
            type="button"
            class="flex w-full items-center justify-between px-4 py-2.5 text-left transition-colors duration-150 ease-[var(--ease-paper)] hover:bg-paper focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ink-indigo/40"
            @click="startReading(ch.id)"
          >
            <span class="truncate text-[13px] text-ink">{{ ch.chapterName }}</span>
            <span v-if="ch.wordCount" class="shrink-0 pl-3 text-[10px] text-mist">{{ ch.wordCount }}字</span>
          </button>
        </li>
      </ul>
    </section>
  </AppShell>
</template>

<style scoped>
.vertical-title {
  writing-mode: vertical-rl;
  letter-spacing: 0.15em;
}
</style>