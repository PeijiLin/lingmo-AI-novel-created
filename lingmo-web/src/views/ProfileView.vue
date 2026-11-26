<script setup lang="ts">
/**
 * 个人主页（规范 4.2：执笔者档案）
 * 三段式：用户卡（头像/昵称/性别/注册时间）→ 数据统计（作品/在读/余额）→ 作者档案（笔名/联系/邀请码）。
 * 作者档案未开档时给出「开设档案」引导；创建作品亦会自动开档（后端 resolveByUserId）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Coins,
  Copy,
  Feather,
  Library,
  Mail,
  MessageCircle,
  PenLine,
  Phone,
  Pencil,
  RefreshCw,
  Ticket,
} from 'lucide-vue-next'
import AppShell from '@/layouts/AppShell.vue'
import { Badge, Button, Card, Combobox, Dialog, Input, Skeleton } from '@/components/ui'
import { useToast } from '@/composables/useToast'
import { useAuth } from '@/composables/useAuth'
import { listAuthorBooks } from '@/api/books'
import {
  getMyAuthorProfile,
  getMyProfile,
  updateMyAuthorProfile,
  updateMyProfile,
} from '@/api/user'
import type { AuthorProfileVo, UserProfileVo } from '@/api/types'

const toast = useToast()
const { store } = useAuth()

/* ---------------- 加载 ---------------- */

const profile = ref<UserProfileVo | null>(null)
const author = ref<AuthorProfileVo | null>(null)
const loading = ref(false)

const displayName = computed(
  () => profile.value?.nickName?.trim() || profile.value?.username || '未命名读者',
)
const avatarChar = computed(() => displayName.value.trim().charAt(0) || '客')
const sexText = computed(() =>
  profile.value?.userSex === 0 ? '男' : profile.value?.userSex === 1 ? '女' : '性别未设',
)
const directionText = computed(() =>
  author.value?.workDirection?.trim() || '方向未设',
)

/** 在读数：与阅读器约定的本地进度账本（novel_read_progress） */
const readingCount = ref(0)

function loadReadingCount() {
  try {
    const progress = JSON.parse(localStorage.getItem('novel_read_progress') || '{}')
    readingCount.value = Object.values(progress).filter(Boolean).length
  } catch {
    readingCount.value = 0
  }
}

async function loadAll() {
  loading.value = true
  try {
    // 用户资料与作者档案并行；作品数仅作统计（失败不阻塞主页）
    const [me, myAuthor] = await Promise.all([
      getMyProfile(),
      getMyAuthorProfile().catch(() => null),
    ])
    profile.value = me
    author.value = myAuthor ?? null
    loadReadingCount()
    if (store.userId) {
      listAuthorBooks(store.userId)
        .then((books) => (worksCount.value = (books ?? []).length))
        .catch(() => undefined)
    }
  } catch (err) {
    toast.error('个人资料加载失败', err instanceof Error ? err.message : undefined)
  } finally {
    loading.value = false
  }
}

const worksCount = ref(0)

onMounted(loadAll)

/** 后端日期兼容（ISO 字符串 / 时间戳），个人主页仅展示到日 */
function formatDate(v?: string | number | null): string {
  if (v == null || v === '') return '—'
  const d = typeof v === 'number' ? new Date(v) : new Date(String(v).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return String(v)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

/* ---------------- 用户资料编辑 ---------------- */

const profileOpen = ref(false)
const profileSaving = ref(false)
const profileForm = reactive({ nickName: '', userSex: '' })

function openProfileEdit() {
  profileForm.nickName = profile.value?.nickName ?? profile.value?.username ?? ''
  profileForm.userSex =
    profile.value?.userSex === 0 || profile.value?.userSex === 1 ? String(profile.value.userSex) : ''
  profileOpen.value = true
}

async function saveProfile() {
  const name = profileForm.nickName.trim()
  if (!name) {
    toast.warning('昵称不能为空')
    return
  }
  profileSaving.value = true
  try {
    profile.value = await updateMyProfile({
      nickName: name,
      // 空串 = 未设置（后端 null 表示不变更，此处语义为保持未设置）
      userSex: profileForm.userSex === '' ? undefined : Number(profileForm.userSex),
    })
    profileOpen.value = false
    toast.success('资料已更新')
  } catch (err) {
    toast.error('资料更新失败', err instanceof Error ? err.message : undefined)
  } finally {
    profileSaving.value = false
  }
}

/* ---------------- 作者档案编辑 ---------------- */

const authorOpen = ref(false)
const authorSaving = ref(false)
const authorForm = reactive({
  penName: '',
  workDirection: '',
  email: '',
  telPhone: '',
  chatAccount: '',
})

/** creatingAuthor = 未开档走首次创建文案 */
const creatingAuthor = computed(() => !author.value)

function openAuthorEdit() {
  authorForm.penName = author.value?.penName ?? profile.value?.username ?? ''
  authorForm.workDirection = author.value?.workDirection?.trim() ?? ''
  authorForm.email = author.value?.email ?? ''
  authorForm.telPhone = author.value?.telPhone ?? ''
  authorForm.chatAccount = author.value?.chatAccount ?? ''
  authorOpen.value = true
}

async function saveAuthor() {
  const penName = authorForm.penName.trim()
  if (!penName) {
    toast.warning('请填写笔名')
    return
  }
  const wasCreating = creatingAuthor.value
  authorSaving.value = true
  try {
    // 后端 upsert：无档案自动开档；空串字段会清空为 null
    author.value = await updateMyAuthorProfile({
      penName,
      workDirection: authorForm.workDirection.trim() || null,
      email: authorForm.email.trim() || null,
      telPhone: authorForm.telPhone.trim() || null,
      chatAccount: authorForm.chatAccount.trim() || null,
    })
    authorOpen.value = false
    toast.success(wasCreating ? '作者档案已开设' : '作者档案已保存')
  } catch (err) {
    toast.error('作者档案保存失败', err instanceof Error ? err.message : undefined)
  } finally {
    authorSaving.value = false
  }
}

/* ---------------- 邀请码复制 ---------------- */

async function copyInvite(code?: string | null) {
  if (!code) return
  try {
    await navigator.clipboard.writeText(code)
    toast.success('邀请码已复制', code)
  } catch {
    toast.warning('复制失败', '请手动抄录邀请码')
  }
}
</script>

<template>
  <AppShell>
    <div class="flex items-center justify-between gap-3">
      <div>
        <h1 class="font-book text-xl font-semibold text-ink">个人主页</h1>
        <p class="mt-1 text-xs text-ink-secondary">执笔者档案：帐内资料与创作身份。</p>
      </div>
      <Button variant="ghost" size="sm" :loading="loading" @click="loadAll">
        <RefreshCw class="size-3.5" />
        刷新
      </Button>
    </div>

    <!-- 骨架 -->
    <div v-if="loading" class="mt-6 space-y-4">
      <Skeleton class="h-40 w-full rounded-lg" />
      <Skeleton class="h-24 w-full rounded-lg" />
      <Skeleton class="h-56 w-full rounded-lg" />
    </div>

    <template v-else>
      <!-- 用户卡 -->
      <Card class="mt-6">
        <div class="flex flex-col gap-5 sm:flex-row sm:items-center">
          <img
            v-if="profile?.userPhoto"
            :src="profile.userPhoto"
            :alt="displayName"
            class="size-16 shrink-0 rounded-full border border-mist-line object-cover"
          />
          <span
            v-else
            class="flex size-16 shrink-0 items-center justify-center rounded-full bg-ink-indigo/10 font-book text-2xl font-semibold text-ink-indigo"
            aria-hidden="true"
          >
            {{ avatarChar }}
          </span>
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2">
              <h2 class="font-book text-xl font-semibold text-ink">{{ displayName }}</h2>
              <Badge :variant="profile?.userSex === 0 || profile?.userSex === 1 ? 'cinnabar' : 'mist'">
                {{ sexText }}
              </Badge>
            </div>
            <p class="mt-1 truncate text-xs text-ink-secondary">
              @{{ profile?.username || '—' }}
              <span class="text-mist"> · 用户 ID {{ profile?.id ?? store.userId ?? '—' }}</span>
            </p>
            <p class="mt-1 text-[11px] text-mist">执笔始于 {{ formatDate(profile?.createTime) }}</p>
          </div>
          <Button variant="outline" size="sm" class="shrink-0 self-start sm:self-auto" @click="openProfileEdit">
            <Pencil class="size-3.5" />
            编辑资料
          </Button>
        </div>
      </Card>

      <!-- 数据统计 -->
      <div class="mt-4 grid grid-cols-3 gap-4">
        <RouterLink to="/workbench" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40 focus-visible:rounded-lg">
          <Card class="transition-shadow duration-200 ease-[var(--ease-paper)] hover:shadow-card-hover">
            <div class="flex items-center gap-3">
              <span class="flex size-9 shrink-0 items-center justify-center rounded-md bg-ink-indigo/10 text-ink-indigo">
                <Feather class="size-4" />
              </span>
              <div class="min-w-0">
                <p class="font-book text-lg font-semibold leading-tight text-ink">{{ worksCount }}</p>
                <p class="mt-0.5 text-[11px] text-mist">我的作品</p>
              </div>
            </div>
          </Card>
        </RouterLink>
        <RouterLink to="/bookshelf" class="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40 focus-visible:rounded-lg">
          <Card class="transition-shadow duration-200 ease-[var(--ease-paper)] hover:shadow-card-hover">
            <div class="flex items-center gap-3">
              <span class="flex size-9 shrink-0 items-center justify-center rounded-md bg-cinnabar-wash text-cinnabar">
                <Library class="size-4" />
              </span>
              <div class="min-w-0">
                <p class="font-book text-lg font-semibold leading-tight text-ink">{{ readingCount }}</p>
                <p class="mt-0.5 text-[11px] text-mist">在读之书</p>
              </div>
            </div>
          </Card>
        </RouterLink>
        <Card>
          <div class="flex items-center gap-3">
            <span class="flex size-9 shrink-0 items-center justify-center rounded-md bg-paper text-ink-secondary">
              <Coins class="size-4" />
            </span>
            <div class="min-w-0">
              <p class="font-book text-lg font-semibold leading-tight text-ink">
                {{ (profile?.accountBalance ?? 0).toLocaleString() }}
              </p>
              <p class="mt-0.5 text-[11px] text-mist">账户余额</p>
            </div>
          </div>
        </Card>
      </div>

      <!-- 作者档案 -->
      <Card class="mt-4">
        <template #title>作者档案</template>
        <template #description>笔名将用于作品署名与展示页作者栏。</template>
        <template #action>
          <Button variant="outline" size="sm" @click="openAuthorEdit">
            <PenLine class="size-3.5" />
            {{ author ? '编辑档案' : '开设档案' }}
          </Button>
        </template>

        <div v-if="author">
          <!-- 笔名行 -->
          <div class="flex flex-wrap items-center gap-3 border-b border-mist-line pb-4">
            <span class="font-book text-2xl font-semibold text-ink">{{ author.penName || '未署笔名' }}</span>
            <Badge :variant="author.workDirection?.trim() ? 'default' : 'mist'">
              {{ directionText }}
            </Badge>
            <span class="text-[11px] text-mist">开档于 {{ formatDate(author.createTime) }}</span>
          </div>

          <!-- 联络方式 -->
          <div class="mt-4 grid gap-px overflow-hidden rounded-md border border-mist-line bg-mist-line sm:grid-cols-2">
            <div class="flex items-start gap-3 bg-paper-surface px-4 py-3">
              <Mail class="mt-0.5 size-4 shrink-0 text-mist" />
              <div class="min-w-0">
                <p class="text-[11px] text-mist">电子邮箱</p>
                <p class="mt-0.5 break-all text-sm text-ink">{{ author.email || '—' }}</p>
              </div>
            </div>
            <div class="flex items-start gap-3 bg-paper-surface px-4 py-3">
              <Phone class="mt-0.5 size-4 shrink-0 text-mist" />
              <div class="min-w-0">
                <p class="text-[11px] text-mist">联系电话</p>
                <p class="mt-0.5 break-all text-sm text-ink">{{ author.telPhone || '—' }}</p>
              </div>
            </div>
            <div class="flex items-start gap-3 bg-paper-surface px-4 py-3">
              <MessageCircle class="mt-0.5 size-4 shrink-0 text-mist" />
              <div class="min-w-0">
                <p class="text-[11px] text-mist">社交账号</p>
                <p class="mt-0.5 break-all text-sm text-ink">{{ author.chatAccount || '—' }}</p>
              </div>
            </div>
            <div class="flex items-start gap-3 bg-paper-surface px-4 py-3">
              <Ticket class="mt-0.5 size-4 shrink-0 text-mist" />
              <div class="min-w-0 flex-1">
                <p class="text-[11px] text-mist">邀请码</p>
                <div class="mt-1 flex items-center gap-2">
                  <span class="break-all font-book text-sm tracking-wider text-ink">{{ author.inviteCode || '—' }}</span>
                  <button
                    v-if="author.inviteCode"
                    type="button"
                    class="rounded-sm p-1 text-mist transition-colors hover:bg-paper hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
                    aria-label="复制邀请码"
                    @click="copyInvite(author.inviteCode)"
                  >
                    <Copy class="size-3.5" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 空态：未开档 -->
        <div v-else class="flex flex-col items-center gap-4 py-12 text-center">
          <span class="flex size-12 items-center justify-center rounded-md bg-cinnabar-wash text-cinnabar">
            <Feather class="size-6" />
          </span>
          <div>
            <p class="font-book text-base font-semibold text-ink">尚未开设作者档案</p>
            <p class="mt-1 text-xs leading-relaxed text-ink-secondary">
              定下笔名与联络方式，作品署名将随之生效；创建作品时也会自动开档。
            </p>
          </div>
          <Button variant="cinnabar" @click="openAuthorEdit">
            <PenLine class="size-4" />
            开设作者档案
          </Button>
        </div>
      </Card>
    </template>

    <!-- 编辑资料对话框 -->
    <Dialog
      v-model:open="profileOpen"
      title="编辑资料"
      description="昵称与性别（昵称 1-20 字）。"
      width="sm"
    >
      <form class="space-y-4" @submit.prevent="saveProfile">
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">昵称</span>
          <Input v-model="profileForm.nickName" placeholder="例：执墨君" maxlength="20" />
        </label>
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">性别</span>
          <Select
            v-model="profileForm.userSex"
            :options="[
              { label: '男', value: '0' },
              { label: '女', value: '1' },
            ]"
            placeholder="未设置"
            width-class="w-36"
          />
        </label>
      </form>
      <template #footer>
        <Button variant="ghost" @click="profileOpen = false">取消</Button>
        <Button variant="cinnabar" :loading="profileSaving" @click="saveProfile">保存</Button>
      </template>
    </Dialog>

    <!-- 作者档案对话框 -->
    <Dialog
      v-model:open="authorOpen"
      title="作者档案"
      description="笔名必填（1-20 字）；其余留空表示不设置。"
      width="md"
    >
      <form class="space-y-4" @submit.prevent="saveAuthor">
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">笔名 <span class="text-cinnabar">*</span></span>
          <Input v-model="authorForm.penName" placeholder="例：执墨君" maxlength="20" />
        </label>
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">创作方向</span>
          <Combobox
            v-model="authorForm.workDirection"
            :options="[
              { label: '男频', value: '男频' },
              { label: '女频', value: '女频' },
              { label: '无特定方向', value: '无特定方向' },
            ]"
            placeholder="选择或输入自定义方向（可留空）"
            maxlength="20"
          />
        </label>
        <div class="grid gap-4 sm:grid-cols-2">
          <label class="block">
            <span class="mb-1.5 block text-xs font-medium text-ink">电子邮箱</span>
            <Input v-model="authorForm.email" placeholder="name@example.com" maxlength="64" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-xs font-medium text-ink">联系电话</span>
            <Input v-model="authorForm.telPhone" placeholder="可选" maxlength="20" />
          </label>
        </div>
        <label class="block">
          <span class="mb-1.5 block text-xs font-medium text-ink">社交账号</span>
          <Input v-model="authorForm.chatAccount" placeholder="可选（微信 / QQ 等）" maxlength="64" />
        </label>
      </form>
      <template #footer>
        <Button variant="ghost" @click="authorOpen = false">取消</Button>
        <Button variant="cinnabar" :loading="authorSaving" @click="saveAuthor">
          {{ creatingAuthor ? '开档' : '保存' }}
        </Button>
      </template>
    </Dialog>
  </AppShell>
</template>
