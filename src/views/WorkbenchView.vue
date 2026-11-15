<script setup lang="ts">
/**
 * 创作工作台聚合页（规范 4.1：登录后第一落点）
 * 我的作品陈列 + 新建作品引导；无作品时首屏即引导新建。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Feather, Plus, RefreshCw } from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Button, Card, Dialog, Input, Select, Textarea, Skeleton } from '@/components/ui'
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
const creating = ref(false)

/* ---------------- 新建作品 ---------------- */

const createOpen = ref(false)
const createForm = reactive({
  bookName: '',
  bookDesc: '',
  workDirection: '0',
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

onMounted(loadBooks)

async function handleCreate() {
  const name = createForm.bookName.trim()
  if (!name) {
    toast.warning('请填写书名')
    return
  }
  creating.value = true
  try {
    // 预留端点（后端待补「建书」接口）：失败降级不阻塞
    const book = await createReserved({
      bookName: name,
      bookDesc: createForm.bookDesc.trim(),
      workDirection: Number(createForm.workDirection),
    })
    toast.success('作品已创建，进入创作台')
    createOpen.value = false
    createForm.bookName = ''
    createForm.bookDesc = ''
    if (book?.id) router.push(`/creator/${book.id}`)
    else await loadBooks()
  } catch (err) {
    toast.warning('后端暂未提供建书接口', '作品需先经后端入库；接口就绪后此处直接可用')
    console.info('createBook reserved endpoint 未就绪', err)
  } finally {
    creating.value = false
  }
}

// 预留：等后端 POST /book/create 就绪后切换为直接调用 api/books.createBook
async function createReserved(payload: { bookName: string; bookDesc?: string; workDirection?: number }) {
  const { createBook } = await import('@/api/books')
  return createBook(payload)
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
        <BookScrollCard v-for="book in books" :key="book.id" :book="book" @open="openCreator" />
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
          <Select
            v-model="createForm.workDirection"
            :options="[
              { label: '男频', value: '0' },
              { label: '女频', value: '1' },
            ]"
            width-class="w-36"
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
