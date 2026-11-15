<script setup lang="ts">
import {
  DialogRoot,
  DialogPortal,
  DialogOverlay,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from 'reka-ui'
import { computed } from 'vue'
import { X } from 'lucide-vue-next'
import { cn } from '@/utils/cn'

const props = withDefaults(
  defineProps<{
    open?: boolean
    title?: string
    description?: string
    /** 抽屉滑出方向 */
    side?: 'right' | 'left'
    /** 抽屉宽度类，如 max-w-sm / w-[420px] */
    widthClass?: string
  }>(),
  { side: 'right', widthClass: 'max-w-md w-[min(92vw,28rem)]' },
)

const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const sideIsRight = computed(() => props.side !== 'left')
</script>

<template>
  <DialogRoot :open="open" @update:open="emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay
        class="fixed inset-0 z-40 bg-ink/25 transition-opacity duration-200 ease-[var(--ease-paper)] data-[state=closed]:opacity-0 data-[state=open]:opacity-100"
      />
      <DialogContent
        :class="cn(
          'fixed top-0 z-50 flex h-full flex-col bg-paper-surface shadow-card-hover',
          'transition-transform duration-250 ease-[var(--ease-paper)]',
          sideIsRight
            ? 'right-0 data-[state=closed]:translate-x-full data-[state=open]:translate-x-0'
            : 'left-0 data-[state=closed]:-translate-x-full data-[state=open]:translate-x-0',
          widthClass,
        )"
      >
        <header
          v-if="title || $slots.header"
          class="flex items-center justify-between gap-4 border-b border-mist-line px-5 py-4"
        >
          <div class="min-w-0">
            <DialogTitle class="font-book text-base font-semibold text-ink">{{ title }}</DialogTitle>
            <DialogDescription v-if="description" class="mt-1 text-xs leading-relaxed text-ink-secondary">
              {{ description }}
            </DialogDescription>
          </div>
          <button
            type="button"
            class="rounded-sm p-1 text-ink-secondary transition-colors hover:bg-paper hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
            aria-label="关闭"
            @click="emit('update:open', false)"
          >
            <X class="size-4" />
          </button>
        </header>
        <div class="flex-1 overflow-y-auto px-5 py-4 text-sm text-ink">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="flex justify-end gap-2 border-t border-mist-line px-5 py-3">
          <slot name="footer" />
        </footer>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
