<!-- src/App.vue：根组件（所有页面自带纸墨壳 / 独立布局，此处仅挂载） -->
<template>
  <RouterView />
  <ToastHost />
</template>

<script setup lang="ts">
import { ToastHost } from '@/components/ui'
</script>