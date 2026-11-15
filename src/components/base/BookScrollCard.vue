<script setup lang="ts">
/**
 * 书卷卡片（规范 3.3 纸面陈列）：书封 + 书名 + 作者 + 概要，
 * 成果以「书摊陈列」呈现而非数据表格；无封面时以竖排书名生成纸面书封。
 */
import { computed } from 'vue'
import { BookText } from 'lucide-vue-next'
import { cn } from '@/utils/cn'
import type { BookInfoVo } from '@/api/types'

const props = defineProps<{
  book: BookInfoVo
  /** 可点击（跳详情/创作台） */
  interactive?: boolean
}>()

const emit = defineEmits<{
  open: [book: BookInfoVo]
}>()

const hasCover = computed(() => !!props.book.picUrl)

const metaParts = computed(() => {
  const parts: string[] = []
  if (props.book.categoryName) parts.push(props.book.categoryName)
  if (props.book.wordCount) parts.push(`${props.book.wordCount} 字`)
  if (props.book.bookStatus === 1) parts.push('已完结')
  return parts
})
</script>

<template>
  <component
    :is="interactive === false ? 'div' : 'button'"
    :type="interactive === false ? undefined : 'button'"
    :class="cn(
      'group flex w-full flex-col overflow-hidden rounded-lg border border-mist-line bg-paper-surface text-left shadow-card',
      interactive !== false && 'cursor-pointer transition-shadow duration-200 ease-[var(--ease-paper)] hover:shadow-card-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40',
    )"
    @click="interactive !== false && emit('open', book)"
  >
    <!-- 书封 -->
    <div class="relative aspect-[3/4] w-full overflow-hidden bg-paper">
      <img
        v-if="hasCover"
        :src="book.picUrl"
        :alt="book.bookName"
        class="size-full object-cover transition-transform duration-300 ease-[var(--ease-paper)] group-hover:scale-[1.02]"
        loading="lazy"
      />
      <!-- 纸面生成书封：竖排书名 + 朱印 -->
      <div v-else class="flex size-full flex-col items-center justify-center gap-3 px-4">
        <span class="cinnabar-seal flex size-8 items-center justify-center rounded-sm bg-cinnabar/90 font-book text-sm font-bold text-paper-surface" aria-hidden="true">书</span>
        <span class="vertical-title max-h-[70%] overflow-hidden font-book text-base leading-6 font-semibold text-ink" :title="book.bookName">
          {{ book.bookName }}
        </span>
      </div>
    </div>

    <!-- 书名与元信息 -->
    <div class="flex flex-col gap-1 border-t border-mist-line px-3 py-2.5">
      <h3 class="truncate font-book text-sm font-semibold text-ink group-hover:text-ink-indigo">
        {{ book.bookName }}
      </h3>
      <p class="flex items-center gap-1 truncate text-[11px] text-ink-secondary">
        <BookText class="size-3 shrink-0 opacity-60" />
        {{ book.authorName || '佚名' }}
      </p>
      <p v-if="metaParts.length" class="truncate text-[10px] text-mist">{{ metaParts.join(' · ') }}</p>
      <p v-if="book.bookDesc" class="mt-0.5 line-clamp-2 text-[11px] leading-4 text-ink-secondary">
        {{ book.bookDesc }}
      </p>
    </div>
  </component>
</template>

<style scoped>
.vertical-title {
  writing-mode: vertical-rl;
  letter-spacing: 0.15em;
}
</style>
