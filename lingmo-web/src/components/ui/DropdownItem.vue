<script setup lang="ts">
import { DropdownMenuItem } from 'reka-ui'
import { cn } from '@/utils/cn'

defineProps<{
  /** 危险操作（删除等）以朱砂提示 */
  danger?: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{ select: [event: Event] }>()
</script>

<template>
  <DropdownMenuItem
    :disabled="disabled"
    :class="cn(
      'flex cursor-pointer select-none items-center gap-2 rounded-sm px-2.5 py-1.5 text-sm outline-none',
      'transition-colors duration-150 ease-[var(--ease-paper)]',
      'data-[highlighted]:bg-paper data-[highlighted]:text-ink',
      danger ? 'text-cinnabar data-[highlighted]:bg-cinnabar-wash' : 'text-ink',
      'data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
    )"
    @select="emit('select', $event)"
  >
    <slot />
  </DropdownMenuItem>
</template>
