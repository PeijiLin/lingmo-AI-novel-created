<script setup lang="ts">
import { cn } from '@/utils/cn'

defineProps<{
  /** 分隔方向 */
  orientation?: 'horizontal' | 'vertical'
}>()
</script>

<template>
  <div
    role="separator"
    :aria-orientation="orientation ?? 'horizontal'"
    :class="cn(
      'shrink-0 bg-mist-line',
      (orientation ?? 'horizontal') === 'horizontal' ? 'h-px w-full' : 'h-full w-px',
    )"
  />
</template>
