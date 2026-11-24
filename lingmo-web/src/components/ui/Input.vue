<script setup lang="ts">
import { cn } from '@/utils/cn'

defineProps<{
  type?: 'text' | 'password'
  placeholder?: string
  disabled?: boolean
  readonly?: boolean
  autocomplete?: string
  name?: string
  maxlength?: string | number
}>()

const model = defineModel<string>()
</script>

<template>
  <input
    v-model="model"
    :type="type ?? 'text'"
    :placeholder="placeholder"
    :disabled="disabled"
    :readonly="readonly"
    :autocomplete="autocomplete"
    :name="name"
    :class="cn(
      'h-9 w-full rounded-md border border-mist-line bg-paper-surface px-3 text-sm text-ink',
      'placeholder:text-mist transition-colors duration-150 ease-[var(--ease-paper)]',
      'hover:border-mist focus:border-ink-indigo/50 focus:outline-none focus:ring-2 focus:ring-ink-indigo/20',
      'disabled:cursor-not-allowed disabled:bg-paper disabled:opacity-60',
    )"
  />
</template>
