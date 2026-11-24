<script setup lang="ts">
import {
  DialogRoot,
  DialogPortal,
  DialogOverlay,
  DialogContent,
  DialogTitle,
  DialogDescription,
  DialogClose,
} from 'reka-ui'
import { X } from 'lucide-vue-next'
import { cn } from '@/utils/cn'

const props = withDefaults(
  defineProps<{
    open?: boolean
    title?: string
    description?: string
    /** 内容宽度：sm 360 / md 480 / lg 640 */
    width?: 'sm' | 'md' | 'lg'
  }>(),
  { width: 'md' },
)

const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const widthClass = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
}[props.width]
</script>

<template>
  <DialogRoot :open="open" @update:open="emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay
        class="fixed inset-0 z-40 bg-ink/25 backdrop-blur-[1px] transition-opacity duration-200 ease-[var(--ease-paper)] data-[state=closed]:opacity-0 data-[state=open]:opacity-100"
      />
      <DialogContent
        :class="cn(
          'fixed top-1/2 left-1/2 z-50 w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2',
          'rounded-lg border border-mist-line bg-paper-surface shadow-card-hover',
          'transition-all duration-200 ease-[var(--ease-paper)] data-[state=closed]:opacity-0 data-[state=open]:opacity-100',
          widthClass,
        )"
      >
        <header
          v-if="title || $slots.header"
          class="flex items-start justify-between gap-4 border-b border-mist-line px-5 py-4"
        >
          <div class="min-w-0">
            <DialogTitle class="font-book text-base font-semibold text-ink">{{ title }}</DialogTitle>
            <DialogDescription v-if="description" class="mt-1 text-xs leading-relaxed text-ink-secondary">
              {{ description }}
            </DialogDescription>
          </div>
          <DialogClose
            class="rounded-sm p-1 text-ink-secondary transition-colors hover:bg-paper hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
            aria-label="关闭"
          >
            <X class="size-4" />
          </DialogClose>
        </header>
        <div class="px-5 py-4 text-sm text-ink">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="flex justify-end gap-2 border-t border-mist-line px-5 py-3">
          <slot name="footer" />
        </footer>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
