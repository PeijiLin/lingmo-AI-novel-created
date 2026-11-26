<script setup lang="ts">
/**
 * 运行详情抽屉：按 runId 查看断点续跑可观测数据。
 * 数据来自 GET /pipeline/run/{runId}：任务状态、成功节点、阶段产物概览（不含正文）。
 */
import { ref, watch } from 'vue'
import { Activity, RefreshCw, TriangleAlert } from 'lucide-vue-next'
import { Badge, Button, Sheet } from '@/components/ui'
import { queryRun } from '@/api/pipeline'
import type { RunSummary } from '@/api/types'

const props = defineProps<{
  open: boolean
  runId: number | null
}>()

const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const loading = ref(false)
const error = ref<string | null>(null)
const summary = ref<RunSummary | null>(null)

async function load() {
  if (!props.runId) return
  loading.value = true
  error.value = null
  try {
    summary.value = await queryRun(props.runId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
    summary.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, props.runId] as const,
  ([open, runId]) => {
    if (open && runId) void load()
  },
  { immediate: true },
)

const stageNames: Record<string, string> = {
  PLANNING: '立纲',
  WRITING: '落笔',
  CRITIC: '审校',
  POLISHING: '润色',
}

/** 节点形如 WRITING:1 / CRITIC:2，PLANNING 无轮次后缀 */
function nodeLabel(node: string) {
  const [stage, round] = node.split(':')
  const name = stageNames[stage] ?? stage
  return round ? `${name} · 第 ${round} 轮` : name
}
</script>

<template>
  <Sheet
    :open="open"
    title="运行详情"
    description="节点与产物概览（不含正文，正文请从朱批或章节稿查看）"
    width-class="w-[min(92vw,48rem)]"
    @update:open="emit('update:open', $event)"
  >
    <div v-if="loading" class="flex items-center gap-2 py-10 text-sm text-ink-secondary">
      <RefreshCw class="size-4 animate-spin text-cinnabar" />
      正在读取运行记录…
    </div>

    <div v-else-if="error" class="rounded-md bg-cinnabar-wash px-3 py-2 text-xs leading-5 text-cinnabar">
      <p class="flex items-center gap-1.5 font-medium">
        <TriangleAlert class="size-3.5" />
        运行详情加载失败
      </p>
      <p class="mt-1 break-all">{{ error }}</p>
      <Button variant="outline" size="sm" class="mt-2" @click="load">重试</Button>
    </div>

    <div v-else-if="summary" class="space-y-6">
      <!-- 任务概览 -->
      <section aria-label="任务概览">
        <h3 class="flex items-center gap-2 font-book text-sm font-semibold text-ink">
          <Activity class="size-4 text-cinnabar" />
          任务 #{{ summary.task?.id ?? runId }}
        </h3>
        <div class="mt-2 flex flex-wrap items-center gap-1.5">
          <Badge variant="outline">{{ summary.task?.mode ?? '—' }}</Badge>
          <Badge :variant="summary.task?.status === 'COMPLETED' ? 'cinnabar' : 'outline'">
            {{ summary.task?.status ?? '—' }}
          </Badge>
          <Badge v-if="summary.task?.currentStage" variant="outline">
            {{ stageNames[summary.task.currentStage] ?? summary.task.currentStage }}
          </Badge>
          <Badge v-if="summary.task?.degraded" variant="cinnabar">已降级</Badge>
        </div>
        <div class="mt-2 flex flex-wrap gap-4 text-xs text-ink-secondary">
          <span v-if="summary.task?.totalTokens">tokens {{ summary.task.totalTokens.toLocaleString() }}</span>
          <span v-if="summary.task?.elapsedMs">耗时 {{ (summary.task.elapsedMs / 1000).toFixed(1) }}s</span>
          <span v-if="summary.task?.error" class="text-cinnabar">{{ summary.task.error }}</span>
        </div>
      </section>

      <!-- 成功节点 -->
      <section aria-label="成功节点">
        <h3 class="font-book text-sm font-semibold text-ink">成功节点（{{ summary.succeededNodes.length }}）</h3>
        <div v-if="summary.succeededNodes.length" class="mt-2 flex flex-wrap gap-1.5">
          <Badge v-for="node in summary.succeededNodes" :key="node" variant="outline">
            {{ nodeLabel(node) }}
          </Badge>
        </div>
        <p v-else class="mt-2 text-xs text-mist">本运行暂无成功节点记录。</p>
      </section>

      <!-- 阶段产物 -->
      <section aria-label="阶段产物">
        <h3 class="font-book text-sm font-semibold text-ink">阶段产物（{{ summary.artifacts.length }}）</h3>
        <div v-if="summary.artifacts.length" class="mt-2 overflow-hidden rounded-md border border-mist-line">
          <table class="w-full text-left text-xs">
            <thead class="bg-paper text-ink-secondary">
              <tr>
                <th class="px-3 py-2 font-medium">阶段</th>
                <th class="px-3 py-2 font-medium">轮次</th>
                <th class="px-3 py-2 font-medium">tokens</th>
                <th class="px-3 py-2 font-medium">时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(artifact, i) in summary.artifacts" :key="i" class="border-t border-mist-line">
                <td class="px-3 py-2 text-ink">{{ stageNames[artifact.stage] ?? artifact.stage }}</td>
                <td class="px-3 py-2 text-ink-secondary">{{ artifact.round }}</td>
                <td class="px-3 py-2 text-ink-secondary">{{ artifact.tokens.toLocaleString() }}</td>
                <td class="px-3 py-2 text-mist">{{ artifact.createdAt }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="mt-2 text-xs text-mist">本运行暂无阶段产物记录。</p>
      </section>
    </div>

    <div v-else class="py-10 text-center text-sm text-mist">暂无运行详情。</div>
  </Sheet>
</template>
