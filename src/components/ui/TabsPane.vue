<script setup lang="ts">
import { TabsContent } from 'reka-ui'
import { cn } from '@/utils/cn'

defineProps<{ value: string }>()
</script>

<template>
  <TabsContent :value="value" :class="cn('mt-3 focus-visible:outline-none')">
    <slot />
  </TabsContent>
</template>
