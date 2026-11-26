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
  listCharacterRelationships,
  listKbSources,
  pageKnowledgeBase,
  removeKbSource,
  updateCharacterCard,
  uploadKbContent,
} from '@/api/knowledge'
import type { CharacterCardVo, CharacterRelationship, KbSourceVo } from '@/api/types'

const route = useRoute()
const toast = useToast()

const bookId = Number(route.params.id)
const characters = ref<CharacterCardVo[]>([])
const loading = ref(false)
const kbId = ref<number | null>(null)
const relations = ref<CharacterRelationship[]>([])

/* ---------------- 角色卡 ---------------- */

interface RelationRow {
  toCharacterId: number | null
  relationshipType: string
  description: string
  intensity: string
}

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
  relationships: RelationRow[]
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
  relationships: [],
}

const editor = reactive<CardForm>({ open: false, id: 0, ...emptyForm })
const saving = ref(false)

function openCreate() {
  Object.assign(editor, { open: true, id: 0 }, emptyForm, { relationships: [] })
}

async function openEdit(card: CharacterCardVo) {
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
    relationships: [],
  })
  try {
    const all = await listCharacterRelationships(bookId)
    editor.relationships = all
      .filter((rel) => rel.fromCharacterId === card.id)
      .map((rel) => ({
        toCharacterId: rel.toCharacterId ?? null,
        relationshipType: rel.relationshipType ?? rel.relationType ?? '',
        description: rel.description ?? '',
        intensity: rel.intensity == null ? '' : String(rel.intensity),
      }))
  } catch {
    // 关系加载失败不阻塞卡片编辑
  }
}

function addRelationship() {
  editor.relationships.push({ toCharacterId: null, relationshipType: '', description: '', intensity: '' })
}

function removeRelationship(index: number) {
  editor.relationships.splice(index, 1)
}

function relationsOf(card: CharacterCardVo) {
  return relations.value.filter((rel) => rel.fromCharacterId === card.id)
}

function relationshipText(rel: CharacterRelationship) {
  const target = characters.value.find((c) => c.id === rel.toCharacterId)
  return `${target?.name ?? '角色'}（${rel.relationshipType ?? rel.relationType ?? '相关'}）`
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
    relationships: editor.relationships
      .filter((rel) => rel.toCharacterId != null && rel.toCharacterId !== editor.id)
      .map((rel) => ({
        toCharacterId: Number(rel.toCharacterId),
        relationshipType: rel.relationshipType.trim() || '相关',
        description: rel.description.trim() || undefined,
        intensity: rel.intensity ? Number(rel.intensity) : undefined,
      })),
  }
}

async function loadCharacters() {
  loading.value = true
  try {
    characters.value = (await listCharacterCards(bookId)) ?? []
    try {
      relations.value = (await listCharacterRelationships(bookId)) ?? []
    } catch {
      relations.value = []
    }
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
const sources = ref<KbSourceVo[]>([])

async function loadSources() {
  try {
    sources.value = (await listKbSources(bookId)) ?? []
  } catch {
    sources.value = []
  }
}

async function removeSource(source: KbSourceVo) {
  if (!source.id) return
  try {
    await removeKbSource(source.id)
    toast.success('素材已删除（文本块与向量同步清理）')
    await loadSources()
  } catch (err) {
    toast.error('删除失败', err instanceof Error ? err.message : undefined)
  }
}

function kindText(kind?: string) {
  if (kind === 'UPLOAD') return '文件'
  if (kind === 'URL') return '链接'
  return '文本'
}

function statusText(status?: string) {
  if (status === 'READY') return '已入库'
  if (status === 'FAILED') return '解析失败'
  if (status === 'PARSING') return '解析中'
  return status ?? '-'
}

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
    await loadSources()
  } catch (err) {
    toast.error('上传失败', explainAiError(err) ?? (err instanceof Error ? err.message : undefined))
  } finally {
    uploading.value = false
  }
}

onMounted(async () => {
  await loadCharacters()
  await ensureKb()
  await loadSources()
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
            <p v-if="relationsOf(card).length" class="text-xs text-ink-secondary">
              <span class="text-mist">关系：</span>{{ relationsOf(card).slice(0, 2).map(relationshipText).join('、') }}
            </p>
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

      <!-- 素材清单（novel_kb_source）：文本块与向量的权威来源 -->
      <div v-if="sources.length" class="mt-4">
        <p class="mb-2 text-xs text-ink-secondary">
          已入库素材 {{ sources.length }} 条；删除素材会同步清理其文本块与检索向量。
        </p>
        <ul class="divide-y divide-mist-line/60 rounded-lg border border-mist-line bg-paper-surface">
          <li
            v-for="source in sources"
            :key="source.id"
            class="flex items-center justify-between gap-3 px-3 py-2 text-sm"
          >
            <div class="min-w-0">
              <p class="truncate text-ink">{{ source.name }}</p>
              <p class="mt-0.5 text-xs text-mist">
                {{ kindText(source.sourceKind) }} · {{ statusText(source.status) }}
                <template v-if="source.createdAt"> · {{ source.createdAt.slice(0, 10) }}</template>
              </p>
            </div>
            <Button
              variant="ghost"
              size="icon-sm"
              :aria-label="`删除素材 ${source.name}`"
              @click="removeSource(source)"
            >
              <Trash class="size-3.5 text-cinnabar" />
            </Button>
          </li>
        </ul>
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
        <div class="sm:col-span-2">
          <div class="flex items-center justify-between">
            <span class="text-xs text-ink-secondary">角色关系</span>
            <button type="button" class="text-xs text-ink-secondary hover:text-ink" @click="addRelationship">
              + 添加关系
            </button>
          </div>
          <p v-if="characters.filter((c) => c.id !== editor.id).length === 0" class="mt-1 text-xs text-mist">
            先建立另一个角色，才能设置关系。
          </p>
          <div
            v-for="(rel, index) in editor.relationships"
            :key="index"
            class="mt-2 grid grid-cols-1 gap-2 rounded-md border border-mist-line/60 bg-paper-surface/60 p-2 sm:grid-cols-12"
          >
            <select
              v-model="rel.toCharacterId"
              class="h-9 w-full rounded-md border border-mist-line bg-paper-surface px-2 text-sm text-ink outline-none focus:ring-1 focus:ring-ink/20 sm:col-span-3"
            >
              <option :value="null">选择角色</option>
              <option v-for="c in characters.filter((c) => c.id !== editor.id)" :key="c.id" :value="c.id">
                {{ c.name }}
              </option>
            </select>
            <Input v-model="rel.relationshipType" class="sm:col-span-3" placeholder="关系：师徒 / 宿敌" />
            <Input v-model="rel.description" class="sm:col-span-4" placeholder="说明（可选）" />
            <Input v-model="rel.intensity" class="sm:col-span-1" placeholder="强度" />
            <button type="button" class="text-left text-xs text-cinnabar hover:underline sm:col-span-1" @click="removeRelationship(index)">
              删除
            </button>
          </div>
          <p class="mt-1 text-xs text-mist">
            关系会写入正典账本，并按当前章注入 Writer / Critic；关系变化须由剧情推进，模型不得自行新增。
          </p>
        </div>
      </form>
      <template #footer>
        <Button variant="ghost" @click="editor.open = false">取消</Button>
        <Button variant="cinnabar" :loading="saving" @click="saveCard">保存</Button>
      </template>
    </Dialog>
  </AppShell>
</template>