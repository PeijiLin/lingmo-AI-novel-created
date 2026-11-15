<template>
  <aside class="w-64 bg-white border-r border-gray-200 h-full flex flex-col">
    <!-- Logo & Brand -->
    <div class="p-6 border-b border-gray-200">
      <h1 class="text-xl font-bold text-gray-800 tracking-tight">NovelImmersion</h1>
    </div>

    <!-- Navigation Menu -->
    <nav class="flex-1 overflow-y-auto px-4 py-2">
      <ul class="space-y-1">
        <li v-for="item in menuItems" :key="item.key">
          <router-link
            :to="item.route"
            class="flex items-center px-4 py-3 text-gray-700 hover:bg-blue-50 hover:text-blue-700 rounded-lg transition-all duration-200 ease-in-out group"
            active-class="bg-blue-100 text-blue-800 font-medium"
            exact-active-class="bg-blue-100 text-blue-800 font-medium"
          >
            <!-- 使用 Tailwind 控制图标大小 -->
            <component
              :is="item.iconComponent"
              :size="16"
              class="w-5 h-5 mr-3 text-gray-600 group-hover:text-blue-600 transition-colors scale-90"
            />
            <span class="font-medium">{{ item.label }}</span>
          </router-link>
        </li>
      </ul>
    </nav>

    <!-- Footer -->
    <div class="p-4 border-t border-gray-200 text-xs text-gray-500">
      <p>© 2025 NovelImmersion</p>
    </div>
  </aside>
</template>

<script setup lang="ts">
import {
  HomeFilled,
  Search,
  Notebook,
  EditPen,
  Trophy,
  User,
  Setting,
  Document
} from '@element-plus/icons-vue'

const menuItems = [
  { key: 'home', label: '首页', route: '/', iconComponent: HomeFilled },
  { key: 'discover', label: '发现好书', route: '/discover', iconComponent: Search },
  { key: 'bookshelf', label: '书架', route: '/bookshelf', iconComponent: Notebook },
  { key: 'library', label: '创作者中心', route: '/library', iconComponent: EditPen },
  { key: 'knowledgeBaseList', label: '创作知识库', route: '/knowledgeBaseList', iconComponent: Document},
  { key: 'ranking', label: '排行', route: '/ranking', iconComponent: Trophy },
  { key: 'profile', label: '我的', route: '/profile', iconComponent: User },
  { key: 'settings', label: '设置', route: '/settings', iconComponent: Setting },
]
</script>

<style scoped>
/* 滚动条样式不变 */
.overflow-y-auto {
  scrollbar-width: thin;
  scrollbar-color: #e5e7eb #f3f4f6;
}

.overflow-y-auto::-webkit-scrollbar {
  width: 6px;
}

.overflow-y-auto::-webkit-scrollbar-track {
  background: #f3f4f6;
  border-radius: 3px;
}

.overflow-y-auto::-webkit-scrollbar-thumb {
  background: #e5e7eb;
  border-radius: 3px;
}

.overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background: #d1d5db;
}
</style>
