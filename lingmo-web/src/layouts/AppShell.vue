<script setup lang="ts">
/**
 * 门户壳（AppShell）：主导航「创作第一」（规范 4.1）
 * 创作工作台为主入口；书架/发现/知识库为创作成果的展示与辅助入口。
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOpen, CircleUserRound, Feather, Library, Search, LogOut } from 'lucide-vue-next'
import { DropdownMenu, DropdownItem, Button } from '@/components/ui'
import { useAuth } from '@/composables/useAuth'
import { cn } from '@/utils/cn'

const route = useRoute()
const router = useRouter()
const { store, logout } = useAuth()
const userMenuOpen = ref(false)

const navItems = [
  { label: '创作工作台', to: '/workbench', icon: Feather, match: /^\/workbench/ },
  { label: '我的书架', to: '/bookshelf', icon: Library, match: /^\/bookshelf/ },
  { label: '发现', to: '/discover', icon: Search, match: /^\/discover/ },
  { label: '知识库', to: '/knowledgeBaseList', icon: BookOpen, match: /^\/knowledgeBase/ },
]

const activeItem = computed(() => navItems.find((item) => item.match.test(route.path)))
</script>

<template>
  <div class="flex min-h-screen flex-col bg-paper">
    <header class="sticky top-0 z-30 border-b border-mist-line bg-paper-surface/95 backdrop-blur-sm">
      <div class="mx-auto flex h-14 w-full max-w-6xl items-center gap-4 px-4 sm:px-6">
        <!-- 品牌：纸墨书卷 -->
        <RouterLink to="/workbench" class="flex items-center gap-2.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40 focus-visible:rounded-sm">
          <span class="flex size-7 items-center justify-center rounded-sm bg-ink-indigo font-book text-sm font-bold text-paper-surface" aria-hidden="true">墨</span>
          <span class="font-book text-base font-semibold text-ink">纸墨书卷</span>
          <span class="hidden text-[10px] leading-none text-mist sm:block">AI 小说创作平台</span>
        </RouterLink>

        <nav class="ml-2 hidden items-center gap-1 sm:flex" aria-label="主导航">
          <RouterLink
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            :class="cn(
              'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-[13px] font-medium transition-colors duration-150 ease-[var(--ease-paper)]',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40',
              activeItem?.to === item.to
                ? 'bg-ink-indigo/10 text-ink-indigo'
                : 'text-ink-secondary hover:bg-paper hover:text-ink',
            )"
          >
            <component :is="item.icon" class="size-3.5" />
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="ml-auto flex items-center gap-2">
          <RouterLink to="/workbench">
            <Button variant="cinnabar" size="sm">
              <Feather class="size-3.5" />
              开始创作
            </Button>
          </RouterLink>
          <DropdownMenu v-model:open="userMenuOpen">
            <template #trigger>
              <button
                type="button"
                class="flex size-8 items-center justify-center rounded-full border border-mist-line bg-paper-surface font-book text-xs text-ink transition-colors hover:border-ink-indigo/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ink-indigo/40"
                aria-label="用户菜单"
              >
                {{ store.userId ? String(store.userId).slice(-2) : '客' }}
              </button>
            </template>
            <div class="px-2.5 py-1.5 text-xs text-ink-secondary">
              用户 {{ store.userId ?? '—' }}
            </div>
            <DropdownItem @select="router.push('/profile')">
              <CircleUserRound class="size-3.5" />
              个人主页
            </DropdownItem>
            <DropdownItem @select="router.push('/workbench')">
              <BookOpen class="size-3.5" />
              我的作品
            </DropdownItem>
            <DropdownItem danger @select="logout()">
              <LogOut class="size-3.5" />
              登出
            </DropdownItem>
          </DropdownMenu>
        </div>
      </div>
    </header>

    <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6">
      <slot />
    </main>

    <footer class="border-t border-mist-line py-4 text-center text-[11px] text-mist">
      纸墨书卷 · 可信多智能体协同小说创作平台
    </footer>
  </div>
</template>
