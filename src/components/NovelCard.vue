<!-- src/components/BookCard.vue -->
<template>
  <div
    class="group relative bg-white rounded-xl shadow-sm hover:shadow-lg transition-all duration-300 overflow-hidden border border-gray-100"
  >
    <!-- 封面图 -->
    <div class="relative aspect-[3/4] overflow-hidden">
      <img
        :src="book.picUrl"
        :alt="book.bookName"
        class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
      />
      <!-- 类型标签 -->
      <div class="absolute top-3 left-3 bg-white/90 backdrop-blur-sm text-xs font-medium px-2 py-1 rounded-full text-gray-700">
        {{ book.categoryName }}
      </div>
    </div>

    <!-- 内容区 -->
    <div class="p-5">
      <h3 class="font-semibold text-gray-900">{{ book.bookName }}</h3>
      <p class="text-sm text-gray-600">by {{ book.authorName }}</p>
      <p class="text-sm text-gray-500 mt-1 line-clamp-2">{{ book.bookDesc }}</p>

      <!-- 最近阅读时间 -->
      <p v-if="showLastRead" class="text-xs text-gray-400 mt-2">
        上次阅读: {{ book.lastChapterUpdateTime }}
      </p>
    </div>
<!--    <button-->
<!--      @click.stop="goToRead(book.id)"-->
<!--      class="mt-3 inline-flex items-center px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-full hover:bg-blue-700 transition-colors shadow-md"-->
<!--    >-->
<!--      📖 开始阅读-->
<!--    </button>-->
  </div>
</template>

<script setup lang="ts">
import {useRouter} from "vue-router";
import type {BookInfoListVo} from "@/api";


defineProps<{
  book: BookInfoListVo
  showLastRead?: boolean
}>()
const router = useRouter()
const goToRead = (id: number) => {
  router.push(`/read/${id}`)
}
</script>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
