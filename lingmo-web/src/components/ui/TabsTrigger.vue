<script setup lang="ts">
import { TabsTrigger } from 'reka-ui'
import { cn } from '@/utils/cn'

defineProps<{ value: string; disabled?: boolean }>()
</script>

<template>
  <TabsTrigger
    :value="value"
    :disabled="disabled"
    :class="cn(
      'inline-flex h-7 items-center justify-center rounded-sm px-3 text-xs font-medium text-ink-secondary',
      'transition-colors duration-150 ease-[var(--ease-paper)]',
      'hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40',
      'data-[state=active]:bg-paper-surface data-[state=active]:text-ink-indigo data-[state=active]:shadow-card',
      'disabled:pointer-events-none disabled:opacity-50',
    )"
  >
    <slot />
  </TabsTrigger>
</template>
