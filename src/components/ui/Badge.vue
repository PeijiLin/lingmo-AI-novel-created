<script lang="ts">
import { cva, type VariantProps } from 'class-variance-authority'

export const badgeVariants = cva(
  'inline-flex items-center gap-1 rounded-sm px-1.5 py-0.5 text-xs font-medium leading-none whitespace-nowrap',
  {
    variants: {
      variant: {
        /** 品牌主色底 */
        default: 'bg-ink-indigo/10 text-ink-indigo',
        /** 朱砂：数据高亮 / 朱批语义 */
        cinnabar: 'bg-cinnabar/10 text-cinnabar',
        /** 描边 */
        outline: 'border border-mist-line text-ink-secondary',
        /** 辅助灰 */
        mist: 'bg-paper text-mist',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

export type BadgeVariant = VariantProps<typeof badgeVariants>['variant']
</script>

<script setup lang="ts">
import { cn } from '@/utils/cn'

defineProps<{ variant?: BadgeVariant }>()
</script>

<template>
  <span :class="cn(badgeVariants({ variant }))"><slot /></span>
</template>
