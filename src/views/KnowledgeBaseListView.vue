<script setup lang="ts">
/**
 * 知识库入口（规范 4.2：创作辅助）
 * 选择一部作品，进入它的知识库（角色 / 世界观 / 素材）。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { BookOpen, RefreshCw } from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Button, Skeleton } from '@/components/ui'
import BookScrollCard from '@/components/base/BookScrollCard.vue'
import { useToast } from '@/composables/useToast'
import { useAuth } from '@/composables/useAuth'
import { listAuthorBooks } from '@/api/books'
import type { BookInfoVo } from '@/api/types'

const router = useRouter()
const toast = useToast()
const { store } = useAuth()

const books = ref<BookInfoVo[]>([])
const loading = ref(false)

async function load() {
  if (!store.userId) return
  loading.value = true
  try {
    books.value = (await listAuthorBooks(store.userId)) ?? []
  } catch (err) {
    toast.error('加载失败', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

function openKb(book: BookInfoVo) {
  if (book.id) router.push(`/knowledgeBase/${book.id}`)
}

onMounted(load)
</script>

<template>
  <AppShell>
    <div class="flex items-center justify-between gap-3">
      <div>
        <h1 class="font-book text-xl font-semibold text-ink">知识库</h1>
        <p class="mt-1 text-xs text-ink-secondary">为每部作品积累角色卡与世界观素材，让 AI 续写不跑偏。</p>
      </div>
      <Button variant="ghost" size="sm" :loading="loading" @click="load">
        <RefreshCw class="size-3.5" />
        刷新
      </Button>
    </div>

    <section class="mt-6" aria-label="作品知识库">
      <div v-if="loading" class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        <div v-for="i in 4" :key="i" class="space-y-2">
          <Skeleton class="aspect-[3/4] w-full rounded-lg" />
          <Skeleton class="h-4 w-3/4" />
        </div>
      </div>

      <p v-else-if="books.length === 0" class="py-16 text-center text-sm text-ink-secondary">
        还没有作品。去创作台新建一部，再为它建立知识库。
      </p>

      <div v-else class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        <BookScrollCard v-for="book in books" :key="book.id" :book="book" @open="openKb" />
      </div>
    </section>
  </AppShell>
</template>