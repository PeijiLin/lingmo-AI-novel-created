<script lang="ts">
export interface SelectOption {
  label: string
  value: string
  disabled?: boolean
}
</script>

<script setup lang="ts">
import {
  SelectRoot,
  SelectTrigger,
  SelectValue,
  SelectPortal,
  SelectContent,
  SelectViewport,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectScrollUpButton,
  SelectScrollDownButton,
} from 'reka-ui'
import { ChevronDown, ChevronUp, Check } from 'lucide-vue-next'
import { cn } from '@/utils/cn'

defineProps<{
  options: SelectOption[]
  placeholder?: string
  disabled?: boolean
  /** 触发器宽度类 */
  widthClass?: string
}>()

const model = defineModel<string>()
</script>

<template>
  <SelectRoot v-model="model" :disabled="disabled">
    <SelectTrigger
      :class="cn(
        'inline-flex h-9 items-center justify-between gap-2 rounded-md border border-mist-line bg-paper-surface px-3 text-sm text-ink',
        'transition-colors duration-150 ease-[var(--ease-paper)] hover:border-mist',
        'focus:border-ink-indigo/50 focus:outline-none focus:ring-2 focus:ring-ink-indigo/20',
        'disabled:cursor-not-allowed disabled:opacity-60 data-[placeholder]:text-mist',
        widthClass ?? 'w-full',
      )"
    >
      <SelectValue :placeholder="placeholder ?? '请选择'" />
      <ChevronDown class="size-4 shrink-0 text-ink-secondary" />
    </SelectTrigger>
    <SelectPortal>
      <SelectContent
        position="popper"
        :side-offset="4"
        class="z-50 overflow-hidden rounded-md border border-mist-line bg-paper-surface shadow-card-hover"
      >
        <SelectScrollDownButton class="flex h-6 items-center justify-center text-ink-secondary">
          <ChevronUp class="size-3.5" />
        </SelectScrollDownButton>
        <SelectViewport class="p-1">
          <SelectItem
            v-for="opt in options"
            :key="opt.value"
            :value="opt.value"
            :disabled="opt.disabled"
            :class="cn(
              'relative flex cursor-pointer select-none items-center rounded-sm py-1.5 pr-8 pl-3 text-sm text-ink outline-none',
              'data-[highlighted]:bg-paper data-[state=checked]:text-ink-indigo data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
            )"
          >
            <SelectItemText>{{ opt.label }}</SelectItemText>
            <SelectItemIndicator class="absolute right-2.5 inline-flex">
              <Check class="size-3.5 text-cinnabar" />
            </SelectItemIndicator>
          </SelectItem>
        </SelectViewport>
        <SelectScrollUpButton class="flex h-6 items-center justify-center text-ink-secondary">
          <ChevronDown class="size-3.5" />
        </SelectScrollUpButton>
      </SelectContent>
    </SelectPortal>
  </SelectRoot>
</template>
