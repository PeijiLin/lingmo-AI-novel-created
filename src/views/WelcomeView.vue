<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <!-- 标题区 -->
    <div class="text-center mt-16 md:mt-24 mb-16">
      <!-- 主标题 -->
      <h1 class="text-3xl sm:text-4xl md:text-5xl font-extrabold text-gray-900 mb-4 tracking-tight">
        欢迎来到
      </h1>
      <!-- 副标题 - 使用渐变色 -->
      <h1 class="text-4xl sm:text-5xl md:text-6xl font-light bg-clip-text text-transparent bg-gradient-to-r from-blue-500 to-purple-600 tracking-wide leading-none mb-6">
        NovelImmersion
      </h1>
      <!-- 描述 -->
      <div class="flex justify-center">
        <p class="text-gray-600 text-base md:text-lg max-w-xl">
          沉浸在文字的世界，遇见专属于你的故事。
        </p>
      </div>
    </div>

    <!-- 精选推荐 -->
    <section class="mb-16">
      <h2 class="text-2xl font-bold text-gray-800 mb-6 pb-2 border-b border-gray-200 inline-block">
        精选推荐
      </h2>
      <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
        <NovelCard
          v-for="book in featuredBooks"
          :key="book.id"
          :book="book"
          class="transition-transform duration-300 ease-in-out hover:-translate-y-1 hover:shadow-lg"
        />
      </div>
    </section>

    <!-- 按类型浏览 -->
    <section class="mb-16">
      <h2 class="text-2xl font-bold text-gray-800 mb-6 pb-2 border-b border-gray-200 inline-block">
        按类型浏览
      </h2>
      <p class="text-gray-500 text-sm mb-8 text-center italic">
        发现属于你的专属世界
      </p>

      <!-- 一级分类：男频 / 女频 -->
      <div class="flex flex-wrap justify-center gap-4 mb-8">
        <button
          v-for="categoryGroup in categoryGroups"
          :key="categoryGroup.label"
          @click="toggleCategoryGroup(categoryGroup.label)"
          :class="[
            'px-6 py-3 rounded-full font-semibold transition-all duration-300 transform',
            activeGroup === categoryGroup.label
              ? 'bg-gradient-to-r from-blue-500 to-indigo-600 text-white shadow-lg scale-105'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200 hover:scale-105'
          ]"
        >
          {{ categoryGroup.label }}
        </button>
      </div>

      <!-- 二级分类：根据一级分类动态显示 -->
      <div v-if="visibleSubCategories.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8 gap-3 justify-items-center">
        <button
          v-for="sub in visibleSubCategories"
          :key="sub.name"
          @click="navigateToCategory(sub.workDirection, sub.id)"
          class="px-4 py-2 bg-white rounded-lg shadow-sm hover:shadow-md border border-gray-200 text-sm font-medium text-gray-700 transition-all duration-200 hover:scale-105 hover:bg-blue-50 hover:border-blue-300 focus:outline-none focus:ring-2 focus:ring-blue-300 focus:ring-opacity-50"
        >
          {{ sub.name }}
        </button>
      </div>
      <div v-else class="text-center text-gray-500 py-8">
        <p>暂无分类数据</p>
      </div>
    </section>

    <!-- 分类下的书籍列表 -->
    <section class="mb-16">
      <h2 class="text-2xl font-bold text-gray-800 mb-6 pb-2 border-b border-gray-200 inline-block">
        {{ activeGroup }}作品
      </h2>
      <div v-if="bookInfosByCategory.length > 0" class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
        <NovelCard
          v-for="book in bookInfosByCategory"
          :key="book.id"
          :book="book"
          class="transition-transform duration-300 ease-in-out hover:-translate-y-1 hover:shadow-lg"
        />
      </div>
      <div v-else class="text-center text-gray-500 py-12">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-16 w-16 mx-auto text-gray-300 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
        </svg>
        <p class="text-lg">该分类下暂无作品</p>
        <p class="text-sm mt-2">敬请期待更多精彩内容</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import NovelCard from "@/components/NovelCard.vue";
import {
  type BookCategoryVo,
  type BookInfoListVo,
  ReadingControllerService
} from "@/api";
import { useRouter } from "vue-router";

// 精选推荐书籍
const featuredBooks = [
  {
    id: 1,
    title: '时光之轮',
    author: '李青玄',
    desc: '一段跨越千年的宿命纠葛，全新奇幻世界冒险——你准备好启程了吗？',
    cover: 'https://picsum.photos/id/1/300/400',
    genre: '玄幻'
  },
  {
    id: 2,
    title: '苍穹榜',
    author: '墨然',
    desc: '少年自荒芜星辰崛起，手握天命，谱写属于自己的史诗。',
    cover: 'https://picsum.photos/id/2/300/400',
    genre: '科幻'
  },
  {
    id: 3,
    title: '暮色吟游者',
    author: '苏暮',
    desc: '在暮色下行走的吟游者，以诗歌和剑踏遍天涯。',
    cover: 'https://picsum.photos/id/3/300/400',
    genre: '言情'
  },
  {
    id: 4,
    title: '修罗武神',
    author: '横刀笑',
    desc: '武极修罗道，谁与争锋？天骄争锋，热血激昂！',
    cover: 'https://picsum.photos/id/4/300/400',
    genre: '玄幻'
  }
]

// 导航到分类页
const router = useRouter()
const bookInfosByCategory = ref<BookInfoListVo[]>([])

const navigateToCategory = async (workDirection: number, id: number) => {
  try {
    const res = await ReadingControllerService.pageBookList({
      pageNum: 1,
      pageSize: 10,
      workDirection: workDirection,
      categoryId: id
    })
    if (res.code === 200)
      bookInfosByCategory.value = res.data?.records || []
  } catch (error) {
    console.error('获取书籍列表失败:', error)
    bookInfosByCategory.value = []
  }
}

// 分类配置
const categories = ref<Record<string, Array<BookCategoryVo>>>({})

onMounted(async () => {
  try {
    const res = await ReadingControllerService.queryBookCategories()
    if (res.code === 200) {
      categories.value = res.data || {}

      // 默认加载男频数据
      if (categories.value['男频'] && categories.value['男频'].length > 0) {
        const firstCategory = categories.value['男频'][0]
        await navigateToCategory(firstCategory.workDirection, firstCategory.id)
      }
    }
  } catch (error) {
    console.error('获取书籍分类失败:', error)
  }
})

// 分组结构定义
const categoryGroups = [
  { label: '男频', key: '男频' },
  { label: '女频', key: '女频' }
]

// 当前激活的一级分类
const activeGroup = ref('男频')

// 计算当前可见的子分类
const visibleSubCategories = computed(() => {
  return categories.value[activeGroup.value] || []
})

// 切换一级分类
const toggleCategoryGroup = (group: string) => {
  activeGroup.value = group

  // 切换分类时加载该分类下的第一个子分类的书籍
  const subCategories = visibleSubCategories.value
  if (subCategories.length > 0) {
    const firstCategory = subCategories[0]
    navigateToCategory(firstCategory.workDirection, firstCategory.id)
  } else {
    bookInfosByCategory.value = []
  }
}
</script>

<style scoped>
/* 如果你的 NovelCard 组件没有自带圆角和阴影，可以在这里添加 */
.novel-card {
  /* @apply rounded-xl shadow-md; */
}
</style>
