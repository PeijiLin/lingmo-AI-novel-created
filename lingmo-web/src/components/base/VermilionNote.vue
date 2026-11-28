<script setup lang="ts">
/**
 * 朱批旁注卡（规范 3.1 签名元素）
 * AI 的续写 / 润色 / 审校结果以稿纸边际的朱砂批注浮现：
 * 朱砂红底卡、墨色文字、细朱线连接锚点、可折叠。
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { ChevronDown, Loader2, Check, X, PenLine } from 'lucide-vue-next'
import type { VermilionNote } from '@/composables/useAiCreation'
import { useMotionPreference } from '@/composables/useMotionPreference'
import StreamingInkText from './StreamingInkText.vue'

const props = defineProps<{
  note: VermilionNote
}>()

const emit = defineEmits<{
  adopt: [id: number]
  dismiss: [id: number]
}>()

const collapsed = ref(false)
const motion = useMotionPreference()
const motionEnabled = computed(() => motion.enabled.value)
const exiting = ref(false)
let exitTimer: ReturnType<typeof setTimeout> | null = null

/** 放弃时先播裁纸动画，再通知父级移除，动画不会被提前卸载。 */
function handleDismiss() {
  if (exiting.value) return
  exiting.value = true
  const id = props.note.id
  exitTimer = setTimeout(() => emit('dismiss', id), motionEnabled.value ? 200 : 0)
}

onBeforeUnmount(() => {
  if (exitTimer !== null) clearTimeout(exitTimer)
})

const kindLabel = computed(() => {
  if (props.note.kind === '审校') return '审校 · REVISION'
  return props.note.kind
})

const streaming = computed(() => props.note.status === 'streaming')
/** 润色草稿态：终稿尚未替换前不允许采纳 */
const draft = computed(() => !!props.note.draft)
</script>

<template>
  <div
    class="vermilion-note group relative w-[16.5rem] rounded-md border border-cinnabar/25 bg-cinnabar-wash px-3.5 py-3 shadow-card"
    :class="[
      note.status === 'dismissed' ? 'opacity-45' : '',
      motionEnabled && !exiting ? 'motion-note-enter' : '',
      exiting ? 'motion-cut' : '',
    ]"
  >
    <!-- 细朱线连接锚点（向左侧稿纸段落延伸） -->
    <span
      class="connector absolute top-6 -left-4 h-px w-4 bg-cinnabar-line"
      :class="motionEnabled ? 'motion-connector' : ''"
      aria-hidden="true"
    />

    <header class="flex items-center gap-2">
      <span class="inline-flex items-center gap-1 rounded-sm bg-cinnabar/10 px-1.5 py-0.5 text-[11px] font-medium text-cinnabar">
        <PenLine class="size-3" />
        {{ kindLabel }}
      </span>
      <span class="min-w-0 flex-1 truncate text-[11px] text-ink-secondary">{{ note.label }}</span>
      <button
        type="button"
        class="rounded-sm p-0.5 text-mist transition-colors hover:text-ink"
        :aria-label="collapsed ? '展开批注' : '折叠批注'"
        @click="collapsed = !collapsed"
      >
        <ChevronDown class="size-3.5 transition-transform duration-200 ease-[var(--ease-paper)]" :class="collapsed ? '-rotate-90' : ''" />
      </button>
    </header>

    <div v-show="!collapsed" class="mt-2">
      <p class="max-h-56 overflow-y-auto font-kai text-[13px] leading-6 whitespace-pre-wrap text-ink">
        <StreamingInkText
          v-if="note.text"
          :text="note.text"
          :disabled="!motionEnabled || note.text.length > 3000"
          :streaming="streaming"
        />
        <template v-else>落笔中…</template>
      </p>
      <p v-if="streaming" class="mt-1 flex items-center gap-1 text-[11px] text-cinnabar">
        <Loader2 class="size-3 animate-spin" />
        <span v-if="motionEnabled" class="motion-ink-drop size-1.5 rounded-full bg-cinnabar" aria-hidden="true" />
        {{ draft ? '草稿落笔中 · 待终稿替换' : '朱批落笔中' }}
      </p>
      <p v-else-if="draft" class="mt-1 flex items-center gap-1 text-[11px] text-cinnabar">
        <Loader2 class="size-3" />
        草稿 · 待终稿替换
      </p>
    </div>

    <footer v-if="!collapsed && !streaming" class="mt-2.5 flex items-center justify-between gap-2">
      <span
        v-if="note.status === 'adopted'"
        role="status"
        aria-label="已采纳"
        title="已采纳"
        class="inline-flex size-7 shrink-0 items-center justify-center rounded-sm border-2 border-cinnabar bg-cinnabar font-book text-[12px] font-semibold leading-none text-paper-surface shadow-card"
        :class="motionEnabled ? 'motion-seal' : ''"
      >
        定
      </span>
      <span v-else-if="note.status === 'dismissed'" class="text-[11px] text-mist">已放弃</span>
      <template v-else>
        <button
          v-if="!draft"
          type="button"
          class="inline-flex h-7 items-center gap-1 rounded-md bg-cinnabar px-2.5 text-xs font-medium text-paper-surface transition-colors duration-150 ease-[var(--ease-paper)] hover:bg-cinnabar/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cinnabar/40"
          @click="emit('adopt', note.id)"
        >
          <Check class="size-3.5" />
          采纳
        </button>
        <button
          type="button"
          class="inline-flex h-7 items-center gap-1 rounded-md px-2 text-xs text-ink-secondary transition-colors duration-150 ease-[var(--ease-paper)] hover:bg-paper hover:text-ink"
          @click="handleDismiss"
        >
          <X class="size-3.5" />
          放弃
        </button>
      </template>
    </footer>
  </div>
</template>
