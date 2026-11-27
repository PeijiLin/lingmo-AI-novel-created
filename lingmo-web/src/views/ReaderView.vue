<script setup lang="ts">
/**
 * 阅读器（规范 3.2 / 4.2：窄栏衬线、墨线进度、翻页、阅读+1）
 * 墨线晕染：当前章节如墨滴晕开，读完留淡墨痕（与朱批「一红一墨」对偶）。
 * 阅读偏好与章节内位置仅存本地，不跨设备同步。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, BookOpen, List, Minus, Plus, Settings2 } from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Button, Sheet } from '@/components/ui'
import { useToast } from '@/composables/useToast'
import { getBookInfo, getChapterContent, pageChapters, reportChapterRead } from '@/api/books'
import type { BookChapterVo, BookInfoVo } from '@/api/types'
import { cn } from '@/utils/cn'
import {
  parseReaderSettings,
  READER_SETTINGS_KEY,
  type ReaderSettings,
  type ReaderTheme,
} from '@/utils/readerSettings'
import {
  clampScrollY,
  getEntry,
  getLatestEntry,
  makeReadingEntry,
  parseReadingProgress,
  READ_PROGRESS_KEY,
  resolveChapterId,
  setEntry,
} from '@/utils/readingProgress'
import {
  markReported,
  parseReported,
  READ_REPORTED_KEY,
  shouldReport,
  type ReportedMap,
} from '@/utils/readTracking'

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

/* ---------------- 阅读偏好 ---------------- */

const settings = ref<ReaderSettings>(parseReaderSettings(readStorage(READER_SETTINGS_KEY)))
const settingsOpen = ref(false)
const lineHeights = [1.6, 1.75, 1.9, 2.1]
const themes: ReaderTheme[] = ['paper', 'sepia', 'night']
const themeLabels: Record<ReaderTheme, string> = { paper: '纸白', sepia: '护眼', night: '夜间' }

const readerStyle = computed(() => ({
  fontSize: `${settings.value.fontSize}px`,
  lineHeight: String(settings.value.lineHeight),
}))

function persistSettings() {
  writeStorage(READER_SETTINGS_KEY, JSON.stringify(settings.value))
}

function stepFontSize(delta: number) {
  const next = Math.min(22, Math.max(16, settings.value.fontSize + delta))
  settings.value = { ...settings.value, fontSize: next }
  persistSettings()
}

function cycleLineHeight() {
  const i = lineHeights.indexOf(settings.value.lineHeight)
  settings.value = { ...settings.value, lineHeight: lineHeights[(i + 1) % lineHeights.length] }
  persistSettings()
}

function setTheme(theme: ReaderTheme) {
  settings.value = { ...settings.value, theme }
  persistSettings()
}

/* ---------------- 进度与阅读量 ---------------- */

const progressMap = ref(parseReadingProgress(readStorage(READ_PROGRESS_KEY)))
const reported = ref<ReportedMap>(parseReported(readStorage(READ_REPORTED_KEY, true)))
const readDelta = ref(0)

function saveProgress(chapterId: number, scrollY: number) {
  progressMap.value = setEntry(progressMap.value, bookId, makeReadingEntry(chapterId, scrollY))
  writeStorage(READ_PROGRESS_KEY, JSON.stringify(progressMap.value))
}

const idx = computed(() => chapters.value.findIndex((c) => c.id === current.value?.id))
const hasPrev = computed(() => idx.value > 0)
const hasNext = computed(() => idx.value >= 0 && idx.value < chapters.value.length - 1)

async function openChapter(chapter: BookChapterVo) {
  const chapterId = chapter.id
  if (!chapterId) return
  const previousId = current.value?.id
  if (previousId && previousId !== chapterId) saveProgress(previousId, window.scrollY)
  current.value = chapter
  content.value = ''
  loading.value = true
  try {
    const data = await getChapterContent(chapterId)
    content.value = data.content ?? ''
    const entry = getEntry(progressMap.value, bookId, chapterId)
    const restoreY = entry ? entry.scrollY : 0
    await nextTick()
    requestAnimationFrame(() => {
      const max = document.documentElement.scrollHeight - document.documentElement.clientHeight
      window.scrollTo({ top: clampScrollY(restoreY, max) })
      onScroll()
    })
    if (shouldReport(reported.value, chapterId)) {
      reported.value = markReported(reported.value, chapterId)
      writeStorage(READ_REPORTED_KEY, JSON.stringify(reported.value), true)
      reportChapterRead(chapterId, bookId)
        .then(() => {
          readDelta.value += 1
        })
        .catch(() => undefined)
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

function onKeydown(e: KeyboardEvent) {
  const target = e.target as HTMLElement | null
  if (target && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))) {
    return
  }
  if (e.key === 'ArrowLeft' && hasPrev.value) {
    e.preventDefault()
    go(-1)
  } else if (e.key === 'ArrowRight' && hasNext.value) {
    e.preventDefault()
    go(1)
  }
}

watch(dirOpen, async (open) => {
  if (!open) return
  await nextTick()
  document
    .querySelector(`[data-chapter-id="${current.value?.id}"]`)
    ?.scrollIntoView({ block: 'nearest' })
})

async function load() {
  loading.value = true
  try {
    book.value = await getBookInfo(bookId)
    const page = await pageChapters({ pageNum: 1, pageSize: 500, bookInfoId: bookId })
    chapters.value = (page.records ?? []).sort((a, b) => (a.chapterNum ?? 0) - (b.chapterNum ?? 0))
    const chapterIds = chapters.value.map((c) => c.id).filter((id): id is number => !!id)
    const savedId = resolveChapterId(getLatestEntry(progressMap.value, bookId), chapterIds)
    const targetId = requestedChapterId ?? savedId
    const target =
      chapters.value.find((c) => c.id === targetId) ?? chapters.value[0] ?? null
    if (target) await openChapter(target)
  } catch (err) {
    toast.error('阅读页加载失败', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

function readStorage(key: string, session = false): string | null {
  try {
    return (session ? sessionStorage : localStorage).getItem(key)
  } catch {
    return null
  }
}

function writeStorage(key: string, value: string, session = false) {
  try {
    (session ? sessionStorage : localStorage).setItem(key, value)
  } catch {
    /* 存储不可用时静默降级 */
  }
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('keydown', onKeydown)
  load()
})

onBeforeUnmount(() => {
  if (current.value?.id) saveProgress(current.value.id, window.scrollY)
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('keydown', onKeydown)
})
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

    <!-- 阅读栏：主题仅作用于本页容器（不含全局与目录抽屉） -->
    <div class="reader-surface" :data-theme="settings.theme">
    <div class="mx-auto max-w-2xl px-6 py-8 sm:px-8">
      <div class="flex items-center justify-between gap-2">
        <button
          type="button"
          class="inline-flex shrink-0 items-center gap-1 rounded-sm text-xs text-ink-secondary transition-colors hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
          @click="dirOpen = true"
        >
          <List class="size-3.5" />
          目录
        </button>
        <h2 class="min-w-0 flex-1 truncate text-center font-book text-base font-semibold text-ink">
          {{ book?.bookName }}{{ current ? ` · ${current.chapterName}` : '' }}
        </h2>
        <div class="flex shrink-0 items-center gap-2">
          <span class="text-[10px] text-mist">{{ (book?.visitCount ?? 0) + readDelta }} 阅读</span>
          <button
            type="button"
            aria-label="阅读设置"
            :aria-expanded="settingsOpen"
            class="inline-flex size-7 items-center justify-center rounded-sm text-ink-secondary transition-colors hover:bg-paper hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
            @click="settingsOpen = !settingsOpen"
          >
            <Settings2 class="size-4" />
          </button>
        </div>
      </div>

      <!-- 阅读偏好面板 -->
      <div
        v-if="settingsOpen"
        class="mt-4 flex flex-wrap items-center gap-x-6 gap-y-3 rounded-lg border border-mist-line bg-paper-surface px-4 py-3 text-xs text-ink-secondary"
      >
        <div class="flex items-center gap-2">
          <span>字号</span>
          <button
            type="button"
            aria-label="减小字号"
            class="inline-flex size-6 items-center justify-center rounded-sm border border-mist-line transition-colors hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
            @click="stepFontSize(-1)"
          >
            <Minus class="size-3" />
          </button>
          <span class="w-5 text-center font-medium text-ink">{{ settings.fontSize }}</span>
          <button
            type="button"
            aria-label="增大字号"
            class="inline-flex size-6 items-center justify-center rounded-sm border border-mist-line transition-colors hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
            @click="stepFontSize(1)"
          >
            <Plus class="size-3" />
          </button>
        </div>
        <button
          type="button"
          class="rounded-sm border border-mist-line px-2 py-1 transition-colors hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
          @click="cycleLineHeight"
        >
          行高 {{ settings.lineHeight }}
        </button>
        <div class="flex items-center gap-1">
          <button
            v-for="theme in themes"
            :key="theme"
            type="button"
            :aria-pressed="settings.theme === theme"
            :class="cn(
              'rounded-sm border px-2 py-1 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40',
              settings.theme === theme
                ? 'border-cinnabar/50 bg-cinnabar-wash text-cinnabar'
                : 'border-mist-line hover:text-ink',
            )"
            @click="setTheme(theme)"
          >
            {{ themeLabels[theme] }}
          </button>
        </div>
      </div>

      <!-- 正文：窄栏衬线；主题仅作用于本地容器 -->
      <div class="mt-10" :style="readerStyle">
        <article v-if="loading && !content" class="space-y-4 font-read">
          <div v-for="i in 8" :key="i" class="h-4 animate-pulse rounded bg-mist-line/60" />
        </article>

        <article v-else class="font-read text-justify text-ink">
          <h1 class="mb-6 text-center font-book text-2xl font-semibold">{{ current?.chapterName }}</h1>
          <div class="whitespace-pre-wrap">{{ content }}</div>
        </article>
      </div>

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
    </div>

    <!-- 目录抽屉 -->
    <Sheet v-model:open="dirOpen" title="目录" width-class="w-[min(92vw,20rem)]">
      <ul class="space-y-0.5">
        <li v-for="ch in chapters" :key="ch.id">
          <button
            type="button"
            :data-chapter-id="ch.id"
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
