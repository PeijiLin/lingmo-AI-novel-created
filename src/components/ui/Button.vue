<script lang="ts">
import { cva, type VariantProps } from 'class-variance-authority'

export const buttonVariants = cva(
  'inline-flex select-none items-center justify-center gap-1.5 whitespace-nowrap font-medium transition-colors duration-150 ease-[var(--ease-paper)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40 focus-visible:ring-offset-1 focus-visible:ring-offset-paper disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        /** 品牌主色：常规关键动作 */
        default: 'bg-ink-indigo text-paper-surface shadow-card hover:bg-ink-indigo/90',
        /** 朱砂：唯一强调，仅用于最关键动作（开始创作 / 采纳朱批） */
        cinnabar: 'bg-cinnabar text-paper-surface shadow-card hover:bg-cinnabar/90',
        /** 纸面描边：次级动作 */
        outline: 'border border-mist-line bg-paper-surface text-ink hover:border-ink-indigo/40 hover:text-ink-indigo',
        /** 幽灵：工具条 / 图标按钮 */
        ghost: 'text-ink-secondary hover:bg-paper hover:text-ink',
        /** 链接形态 */
        link: 'text-ink-indigo underline-offset-4 hover:text-cinnabar hover:underline',
      },
      size: {
        default: 'h-9 rounded-md px-4 text-sm',
        sm: 'h-8 rounded-md px-3 text-xs',
        lg: 'h-10 rounded-md px-6 text-base',
        icon: 'size-9 rounded-md',
        'icon-sm': 'size-7 rounded-sm',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

export type ButtonVariant = VariantProps<typeof buttonVariants>['variant']
export type ButtonSize = VariantProps<typeof buttonVariants>['size']
</script>

<script setup lang="ts">
import { Loader2 } from 'lucide-vue-next'
import { cn } from '@/utils/cn'

defineProps<{
  variant?: ButtonVariant
  size?: ButtonSize
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
  loading?: boolean
}>()
</script>

<template>
  <button
    :type="type ?? 'button'"
    :disabled="disabled || loading"
    :class="cn(buttonVariants({ variant, size }))"
  >
    <Loader2 v-if="loading" class="size-4 animate-spin" />
    <slot />
  </button>
</template>
