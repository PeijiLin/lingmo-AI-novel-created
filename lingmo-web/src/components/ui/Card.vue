<script setup lang="ts">
import { cn } from '@/utils/cn'

// interactive 缺省 false（Vue 布尔转型），显式声明以便读侧不依赖隐式转型
withDefaults(
  defineProps<{
    /** paper：纸面白卡 + 投影（默认陈列卡）；plain：无底色面板容器 */
    variant?: 'paper' | 'plain'
    /** 可交互卡片（有悬停与指针） */
    interactive?: boolean
  }>(),
  { variant: 'paper', interactive: false },
)
</script>

<template>
  <div
    :class="cn(
      'rounded-lg',
      variant === 'plain' ? '' : 'bg-paper-surface border border-mist-line shadow-card',
      interactive && 'cursor-pointer transition-shadow duration-200 ease-[var(--ease-paper)] hover:shadow-card-hover',
    )"
  >
    <!-- 头部：标题 / 描述 / 右侧动作 -->
    <header
      v-if="$slots.header || $slots.title || $slots.action"
      class="flex items-start justify-between gap-3 px-5 pt-4 pb-3"
    >
      <div class="min-w-0">
        <div class="font-book text-base font-semibold text-ink"><slot name="title" /></div>
        <p v-if="$slots.description" class="mt-0.5 text-xs leading-relaxed text-ink-secondary">
          <slot name="description" />
        </p>
      </div>
      <div v-if="$slots.action" class="shrink-0"><slot name="action" /></div>
    </header>
    <div class="px-5 pb-5" :class="cn($slots.header || $slots.title ? 'pt-1' : 'pt-5')">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="border-t border-mist-line px-5 py-3">
      <slot name="footer" />
    </footer>
  </div>
</template>
