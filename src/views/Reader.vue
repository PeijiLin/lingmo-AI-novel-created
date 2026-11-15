<template>
  <div
    ref="readerContainer"
    class="reader-container relative h-screen w-full overflow-hidden bg-amber-50 text-gray-800 cursor-none"
    :class="{ 'night-mode': isNightMode }"
    @click="toggleControls"
    @touchmove.prevent
  >
    <!-- 动态背景（根据书籍类型变化） -->
    <div class="absolute inset-0 z-0">
      <img
        :src="currentBackground"
        alt="Atmospheric Background"
        class="h-full w-full object-cover opacity-20 transition-opacity duration-1000"
        @error="handleImageError"
      />
      <div
        class="absolute inset-0 bg-gradient-to-b from-transparent via-white to-amber-50 opacity-60"
      ></div>
    </div>

    <!-- 环境音效控制（隐藏但后台播放） -->
    <audio ref="ambientAudio" loop></audio>

    <!-- 阅读内容区 -->
    <div class="relative z-10 flex h-full items-center justify-center p-6 sm:p-10">
      <div class="max-w-3xl text-center">
        <div
          v-for="(paragraph, idx) in currentChapter.content"
          :key="idx"
          class="mb-8 last:mb-0 leading-relaxed text-lg sm:text-xl md:text-2xl font-serif"
          :style="{ 'font-weight': idx === 0 ? '700' : '400', opacity: paragraph.opacity }"
        >
          {{ paragraph.text }}
        </div>
      </div>
    </div>

    <!-- 底部阅读进度指示器（极简设计） -->
    <div class="absolute bottom-8 left-1/2 transform -translate-x-1/2 z-20">
      <div class="flex items-center space-x-2 text-gray-600 text-sm">
        <span class="opacity-80">📖</span>
        <span>{{ Math.round(progress * 100) }}%</span>
        <span class="w-24 h-1 bg-gray-200 rounded-full overflow-hidden">
          <div
            class="h-full bg-gradient-to-r from-amber-500 to-orange-500 transition-all duration-300"
            :style="{ width: `${progress * 100}%` }"
          ></div>
        </span>
        <span class="opacity-80">{{ currentChapter.title }}</span>
      </div>
    </div>

    <!-- 控制层（仅在静止时显示） -->
    <div
      class="absolute inset-0 z-30 flex items-center justify-center pointer-events-none"
      :class="{ 'opacity-100': showControls, 'opacity-0': !showControls }"
    >
      <button
        @click="toggleNightMode"
        class="text-gray-700 text-2xl font-light hover:text-amber-600 transition-colors pointer-events-auto"
      >
        {{ isNightMode ? '🌙 夜间模式' : '☀️ 日间模式' }}
      </button>
    </div>

    <!-- 加载遮罩 -->
    <div
      v-if="isLoading"
      class="absolute inset-0 flex items-center justify-center z-50 bg-white bg-opacity-90"
    >
      <div class="text-center">
        <div class="w-16 h-16 border-4 border-amber-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
        <p class="text-gray-600">加载章节中…</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'

// 模拟书籍数据（实际项目中应从 API 获取）
const books = {
  'shiguangzhilun': {
    title: '时光之轮',
    author: '李青玄',
    genre: '玄幻',
    chapters: [
      {
        title: '第一章：星陨之兆',
        content: [
          { text: '天穹裂开一道缝隙，亿万星辰如泪滴坠落人间。', opacity: 1 },
          { text: '他睁开眼，看见自己站在一座倒悬的古城之上，脚下是流动的银河。', opacity: 1 },
          { text: '没有人记得他是谁，但所有神明都在恐惧他的名字。', opacity: 1 },
          { text: '那名字，刻在时间的尽头，无人敢念。', opacity: 1 }
        ]
      },
      {
        title: '第二章：遗忘之誓',
        content: [
          { text: '她曾是他最信任的人，却亲手将他封印于轮回之中。', opacity: 1 },
          { text: '如今，她忘了他，而他，仍记得她的每一缕发香。', opacity: 1 },
          { text: '命运说：重逢即是毁灭。可他，甘愿再死一万次。', opacity: 1 }
        ]
      }
    ]
  }
}

const route = useRoute()
const router = useRouter()

const bookId = route.params.id as string
const chapterIndex = ref(0)

const isLoading = ref(true)
const showControls = ref(false)
const isNightMode = ref(false) // 👈 默认为日间模式
const progress = ref(0)

const readerContainer = ref<HTMLDivElement | null>(null)

// 当前书籍和章节
const currentBook = computed(() => books[bookId as keyof typeof books])
const currentChapter = computed(() => currentBook.value?.chapters[chapterIndex.value] || {})

// 动态背景（根据类型匹配）
const currentBackground = computed(() => {
  if (!currentBook.value) return '/images/default-bg.jpg'
  const genre = currentBook.value.genre
  const backgrounds: Record<string, string> = {
    玄幻: 'https://picsum.photos/id/10/1920/1080', // 古风山水
    言情: 'https://picsum.photos/id/12/1920/1080', // 温暖窗景
    科幻: 'https://picsum.photos/id/15/1920/1080', // 星空与城市
    悬疑: 'https://picsum.photos/id/17/1920/1080', // 林中雾气
    仙侠: 'https://picsum.photos/id/18/1920/1080', // 云海仙山
    都市: 'https://picsum.photos/id/20/1920/1080'  // 咖啡馆窗外
  }
  return backgrounds[genre] || 'https://picsum.photos/id/10/1920/1080'
})

// 图片加载失败处理
const handleImageError = (e: Event) => {
  const img = e.target as HTMLImageElement
  img.src = 'https://picsum.photos/id/10/1920/1080'
}

// 播放环境音效（模拟）
const ambientAudio = ref<HTMLAudioElement | null>(null)

const playAmbientSound = () => {
  const sounds: Record<string, string> = {
    玄幻: 'https://assets.mixkit.co/sfx/preview/mixkit-ancient-chinese-music-233.mp3',
    言情: 'https://assets.mixkit.co/sfx/preview/mixkit-rain-on-window-256.mp3',
    科幻: 'https://assets.mixkit.co/sfx/preview/mixkit-futuristic-synth-wave-257.mp3',
    仙侠: 'https://assets.mixkit.co/sfx/preview/mixkit-ancient-chinese-guitar-music-232.mp3',
    默认: 'https://assets.mixkit.co/sfx/preview/mixkit-soft-ambient-background-music-231.mp3'
  }

  const soundUrl = sounds[currentBook.value?.genre || '默认']
  if (ambientAudio.value) {
    ambientAudio.value.src = soundUrl
    ambientAudio.value.play().catch(e => console.log('音频播放失败:', e))
  }
}

// 初始化
onMounted(async () => {
  await nextTick()
  isLoading.value = false
  playAmbientSound()
  startProgressTracking()
  document.body.style.overflow = 'hidden' // 禁止滚动
})

onUnmounted(() => {
  if (ambientAudio.value) ambientAudio.value.pause()
  document.body.style.overflow = 'auto'
})

// 进度追踪：每阅读 100 字，进度+1%
const startProgressTracking = () => {
  let totalChars = 0
  currentBook.value?.chapters.forEach(chap => {
    chap.content.forEach(p => {
      totalChars += p.text.length
    })
  })

  let readChars = 0
  const interval = setInterval(() => {
    if (readChars >= totalChars) {
      clearInterval(interval)
      return
    }
    readChars += 10
    progress.value = Math.min(readChars / totalChars, 1)
  }, 100)
}

// 翻页逻辑
const nextPage = () => {
  if (chapterIndex.value < (currentBook.value?.chapters.length || 0) - 1) {
    chapterIndex.value++
    progress.value = 0
    isLoading.value = true
    setTimeout(() => {
      isLoading.value = false
    }, 500)
  }
}

const prevPage = () => {
  if (chapterIndex.value > 0) {
    chapterIndex.value--
    progress.value = 0
    isLoading.value = true
    setTimeout(() => {
      isLoading.value = false
    }, 500)
  }
}

// 点击屏幕切换控制层显示
const toggleControls = () => {
  showControls.value = !showControls.value
  if (showControls.value) {
    setTimeout(() => {
      showControls.value = false
    }, 3000)
  }
}

// 切换昼夜模式
const toggleNightMode = () => {
  isNightMode.value = !isNightMode.value
}
</script>

<style scoped>
.reader-container {
  font-family: 'Source Han Serif SC', 'SimSun', 'Times New Roman', serif;
  line-height: 1.9;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* 深夜模式增强 */
.night-mode .reader-container {
  background-color: #121212;
  color: #e0e0d0;
}

.night-mode .reader-container::before,
.night-mode .bg-gradient-to-b {
  display: none;
}

.night-mode .text-gray-800 {
  color: #e0e0d0 !important;
}

.night-mode .bg-gray-200 {
  background-color: #333 !important;
}

.night-mode .bg-gradient-to-r.from-amber-500.to-orange-500 {
  background: linear-gradient(to right, #fbbf24, #f97316) !important;
}

.night-mode .text-gray-600 {
  color: #bbb !important;
}

/* 模拟纸张质感 */
.reader-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><rect width="100" height="100" fill="%23fdfaf5"/><path d="M0,0 L100,100 M100,0 L0,100" stroke="%23e8e0d0" stroke-width="0.5" opacity="0.08"/></svg>');
  opacity: 0.05;
  pointer-events: none;
}
</style>
