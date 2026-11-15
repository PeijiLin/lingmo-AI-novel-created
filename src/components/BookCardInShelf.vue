<!-- src/components/BookCardInShelf.vue -->
<template>
  <div class="flex items-start gap-4 p-5 bg-white rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow duration-200">
    <!-- 封面图 -->
    <div class="flex-shrink-0 w-20 h-28 overflow-hidden rounded-lg">
      <img
        :src="book.cover"
        :alt="book.title"
        class="w-full h-full object-cover"
      />
    </div>

    <!-- 内容区 -->
    <div class="flex-1 min-w-0">
      <h3 class="font-semibold text-gray-900 text-lg leading-tight">{{ book.title }}</h3>
      <p class="text-sm text-gray-600 mt-1">by {{ book.author }}</p>

      <!-- 类型标签 -->
      <span class="inline-block mt-2 px-2 py-1 bg-blue-50 text-blue-700 text-xs rounded-full font-medium">
        {{ book.genre }}
      </span>

      <!-- 阅读进度条 -->
      <div class="mt-3">
        <div class="flex items-center justify-between text-xs text-gray-600 mb-1">
          <span>阅读进度</span>
          <span>{{ book.progress }}%</span>
        </div>
        <div class="w-full h-2 bg-gray-200 rounded-full overflow-hidden">
          <div
            class="h-full bg-blue-500 transition-all duration-500 ease-out"
            :style="{ width: `${book.progress}%` }"
          ></div>
        </div>
      </div>

      <!-- 最后阅读时间 -->
      <p class="text-xs text-gray-500 mt-2">
        上次阅读：{{ book.lastRead }}
      </p>

      <!-- 删除按钮 -->
      <button
        @click="$emit('remove', book.id)"
        class="mt-3 inline-flex items-center px-3 py-1 text-xs font-medium text-red-600 bg-red-50 rounded-full hover:bg-red-100 transition-colors"
      >
        <svg class="w-3 h-3 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
        </svg>
        移除
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Book {
  id: number
  title: string
  author: string
  cover: string
  progress: number
  lastRead: string
  genre: string
}

defineProps<{
  book: Book
}>()

const emit = defineEmits(['remove'])
</script>
