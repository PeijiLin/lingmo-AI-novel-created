<template>
  <div class="flex min-h-screen bg-gray-50">
    <Sidebar class="hidden sm:block w-64" />
    <main class="flex-1 pt-4 sm:ml-64">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import Sidebar from '@/components/Sidebar.vue'
</script>

<style scoped>
</style>
