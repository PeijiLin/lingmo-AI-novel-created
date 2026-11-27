<script setup lang="ts">
/**
 * 创作工作台聚合页（规范 4.1：登录后第一落点）
 * 我的作品陈列 + 新建作品引导；无作品时首屏即引导新建。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Feather, Plus, RefreshCw } from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Button, Card, Combobox, Dialog, Input, Textarea, Skeleton } from '@/components/ui'
import BookScrollCard from '@/components/base/BookScrollCard.vue'
import { useToast } from '@/composables/useToast'
import { useAuth } from '@/composables/useAuth'
import { createBook, listAuthorBooks, listCategories } from '@/api/books'
import { toCategoryOptions } from '@/utils/categoryOptions'
import type { ComboboxOption } from '@/components/ui'
import type { BookInfoVo } from '@/api/types'

const router = useRouter()
const toast = useToast()
const { store } = useAuth()

const books = ref<BookInfoVo[]>([])
const loading = ref(false)
const creating = ref(false)
const directionOptions = ref<ComboboxOption[]>([])

/* ---------------- 新建作品 ---------------- */

const createOpen = ref(false)
const createForm = reactive({
  bookName: '',
  bookDesc: '',
  workDirection: '',
})

async function loadBooks() {
  if (!store.userId) return
  loading.value = true
  try {
    books.value = (await listAuthorBooks(store.userId)) ?? []
  } catch (err) {
    toast.error('作品列表加载失败', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    directionOptions.value = toCategoryOptions(await listCategories())
  } catch (err) {
    directionOptions.value = []
    toast.warning('分类列表加载失败，可手动输入方向', err instanceof Error ? err.message : undefined)
  }
}

onMounted(() => {
  loadBooks()
  loadCategories()
})

async function handleCreate() {
  const name = createForm.bookName.trim()
  if (!name) {
    toast.warning('请填写书名')
    return
  }
  creating.value = true
  try {
    // 后端 POST /book/create 已就绪：authorId 缺省时后端从登录态自动解析/创建作者档案
    const book = await createBook({
      bookName: name,
      bookDesc: createForm.bookDesc.trim(),
      workDirection: createForm.workDirection.trim() || null,
    })
    toast.success('作品已创建，进入创作台')
    createOpen.value = false
    createForm.bookName = ''
    createForm.bookDesc = ''
    if (book?.id) router.push(`/creator/${book.id}`)
    else await loadBooks()
  } catch (err) {
    toast.error('创建作品失败', err instanceof Error ? err.message : undefined)
  } finally {
    creating.value = false
  }
}

function openCreator(book: BookInfoVo) {
  if (!book.id) return
  router.push(`/creator/${book.id}`)
}
</script>

<template>
  <AppShell>
    <div class="flex items-center justify-between gap-3">
      <div>
        <h1 class="font-book text-xl font-semibold text-ink">创作工作台</h1>
        <p class="mt-1 text-xs text-ink-secondary">从一支笔、一叠稿纸开始——你的下一部作品。</p>
      </div>
      <div class="flex items-center gap-2">
        <Button variant="ghost" size="sm" :loading="loading" @click="loadBooks">
          <RefreshCw class="size-3.5" />
          刷新
        </Button>
        <Button variant="cinnabar" size="sm" @click="createOpen = true">
          <Plus class="size-3.5" />
          新建作品
        </Button>
      </div>
    </div>

    <!-- 作品陈列 -->
    <section class="mt-6" aria-label="我的作品">
      <div v-if="loading" class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        <div v-for="i in 4" :key="i" class="space-y-2">
          <Skeleton class="aspect-[3/4] w-full rounded-lg" />
          <Skeleton class="h-4 w-3/4" />
        </div>
      </div>

      <div v-else-if="books.length > 0" class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        <div v-for="book in books" :key="book.id" class="relative">
          <BookScrollCard :book="book" @open="openCreator" />
          <button
            v-if="book.id"
            type="button"
            class="absolute right-2 bottom-2 rounded-md border border-mist-line bg-paper-surface/95 px-2 py-1 text-[11px] text-ink-secondary shadow-card transition-colors hover:border-cinnabar/40 hover:text-cinnabar focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cinnabar/40"
            @click.stop="router.push(`/governance/${book.id}`)"
          >
            质量治理
          </button>
        </div>
      </div>

      <!-- 空态：首屏即创作 -->
      <Card v-else variant="plain" class="mt-2 border border-dashed border-mist-line bg-paper-surface/60">
        <div class="flex flex-col items-center gap-4 py-14 text-center">
          <span class="flex size-12 items-center justify-center rounded-md bg-cinnabar-wash text-cinnabar">
            <Feather class="size-6" />
          </span>
          <div>
            <p class="font-book text-base font-semibold text-ink">还没有作品</p>
            <p class="mt-1 text-xs leading-relaxed text-ink-secondary">
              立起第一叠稿纸：创建作品后即可用四档流水线与朱批旁注协作成书。
            </p>
          </div>
          <Button variant="cinnabar" @click="createOpen = true">
            <Feather class="size-4" />
            新建作品
          </Button>
        </div>
      </Card>
    </section>

    <!-- 新建作品对话框（文房表单） -->
    <Dialog
      v-model:open="createOpen"
      title="新建作品"
      description="一部作品的开始：一个书名，与一段想说的话。"
      width="md"
    >
      <form class="space-y-4" @submit.prevent="handleCreate">
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">书名</span>
          <Input v-model="createForm.bookName" placeholder="例：悬浮之城" maxlength="30" />
        </label>
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">作品方向</span>
          <Combobox
            v-model="createForm.workDirection"
            :options="directionOptions"
            placeholder="选择或输入自定义方向（可留空）"
            maxlength="20"
          />
        </label>
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">一句话简介</span>
          <Textarea v-model="createForm.bookDesc" :rows="3" placeholder="这本书讲什么？（可留空，之后在知识库完善世界观）" maxlength="200" />
        </label>
        <p class="text-[11px] leading-4 text-mist">
          创建后进入创作台：左侧章节树、中央稿纸、右侧四档 AI 流水线（FAST / STANDARD / REFINED / PRECISE）。
        </p>
      </form>
      <template #footer>
        <Button variant="ghost" @click="createOpen = false">取消</Button>
        <Button variant="cinnabar" :loading="creating" @click="handleCreate">立卷开笔</Button>
      </template>
    </Dialog>
  </AppShell>
</template>
