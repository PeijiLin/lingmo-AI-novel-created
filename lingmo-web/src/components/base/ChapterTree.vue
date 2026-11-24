<script setup lang="ts">
/**
 * 章节树（创作台左栏）：章节列表 / 当前章高亮 / 增删改选
 * 对话框（重命名 / 删除确认）由父级承载，本组件只发事件。
 */
import { computed } from 'vue'
import { FileText, MoreHorizontal, Plus } from 'lucide-vue-next'
import { Button, DropdownMenu, DropdownItem } from '@/components/ui'
import { cn } from '@/utils/cn'
import type { ChapterDraft } from '@/api/types'

const props = defineProps<{
  chapters: ChapterDraft[]
  currentId?: number
  loading?: boolean
}>()

const emit = defineEmits<{
  select: [chapter: ChapterDraft]
  create: []
  rename: [chapter: ChapterDraft]
}>()

const sorted = computed(() =>
  [...props.chapters].sort((a, b) => (a.number ?? 0) - (b.number ?? 0)),
)
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <header class="flex items-center justify-between gap-2 px-4 pt-4 pb-2">
      <h2 class="font-book text-sm font-semibold text-ink">章节</h2>
      <Button variant="ghost" size="icon-sm" title="新增章节" aria-label="新增章节" :loading="loading" @click="emit('create')">
        <Plus class="size-4" />
      </Button>
    </header>

    <nav class="min-h-0 flex-1 overflow-y-auto px-2 pb-4" aria-label="章节列表">
      <p v-if="loading" class="px-2 py-3 text-xs text-mist">章节装载中…</p>
      <p v-else-if="sorted.length === 0" class="px-3 py-4 text-xs leading-relaxed text-mist">
        尚无章节。点击右上「+」落下第一笔。
      </p>
      <ul v-else class="space-y-0.5">
        <li
          v-for="chapter in sorted"
          :key="chapter.id"
          class="group/chapter relative"
        >
          <button
            type="button"
            :class="cn(
              'flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-left transition-colors duration-150 ease-[var(--ease-paper)]',
              'hover:bg-paper focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40',
              currentId === chapter.id ? 'bg-ink-indigo/8 text-ink-indigo' : 'text-ink-secondary',
            )"
            @click="emit('select', chapter)"
          >
            <FileText class="size-3.5 shrink-0 opacity-70" />
            <span class="min-w-0 flex-1 truncate text-[13px]">{{ chapter.title || '未命名章节' }}</span>
            <span v-if="chapter.wordCount" class="shrink-0 text-[10px] text-mist">{{ chapter.wordCount }}字</span>
          </button>

          <div class="absolute top-1.5 right-1 opacity-0 transition-opacity duration-150 group-hover/chapter:opacity-100">
            <DropdownMenu>
              <template #trigger>
                <Button variant="ghost" size="icon-sm" aria-label="章节操作">
                  <MoreHorizontal class="size-3.5" />
                </Button>
              </template>
              <DropdownItem @select="emit('rename', chapter)">重命名</DropdownItem>
            </DropdownMenu>
          </div>
        </li>
      </ul>
    </nav>
  </div>
</template>
