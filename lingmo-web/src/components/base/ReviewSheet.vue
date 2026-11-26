<script setup lang="ts">
/**
 * 人审抽屉：在创作台内处理"发现问题 → 改稿 → 继续写"闭环。
 * 用 Sheet 而非跳转页面，保留稿纸（含未保存内容）不卸载。
 */
import { ref, watch } from 'vue'
import { Check, RefreshCw, ShieldCheck, TriangleAlert, X } from 'lucide-vue-next'
import { Badge, Button, Input, Sheet, Tabs, TabsList, TabsPane, TabsTrigger } from '@/components/ui'
import { useToast } from '@/composables/useToast'
import { useReviewQueue } from '@/composables/useReviewQueue'
import type { ReviewType } from '@/api/types'

const props = defineProps<{
  open: boolean
  bookId: number
}>()

const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const toast = useToast()
const queue = useReviewQueue(props.bookId)
const activeType = ref<ReviewType>('conflict')
const notes = ref<Record<string, string>>({})

const typeOptions: { value: ReviewType; label: string }[] = [
  { value: 'conflict', label: '正典冲突' },
  { value: 'assertion', label: '待证断言' },
  { value: 'safety', label: '安全记录' },
  { value: 'unmet', label: '未达标章' },
]

watch(
  () => props.open,
  (open) => {
    if (open) void queue.load()
  },
  { immediate: true },
)

/** 四类实体字段不完全一致，统一用安全读取避免联合类型访问报错 */
function field(item: unknown, key: string): unknown {
  return (item as Record<string, unknown>)[key]
}

function noteKey(type: ReviewType, id: number | undefined) {
  return `${type}-${id ?? 0}`
}

function countOf(type: ReviewType) {
  return queue.overview.value?.counts?.[type] ?? 0
}

/** 列表项的简短副标题（按人审类型取关键字段） */
function subtitle(type: ReviewType, item: Record<string, unknown>) {
  if (type === 'conflict') {
    const kind = item.conflictKind ?? '状态变更'
    return `${kind}${item.oldValue ? `：${String(item.oldValue)} → ${String(item.newValue ?? '')}` : ''}`
  }
  if (type === 'assertion') {
    return `${item.assertionType ?? '断言'} · 确认 ${item.confirmations ?? 0} 次${item.expiresChapter ? ` · 到期第 ${item.expiresChapter} 章` : ''}`
  }
  if (type === 'safety') {
    return `${item.layer ?? '—'} 层 · ${item.category ?? '—'} · ${item.action ?? '—'}`
  }
  return `任务 #${item.id ?? '—'} · ${item.mode ?? '—'} · ${item.status ?? '—'}`
}

function titleOf(type: ReviewType, item: Record<string, unknown>) {
  if (type === 'unmet') return `第 ${item.chapterId ?? '?'} 章`
  if (type === 'conflict') return `角色 #${item.characterId ?? '—'}`
  if (type === 'assertion') return `角色 #${item.characterId ?? '—'}`
  return String(item.detail ?? '安全审计记录').slice(0, 80)
}

async function decide(type: ReviewType, id: number | undefined, accept: boolean) {
  if (!id) return
  try {
    await queue.decide(type, id, accept, notes.value[noteKey(type, id)])
    toast.success(accept ? '已通过' : '已驳回', '待办计数已更新')
  } catch (err) {
    toast.error('裁定失败', err instanceof Error ? err.message : String(err))
  }
}
</script>

<template>
  <Sheet
    :open="open"
    title="人审队列"
    description="四类待办：正典冲突、待证断言、安全记录、未达标章"
    width-class="w-[min(92vw,52rem)]"
    @update:open="emit('update:open', $event)"
  >
    <div class="flex flex-wrap items-center gap-1.5">
      <Badge v-for="opt in typeOptions" :key="opt.value" variant="outline">
        {{ opt.label }} {{ countOf(opt.value) }}
      </Badge>
      <Button variant="ghost" size="sm" class="ml-auto" :loading="queue.loading.value" @click="queue.load(true)">
        <RefreshCw class="size-3.5" />
        刷新
      </Button>
    </div>

    <p v-if="queue.error.value" class="mt-3 flex items-center gap-1.5 rounded-md bg-cinnabar-wash px-3 py-2 text-xs text-cinnabar">
      <TriangleAlert class="size-3.5 shrink-0" />
      {{ queue.error.value }}
    </p>

    <Tabs v-model="activeType" class="mt-3">
      <TabsList>
        <TabsTrigger v-for="opt in typeOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}（{{ countOf(opt.value) }}）
        </TabsTrigger>
      </TabsList>

      <TabsPane v-for="opt in typeOptions" :key="opt.value" :value="opt.value">
        <ul v-if="(queue.overview.value?.[opt.value] ?? []).length" class="space-y-2">
          <li
            v-for="(item, i) in (queue.overview.value?.[opt.value] ?? [])"
            :key="Number(field(item, 'id') ?? i)"
            class="rounded-md border border-mist-line bg-paper-surface px-3 py-2.5"
          >
            <div class="flex items-start gap-2">
              <ShieldCheck class="mt-0.5 size-3.5 shrink-0 text-cinnabar" />
              <div class="min-w-0 flex-1">
                <p class="text-[13px] font-medium text-ink">{{ titleOf(opt.value, item as unknown as Record<string, unknown>) }}</p>
                <p class="mt-0.5 break-all text-[11px] leading-4 text-ink-secondary">
                  {{ subtitle(opt.value, item as unknown as Record<string, unknown>) }}
                </p>
              </div>
              <Badge v-if="field(item, 'status')" variant="outline">{{ field(item, 'status') }}</Badge>
            </div>
            <div class="mt-2 flex items-center gap-2">
              <Input
                v-model="notes[noteKey(opt.value, Number(field(item, 'id') ?? 0))]"
                class="h-8 flex-1 text-xs"
                placeholder="裁定备注（可留空）"
                maxlength="200"
              />
              <Button
                variant="outline"
                size="sm"
                :disabled="queue.deciding.value"
                @click="decide(opt.value, Number(field(item, 'id') ?? 0), false)"
              >
                <X class="size-3.5" />
                驳回
              </Button>
              <Button
                variant="cinnabar"
                size="sm"
                :disabled="queue.deciding.value"
                @click="decide(opt.value, Number(field(item, 'id') ?? 0), true)"
              >
                <Check class="size-3.5" />
                通过
              </Button>
            </div>
          </li>
        </ul>
        <p v-else class="py-8 text-center text-xs text-mist">暂无{{ opt.label }}待办。</p>
      </TabsPane>
    </Tabs>
  </Sheet>
</template>
