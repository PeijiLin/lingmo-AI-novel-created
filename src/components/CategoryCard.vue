<!-- src/components/CategoryCard.vue -->
<template>
  <button
    @click="$emit('click', category)"
    class="group relative flex flex-col items-center justify-center p-4 bg-white rounded-xl shadow-sm hover:shadow-md transition-all duration-200 border border-gray-100 text-center cursor-pointer"
  >
    <!-- 图标 -->
    <div
      :class="`w-12 h-12 rounded-full flex items-center justify-center text-white text-lg ${category.color} group-hover:scale-110 transition-transform`"
    >
      <i :class="category.icon"></i>
    </div>

    <!-- 标签文字 -->
    <span class="mt-3 text-sm font-medium text-gray-800 line-clamp-1">
      {{ category.label }}
    </span>

    <!-- 悬停阴影效果 -->
    <div class="absolute inset-0 rounded-xl opacity-0 group-hover:opacity-10 transition-opacity pointer-events-none"></div>
  </button>
</template>

<script setup lang="ts">
interface Category {
  key: string
  label: string
  icon: string
  color: string
}

defineProps<{
  category: Category
}>()

const emit = defineEmits(['click'])
</script>
