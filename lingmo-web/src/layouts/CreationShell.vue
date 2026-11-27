<script setup lang="ts">
/**
 * 工作台壳（CreationShell）：沉浸式创作布局
 * 顶栏（返回 / 书名 / 保存 / 发布）+ 左章节树 + 中稿纸 + 右流水线面板。
 */
defineProps<{
  /** 书名（顶栏展示） */
  bookTitle?: string
  /** 章节题（顶栏展示） */
  chapterTitle?: string
  /** 专注模式：隐藏左右侧栏，让稿纸独占宽度 */
  focusMode?: boolean
}>()

const emit = defineEmits<{
  save: []
  publish: []
  back: []
}>()
</script>

<template>
  <div class="flex h-screen flex-col overflow-hidden bg-paper">
    <!-- 顶栏 -->
    <header class="flex h-12 shrink-0 items-center gap-3 border-b border-mist-line bg-paper-surface px-3 sm:px-4">
      <button
        type="button"
        class="inline-flex shrink-0 items-center gap-1 rounded-sm px-2 py-1 text-xs text-ink-secondary transition-colors hover:bg-paper hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
        aria-label="返回工作台"
        @click="emit('back')"
      >
        <span aria-hidden="true">←</span>
        <span class="hidden sm:inline">工作台</span>
      </button>
      <div class="min-w-0 flex-1">
        <slot name="title">
          <span class="font-book text-sm font-semibold text-ink">{{ bookTitle || '未命名作品' }}</span>
          <span v-if="chapterTitle" class="ml-2 hidden text-xs text-ink-secondary sm:inline">{{ chapterTitle }}</span>
        </slot>
      </div>
      <slot name="actions" />
    </header>

    <!-- 三栏主体 -->
    <div class="flex min-h-0 flex-1">
      <aside v-if="!focusMode" class="hidden w-60 shrink-0 border-r border-mist-line bg-paper-surface md:flex md:flex-col">
        <slot name="left" />
      </aside>
      <main class="flex min-w-0 flex-1 flex-col">
        <slot />
      </main>
      <aside v-if="!focusMode" class="hidden w-[21rem] shrink-0 border-l border-mist-line bg-paper-surface xl:flex xl:flex-col">
        <slot name="right" />
      </aside>
    </div>
  </div>
</template>
