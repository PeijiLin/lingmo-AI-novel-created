<script setup lang="ts">
import { TabsRoot } from 'reka-ui'

const model = defineModel<string>()
</script>

<template>
  <TabsRoot v-model="model" class="w-full">
    <slot />
  </TabsRoot>
</template>
