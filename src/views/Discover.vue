<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <!-- 标题 -->
    <div class="mb-8">
      <h1 class="text-3xl font-bold text-gray-900">发现好书</h1>
      <p class="text-gray-600 mt-1">探索万千世界，总有一本属于你</p>
    </div>

    <!-- 分类导航区 -->
    <div class="mb-10">
      <h2 class="text-lg font-semibold text-gray-800 mb-4">热门分类</h2>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
        <CategoryCard
          v-for="category in categories"
          :key="category.key"
          :category="category"
          @click="selectCategory(category)"
        />
      </div>
    </div>

    <!-- 榜单推荐区 -->
    <div class="mb-12">
      <h2 class="text-xl font-semibold text-gray-800 mb-6">今日推荐</h2>

      <!-- 畅销榜 -->
      <section class="mb-8">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-medium text-gray-900">🏆 畅销榜</h3>
          <router-link
            to="/ranking"
            class="text-blue-600 text-sm font-medium hover:underline"
          >
            查看全部 →
          </router-link>
        </div>
        <div class="space-y-4">
          <BookPreview
            v-for="book in topSellingBooks"
            :key="book.id"
            :book="book"
            @add-to-shelf="handleAddToShelf(book)"
          />
        </div>
      </section>

      <!-- 新书榜 -->
      <section>
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-medium text-gray-900">🆕 新书榜</h3>
          <router-link
            to="/ranking"
            class="text-blue-600 text-sm font-medium hover:underline"
          >
            查看全部 →
          </router-link>
        </div>
        <div class="space-y-4">
          <BookPreview
            v-for="book in newReleaseBooks"
            :key="book.id"
            :book="book"
            @add-to-shelf="handleAddToShelf(book)"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import BookPreview from "@/components/BookPreview.vue";
import CategoryCard from "@/components/CategoryCard.vue";

interface Book {
  id: number
  title: string
  author: string
  cover: string
  genre: string
  progress?: number
}

interface Category {
  key: string
  label: string
  icon: string
  color: string
}

// 分类数据（支持图标 + 颜色）
const categories = ref<Category[]>([
  { key: 'male', label: '男频', icon: 'fas fa-male', color: 'bg-blue-500' },
  { key: 'female', label: '女频', icon: 'fas fa-female', color: 'bg-pink-500' },
  { key: 'xuanhuan', label: '玄幻', icon: 'fas fa-dragon', color: 'bg-purple-500' },
  { key: 'xiuxian', label: '仙侠', icon: 'fas fa-sword', color: 'bg-indigo-500' },
  { key: 'yanqing', label: '言情', icon: 'fas fa-heart', color: 'bg-red-500' },
  { key: 'kehuan', label: '科幻', icon: 'fas fa-rocket', color: 'bg-teal-500' },
  { key: 'dushi', label: '都市', icon: 'fas fa-city', color: 'bg-gray-500' },
  { key: 'xuanyi', label: '悬疑', icon: 'fas fa-magnifying-glass', color: 'bg-orange-500' },
  { key: 'lishi', label: '历史', icon: 'fas fa-scroll', color: 'bg-yellow-500' },
  { key: 'youxi', label: '游戏', icon: 'fas fa-gamepad', color: 'bg-green-500' },
  { key: 'jingji', label: '竞技', icon: 'fas fa-trophy', color: 'bg-red-600' },
  { key: 'qingxiaoshuo', label: '轻小说', icon: 'fas fa-book-open', color: 'bg-blue-400' },
  { key: 'tongren', label: '同人', icon: 'fas fa-user-friends', color: 'bg-purple-400' },
  { key: 'erciyuan', label: '二次元', icon: 'fas fa-mask', color: 'bg-pink-400' },
  { key: 'chuanyue', label: '穿越', icon: 'fas fa-time-travel', color: 'bg-emerald-500' },
  { key: 'chongsheng', label: '重生', icon: 'fas fa-recycle', color: 'bg-cyan-500' }
])

// 推荐书籍数据
const topSellingBooks = ref<Book[]>([
  {
    id: 1,
    title: '诡秘之主',
    author: '爱潜水的乌贼',
    cover: 'https://picsum.photos/id/10/300/400',
    genre: '玄幻',
    progress: 98
  },
  {
    id: 2,
    title: '大奉打更人',
    author: '卖报小郎君',
    cover: 'https://picsum.photos/id/11/300/400',
    genre: '玄幻',
    progress: 95
  },
  {
    id: 3,
    title: '凡人修仙传',
    author: '忘语',
    cover: 'https://picsum.photos/id/12/300/400',
    genre: '仙侠',
    progress: 92
  },
  {
    id: 4,
    title: '第一序列',
    author: '会说话的肘子',
    cover: 'https://picsum.photos/id/13/300/400',
    genre: '科幻',
    progress: 88
  },
  {
    id: 5,
    title: '我有一座冒险屋',
    author: '我会修空调',
    cover: 'https://picsum.photos/id/14/300/400',
    genre: '悬疑',
    progress: 85
  }
])

const newReleaseBooks = ref<Book[]>([
  {
    id: 6,
    title: '深空彼岸',
    author: '辰东',
    cover: 'https://picsum.photos/id/15/300/400',
    genre: '玄幻',
    progress: 12
  },
  {
    id: 7,
    title: '夜的命名术',
    author: '会说话的肘子',
    cover: 'https://picsum.photos/id/16/300/400',
    genre: '都市',
    progress: 15
  },
  {
    id: 8,
    title: '道诡异仙',
    author: '狐尾的笔',
    cover: 'https://picsum.photos/id/17/300/400',
    genre: '悬疑',
    progress: 8
  },
  {
    id: 9,
    title: '灵境行者',
    author: '卖报小郎君',
    cover: 'https://picsum.photos/id/18/300/400',
    genre: '都市',
    progress: 5
  },
  {
    id: 10,
    title: '我的治愈系游戏',
    author: '我会修空调',
    cover: 'https://picsum.photos/id/19/300/400',
    genre: '悬疑',
    progress: 3
  }
])

// 点击分类时触发事件（未来可跳转到分类列表页）
const selectCategory = (category: Category) => {
  console.log('用户点击了分类:', category.label)
  // 可以路由跳转到 /category/xuanhuan
  // router.push(`/category/${category.key}`)
}

// 添加到书架（复用 Bookshelf 中的逻辑）
const handleAddToShelf = (book: Book) => {
  // 这里可以调用全局状态管理（如 Pinia）或 localStorage
  alert(`已将《${book.title}》添加到书架！`)
  // 实际项目中建议使用 Pinia 状态管理
}
</script>
