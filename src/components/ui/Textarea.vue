<script setup lang="ts">
import { cn } from '@/utils/cn'

defineProps<{
  placeholder?: string
  rows?: number
  disabled?: boolean
  readonly?: boolean
  maxlength?: string | number
}>()

const model = defineModel<string>()
</script>

<template>
  <textarea
    v-model="model"
    :placeholder="placeholder"
    :rows="rows ?? 4"
    :disabled="disabled"
    :readonly="readonly"
    :maxlength="maxlength"
    :class="cn(
      'w-full resize-y rounded-md border border-mist-line bg-paper-surface px-3 py-2 text-sm leading-relaxed text-ink',
      'placeholder:text-mist transition-colors duration-150 ease-[var(--ease-paper)]',
      'hover:border-mist focus:border-ink-indigo/50 focus:outline-none focus:ring-2 focus:ring-ink-indigo/20',
      'disabled:cursor-not-allowed disabled:bg-paper disabled:opacity-60',
    )"
  />
</template>
