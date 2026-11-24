<script setup lang="ts">
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuPortal,
  DropdownMenuContent,
} from 'reka-ui'
import { cn } from '@/utils/cn'

defineProps<{
  /** 菜单对齐 */
  align?: 'start' | 'center' | 'end'
}>()

const open = defineModel<boolean>('open', { default: false })
</script>

<template>
  <DropdownMenuRoot v-model:open="open">
    <DropdownMenuTrigger as-child class="outline-none">
      <slot name="trigger" />
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent
        :align="align ?? 'end'"
        :side-offset="6"
        :class="cn(
          'z-50 min-w-40 overflow-hidden rounded-md border border-mist-line bg-paper-surface p-1 shadow-card-hover',
        )"
      >
        <slot />
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>
