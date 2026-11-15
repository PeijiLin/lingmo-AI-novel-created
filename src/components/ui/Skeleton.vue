<script setup lang="ts">
import { cn } from '@/utils/cn'

const props = defineProps<{ class?: string }>()
</script>

<template>
  <div
    :class="cn('animate-pulse rounded-md bg-mist-line/60', props.class)"
    aria-hidden="true"
  />
</template>
