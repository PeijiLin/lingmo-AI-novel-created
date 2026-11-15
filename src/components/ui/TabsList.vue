<script setup lang="ts">
import { TabsList } from 'reka-ui'
import { cn } from '@/utils/cn'
</script>

<template>
  <TabsList
    :class="cn('inline-flex items-center gap-1 rounded-md bg-paper p-1 border border-mist-line')"
  >
    <slot />
  </TabsList>
</template>
