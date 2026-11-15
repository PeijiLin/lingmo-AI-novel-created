<!-- src/App.vue -->
<template>
  <Layout />
  <!-- 全站轻提示宿主（纸墨自研 Toast） -->
  <ToastHost />
</template>

<script setup lang="ts">
import Layout from '@/components/Layout.vue'
import { ToastHost } from '@/components/ui'
</script>

<style scoped>
/* 为小屏幕设备添加响应式支持 */
@media (max-width: 768px) {
  main {
    margin-left: 0;
    padding: 1rem;
  }
}
</style>
