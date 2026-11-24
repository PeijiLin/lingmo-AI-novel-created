<script setup lang="ts">
/**
 * 落地页（规范 4.2 门户：首屏即创作，非门户陈列）
 * 未登录访客：一句话定位 + 登录/注册引导；已登录直接进创作台。
 */
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Feather, BookOpen, Sparkles, PenLine } from 'lucide-vue-next'
import { Button } from '@/components/ui'
import { isAuthenticated } from '@/utils/token'

const router = useRouter()

onMounted(() => {
  if (isAuthenticated()) router.replace('/workbench')
})
</script>

<template>
  <div class="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-paper px-6">
    <!-- 纸面底纹：淡淡的稿线 -->
    <div class="pointer-events-none absolute inset-0 opacity-[0.05]" aria-hidden="true">
      <div v-for="i in 24" :key="i" class="mx-auto h-px w-full max-w-3xl bg-ink" :style="{ marginTop: `${i * 32}px` }" />
    </div>

    <div class="relative z-10 flex max-w-2xl flex-col items-center text-center">
      <span class="flex size-16 items-center justify-center rounded-lg bg-ink-indigo font-book text-3xl font-bold text-paper-surface shadow-card">墨</span>
      <h1 class="mt-6 font-book text-4xl font-semibold tracking-wide text-ink">纸墨书卷</h1>
      <p class="mt-3 font-kai text-lg leading-relaxed text-ink-secondary">落一笔，成一章；一叠稿纸，一支 AI 共笔。</p>

      <div class="mt-8 grid w-full max-w-xl gap-3 sm:grid-cols-3">
        <div class="rounded-md border border-mist-line bg-paper-surface px-4 py-3 text-left shadow-card">
          <PenLine class="size-4 text-cinnabar" />
          <p class="mt-1.5 text-[13px] font-medium text-ink">四档流水线</p>
          <p class="mt-0.5 text-[11px] leading-4 text-ink-secondary">FAST / STANDARD / REFINED / PRECISE 多智能体协作成章</p>
        </div>
        <div class="rounded-md border border-mist-line bg-paper-surface px-4 py-3 text-left shadow-card">
          <Sparkles class="size-4 text-cinnabar" />
          <p class="mt-1.5 text-[13px] font-medium text-ink">朱批旁注</p>
          <p class="mt-0.5 text-[11px] leading-4 text-ink-secondary">AI 的续写与审校如稿边朱批，采纳才落笔</p>
        </div>
        <div class="rounded-md border border-mist-line bg-paper-surface px-4 py-3 text-left shadow-card">
          <BookOpen class="size-4 text-cinnabar" />
          <p class="mt-1.5 text-[13px] font-medium text-ink">书卷陈列</p>
          <p class="mt-0.5 text-[11px] leading-4 text-ink-secondary">成果上架书架与排行，被阅读、被激发</p>
        </div>
      </div>

      <div class="mt-8 flex items-center gap-3">
        <RouterLink to="/register">
          <Button variant="cinnabar" size="lg"><Feather class="size-4" />成为作者</Button>
        </RouterLink>
        <RouterLink to="/login">
          <Button variant="outline" size="lg">返回登录</Button>
        </RouterLink>
      </div>

      <p class="mt-8 text-[11px] text-mist">创作 / 阅读 / 展示 · 可信多智能体协同小说创作平台</p>
    </div>
  </div>
</template>