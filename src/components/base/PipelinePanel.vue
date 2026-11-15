<script setup lang="ts">
/**
 * 四档流水线面板（规范 4.2 创作工作台核心交互）
 * 模式选择 → 同步/异步通道 → 动作（续写/润色/审校）→ 阶段进度墨线 → token 用量
 */
import { computed } from 'vue'
import { Play, Square, Sparkles, Layers, GitCompareArrows, Gauge, TriangleAlert } from 'lucide-vue-next'
import { Badge, Button, Select } from '@/components/ui'
import { cn } from '@/utils/cn'
import type { CreationMode } from '@/api/types'
import type { AiAction, AiRunState, RunChannel } from '@/composables/useAiCreation'

const props = defineProps<{
  state: AiRunState
  /** 润色/审校是否可用（依赖选区） */
  hasSelection: boolean
}>()

const mode = defineModel<CreationMode>('mode', { required: true })
const maxRewriteRounds = defineModel<string>('maxRewriteRounds', { required: true })
const channel = defineModel<RunChannel>('channel', { required: true })
const action = defineModel<AiAction>('action', { required: true })

const emit = defineEmits<{
  run: [action: AiAction]
  abort: []
}>()

interface ModeOption {
  value: CreationMode
  name: string
  tag: string
  desc: string
}

const modeOptions: ModeOption[] = [
  { value: 'FAST', name: '快笔', tag: 'FAST', desc: '单链路直写，最快出稿' },
  { value: 'STANDARD', name: '稳稿', tag: 'STANDARD', desc: '写作 + 审校回路，最多重写 N 轮' },
  { value: 'REFINED', name: '精修', tag: 'REFINED', desc: '立纲 → 落笔 → 审校 → 润色' },
  { value: 'PRECISE', name: '极致', tag: 'PRECISE', desc: '双稿并行择优，最慢最重' },
]

const stageNames: Record<string, string> = {
  PLANNING: '立纲',
  WRITING: '落笔',
  CRITIC: '审校',
  POLISHING: '润色',
  COMPLETED: '完成',
  FAILED: '失败',
}

const canRun = computed(() => {
  if (props.state.running) return false
  if (action.value === '润色' && !props.hasSelection) return false
  return true
})

const actionHint = computed(() => {
  if (action.value === '续写') return 'AI 在本章末尾续写，产出一枚「续写」朱批'
  if (action.value === '润色') return props.hasSelection ? '润色当前选中文字，采纳后替换选区' : '请先在稿纸上选中要润色的文字'
  return '深度审校本章（强制 REFINED 及以上），产出修订朱批'
})

/** 审校强制 REFINED+：本地即时校正显示 */
const effectiveMode = computed<CreationMode>(() => {
  if (action.value === '审校' && (mode.value === 'FAST' || mode.value === 'STANDARD')) return 'REFINED'
  return mode.value
})

function pickMode(value: CreationMode) {
  mode.value = value
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col overflow-y-auto px-4 pb-6">
    <!-- 四档模式 -->
    <section aria-label="创作模式">
      <h3 class="font-book text-sm font-semibold text-ink">创作模式</h3>
      <div class="mt-2 grid grid-cols-2 gap-1.5">
        <button
          v-for="opt in modeOptions"
          :key="opt.value"
          type="button"
          :class="cn(
            'rounded-md border px-2.5 py-2 text-left transition-all duration-150 ease-[var(--ease-paper)]',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40',
            effectiveMode === opt.value
              ? 'border-cinnabar/40 bg-cinnabar-wash shadow-card'
              : 'border-mist-line bg-paper-surface hover:border-ink-indigo/30',
          )"
          :aria-pressed="effectiveMode === opt.value"
          @click="pickMode(opt.value)"
        >
          <span class="flex items-center gap-1.5">
            <span :class="cn('font-book text-[13px] font-semibold', effectiveMode === opt.value ? 'text-cinnabar' : 'text-ink')">
              {{ opt.name }}
            </span>
            <span class="text-[10px] tracking-wide text-mist">{{ opt.tag }}</span>
          </span>
          <span class="mt-0.5 block text-[11px] leading-4 text-ink-secondary">{{ opt.desc }}</span>
        </button>
      </div>
    </section>

    <!-- 参数 -->
    <section class="mt-3" aria-label="流水线参数">
      <div class="flex items-center gap-2">
        <label class="shrink-0 text-xs text-ink-secondary" for="rounds">重写上限</label>
        <Select
          v-model="maxRewriteRounds"
          width-class="w-24"
          :options="[
            { label: '1 轮', value: '1' },
            { label: '2 轮', value: '2' },
            { label: '3 轮', value: '3' },
          ]"
          :disabled="effectiveMode === 'FAST'"
        />
        <label class="ml-auto flex items-center gap-1.5 text-xs text-ink-secondary">
          <input v-model="channel" type="radio" value="sync" class="accent-ink-indigo" name="channel" />
          流式
          <input v-model="channel" type="radio" value="async" class="ml-1.5 accent-ink-indigo" name="channel" />
          整章任务
        </label>
      </div>
    </section>

    <!-- 动作 -->
    <section class="mt-3" aria-label="创作动作">
      <div class="flex rounded-md bg-paper p-1" role="tablist" aria-label="创作动作">
        <button
          v-for="a in ['续写', '润色', '审校'] as AiAction[]"
          :key="a"
          type="button"
          role="tab"
          :aria-selected="action === a"
          :class="cn(
            'flex-1 rounded-sm py-1.5 text-xs font-medium transition-colors duration-150 ease-[var(--ease-paper)]',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40',
            action === a ? 'bg-paper-surface text-ink-indigo shadow-card' : 'text-ink-secondary hover:text-ink',
          )"
          @click="action = a"
        >
          {{ a }}
        </button>
      </div>
      <p class="mt-1.5 text-[11px] leading-4 text-ink-secondary">{{ actionHint }}</p>
    </section>

    <!-- 运行 / 中止 -->
    <div class="mt-3 flex items-center gap-2">
      <Button v-if="!state.running" variant="cinnabar" class="flex-1" :disabled="!canRun" @click="emit('run', action)">
        <Play class="size-3.5" />
        {{ channel === 'async' ? '提交整章任务' : '开始' }}{{ action }}
      </Button>
      <Button v-else variant="outline" class="flex-1" @click="emit('abort')">
        <Square class="size-3.5" />
        中止
      </Button>
    </div>

    <!-- 进度时间线（墨线 + 朱点） -->
    <section v-if="state.stages.length > 0 || state.running" class="mt-4" aria-label="流水线进度">
      <h3 class="flex items-center gap-2 font-book text-sm font-semibold text-ink">
        流水线
        <Badge v-if="state.running" variant="cinnabar">
          <Sparkles class="size-3" />
          执行中
        </Badge>
        <Badge v-else-if="state.taskStatus" variant="outline">{{ state.taskStatus }}</Badge>
      </h3>

      <ol class="mt-2 space-y-0">
        <li v-for="(stage, i) in state.stages" :key="i" class="relative flex gap-3 pb-3 last:pb-0">
          <!-- 墨线 -->
          <span
            v-if="i !== state.stages.length - 1"
            class="absolute top-4 left-[5px] h-[calc(100%-1rem)] w-px bg-mist-line"
            aria-hidden="true"
          />
          <span
            :class="cn(
              'relative z-10 mt-0.5 size-[11px] shrink-0 rounded-full border-2',
              stage.status === 'REVISION' ? 'border-cinnabar bg-cinnabar' : 'border-ink-indigo bg-paper-surface',
            )"
            aria-hidden="true"
          />
          <div class="min-w-0 flex-1">
            <p class="text-[13px] leading-5 text-ink">
              {{ stageNames[stage.code] ?? stage.code }}
              <span v-if="stage.status === 'REVISION'" class="ml-1 text-[11px] text-cinnabar">第 {{ stage.round }} 轮重写</span>
            </p>
            <p v-if="stage.status === 'STARTED' && stage.total" class="text-[11px] text-mist">
              阶段 {{ stage.index }}/{{ stage.total }}
            </p>
          </div>
        </li>
        <li v-if="state.running && state.stages.length === 0" class="flex items-center gap-2 text-xs text-ink-secondary">
          <Gauge class="size-3.5 animate-pulse text-cinnabar" />
          管道启动中…
        </li>
      </ol>

      <!-- 异步任务态 -->
      <p v-if="state.channel === 'async' && state.taskId" class="mt-1 text-[11px] text-ink-secondary">
        任务 #{{ state.taskId }} · {{ state.taskStatus }}<span v-if="state.currentStage"> · {{ stageNames[state.currentStage.code] ?? state.currentStage.code }}</span>
      </p>

      <!-- 用量 -->
      <div v-if="state.usage" class="mt-2 flex flex-wrap items-center gap-1.5">
        <Badge variant="cinnabar"><Layers class="size-3" />{{ state.usage.totalTokens.toLocaleString() }} tokens</Badge>
        <Badge variant="outline">{{ (state.usage.elapsedMs / 1000).toFixed(1) }}s</Badge>
        <Badge v-if="state.usage.stages" variant="outline">{{ state.usage.stages }} 阶段</Badge>
      </div>

      <p v-if="state.degraded" class="mt-2 flex items-center gap-1 text-[11px] text-cinnabar">
        <TriangleAlert class="size-3.5" />
        部分阶段降级完成（产物仍可用，建议复核）
      </p>

      <p v-if="state.error" class="mt-2 rounded-md bg-cinnabar-wash px-2.5 py-1.5 text-[11px] leading-4 text-cinnabar">
        {{ state.error }}
      </p>
    </section>

    <!-- 模式说明脚注 -->
    <p class="mt-auto pt-4 text-[11px] leading-4 text-mist">
      <GitCompareArrows class="mb-0.5 mr-1 inline size-3" />
      四档即后端多智能体拓扑：FAST[Writer]、STANDARD[Writer→Critic 回路]、REFINED[Planner→Writer→Critic→Polisher]、PRECISE[双稿并行择优]。
    </p>
  </div>
</template>
