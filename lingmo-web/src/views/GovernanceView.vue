<script setup lang="ts">
/**
 * 质量治理页：书级低频配置（成本账本 + 规则豁免/叙诡保护区）。
 * 人审队列在创作台内以 Sheet 处理，不在此页——保留稿纸编辑态。
 */
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  BookOpen,
  Download,
  RefreshCw,
  ShieldOff,
  TriangleAlert,
} from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Badge, Button, Card, Input, Tabs, TabsList, TabsPane, TabsTrigger } from '@/components/ui'
import { useToast } from '@/composables/useToast'
import { useCost } from '@/composables/useCost'
import { useRuleExemption } from '@/composables/useRuleExemption'
import { getBookInfo } from '@/api/books'
import type { BookInfoVo, NovelRuleExemption } from '@/api/types'
import { isValidDate, parsePositiveInt } from '@/utils/validation'

const props = defineProps<{ id: string | number }>()
const router = useRouter()
const toast = useToast()

const bookId = Number(props.id)
if (!Number.isFinite(bookId) || bookId <= 0) router.replace('/workbench')

const book = ref<BookInfoVo | null>(null)
const activeTab = ref('cost')
const cost = useCost(bookId)
const exemption = useRuleExemption(bookId)

onMounted(async () => {
  void cost.load()
  try {
    book.value = await getBookInfo(bookId)
  } catch {
    /* 书名非关键路径，失败静默 */
  }
})

watch(activeTab, (tab) => {
  if (tab === 'cost') void cost.load()
  else void exemption.load()
})

async function handleDownload() {
  try {
    await cost.download()
    toast.success('成本明细已导出')
  } catch (err) {
    toast.error('导出失败', err instanceof Error ? err.message : String(err))
  }
}

/* ---------------- 规则豁免表单 ---------------- */

const form = reactive({
  ruleId: '',
  reason: '',
  chapterFrom: '',
  chapterTo: '',
  reviewAt: '',
})

async function handleExempt() {
  const ruleId = form.ruleId.trim()
  if (!ruleId) {
    toast.warning('请填写规则 ID')
    return
  }
  const hasFrom = form.chapterFrom.trim().length > 0
  const hasTo = form.chapterTo.trim().length > 0
  if (hasFrom !== hasTo) {
    toast.warning('叙诡保护区需同时填写起始章与结束章')
    return
  }
  const chapterFrom = hasFrom ? parsePositiveInt(form.chapterFrom) : undefined
  const chapterTo = hasTo ? parsePositiveInt(form.chapterTo) : undefined
  if ((hasFrom && chapterFrom === undefined) || (hasTo && chapterTo === undefined)) {
    toast.warning('章节号需为正整数')
    return
  }
  if (chapterFrom !== undefined && chapterTo !== undefined && chapterFrom > chapterTo) {
    toast.warning('保护区区间非法：起始章大于结束章')
    return
  }
  const reviewAt = form.reviewAt.trim()
  if (reviewAt && !isValidDate(reviewAt)) {
    toast.warning('复核时间需为有效日期（YYYY-MM-DD）')
    return
  }
  try {
    await exemption.exempt({
      ruleId,
      reason: form.reason.trim() || undefined,
      chapterFrom,
      chapterTo,
      reviewAt: reviewAt || undefined,
    })
    toast.success(
      chapterFrom !== undefined ? '叙诡保护区已登记' : '整本豁免已登记',
      '可在到期复核列表中跟踪',
    )
    form.ruleId = ''
    form.reason = ''
    form.chapterFrom = ''
    form.chapterTo = ''
    form.reviewAt = ''
  } catch (err) {
    toast.error('登记失败', err instanceof Error ? err.message : String(err))
  }
}

async function handleLongTerm(row: NovelRuleExemption) {
  if (!row.ruleId) return
  try {
    await exemption.review(row.ruleId)
    toast.success('已设为长期有效')
  } catch (err) {
    toast.error('操作失败', err instanceof Error ? err.message : String(err))
  }
}

async function handleRevoke(row: NovelRuleExemption) {
  if (!row.ruleId) return
  try {
    await exemption.revoke(row.ruleId)
    toast.success('豁免已撤销')
  } catch (err) {
    toast.error('撤销失败', err instanceof Error ? err.message : String(err))
  }
}
</script>

<template>
  <AppShell>
    <div class="flex items-center gap-3">
      <Button variant="ghost" size="sm" @click="router.push('/workbench')">
        <ArrowLeft class="size-3.5" />
        工作台
      </Button>
      <div>
        <h1 class="font-book text-xl font-semibold text-ink">质量治理 · {{ book?.bookName || '作品' }}</h1>
        <p class="mt-1 text-xs text-ink-secondary">成本账本与规则豁免（叙诡保护区）</p>
      </div>
    </div>

    <Tabs v-model="activeTab" class="mt-5">
      <TabsList>
        <TabsTrigger value="cost">成本账本</TabsTrigger>
        <TabsTrigger value="rules">规则豁免（{{ exemption.items.value.length }}）</TabsTrigger>
      </TabsList>

      <!-- 成本账本 -->
      <TabsPane value="cost">
        <Card class="p-4">
          <div class="flex items-center gap-2">
            <h2 class="font-book text-sm font-semibold text-ink">模式 × 节点 × 模型</h2>
            <Button
              variant="outline"
              size="sm"
              class="ml-auto"
              :loading="cost.loading.value"
              @click="cost.load(true)"
            >
              <RefreshCw class="size-3.5" />
              刷新
            </Button>
            <Button variant="cinnabar" size="sm" :loading="cost.exporting.value" @click="handleDownload">
              <Download class="size-3.5" />
              导出 CSV
            </Button>
          </div>

          <p v-if="cost.error.value" class="mt-3 flex items-center gap-1.5 rounded-md bg-cinnabar-wash px-3 py-2 text-xs text-cinnabar">
            <TriangleAlert class="size-3.5" />
            {{ cost.error.value }}
          </p>

          <div v-else-if="cost.rows.value.length" class="mt-3 overflow-x-auto">
            <table class="w-full min-w-[42rem] text-left text-xs">
              <thead class="bg-paper text-ink-secondary">
                <tr>
                  <th class="px-3 py-2 font-medium">模式</th>
                  <th class="px-3 py-2 font-medium">节点</th>
                  <th class="px-3 py-2 font-medium">模型</th>
                  <th class="px-3 py-2 font-medium">次数</th>
                  <th class="px-3 py-2 font-medium">tokens</th>
                  <th class="px-3 py-2 font-medium">成本（元）</th>
                  <th class="px-3 py-2 font-medium">计价</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in cost.rows.value" :key="i" class="border-t border-mist-line">
                  <td class="px-3 py-2 text-ink">{{ row.mode }}</td>
                  <td class="px-3 py-2 text-ink-secondary">{{ row.node }}</td>
                  <td class="px-3 py-2 text-ink-secondary">{{ row.model }}</td>
                  <td class="px-3 py-2 text-ink-secondary">{{ row.runs }}</td>
                  <td class="px-3 py-2 text-ink-secondary">{{ row.totalTokens.toLocaleString() }}</td>
                  <td class="px-3 py-2 text-ink">{{ row.cost }}</td>
                  <td class="px-3 py-2">
                    <Badge :variant="row.priced ? 'cinnabar' : 'outline'">{{ row.priced ? '已计价' : '未计价' }}</Badge>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else-if="!cost.loading.value" class="py-10 text-center text-xs text-mist">本书暂无成本记录。</p>
        </Card>
      </TabsPane>

      <!-- 规则豁免 -->
      <TabsPane value="rules">
        <Card class="p-4">
          <h2 class="font-book text-sm font-semibold text-ink">登记豁免 / 保护区</h2>
          <p class="mt-1 text-[11px] leading-4 text-ink-secondary">
            不填章节区间表示整本豁免；填写起止章表示叙诡保护区。留空复核时间表示长期有效。
          </p>
          <div class="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            <label class="flex flex-col gap-1 text-xs text-ink-secondary">
              规则 ID
              <Input v-model="form.ruleId" placeholder="如 WORLD_RULE_3" maxlength="80" />
            </label>
            <label class="flex flex-col gap-1 text-xs text-ink-secondary">
              原因
              <Input v-model="form.reason" placeholder="误报 / 叙诡需要" maxlength="200" />
            </label>
            <label class="flex flex-col gap-1 text-xs text-ink-secondary">
              起始章
              <Input v-model="form.chapterFrom" inputmode="numeric" placeholder="留空=整本" />
            </label>
            <label class="flex flex-col gap-1 text-xs text-ink-secondary">
              结束章
              <Input v-model="form.chapterTo" inputmode="numeric" placeholder="留空=整本" />
            </label>
            <label class="flex flex-col gap-1 text-xs text-ink-secondary">
              复核时间
              <Input v-model="form.reviewAt" placeholder="YYYY-MM-DD（可留空）" maxlength="30" />
            </label>
          </div>
          <div class="mt-3 flex justify-end">
            <Button variant="cinnabar" size="sm" @click="handleExempt">
              <ShieldOff class="size-3.5" />
              登记
            </Button>
          </div>
        </Card>

        <Card class="mt-3 p-4">
          <div class="flex items-center gap-2">
            <h2 class="font-book text-sm font-semibold text-ink">清单</h2>
            <Badge v-if="exemption.due.value.length" variant="cinnabar">到期复核 {{ exemption.due.value.length }}</Badge>
            <Button
              variant="ghost"
              size="sm"
              class="ml-auto"
              :loading="exemption.loading.value"
              @click="exemption.load(true)"
            >
              <RefreshCw class="size-3.5" />
              刷新
            </Button>
          </div>

          <p v-if="exemption.error.value" class="mt-3 flex items-center gap-1.5 rounded-md bg-cinnabar-wash px-3 py-2 text-xs text-cinnabar">
            <TriangleAlert class="size-3.5" />
            {{ exemption.error.value }}
          </p>

          <ul v-else-if="exemption.items.value.length" class="mt-3 space-y-2">
            <li
              v-for="row in exemption.items.value"
              :key="row.id ?? row.ruleId"
              class="flex flex-wrap items-center gap-2 rounded-md border border-mist-line bg-paper-surface px-3 py-2.5"
            >
              <BookOpen class="size-3.5 shrink-0 text-cinnabar" />
              <div class="min-w-0 flex-1">
                <p class="truncate text-[13px] font-medium text-ink">{{ row.ruleId }}</p>
                <p class="truncate text-[11px] text-ink-secondary">
                  {{ row.reason || '未填原因' }}
                  <span v-if="row.chapterFrom || row.chapterTo">
                    · 第 {{ row.chapterFrom ?? '?' }}—{{ row.chapterTo ?? '?' }} 章
                  </span>
                  <span v-else> · 整本</span>
                </p>
              </div>
              <Badge v-if="row.reviewAt" variant="outline">复核 {{ row.reviewAt }}</Badge>
              <Badge v-else variant="outline">长期有效</Badge>
              <Button variant="ghost" size="sm" @click="handleLongTerm(row)">设为长期</Button>
              <Button variant="ghost" size="sm" @click="handleRevoke(row)">撤销</Button>
            </li>
          </ul>
          <p v-else-if="!exemption.loading.value" class="py-10 text-center text-xs text-mist">暂无豁免记录。</p>
        </Card>
      </TabsPane>
    </Tabs>
  </AppShell>
</template>
