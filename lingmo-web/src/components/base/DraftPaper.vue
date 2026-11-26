<script setup lang="ts">
/**
 * 稿纸编辑器（规范 3.1 / 5.4）
 * - Tiptap 封装：霞鹜文楷稿纸、克制工具栏、字数统计、选区上报
 * - 朱批锚定：ProseMirror decoration 标记锚定段落（不入文档数据），
 *   浮动层由 coordsAtPos 定位朱批卡于段落边际——审阅不污染稿纸，采纳时才真正落笔
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { EditorContent, useEditor, type JSONContent } from '@tiptap/vue-3'
import type { Editor } from '@tiptap/core'
import { Extension } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import CharacterCount from '@tiptap/extension-character-count'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import {
  Bold,
  Italic,
  Strikethrough,
  Heading2,
  Quote,
  Undo2,
  Redo2,
  Minus,
} from 'lucide-vue-next'
import { cn } from '@/utils/cn'
import { textToTiptapDoc, tiptapDocToPlainText } from '@/utils/tiptap'
import VermilionNote from './VermilionNote.vue'
import type { VermilionNote as VermilionNoteData } from '@/composables/useAiCreation'

/* ---------------- 朱批锚点 decoration 插件（不入文档数据） ---------------- */

interface AnchorMeta {
  positions: number[]
}

const anchorKey = new PluginKey<DecorationSet>('vermilionAnchor')

const VermilionAnchor = Extension.create({
  name: 'vermilionAnchor',
  addProseMirrorPlugins() {
    return [
      new Plugin<DecorationSet>({
        key: anchorKey,
        state: {
          init: () => DecorationSet.empty,
          apply(tr, old) {
            const meta = tr.getMeta(anchorKey) as AnchorMeta | undefined
            if (meta) {
              const decos: Decoration[] = []
              for (const pos of meta.positions) {
                const clamped = Math.min(Math.max(pos, 0), tr.doc.content.size)
                const $pos = tr.doc.resolve(clamped)
                for (let depth = $pos.depth; depth >= 0; depth -= 1) {
                  const node = $pos.node(depth)
                  if (node.type.name === 'paragraph' || node.type.name === 'heading') {
                    const start = $pos.before(depth)
                    decos.push(Decoration.node(start, start + node.nodeSize, { class: 'vermilion-anchor' }))
                    break
                  }
                }
              }
              return DecorationSet.create(tr.doc, decos)
            }
            return old.map(tr.mapping, tr.doc)
          },
        },
        props: {
          decorations(state) {
            return anchorKey.getState(state) ?? DecorationSet.empty
          },
        },
      }),
    ]
  },
})

/* ---------------- 组件 ---------------- */

const props = defineProps<{
  /** 朱批列表（浮动层渲染 + 段落锚定） */
  notes: VermilionNoteData[]
  /** 稿纸右侧边际是否渲染浮动朱批层（宽屏）；窄屏由父级收进侧栏 */
  marginNotes?: boolean
}>()

const emit = defineEmits<{
  'selection-change': [payload: { from: number; to: number; empty: boolean; text: string }]
  'word-count-change': [count: number]
  adopt: [noteId: number]
  dismiss: [noteId: number]
}>()

const paperEl = ref<HTMLElement | null>(null)

function countWordsOf(target: Editor | undefined): number {
  if (!target) return 0
  return target.storage.characterCount?.characters?.() ?? 0
}

/**
 * Tiptap 3.11 的 EditorContent 在挂载时检查 `editor.view.dom.firstChild`，
 * 空文档（无段落节点）会被判定为无内容而跳过挂载，且之后不会重试——
 * 表现就是稿纸永远空白。因此所有文档入口都保证至少有一个空段落。
 */
function normalizeDoc(doc: unknown): JSONContent {
  if (doc && typeof doc === 'object') {
    const candidate = doc as JSONContent
    if (candidate.type === 'doc' && Array.isArray(candidate.content) && candidate.content.length > 0) {
      return candidate
    }
  }
  return { type: 'doc', content: [{ type: 'paragraph' }] }
}

const editor = useEditor({
  extensions: [
    StarterKit,
    Placeholder.configure({ placeholder: '从这里落笔，写下这一章…' }),
    CharacterCount,
    VermilionAnchor,
  ],
  content: normalizeDoc(undefined),
  editorProps: {
    attributes: {
      class: 'draft-paper-prose focus:outline-none',
    },
  },
  onUpdate({ editor }) {
    wordCount.value = countWordsOf(editor)
    emit('word-count-change', wordCount.value)
    scheduleRecompute()
  },
  onSelectionUpdate({ editor }) {
    const { from, to, empty } = editor.state.selection
    emit('selection-change', {
      from,
      to,
      empty,
      text: editor.state.doc.textBetween(from, to, '\n'),
    })
  },
})

// useEditor 返回 shallowRef，事务不会自动触发 Vue 响应式；
// 字数必须在 onUpdate / setDoc 中显式写入 ref，否则工具栏会一直显示 0 字。
const wordCount = ref(0)

/* ---------------- 对外方法 ---------------- */

function setDoc(doc: JSONContent | unknown) {
  if (!editor.value) return
  let parsed: unknown = doc
  if (typeof doc === 'string' && doc.trim()) {
    try {
      parsed = JSON.parse(doc)
    } catch {
      parsed = textToTiptapDoc(doc)
    }
  }
  const content = normalizeDoc(parsed)
  editor.value.commands.setContent(content, { emitUpdate: false })
  nextTick(() => {
    wordCount.value = countWordsOf(editor.value)
    emit('word-count-change', wordCount.value)
    syncAnchors()
    scheduleRecompute()
  })
}

function getDoc(): JSONContent | null {
  return editor.value?.getJSON() ?? null
}

function getPlainText(): string {
  if (!editor.value) return ''
  return tiptapDocToPlainText(editor.value.getJSON())
}

function appendText(text: string) {
  if (!editor.value) return
  const doc = textToTiptapDoc(text)
  editor.value.chain().focus('end').insertContentAt(editor.value.state.doc.content.size, doc).run()
}

function replaceRange(from: number, to: number, text: string) {
  if (!editor.value) return
  const doc = textToTiptapDoc(text)
  editor.value.chain().focus().deleteRange({ from, to }).insertContentAt(from, doc).run()
}

function focus() {
  editor.value?.commands.focus()
}

/** 文档末尾 ProseMirror 位置（续写朱批锚点用） */
function getEndPos(): number {
  return editor.value?.state.doc.content.size ?? 0
}

defineExpose({ setDoc, getDoc, getPlainText, appendText, replaceRange, focus, getEndPos, wordCount })

/* ---------------- 浮动朱批层定位 ---------------- */

interface NoteLayout {
  id: number
  top: number
}

const noteLayouts = ref<NoteLayout[]>([])
let recomputeTimer: ReturnType<typeof setTimeout> | null = null

function scheduleRecompute() {
  if (recomputeTimer) clearTimeout(recomputeTimer)
  recomputeTimer = setTimeout(recomputeNoteLayouts, 60)
}

function recomputeNoteLayouts() {
  const view = editor.value?.view
  if (!view || !paperEl.value) {
    noteLayouts.value = []
    return
  }
  const editorRect = view.dom.getBoundingClientRect()
  const layouts: NoteLayout[] = []
  for (const note of props.notes) {
    if (note.status === 'dismissed') continue
    const pos = Math.min(Math.max(note.anchorPos, 0), view.state.doc.content.size)
    try {
      const coords = view.coordsAtPos(pos)
      layouts.push({ id: note.id, top: coords.top - editorRect.top })
    } catch {
      // 锚点失效（文档结构变化后越界）：跳过，不阻塞其他批注
    }
  }
  noteLayouts.value = layouts
}

function syncAnchors() {
  const view = editor.value?.view
  if (!view) return
  const positions = props.notes
    .filter((n) => n.status === 'streaming' || n.status === 'ready')
    .map((n) => Math.min(Math.max(n.anchorPos, 0), view.state.doc.content.size))
  view.dispatch(view.state.tr.setMeta(anchorKey, { positions }))
}

watch(
  () => props.notes.map((n) => `${n.id}:${n.status}:${n.text.length}`).join('|'),
  () => {
    syncAnchors()
    scheduleRecompute()
  },
  { deep: false },
)

let resizeObserver: ResizeObserver | null = null
let scrollHandler: (() => void) | null = null

onMounted(() => {
  resizeObserver = new ResizeObserver(() => scheduleRecompute())
  if (editor.value?.view?.dom) resizeObserver.observe(editor.value.view.dom)
  scrollHandler = () => scheduleRecompute()
  window.addEventListener('scroll', scrollHandler, { passive: true })
  window.addEventListener('resize', scrollHandler, { passive: true })
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  if (scrollHandler) {
    window.removeEventListener('scroll', scrollHandler)
    window.removeEventListener('resize', scrollHandler)
  }
  if (recomputeTimer) clearTimeout(recomputeTimer)
  editor.value?.destroy()
})

const visibleNotes = computed(() => props.notes.filter((n) => n.status !== 'dismissed'))

function layoutOf(id: number): NoteLayout | undefined {
  return noteLayouts.value.find((l) => l.id === id)
}

interface ToolButton {
  title: string
  icon: Component
  run: () => void
  active: boolean
  disabled: boolean
}

const toolbar = computed<ToolButton[]>(() => {
  const e = editor.value
  if (!e) return []
  return [
    { title: '撤销', icon: Undo2, run: () => e.chain().focus().undo().run(), active: false, disabled: !e.can().undo() },
    { title: '重做', icon: Redo2, run: () => e.chain().focus().redo().run(), active: false, disabled: !e.can().redo() },
    { title: '加粗', icon: Bold, run: () => e.chain().focus().toggleBold().run(), active: e.isActive('bold'), disabled: false },
    { title: '斜体', icon: Italic, run: () => e.chain().focus().toggleItalic().run(), active: e.isActive('italic'), disabled: false },
    { title: '删除线', icon: Strikethrough, run: () => e.chain().focus().toggleStrike().run(), active: e.isActive('strike'), disabled: false },
    { title: '章内小节', icon: Heading2, run: () => e.chain().focus().toggleHeading({ level: 2 }).run(), active: e.isActive('heading', { level: 2 }), disabled: false },
    { title: '引文', icon: Quote, run: () => e.chain().focus().toggleBlockquote().run(), active: e.isActive('blockquote'), disabled: false },
    { title: '分隔线', icon: Minus, run: () => e.chain().focus().setHorizontalRule().run(), active: false, disabled: false },
  ]
})
</script>

<template>
  <div ref="paperEl" class="paper-shell relative flex h-full min-h-0 w-full flex-col bg-paper-surface">
    <!-- 工具栏 -->
    <div v-if="editor" class="flex items-center gap-0.5 border-b border-mist-line px-3 py-1.5">
      <template v-for="(btn, i) in toolbar" :key="btn.title">
        <span v-if="i === 2 || i === 6" class="mx-1 h-4 w-px bg-mist-line" />
        <button
          type="button"
          :title="btn.title"
          :aria-label="btn.title"
          :disabled="btn.disabled"
          :class="cn(
            'inline-flex size-7 items-center justify-center rounded-sm text-ink-secondary transition-colors duration-150 ease-[var(--ease-paper)]',
            'hover:bg-paper hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40',
            btn.active && 'bg-ink-indigo/10 text-ink-indigo',
            btn.disabled && 'pointer-events-none opacity-40',
          )"
          @click="btn.run"
        >
          <component :is="btn.icon" class="size-3.5" />
        </button>
      </template>
      <span class="ml-auto shrink-0 pr-1 text-xs text-ink-secondary">{{ wordCount }} 字</span>
    </div>

    <!-- 稿纸主体 -->
    <div class="relative min-h-0 flex-1 overflow-y-auto bg-paper">
      <div class="paper-column relative mx-auto w-full max-w-3xl px-6 py-10 lg:px-10 lg:pr-[19.5rem]">
        <EditorContent :editor="editor" class="paper-editor" />

        <!-- 浮动朱批层：锚定段落边际（宽屏稿纸右栏；窄屏收进右侧抽屉） -->
        <div
          v-if="marginNotes"
          class="pointer-events-none absolute inset-y-0 right-6 z-10 hidden w-[16.5rem] lg:right-10 lg:block"
          aria-label="朱批旁注"
        >
          <div
            v-for="note in visibleNotes"
            :key="note.id"
            class="pointer-events-auto absolute right-0"
            :style="{ top: `${layoutOf(note.id) ? Math.max(0, layoutOf(note.id)!.top) : -9999}px` }"
          >
            <VermilionNote :note="note" @adopt="emit('adopt', $event)" @dismiss="emit('dismiss', $event)" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
/* 稿纸正文：楷体、书卷行距（全局类供 ProseMirror 内容使用，非 scoped） */
.draft-paper-prose {
  font-family: var(--font-kai);
  font-size: 16px;
  line-height: 1.9;
  color: var(--ink);
  caret-color: var(--cinnabar);
}

.draft-paper-prose p {
  margin: 0 0 0.9em;
  text-indent: 2em;
}

.draft-paper-prose p.is-editor-empty:first-child::before {
  content: attr(data-placeholder);
  color: var(--mist);
  float: left;
  height: 0;
  pointer-events: none;
}

.draft-paper-prose h2 {
  font-family: var(--font-book);
  font-size: 1.05em;
  font-weight: 600;
  margin: 1.2em 0 0.6em;
}

.draft-paper-prose blockquote {
  border-left: 2px solid var(--mist-line);
  color: var(--ink-secondary);
  margin: 0.8em 0;
  padding-left: 1em;
}

.draft-paper-prose hr {
  border: none;
  border-top: 1px solid var(--mist-line);
  margin: 1.6em auto;
  width: 30%;
}

/* 朱批锚定段落：纸面淡朱晕 + 细朱线纵贯 */
.draft-paper-prose .vermilion-anchor {
  background: linear-gradient(to right, rgba(180, 68, 59, 0.04), transparent 24%);
  box-shadow: inset 2px 0 0 var(--cinnabar-line);
}
</style>
