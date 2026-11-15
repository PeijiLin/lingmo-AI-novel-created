<template>
  <div class="reader-container" :class="backgroundColor">
    <div class="reader-header">
      <button @click="goHome" class="back-btn">←</button>
      <h1 class="book-title">{{ bookTitle }}</h1>
      <div class="reading-progress">{{ progress }}%</div>
    </div>
    
    <div class="reader-content" @click="handleContentClick">
      <div class="chapter-title">
        <h2>{{ chapterTitle }}</h2>
      </div>
      <div class="chapter-content" v-html="chapterContent"></div>
    </div>
    
    <div class="reader-footer">
      <button @click="prevChapter" :disabled="currentChapter <= 1">上一章</button>
      <button @click="toggleSettings">设置</button>
      <button @click="nextChapter" :disabled="currentChapter >= totalChapters">下一章</button>
    </div>
    
    <div class="settings-panel" v-if="showSettings">
      <div class="setting-item">
        <label for="font-size">字体大小:</label>
        <input type="range" id="font-size" min="12" max="24" v-model="fontSize">
        <span>{{ fontSize }}px</span>
      </div>
      <div class="setting-item">
        <label for="background-color">背景颜色:</label>
        <select id="background-color" v-model="backgroundColor">
          <option value="paper">纸张白</option>
          <option value="dark">夜间黑</option>
          <option value="green">护眼绿</option>
        </select>
      </div>
      <div class="setting-item">
        <label for="line-height">行间距:</label>
        <input type="range" id="line-height" min="1.2" max="2.0" step="0.1" v-model="lineHeight">
        <span>{{ lineHeight }}</span>
      </div>
      <button @click="applySettings">应用设置</button>
    </div>
    
    <!-- 侧边菜单 -->
    <div class="side-menu" v-if="showMenu">
      <div class="menu-header">
        <h2>目录</h2>
        <button @click="toggleMenu" class="close-btn">×</button>
      </div>
      <div class="chapter-list">
        <div 
          v-for="chapter in chapters" 
          :key="chapter.id"
          class="chapter-item"
          :class="{ active: chapter.id === currentChapter }"
          @click="goToChapter(chapter.id)"
        >
          {{ chapter.title }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const props = defineProps({
  bookId: {
    type: String,
    default: '1'
  }
})

// 根据书籍ID获取书籍信息
const bookData: Record<string, any> = {
  '1': {
    title: '创世大陆',
    chapters: [
      { id: 1, title: '第一章：初入异界' },
      { id: 2, title: '第二章：村庄奇遇' },
      { id: 3, title: '第三章：神秘力量' },
      { id: 4, title: '第四章：踏上征程' },
      { id: 5, title: '第五章：初识魔法' }
    ]
  },
  '2': {
    title: '异界之旅',
    chapters: [
      { id: 1, title: '第一章：穿越开始' },
      { id: 2, title: '第二章：新的世界' },
      { id: 3, title: '第三章：初次战斗' }
    ]
  }
}

const bookTitle = ref('创世大陆')
const chapterTitle = ref('第一章：初入异界')
const chapterContent = ref(`
  <p>李明睁开眼睛，发现自己躺在一片陌生的草地上。天空是淡紫色的，有两个太阳悬挂在空中。</p>
  <p>“这是哪里？”他喃喃自语，努力回忆着之前发生的事情。</p>
  <p>记忆中，他正在使用那个新买的智能小说创作平台，想要体验一下AI辅助写作的功能。结果一阵眩晕后，就出现在了这里。</p>
  <p>不远处传来脚步声，李明赶紧站起身来。一个身穿奇异服装的少年正朝他走来。</p>
  <p>“你是新来的吗？欢迎来到创世大陆！”少年热情地打招呼。</p>
  <p>李明愣住了，创世大陆？这不是他正在创作的小说中的世界吗？难道……</p>
  <p>“我叫小风，是这个村庄的向导。你是从外面世界来的吧？”少年友善地问道。</p>
  <p>李明点点头，不知道该如何解释自己的情况。他决定先了解这个世界再说。</p>
  <p>“那太好了！我们正需要像你这样有见识的人。跟我来吧，我带你去见村长。”小风伸出手，邀请李明一起前行。</p>
  <p>李明犹豫了一下，最终还是握住了小风的手。不管怎样，他需要了解这个世界的规则，才能找到回家的路。</p>
`)

const currentChapter = ref(1)
const totalChapters = ref(5)
const progress = ref(0)
const showSettings = ref(false)
const showMenu = ref(false)

// 章节列表
const chapters = ref([
  { id: 1, title: '第一章：初入异界' },
  { id: 2, title: '第二章：村庄奇遇' },
  { id: 3, title: '第三章：神秘力量' },
  { id: 4, title: '第四章：踏上征程' },
  { id: 5, title: '第五章：初识魔法' }
])

// 阅读设置
const fontSize = ref(16)
const backgroundColor = ref('paper')
const lineHeight = ref(1.6)

const goHome = () => {
  router.push('/')
}

const toggleMenu = () => {
  showMenu.value = !showMenu.value
}

const handleContentClick = (event: MouseEvent) => {
  // 点击内容区域翻页逻辑 - 简单实现为下一页
  nextChapter()
}

const prevChapter = () => {
  if (currentChapter.value > 1) {
    currentChapter.value--
    updateChapterInfo()
  }
}

const nextChapter = () => {
  if (currentChapter.value < totalChapters.value) {
    currentChapter.value++
    updateChapterInfo()
  }
}

const goToChapter = (chapterId: number) => {
  currentChapter.value = chapterId
  updateChapterInfo()
  showMenu.value = false
}

const toggleSettings = () => {
  showSettings.value = !showSettings.value
}

const applySettings = () => {
  // 应用阅读设置
  document.documentElement.style.setProperty('--reader-font-size', `${fontSize.value}px`)
  document.documentElement.style.setProperty('--reader-line-height', lineHeight.value.toString())
  
  showSettings.value = false
}

const updateChapterInfo = () => {
  // 根据当前章节更新标题和内容（模拟）
  chapterTitle.value = chapters.value.find(ch => ch.id === currentChapter.value)?.title || `第${currentChapter.value}章`
  updateProgress()
}

const updateProgress = () => {
  progress.value = Math.round((currentChapter.value / totalChapters.value) * 100)
}

onMounted(() => {
  // 根据传入的书籍ID设置书籍信息
  if (bookData[props.bookId]) {
    bookTitle.value = bookData[props.bookId].title
    chapters.value = bookData[props.bookId].chapters
    totalChapters.value = chapters.value.length
  }
  
  updateProgress()
  applySettings()
})
</script>

<style scoped>
.reader-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 0;
  margin: 0;
  font-family: 'Microsoft YaHei', 'SimHei', sans-serif;
  transition: background-color 0.3s ease, color 0.3s ease;
}

.reader-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  border-bottom: 1px solid #eee;
  background-color: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(5px);
  position: sticky;
  top: 0;
  z-index: 100;
}

.back-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  padding: 5px 10px;
}

.book-title {
  font-size: 18px;
  margin: 0;
  flex-grow: 1;
  text-align: center;
  padding: 0 20px;
}

.reading-progress {
  font-size: 14px;
  color: #666;
}

.reader-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  font-size: var(--reader-font-size, 16px);
  line-height: var(--reader-line-height, 1.6);
}

.chapter-title h2 {
  text-align: center;
  margin-bottom: 30px;
  font-weight: normal;
}

.chapter-content p {
  margin-bottom: 20px;
  text-indent: 2em;
}

.reader-footer {
  display: flex;
  justify-content: space-between;
  padding: 15px 20px;
  border-top: 1px solid #eee;
  background-color: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(5px);
  position: sticky;
  bottom: 0;
}

.reader-footer button {
  padding: 8px 16px;
  background-color: #f0f0f0;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.reader-footer button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.settings-panel {
  position: fixed;
  bottom: 60px;
  left: 50%;
  transform: translateX(-50%);
  background: white;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 200;
  width: 300px;
}

.setting-item {
  margin-bottom: 15px;
}

.setting-item label {
  display: block;
  margin-bottom: 5px;
}

.setting-item input,
.setting-item select {
  width: 100%;
  padding: 5px;
  margin-bottom: 5px;
}

.setting-item span {
  font-size: 0.9rem;
  color: #666;
}

.settings-panel button {
  width: 100%;
  padding: 10px;
  background-color: #42b983;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

/* 侧边菜单 */
.side-menu {
  position: fixed;
  top: 0;
  left: 0;
  width: 300px;
  height: 100vh;
  background: white;
  box-shadow: 2px 0 10px rgba(0, 0, 0, 0.1);
  z-index: 300;
  overflow-y: auto;
}

.menu-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  border-bottom: 1px solid #eee;
}

.menu-header h2 {
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
}

.chapter-list {
  padding: 10px 0;
}

.chapter-item {
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}

.chapter-item:hover {
  background-color: #f5f5f5;
}

.chapter-item.active {
  background-color: #42b983;
  color: white;
}

/* 背景颜色主题 */
.paper {
  background-color: #f8f5ee;
  color: #333;
}

.dark {
  background-color: #1a1a1a;
  color: #ddd;
}

.green {
  background-color: #e3f0e1;
  color: #333;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .side-menu {
    width: 250px;
  }
  
  .reader-header,
  .reader-footer {
    padding: 10px 15px;
  }
  
  .reader-content {
    padding: 15px;
  }
  
  .settings-panel {
    width: 250px;
    left: 50%;
    transform: translateX(-50%);
  }
}
</style>