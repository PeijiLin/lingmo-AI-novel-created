<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <!-- 标题 -->
    <div class="mb-8">
      <h1 class="text-3xl font-bold text-gray-900">我的书架</h1>
      <p class="text-gray-600 mt-1">你正在阅读和收藏的所有故事</p>
    </div>

    <!-- 分类筛选栏 -->
    <div class="mb-8 overflow-x-auto pb-2 -mx-4 px-4 scrollbar-hide">
      <div class="flex gap-2 min-w-max">
        <button
          v-for="category in categories"
          :key="category.key"
          @click="selectedCategory = category.key"
          :class="[
            'px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-all duration-200',
            selectedCategory === category.key
              ? 'bg-blue-600 text-white shadow-md transform scale-105'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          ]"
        >
          {{ category.label }}
        </button>
      </div>
    </div>

    <!-- 无书籍提示 -->
    <div v-if="filteredBooks.length === 0" class="text-center py-16">
      <svg class="w-16 h-16 mx-auto text-gray-300 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.746 0 3.332.477 4.5 1.253v13C19.832 18.477 18.246 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"></path>
      </svg>
      <h3 class="text-lg font-medium text-gray-700 mb-2">暂无该类书籍</h3>
      <p class="text-gray-500">去首页挑选一本符合你口味的小说吧！</p>
    </div>

    <!-- 书架列表 -->
    <div class="space-y-6">
      <BookCardInShelf
        v-for="book in filteredBooks"
        :key="book.id"
        :book="book"
        @remove="handleRemove(book.id)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import BookCardInShelf from '@/components/BookCardInShelf.vue'

interface Book {
  id: number
  title: string
  author: string
  cover: string
  progress: number
  lastRead: string
  genre: string // 新增字段：类型
}

// 数据源
const bookshelf = ref<Book[]>([
  {
    id: 1,
    title: '时光之轮',
    author: '李青玄',
    cover: 'https://picsum.photos/id/1/300/400',
    progress: 85,
    lastRead: '2025/9/11 20:30:40',
    genre: '玄幻' // 👈 关键字段
  },
  {
    id: 2,
    title: '苍穹榜',
    author: '墨然',
    cover: 'https://picsum.photos/id/2/300/400',
    progress: 72,
    lastRead: '2025/9/10 19:15:22',
    genre: '玄幻'
  },
  {
    id: 3,
    title: '暮色吟游者',
    author: '苏暮',
    cover: 'https://picsum.photos/id/3/300/400',
    progress: 40,
    lastRead: '2025/9/9 21:00:10',
    genre: '言情'
  },
  {
    id: 4,
    title: '修罗武神',
    author: '横刀笑',
    cover: 'https://picsum.photos/id/4/300/400',
    progress: 78,
    lastRead: '2025/9/8 22:15:00',
    genre: '玄幻'
  },
  {
    id: 5,
    title: '星辰变',
    author: '我吃西红柿',
    cover: 'https://picsum.photos/id/5/300/400',
    progress: 92,
    lastRead: '2025/9/7 20:05:30',
    genre: '玄幻'
  },
  {
    id: 6,
    title: '你是我的小确幸',
    author: '丁墨',
    cover: 'https://picsum.photos/id/6/300/400',
    progress: 65,
    lastRead: '2025/9/6 19:40:10',
    genre: '言情'
  },
  {
    id: 7,
    title: '三体',
    author: '刘慈欣',
    cover: 'https://picsum.photos/id/7/300/400',
    progress: 100,
    lastRead: '2025/9/5 21:20:00',
    genre: '科幻'
  }
])

// 分类配置（可扩展）
const categories = [
  { key: 'all', label: '全部' },
  { key: 'male', label: '男频' },
  { key: 'female', label: '女频' },
  { key: 'xuanhuan', label: '玄幻' },
  { key: 'xiuxian', label: '仙侠' },
  { key: 'yanqing', label: '言情' },
  { key: 'kehuan', label: '科幻' },
  { key: 'dushi', label: '都市' },
  { key: 'xuanyi', label: '悬疑' },
  { key: 'lishi', label: '历史' }
]

// 当前选中分类
const selectedCategory = ref('all')

// 过滤逻辑：根据分类映射
const filteredBooks = computed(() => {
  if (selectedCategory.value === 'all') return bookshelf.value

  const mapping: Record<string, string[]> = {
    male: ['玄幻', '仙侠', '武侠', '历史', '军事', '游戏', '竞技'],
    female: ['言情', '穿越', '重生', '甜宠', '宫斗', '种田'],
    xuanhuan: ['玄幻'],
    xiuxian: ['仙侠'],
    yanqing: ['言情'],
    kehuan: ['科幻'],
    dushi: ['都市'],
    xuanyi: ['悬疑'],
    lishi: ['历史']
  }

  const targetGenres = mapping[selectedCategory.value as keyof typeof mapping] || []

  if (targetGenres.length > 0) {
    return bookshelf.value.filter(book => targetGenres.includes(book.genre))
  }

  // 如果是单个类型（如‘玄幻’），直接匹配
  return bookshelf.value.filter(book => book.genre === selectedCategory.value)
})
</script>

<style scoped>
/* 隐藏横向滚动条，保留滑动功能 */
.overflow-x-auto::-webkit-scrollbar {
  display: none;
}
.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
