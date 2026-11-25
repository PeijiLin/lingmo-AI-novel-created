<script setup lang="ts">
/**
 * 知识库详情（规范 4.2：角色 / 世界观 / 素材）
 * 角色卡建、改、删 + 世界观素材入库（供 AI 续写检索）。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { PenLine, Plus, Trash } from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Button, Card, Dialog, Input, Skeleton, Textarea } from '@/components/ui'
import { useToast } from '@/composables/useToast'
import { explainAiError } from '@/utils/aiError'
import {
  addKnowledgeBase,
  createCharacterCard,
  deleteCharacterCard,
  listCharacterCards,
  pageKnowledgeBase,
  updateCharacterCard,
  uploadKbContent,
} from '@/api/knowledge'
import type { CharacterCardVo } from '@/api/types'

const route = useRoute()
const toast = useToast()

const bookId = Number(route.params.id)
const characters = ref<CharacterCardVo[]>([])
const loading = ref(false)
const kbId = ref<number | null>(null)

/* ---------------- 角色卡 ---------------- */

interface CardForm {
  open: boolean
  id: number
  name: string
  age: string
  gender: string
  occupation: string
  personality: string
  background: string
  goals: string
  weaknesses: string
}

const emptyForm: Omit<CardForm, 'open' | 'id'> = {
  name: '',
  age: '',
  gender: '',
  occupation: '',
  personality: '',
  background: '',
  goals: '',
  weaknesses: '',
}

const editor = reactive<CardForm>({ open: false, id: 0, ...emptyForm })
const saving = ref(false)

function openCreate() {
  Object.assign(editor, { open: true, id: 0 }, emptyForm)
}

function openEdit(card: CharacterCardVo) {
  Object.assign(editor, {
    open: true,
    id: card.id ?? 0,
    name: card.name ?? '',
    age: card.age == null ? '' : String(card.age),
    gender: card.gender ?? '',
    occupation: card.occupation ?? '',
    personality: card.personality ?? '',
    background: card.background ?? '',
    goals: card.goals ?? '',
    weaknesses: card.weaknesses ?? '',
  })
}

function payload() {
  return {
    name: editor.name.trim(),
    age: editor.age ? Number(editor.age) : undefined,
    gender: editor.gender.trim() || undefined,
    occupation: editor.occupation.trim() || undefined,
    personality: editor.personality.trim() || undefined,
    background: editor.background.trim() || undefined,
    goals: editor.goals.trim() || undefined,
    weaknesses: editor.weaknesses.trim() || undefined,
  }
}

async function loadCharacters() {
  loading.value = true
  try {
    characters.value = (await listCharacterCards(bookId)) ?? []
  } catch (err) {
    toast.error('角色卡加载失败', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

async function saveCard() {
  if (!editor.name.trim()) {
    toast.warning('请填写角色名')
    return
  }
  saving.value = true
  try {
    if (editor.id) {
      await updateCharacterCard({ ...payload(), id: editor.id, bookId })
      toast.success('角色卡已更新')
    } else {
      await createCharacterCard({ ...payload(), bookId })
      toast.success('角色卡已建立')
    }
    editor.open = false
    await loadCharacters()
  } catch (err) {
    toast.error('保存失败', err instanceof Error ? err.message : undefined)
  } finally {
    saving.value = false
  }
}

async function removeCard(id: number) {
  try {
    await deleteCharacterCard(id)
    toast.success('已删除角色卡')
    await loadCharacters()
  } catch (err) {
    toast.error('删除失败', err instanceof Error ? err.message : undefined)
  }
}

/* ---------------- 世界观素材 ---------------- */

const material = ref('')
const uploading = ref(false)

async function ensureKb(): Promise<number | null> {
  if (kbId.value) return kbId.value
  try {
    const page = await pageKnowledgeBase({ pageNum: 1, pageSize: 1, bookId })
    if (page.records?.length && page.records[0].id) {
      kbId.value = page.records[0].id
      return kbId.value
    }
    const created = await addKnowledgeBase({ bookId, name: '作品知识库', description: '' })
    kbId.value = created || null
    return kbId.value
  } catch {
    return null
  }
}

async function submitMaterial() {
  const text = material.value.trim()
  if (!text) {
    toast.warning('先写一段世界观 / 素材')
    return
  }
  uploading.value = true
  try {
    const id = await ensureKb()
    if (!id) {
      toast.warning('知识库初始化失败，请稍后重试')
      return
    }
    await uploadKbContent(id, text)
    toast.success('素材已入库')
    material.value = ''
  } catch (err) {
    toast.error('上传失败', explainAiError(err) ?? (err instanceof Error ? err.message : undefined))
  } finally {
    uploading.value = false
  }
}

onMounted(async () => {
  await loadCharacters()
  await ensureKb()
})
</script>

<template>
  <AppShell>
    <div class="flex items-center justify-between gap-3">
      <div>
        <h1 class="font-book text-xl font-semibold text-ink">知识库</h1>
        <p class="mt-1 text-xs text-ink-secondary">角色卡与世界观素材，供 AI 续写时检索。</p>
      </div>
      <Button variant="cinnabar" size="sm" @click="openCreate">
        <Plus class="size-3.5" />
        新建角色卡
      </Button>
    </div>

    <!-- 角色卡 -->
    <section class="mt-6" aria-label="角色卡">
      <h2 class="font-book text-base font-semibold text-ink">角色卡</h2>
      <div v-if="loading" class="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Skeleton v-for="i in 3" :key="i" class="h-40 rounded-lg" />
      </div>
      <p v-else-if="characters.length === 0" class="mt-3 text-sm text-ink-secondary">
        还没有角色。为你的主角立一张卡。
      </p>
      <div v-else class="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Card v-for="card in characters" :key="card.id" :interactive="false">
          <template #title>{{ card.name }}</template>
          <template #description>
            <template v-if="card.gender">{{ card.gender }}</template>
            <template v-if="card.age"> · {{ card.age }} 岁</template>
            <template v-if="card.occupation"> · {{ card.occupation }}</template>
          </template>
          <template #action>
            <div class="flex items-center gap-1">
              <Button variant="ghost" size="icon-sm" aria-label="编辑角色卡" @click="openEdit(card)">
                <PenLine class="size-3.5" />
              </Button>
              <Button variant="ghost" size="icon-sm" aria-label="删除角色卡" @click="removeCard(card.id!)">
                <Trash class="size-3.5 text-cinnabar" />
              </Button>
            </div>
          </template>
          <div class="space-y-1.5">
            <p v-if="card.personality" class="text-xs text-ink-secondary"><span class="text-mist">性格：</span>{{ card.personality }}</p>
            <p v-if="card.goals" class="text-xs text-ink-secondary"><span class="text-mist">目标：</span>{{ card.goals }}</p>
            <p v-if="card.weaknesses" class="text-xs text-ink-secondary"><span class="text-mist">弱点：</span>{{ card.weaknesses }}</p>
            <p v-if="card.background" class="line-clamp-2 text-xs leading-relaxed text-ink-secondary">
              <span class="text-mist">背景：</span>{{ card.background }}
            </p>
          </div>
        </Card>
      </div>
    </section>

    <!-- 世界观素材 -->
    <section class="mt-8" aria-label="世界观素材">
      <h2 class="font-book text-base font-semibold text-ink">世界观素材</h2>
      <div class="mt-3 rounded-lg border border-mist-line bg-paper-surface p-4 shadow-card">
        <Textarea v-model="material" :rows="3" placeholder="写下一段世界观设定或剧情素材，入库后与 AI 续写时检索…" />
        <div class="mt-2 flex justify-end">
          <Button variant="outline" size="sm" :loading="uploading" @click="submitMaterial">入库</Button>
        </div>
      </div>
    </section>

    <!-- 角色卡编辑 -->
    <Dialog
      :open="editor.open"
      :title="editor.id ? '编辑角色卡' : '新建角色卡'"
      width="lg"
      @update:open="(v) => !v && (editor.open = false)"
    >
      <form class="grid grid-cols-1 gap-3 sm:grid-cols-2" @submit.prevent="saveCard">
        <label class="block">
          <span class="mb-1 block text-xs text-ink-secondary">姓名</span>
          <Input v-model="editor.name" placeholder="角色名" />
        </label>
        <div class="grid grid-cols-2 gap-3">
          <label class="block">
            <span class="mb-1 block text-xs text-ink-secondary">年龄</span>
            <Input v-model="editor.age" placeholder="如 18" />
          </label>
          <label class="block">
            <span class="mb-1 block text-xs text-ink-secondary">性别</span>
            <Input v-model="editor.gender" placeholder="男 / 女" />
          </label>
        </div>
        <label class="block sm:col-span-2">
          <span class="mb-1 block text-xs text-ink-secondary">职业</span>
          <Input v-model="editor.occupation" placeholder="如 旅人 / 铸钟者" />
        </label>
        <label class="block sm:col-span-2">
          <span class="mb-1 block text-xs text-ink-secondary">性格</span>
          <Input v-model="editor.personality" placeholder="如 外言喻内，护短" />
        </label>
        <label class="block sm:col-span-2">
          <span class="mb-1 block text-xs text-ink-secondary">背景</span>
          <Textarea v-model="editor.background" :rows="2" placeholder="人物背景…" />
        </label>
        <label class="block sm:col-span-2">
          <span class="mb-1 block text-xs text-ink-secondary">目标 / 弱点</span>
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Input v-model="editor.goals" placeholder="目标" />
            <Input v-model="editor.weaknesses" placeholder="弱点" />
          </div>
        </label>
      </form>
      <template #footer>
        <Button variant="ghost" @click="editor.open = false">取消</Button>
        <Button variant="cinnabar" :loading="saving" @click="saveCard">保存</Button>
      </template>
    </Dialog>
  </AppShell>
</template>