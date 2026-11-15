<!-- src/App.vue：按路由 meta.shell 决定旧门户壳或裸视图（绞杀者迁移期） -->
<template>
  <Layout v-if="shell === 'portal'">
    <RouterView />
  </Layout>
  <RouterView v-else />
  <!-- 全站轻提示宿主（纸墨自研 Toast） -->
  <ToastHost />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Layout from '@/components/Layout.vue'
import { ToastHost } from '@/components/ui'

const route = useRoute()
const shell = computed(() => route.meta.shell)
</script>
