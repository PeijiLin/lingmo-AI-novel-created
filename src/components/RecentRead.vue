<template>
  <section>
    <h2 class="text-2xl font-bold text-gray-800 mb-6">最近阅读</h2>
    <div class="bg-white rounded-xl shadow-lg overflow-hidden">
      <div class="flex">
        <div class="w-1/3">
          <img :src="book.cover" alt="封面" class="w-full h-64 object-cover">
        </div>
        <div class="w-2/3 p-6">
          <h3 class="font-bold text-gray-800">{{ book.title }}</h3>
          <p class="text-sm text-gray-600">by {{ book.author }}</p>
          <p class="text-gray-700 text-sm mt-2">{{ book.description }}</p>
          <div class="mt-4 text-xs text-gray-500">
            上次阅读: {{ book.lastRead }}
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps({
  book: {
    type: Object,
    required: true
  }
})
</script>
