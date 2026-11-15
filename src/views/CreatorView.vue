<script setup lang="ts">
/**
 * 创作工作台（规范 4.2 核心页 · 重做）
 * 左：章节树 | 中：稿纸（Tiptap + 浮动朱批） | 右：四档流水线面板
 * 朱批语义：续写（章末追加）/ 润色（替换选区）/ 审校（全章修订），
 * 采纳时才真正落笔——审阅过程不污染稿纸。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Sparkles, Save, Send } from 'lucide-vue-next'
import CreationShell from '@/layouts/CreationShell.vue'
import { Button, Dialog, Input, Sheet } from '@/components/ui'
import ChapterTree from '@/components/base/ChapterTree.vue'
import DraftPaper from '@/components/base/DraftPaper.vue'
import PipelinePanel from '@/components/base/PipelinePanel.vue'
import VermilionNote from '@/components/base/VermilionNote.vue'
import { useToast } from '@/composables/useToast'
import { useChapters } from '@/composables/useChapters'
import { useAiCreation, type AiAction } from '@/composables/useAiCreation'
import { getBookInfo, publishBook } from '@/api/books'
import type { BookInfoVo, ChapterDraft, CreationMode } from '@/api/types'
import { textToTiptapDoc } from '@/utils/tiptap'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const bookId = Number(route.params.id)
if (!Number.isFinite(bookId) || bookId <= 0) {
  router.replace('/workbench')
}

/* ---------------- 书籍与章节 ---------------- */

const book = ref<BookInfoVo | null>(null)
const chapterTitle = ref('')
const chapters = useChapters(bookId)
const paperRef = ref<InstanceType<typeof DraftPaper> | null>(null)

const currentChapter = computed<ChapterDraft | null>(() => chapters.currentChapter.value)

async function loadBook() {
  try {
    book.value = await getBookInfo(bookId)
  } catch (err) {
    toast.error('作品信息加载失败', err instanceof Error ? err.message : undefined)
  }
}

function applyChapter(chapter: ChapterDraft | null) {
  chapterTitle.value = chapter?.title ?? ''
  paperRef.value?.setDoc(chapter?.content ?? { type: 'doc', content: [] })
}

watch(
  () => chapters.currentChapter.value,
  (chapter) => applyChapter(chapter),
)

onMounted(async () => {
  await Promise.all([loadBook(), chapters.loadAll()])
  applyChapter(chapters.currentChapter.value)
})

/* ---------------- 保存 / 发布 ---------------- */

const saving = computed(() => chapters.saving.value)
const publishing = ref(false)

async function handleSave() {
  const chapter = currentChapter.value
  if (!chapter?.id || !paperRef.value) {
    toast.warning('请先选择或创建章节')
    return
  }
  try {
    await chapters.save({
      id: chapter.id,
      title: chapterTitle.value.trim() || `第${chapter.number ?? '?'}章`,
      wordCount: paperRef.value.wordCount ?? 0,
      content: paperRef.value.getDoc() ?? { type: 'doc', content: [] },
    })
    toast.success('草稿已保存')
  } catch (err) {
    toast.error('保存失败', err instanceof Error ? err.message : undefined)
  }
}

async function handlePublish() {
  publishing.value = true
  try {
    await handleSave()
    await publishBook(bookId)
    toast.success('作品已发布', '将出现在发现与排行（榜单接口就绪后即时生效）')
  } catch (err) {
    // 预留端点降级（规范 6：发布二态为后端演进项）
    toast.warning('发布接口暂未开放', '后端补齐「草稿/发布」字段后此处即时可用；草稿已保存')
    console.info('publishBook reserved endpoint 未就绪', err)
  } finally {
    publishing.value = false
  }
}

function onKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
    e.preventDefault()
    handleSave()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

/* ---------------- 章节操作 ---------------- */

async function handleCreateChapter() {
  const next = (chapters.chapters.value.length ?? 0) + 1
  try {
    await chapters.create(`第${next}章`, undefined)
    toast.success(`已开新章：第${next}章`)
  } catch (err) {
    toast.error('新增章节失败', err instanceof Error ? err.message : undefined)
  }
}

const renameTarget = ref<ChapterDraft | null>(null)
const renameTitle = ref('')

function openRename(chapter: ChapterDraft) {
  renameTarget.value = chapter
  renameTitle.value = chapter.title ?? ''
}

async function confirmRename() {
  const target = renameTarget.value
  if (!target?.id || !paperRef.value) return
  const title = renameTitle.value.trim()
  if (!title) {
    toast.warning('章节标题不能为空')
    return
  }
  try {
    await chapters.save({
      id: target.id,
      title,
      wordCount: target.id === currentChapter.value?.id ? (paperRef.value.wordCount ?? 0) : (target.wordCount ?? 0),
      content:
        target.id === currentChapter.value?.id
          ? (paperRef.value.getDoc() ?? { type: 'doc', content: [] })
          : (target.content ?? { type: 'doc', content: [] }),
    })
    if (target.id === currentChapter.value?.id) chapterTitle.value = title
    renameTarget.value = null
    toast.success('章节已重命名')
  } catch (err) {
    toast.error('重命名失败', err instanceof Error ? err.message : undefined)
  }
}

/* ---------------- AI 流水线（朱批闭环） ---------------- */

const ai = useAiCreation()

const mode = ref<CreationMode>('STANDARD')
const maxRewriteRounds = ref('2')
const channel = ref<'sync' | 'async'>('sync')
const action = ref<AiAction>('续写')

const selection = reactive({ from: 0, to: 0, empty: true, text: '' })
const hasSelection = computed(() => !selection.empty && selection.text.trim().length > 0)

/** 润色朱批的原选区（采纳时替换用；运行期间选区可能漂移） */
const polishRanges = new Map<number, { from: number; to: number }>()

function buildRequest(act: AiAction) {
  const paper = paperRef.value
  const plain = paper?.getPlainText() ?? ''
  let userInput = ''
  let creationType = '续写'
  let anchorPos = 0
  let label = ''

  if (act === '续写') {
    userInput = plain || '（本章尚无内容，请自由开篇）'
    creationType = '续写'
    anchorPos = paper?.getEndPos() ?? 0
    label = '本章末尾'
  } else if (act === '润色') {
    userInput = selection.text
    creationType = '润色'
    anchorPos = selection.from
    label = `选中 ${selection.text.length} 字`
  } else {
    userInput = plain || '（本章尚无内容）'
    creationType = '润色' // 审校经 REFINED+ 管道产出修订稿
    anchorPos = 0
    label = '全章审校'
  }

  // 审校强制 REFINED 及以上（面板会同步显示校正后模式）
  const effectiveMode: CreationMode =
    act === '审校' && (mode.value === 'FAST' || mode.value === 'STANDARD') ? 'REFINED' : mode.value

  return {
    request: {
      bookId,
      chapterId: currentChapter.value?.id,
      title: chapterTitle.value.trim() || undefined,
      userInput,
      creationType,
      mode: effectiveMode,
      maxRewriteRounds: effectiveMode === 'FAST' ? undefined : Number(maxRewriteRounds.value),
    },
    anchorPos,
    label,
  }
}

async function handleRun(act: AiAction) {
  if (!currentChapter.value?.id) {
    toast.warning('请先创建章节再唤起 AI')
    return
  }
  if (act === '润色' && !hasSelection.value) {
    toast.warning('请先在稿纸上选中要润色的文字')
    return
  }
  const { request, anchorPos, label } = buildRequest(act)
  try {
    const noteId = await runWithNoteCapture(act, request, anchorPos, label)
    if (act === '润色') polishRanges.set(noteId, { from: selection.from, to: selection.to })
    if (act !== '润色') polishRanges.delete(noteId)
  } catch (err) {
    toast.error('AI 创作失败', err instanceof Error ? err.message : undefined)
  }
}

/** 捕获本次运行的朱批 id（runSync 内部开卡后无回传，这里通过 notes 首位推断） */
async function runWithNoteCapture(
  act: AiAction,
  request: Parameters<typeof ai.run>[0]['request'],
  anchorPos: number,
  label: string,
): Promise<number> {
  const before = new Set(ai.notes.map((n) => n.id))
  await ai.run({
    request,
    action: act,
    anchorPos,
    label,
    channel: channel.value,
    onAsyncCompleted: async () => {
      // 整章任务完成：重载章节（后端已把终稿落库）
      await chapters.loadAll()
      const fresh = chapters.chapters.value.find((c) => c.id === request.chapterId)
      if (fresh) {
        chapters.select(fresh)
        applyChapter(fresh)
      }
      toast.success('整章任务完成', '终稿已落库并载入稿纸')
    },
  })
  const created = ai.notes.find((n) => !before.has(n.id))
  return created?.id ?? -1
}

function handleAdopt(noteId: number) {
  const note = ai.notes.find((n) => n.id === noteId)
  if (!note || note.status === 'adopted') return
  const paper = paperRef.value
  if (!paper) return

  if (note.kind === '续写') {
    paper.appendText(note.text)
    toast.success('续写已落到章末')
  } else if (note.kind === '润色') {
    const range = polishRanges.get(noteId)
    if (range) {
      paper.replaceRange(range.from, range.to, note.text)
      toast.success('选区已替换为润色稿')
    } else {
      paper.appendText(note.text)
      toast.warning('原选区已失效，润色稿已附于章末')
    }
    polishRanges.delete(noteId)
  } else {
    paper.setDoc(textToTiptapDoc(note.text))
    toast.success('已采纳审校稿（全章替换，可 Ctrl+Z 撤销）')
  }
  ai.adoptNote(noteId)
}

function handleDismiss(noteId: number) {
  polishRanges.delete(noteId)
  ai.dismissNote(noteId)
}

const activeNotes = computed(() => ai.notes.filter((n) => n.status !== 'dismissed'))

/* ---------------- 窄屏流水线抽屉 ---------------- */

const pipelineSheetOpen = ref(false)
</script>

<template>
  <CreationShell :book-title="book?.bookName" @back="router.push('/workbench')">
    <template #title>
      <span class="font-book text-sm font-semibold text-ink">{{ book?.bookName || '未命名作品' }}</span>
      <input
        v-model="chapterTitle"
        class="ml-2 min-w-0 flex-1 rounded-sm border-none bg-transparent text-xs text-ink-secondary outline-none transition-colors placeholder:text-mist focus:bg-paper focus:px-1.5 focus:ring-1 focus:ring-ink-indigo/30"
        :placeholder="currentChapter ? '章节标题…' : '（尚未选择章节）'"
        aria-label="章节标题"
      >
    </template>

    <template #actions>
      <!-- 窄屏：唤起流水线抽屉 -->
      <Button variant="outline" size="sm" class="xl:hidden" @click="pipelineSheetOpen = true">
        <Sparkles class="size-3.5 text-cinnabar" />
        流水线
      </Button>
      <Button variant="outline" size="sm" :loading="saving" @click="handleSave">
        <Save class="size-3.5" />
        保存草稿
      </Button>
      <Button variant="cinnabar" size="sm" :loading="publishing" @click="handlePublish">
        <Send class="size-3.5" />
        发布
      </Button>
    </template>

    <template #left>
      <ChapterTree
        :chapters="chapters.chapters.value"
        :current-id="currentChapter?.id"
        :loading="chapters.loading.value"
        @select="chapters.select"
        @create="handleCreateChapter"
        @rename="openRename"
      />
    </template>

    <!-- 中央稿纸：朱批浮层 + 采纳/放弃 -->
    <DraftPaper
      ref="paperRef"
      :notes="ai.notes"
      margin-notes
      @selection-change="Object.assign(selection, $event)"
      @adopt="handleAdopt"
      @dismiss="handleDismiss"
    />

    <template #right>
      <div class="flex h-full min-h-0 flex-col">
        <header class="flex shrink-0 items-center gap-2 border-b border-mist-line px-4 py-3">
          <Sparkles class="size-4 text-cinnabar" />
          <h2 class="font-book text-sm font-semibold text-ink">AI 流水线</h2>
        </header>
        <PipelinePanel
          v-model:mode="mode"
          v-model:max-rewrite-rounds="maxRewriteRounds"
          v-model:channel="channel"
          v-model:action="action"
          :state="ai.state"
          :has-selection="hasSelection"
          @run="handleRun"
          @abort="ai.abort"
        />
      </div>
    </template>
  </CreationShell>

  <!-- 重命名对话框 -->
  <Dialog
    :open="!!renameTarget"
    title="重命名章节"
    width="sm"
    @update:open="(v) => !v && (renameTarget = null)"
  >
    <Input v-model="renameTitle" placeholder="章节标题" maxlength="50" @keydown.enter="confirmRename" />
    <template #footer>
      <Button variant="ghost" @click="renameTarget = null">取消</Button>
      <Button variant="cinnabar" @click="confirmRename">保存</Button>
    </template>
  </Dialog>

  <!-- 窄屏：流水线 + 朱批抽屉 -->
  <Sheet v-model:open="pipelineSheetOpen" title="AI 流水线与朱批" width-class="w-[min(92vw,22rem)]">
    <PipelinePanel
      v-model:mode="mode"
      v-model:max-rewrite-rounds="maxRewriteRounds"
      v-model:channel="channel"
      v-model:action="action"
      :state="ai.state"
      :has-selection="hasSelection"
      @run="(a) => { pipelineSheetOpen = false; handleRun(a) }"
      @abort="ai.abort"
    />
    <section v-if="activeNotes.length" class="mt-4" aria-label="朱批列表">
      <h3 class="font-book text-sm font-semibold text-ink">朱批（{{ activeNotes.length }}）</h3>
      <div class="mt-2 space-y-2">
        <VermilionNote
          v-for="note in activeNotes"
          :key="note.id"
          :note="note"
          @adopt="handleAdopt"
          @dismiss="handleDismiss"
        />
      </div>
    </section>
  </Sheet>
</template>
