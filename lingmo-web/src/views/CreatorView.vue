<script setup lang="ts">
/**
 * 创作工作台（规范 4.2 核心页 · 重做）
 * 左：章节树 | 中：稿纸（Tiptap + 浮动朱批） | 右：四档流水线面板
 * 朱批语义：续写（章末追加）/ 润色（替换选区）/ 审校（全章修订），
 * 采纳时才真正落笔——审阅过程不污染稿纸。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Sparkles, Save, Send, History, ShieldCheck, ListTree, Maximize2, Minimize2 } from 'lucide-vue-next'
import CreationShell from '@/layouts/CreationShell.vue'
import { Button, Dialog, Input, Sheet } from '@/components/ui'
import ChapterTree from '@/components/base/ChapterTree.vue'
import DraftPaper from '@/components/base/DraftPaper.vue'
import PipelinePanel from '@/components/base/PipelinePanel.vue'
import PipelineRunInspector from '@/components/base/PipelineRunInspector.vue'
import VermilionNote from '@/components/base/VermilionNote.vue'
import ReviewSheet from '@/components/base/ReviewSheet.vue'
import { useToast } from '@/composables/useToast'
import { useReviewQueue } from '@/composables/useReviewQueue'
import { explainAiError } from '@/utils/aiError'
import { useChapters } from '@/composables/useChapters'
import { useAiCreation, type AiAction } from '@/composables/useAiCreation'
import { useMotionPreference } from '@/composables/useMotionPreference'
import { getBookInfo, publishBook } from '@/api/books'
import type { BookInfoVo, ChapterDraft, CreationMode } from '@/api/types'
import { textToTiptapDoc } from '@/utils/tiptap'
import { parsePositiveInt } from '@/utils/validation'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const bookId = Number(route.params.id)
if (!Number.isFinite(bookId) || bookId <= 0) {
  router.replace('/workbench')
}

/** P1 试点：一键开关动效，便于真实使用时对照体验。 */
const motion = useMotionPreference()

/* ---------------- 书籍与章节 ---------------- */

const book = ref<BookInfoVo | null>(null)
const chapterTitle = ref('')
const chapters = useChapters(bookId)
const paperRef = ref<InstanceType<typeof DraftPaper> | null>(null)
const focusMode = ref(false)

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

/* ---------------- 人审待办（延迟拉取，不阻塞创作台首屏） ---------------- */

const review = useReviewQueue(bookId)
const reviewSheetOpen = ref(false)
const pendingReviewCount = computed(() => {
  const counts = review.overview.value?.counts
  if (!counts) return 0
  return (counts.conflict ?? 0) + (counts.assertion ?? 0) + (counts.safety ?? 0) + (counts.unmet ?? 0)
})

onMounted(async () => {
  await Promise.all([loadBook(), chapters.loadAll()])
  applyChapter(chapters.currentChapter.value)
})

onMounted(() => {
  const loadReviewBadge = () => {
    void review.load()
  }
  if (typeof window.requestIdleCallback === 'function') {
    window.requestIdleCallback(loadReviewBadge)
  } else {
    window.setTimeout(loadReviewBadge, 2000)
  }
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
    toast.success('作品已发布', '草稿已转写为正式章节，将出现在发现与排行')
  } catch (err) {
    toast.error('发布失败', err instanceof Error ? err.message : undefined)
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
const targetWordCount = ref('')
const pov = ref('')

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
      targetWordCount: parsePositiveInt(targetWordCount.value),
      pov: pov.value.trim() || undefined,
    },
    anchorPos,
    label,
  }
}

async function handleRun(act: AiAction) {
  if (targetWordCount.value.trim() && parsePositiveInt(targetWordCount.value) === undefined) {
    toast.warning('目标字数需为正整数')
    return
  }
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
    // 异步整章任务没有流式朱批卡（noteId 为 -1），不要把 -1 写进润色区间表
    if (channel.value === 'sync') {
      if (act === '润色') polishRanges.set(noteId, { from: selection.from, to: selection.to })
      else polishRanges.delete(noteId)
    }
  } catch (err) {
    toast.error('AI 创作失败', explainAiError(err) ?? (err instanceof Error ? err.message : undefined))
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

/* ---------------- 流水线抽屉与运行详情 ---------------- */

const pipelineSheetOpen = ref(false)
const chapterSheetOpen = ref(false)
const runInspectorOpen = ref(false)
</script>

<template>
  <CreationShell :book-title="book?.bookName" :focus-mode="focusMode" @back="router.push('/workbench')">
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
      <div class="ml-auto flex shrink-0 items-center gap-1">
        <!-- 窄屏：章节树抽屉（顶栏空间有限，按钮只留图标） -->
        <Button
          variant="ghost"
          size="sm"
          :class="focusMode ? '' : 'md:hidden'"
          aria-label="章节目录"
          @click="chapterSheetOpen = true"
        >
          <ListTree class="size-3.5" />
          <span class="hidden sm:inline">章节</span>
        </Button>
        <Button
          variant="ghost"
          size="sm"
          :aria-label="focusMode ? '退出专注模式' : '专注模式'"
          :aria-pressed="focusMode"
          @click="focusMode = !focusMode"
        >
          <Minimize2 v-if="focusMode" class="size-3.5 text-cinnabar" />
          <Maximize2 v-else class="size-3.5" />
          <span class="hidden sm:inline">{{ focusMode ? '退出专注' : '专注' }}</span>
        </Button>
        <!-- 人审队列：Sheet 内处理，不卸载稿纸 -->
        <Button variant="outline" size="sm" aria-label="人审队列" @click="reviewSheetOpen = true">
          <ShieldCheck class="size-3.5 text-cinnabar" />
          <span class="hidden sm:inline">人审{{ pendingReviewCount ? ` ${pendingReviewCount}` : '' }}</span>
        </Button>
        <!-- 窄屏：唤起流水线抽屉 -->
        <Button variant="outline" size="sm" :class="focusMode ? '' : 'xl:hidden'" aria-label="AI 流水线" @click="pipelineSheetOpen = true">
          <Sparkles class="size-3.5 text-cinnabar" />
          <span class="hidden sm:inline">流水线</span>
        </Button>
        <Button variant="outline" size="sm" aria-label="保存草稿" :loading="saving" @click="handleSave">
          <Save class="size-3.5" />
          <span class="hidden sm:inline">保存草稿</span>
        </Button>
        <Button variant="cinnabar" size="sm" aria-label="发布" :loading="publishing" @click="handlePublish">
          <Send class="size-3.5" />
          <span class="hidden sm:inline">发布</span>
        </Button>
      </div>
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
          <Button
            variant="ghost"
            size="sm"
            class="ml-auto"
            :aria-pressed="motion.enabled.value"
            :title="motion.enabled.value ? '关闭动效' : '开启动效'"
            @click="motion.toggle()"
          >
            <Sparkles class="size-3.5" />
            动效{{ motion.enabled.value ? '开' : '关' }}
          </Button>
          <Button
            v-if="ai.state.taskId"
            variant="ghost"
            size="sm"
            @click="runInspectorOpen = true"
          >
            <History class="size-3.5" />
            运行详情
          </Button>
        </header>
        <PipelinePanel
          v-model:mode="mode"
          v-model:max-rewrite-rounds="maxRewriteRounds"
          v-model:channel="channel"
          v-model:action="action"
          v-model:target-word-count="targetWordCount"
          v-model:pov="pov"
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
      v-model:target-word-count="targetWordCount"
      v-model:pov="pov"
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

  <!-- 窄屏：章节目录抽屉（宽屏由 CreationShell 左栏直接展示） -->
  <Sheet v-model:open="chapterSheetOpen" title="章节目录" width-class="w-[min(92vw,22rem)]">
    <ChapterTree
      :chapters="chapters.chapters.value"
      :current-id="currentChapter?.id"
      :loading="chapters.loading.value"
      @select="(chapter) => { chapters.select(chapter); chapterSheetOpen = false }"
      @create="handleCreateChapter"
      @rename="openRename"
    />
  </Sheet>

  <!-- 运行详情：节点与阶段产物概览 -->
  <PipelineRunInspector v-model:open="runInspectorOpen" :run-id="ai.state.taskId" />

  <!-- 人审抽屉：保留稿纸编辑态 -->
  <ReviewSheet v-model:open="reviewSheetOpen" :book-id="bookId" />
</template>
