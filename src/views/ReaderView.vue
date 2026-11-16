<script setup lang="ts">
/**
 * 阅读器（规范 3.2 / 4.2：窄栏衬线、墨线进度、翻页、阅读+1）
 * 墨线晕染：当前章节如墨滴晕开，读完留淡墨痕（与朱批「一红一墨」对偶）。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, BookOpen, List } from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Button, Sheet } from '@/components/ui'
import { useToast } from '@/composables/useToast'
import { getBookInfo, getChapterContent, pageChapters, reportChapterRead } from '@/api/books'
import type { BookChapterVo, BookInfoVo } from '@/api/types'
import { cn } from '@/utils/cn'

const route = useRoute()
const toast = useToast()

const bookId = Number(route.params.id)
const requestedChapterId = Number(route.query.chapterId) || undefined

const book = ref<BookInfoVo | null>(null)
const chapters = ref<BookChapterVo[]>([])
const current = ref<BookChapterVo | null>(null)
const content = ref('')
const loading = ref(false)
const dirOpen = ref(false)
const progress = ref(0)

const idx = computed(() => chapters.value.findIndex((c) => c.id === current.value?.id))
const hasPrev = computed(() => idx.value > 0)
const hasNext = computed(() => idx.value >= 0 && idx.value < chapters.value.length - 1)

async function openChapter(chapter: BookChapterVo) {
  current.value = chapter
  content.value = ''
  loading.value = true
  try {
    const data = await getChapterContent(chapter.id!)
    content.value = data.content ?? ''
    // 阅读量 +1 上报（后端接口已就绪；失败静默降级，不阻塞阅读）
    reportChapterRead(chapter.id!, bookId).catch(() => undefined)
    // 记录本地阅读进度
    try {
      const shelf = JSON.parse(localStorage.getItem('novel_read_progress') || '{}')
      shelf[bookId] = chapter.id
      localStorage.setItem('novel_read_progress', JSON.stringify(shelf))
    } catch {
      /* ignore */
    }
  } catch (err) {
    toast.error('章节加载失败', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

function go(delta: number) {
  const next = chapters.value[idx.value + delta]
  if (next) openChapter(next)
}

function onScroll() {
  const el = document.documentElement
  const max = el.scrollHeight - el.clientHeight
  progress.value = max > 0 ? Math.min(1, window.scrollY / max) : 0
}

async function load() {
  loading.value = true
  try {
    book.value = await getBookInfo(bookId)
    const page = await pageChapters({ pageNum: 1, pageSize: 500, bookInfoId: bookId })
    chapters.value = (page.records ?? []).sort((a, b) => (a.chapterNum ?? 0) - (b.chapterNum ?? 0))
    const target =
      chapters.value.find((c) => c.id === requestedChapterId) ?? chapters.value[0] ?? null
    if (target) await openChapter(target)
  } catch (err) {
    toast.error('阅读页加载失败', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  load()
})

onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <AppShell>
    <!-- 墨线进度（顶部，随滚动晕染） -->
    <div class="pointer-events-none fixed inset-x-0 top-0 z-40 h-0.5 bg-mist-line">
      <div
        class="h-full bg-ink-indigo transition-[width] duration-150 ease-[var(--ease-paper)]"
        :style="{ width: `${progress * 100}%` }"
      />
    </div>

    <!-- 阅读栏 -->
    <div class="mx-auto max-w-2xl px-6 py-8 sm:px-8">
      <div class="flex items-center justify-between">
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded-sm text-xs text-ink-secondary transition-colors hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
          @click="dirOpen = true"
        >
          <List class="size-3.5" />
          目录
        </button>
        <h2 class="min-w-0 flex-1 truncate text-center font-book text-base font-semibold text-ink">
          {{ book?.bookName }}{{ current ? ` · ${current.chapterName}` : '' }}
        </h2>
        <span class="w-12" />
      </div>

      <!-- 正文：窄栏衬线 -->
      <article v-if="loading && !content" class="mt-10 space-y-4 font-read">
        <div v-for="i in 8" :key="i" class="h-4 animate-pulse rounded bg-mist-line/60" />
      </article>

      <article v-else class="mt-10 font-read text-[17px] leading-8 text-ink">
        <h1 class="mb-6 text-center font-book text-2xl font-semibold">{{ current?.chapterName }}</h1>
        <div class="whitespace-pre-wrap text-justify">{{ content }}</div>
      </article>

      <p v-if="!loading && chapters.length === 0" class="py-20 text-center text-sm text-ink-secondary">
        这本书还没有可读章节。
      </p>

      <!-- 翻页 -->
      <nav v-if="chapters.length" class="mt-12 flex items-center justify-between gap-4 border-t border-mist-line pt-6">
        <Button variant="outline" :disabled="!hasPrev" @click="go(-1)">
          <ArrowLeft class="size-4" />
          上一章
        </Button>
        <span class="text-xs text-mist">{{ idx + 1 }} / {{ chapters.length }}</span>
        <Button variant="outline" :disabled="!hasNext" @click="go(1)">
          下一章
          <ArrowRight class="size-4" />
        </Button>
      </nav>
    </div>

    <!-- 目录抽屉 -->
    <Sheet v-model:open="dirOpen" title="目录" width-class="w-[min(92vw,20rem)]">
      <ul class="space-y-0.5">
        <li v-for="ch in chapters" :key="ch.id">
          <button
            type="button"
            :class="cn(
              'flex w-full items-center justify-between rounded-md px-2.5 py-2 text-left text-[13px] transition-colors duration-150 ease-[var(--ease-paper)]',
              'hover:bg-paper focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40',
              current?.id === ch.id ? 'bg-ink-indigo/8 text-ink-indigo' : 'text-ink-secondary',
            )"
            @click="dirOpen = false; openChapter(ch)"
          >
            <span class="truncate">{{ ch.chapterName }}</span>
            <BookOpen v-if="current?.id === ch.id" class="size-3.5 shrink-0 text-cinnabar" />
          </button>
        </li>
      </ul>
    </Sheet>
  </AppShell>
</template>