<script setup lang="ts">
import { Check, AlertCircle, AlertTriangle, Info, X } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import { cn } from '@/utils/cn'

const { toasts, dismiss } = useToast()

const variantIcon = {
  default: Info,
  success: Check,
  error: AlertCircle,
  warning: AlertTriangle,
} as const

const variantClass = {
  default: 'text-ink-secondary',
  success: 'text-ink-indigo',
  error: 'text-cinnabar',
  warning: 'text-cinnabar',
} as const
</script>

<template>
  <Teleport to="body">
    <div class="pointer-events-none fixed top-4 left-1/2 z-[70] flex w-full max-w-sm -translate-x-1/2 flex-col gap-2 px-4">
      <TransitionGroup
        enter-active-class="transition-all duration-200 ease-[var(--ease-paper)]"
        enter-from-class="-translate-y-2 opacity-0"
        leave-active-class="transition-all duration-150 ease-[var(--ease-paper)]"
        leave-to-class="opacity-0"
      >
        <div
          v-for="item in toasts"
          :key="item.id"
          role="status"
          :class="cn(
            'pointer-events-auto flex items-start gap-2.5 rounded-md border border-mist-line bg-paper-surface px-3.5 py-2.5 shadow-card-hover',
          )"
        >
          <component :is="variantIcon[item.variant]" :class="cn('mt-0.5 size-4 shrink-0', variantClass[item.variant])" />
          <div class="min-w-0 flex-1">
            <p class="text-sm leading-snug font-medium text-ink">{{ item.title }}</p>
            <p v-if="item.description" class="mt-0.5 text-xs leading-relaxed text-ink-secondary">{{ item.description }}</p>
          </div>
          <button
            type="button"
            class="shrink-0 rounded-sm p-0.5 text-mist transition-colors hover:text-ink"
            aria-label="关闭提示"
            @click="dismiss(item.id)"
          >
            <X class="size-3.5" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>
