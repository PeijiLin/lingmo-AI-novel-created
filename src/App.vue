<!-- src/App.vue -->
<template>
  <Layout></Layout>
</template>

<script setup lang="ts">
import Layout from "@/components/Layout.vue";
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
