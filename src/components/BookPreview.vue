<!-- src/components/BookPreview.vue -->
<template>
  <div class="flex items-start gap-4 p-4 bg-white rounded-xl shadow-sm hover:shadow-md transition-shadow duration-200 border border-gray-100">
    <!-- 封面图 -->
    <div class="flex-shrink-0 w-16 h-20 overflow-hidden rounded-lg">
      <img
        :src="book.cover"
        :alt="book.title"
        class="w-full h-full object-cover"
      />
    </div>

    <!-- 内容 -->
    <div class="flex-1 min-w-0">
      <h3 class="font-semibold text-gray-900 text-sm leading-tight">{{ book.title }}</h3>
      <p class="text-xs text-gray-600 mt-1">by {{ book.author }}</p>
      <span class="inline-block mt-1 px-2 py-1 bg-blue-50 text-blue-700 text-xs rounded-full font-medium">
        {{ book.genre }}
      </span>

      <!-- 阅读进度（可选） -->
      <p v-if="book.progress" class="text-xs text-gray-500 mt-1">
        {{ book.progress }}% 已读
      </p>
    </div>

    <!-- 加入书架按钮 -->
    <button
      @click="$emit('add-to-shelf', book)"
      class="flex-shrink-0 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-full hover:bg-blue-700 transition-colors shadow-sm"
    >
      加入书架
    </button>
  </div>
</template>

<script setup lang="ts">
interface Book {
  id: number
  title: string
  author: string
  cover: string
  genre: string
  progress?: number
}

defineProps<{
  book: Book
}>()

const emit = defineEmits(['add-to-shelf'])
</script>
