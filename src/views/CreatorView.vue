<template>
  <el-container class="writer-layout">
    <!-- 左侧侧边栏 -->
    <el-aside width="340px" class="sidebar">
      <div class="sidebar-header">
        <h2>🚀 AI 助手</h2>
      </div>

      <el-scrollbar class="sidebar-content">
        <div class="sidebar-padding">
          <!-- 创作工具卡片 -->
          <el-card shadow="never" class="ai-tool-card">
            <template #header>
              <div class="font-medium">创作工具</div>
            </template>

            <el-tabs v-model="activeAiTab" class="ai-tools-tabs">
              <el-tab-pane label="智能续写" name="continue">
                <p class="ai-tool-desc">AI 将在您光标位置或文章末尾继续书写。</p>
                <el-button
                  type="primary"
                  class="full-width-btn"
                  :loading="aiLoading"
                  @click="handleAiAction('continue')"
                >
                  开始续写
                </el-button>
              </el-tab-pane>
              <el-tab-pane label="灵感激发" name="inspire">
                <p class="ai-tool-desc">输入关键词，AI 帮您构思情节。</p>
                <el-input
                  v-model="aiPrompt"
                  type="textarea"
                  :rows="3"
                  placeholder="例如：一个雨夜，侦探收到了神秘的包裹..."
                  class="ai-prompt-input"
                />
                <el-button
                  type="primary"
                  class="full-width-btn"
                  :loading="aiLoading"
                  @click="handleAiAction('inspire')"
                >
                  获取灵感
                </el-button>
              </el-tab-pane>
              <el-tab-pane label="内容润色" name="polish">
                <p class="ai-tool-desc">请<strong>选中</strong>您想优化的文字，然后点击润色。</p>
                <el-button
                  type="primary"
                  class="full-width-btn"
                  :loading="aiLoading"
                  @click="handleAiAction('polish')"
                >
                  开始润色
                </el-button>
              </el-tab-pane>
            </el-tabs>
          </el-card>

          <!-- 章节列表卡片（始终显示） -->
          <el-card shadow="never" class="chapter-list-card">
            <template #header>
              <div class="chapter-header">
                <span class="font-medium">章节列表</span>
                <el-button
                  type="primary"
                  size="small"
                  :loading="loadingChapters"
                  @click="addNewChapter"
                >
                  新增章节
                </el-button>
              </div>
            </template>

            <el-scrollbar style="max-height: 400px; padding-right: 8px;">
              <div v-if="chapterList.length === 0" class="no-chapters-section">
                <p class="no-chapters">暂无章节</p>
                <p class="hint-text">点击“新增章节”开始创作</p>
              </div>
              <ul v-else class="chapter-list">
                <li
                  v-for="chapter in chapterList"
                  :key="chapter?.id"
                  class="chapter-item"
                  :class="{ active: currentChapterId === chapter?.id }"
                  @click="loadChapter(chapter)"
                >
                  {{ chapter.title }}
                </li>
              </ul>
            </el-scrollbar>
          </el-card>
        </div>
      </el-scrollbar>
    </el-aside>

    <!-- 主编辑区 -->
    <el-container class="main-content">
      <el-header class="editor-header" height="60px">
        <div class="header-left">
          <el-input
            v-model="chapterTitle"
            placeholder="输入章节标题"
            class="chapter-title-input"
            :border="false"
          />
        </div>
        <div class="header-right">
          <el-statistic title="当前字数" :value="wordCount" class="word-count-stat" />
          <el-button type="primary" :loading="saving" @click="handleSave">保 存</el-button>
          <el-avatar> 作 </el-avatar>
        </div>
      </el-header>

      <el-main class="editor-main-area">
        <div class="paper">
          <!-- Tiptap 工具栏 -->
          <div v-if="editor" class="tiptap-toolbar">
            <el-button-group>
              <el-button
                text
                @click="editor.chain().focus().toggleBold().run()"
                :type="editor.isActive('bold') ? 'primary' : 'default'"
              >
                <span style="font-weight: bold;">B</span>
              </el-button>
              <el-button
                text
                @click="editor.chain().focus().toggleItalic().run()"
                :type="editor.isActive('italic') ? 'primary' : 'default'"
              >
                <span style="font-style: italic;">I</span>
              </el-button>
              <el-button
                text
                @click="editor.chain().focus().toggleStrike().run()"
                :type="editor.isActive('strike') ? 'primary' : 'default'"
              >
                <span style="text-decoration: line-through;">S</span>
              </el-button>
            </el-button-group>

            <el-button-group class="toolbar-group">
              <el-button
                text
                @click="editor.chain().focus().setHeading({ level: 1 }).run()"
                :type="editor.isActive('heading', { level: 1 }) ? 'primary' : 'default'"
              >
                H1
              </el-button>
              <el-button
                text
                @click="editor.chain().focus().setHeading({ level: 2 }).run()"
                :type="editor.isActive('heading', { level: 2 }) ? 'primary' : 'default'"
              >
                H2
              </el-button>
              <el-button
                text
                @click="editor.chain().focus().setHeading({ level: 3 }).run()"
                :type="editor.isActive('heading', { level: 3 }) ? 'primary' : 'default'"
              >
                H3
              </el-button>
              <el-button
                text
                @click="editor.chain().focus().setParagraph().run()"
                :type="editor.isActive('paragraph') ? 'primary' : 'default'"
              >
                P
              </el-button>
            </el-button-group>

            <el-button-group class="toolbar-group">
              <el-button
                text
                @click="editor.chain().focus().toggleBulletList().run()"
                :type="editor.isActive('bulletList') ? 'primary' : 'default'"
              >
                无序列表
              </el-button>
              <el-button
                text
                @click="editor.chain().focus().toggleOrderedList().run()"
                :type="editor.isActive('orderedList') ? 'primary' : 'default'"
              >
                有序列表
              </el-button>
            </el-button-group>

            <el-button-group class="toolbar-group">
              <el-button text @click="editor.chain().focus().undo().run()">撤销</el-button>
              <el-button text @click="editor.chain().focus().redo().run()">重做</el-button>
            </el-button-group>
          </div>

          <!-- Tiptap 编辑器 -->
          <el-scrollbar class="tiptap-editor-wrapper">
            <editor-content :editor="editor" />
          </el-scrollbar>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {useEditor, EditorContent, type JSONContent} from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import CharacterCount from '@tiptap/extension-character-count'
import {useRoute} from "vue-router";
import {
  type AiChatRequest,
  type CreateBook,
  type CreateBookPageRequest, type CreateBookVo,
  CreationBookControllerService,
} from "@/api";

import {useUserStore} from "@/stores/user.ts";
import {streamAiPost} from "@/utils/streamPost.ts";
const user = useUserStore()
const route = useRoute()

// 获取书籍id
const novelId = parseInt(<string>route.params.id)
const hasMore = ref(true)
const loadingMore = ref(false)
const createBookPageRequest = ref<CreateBookPageRequest>({
  pageNum: 1,
  pageSize: 10,
  bookId: novelId,
  sort: false
})
const chapterTitle = ref('第一章：悬浮之城')
const activeAiTab = ref('continue')
const aiPrompt = ref('')
const aiLoading = ref(false)
const saving = ref(false)
const loadingChapters = ref(false)
const authorId = user.authorId
// 章节管理
const chapterList = ref<Array<CreateBookVo>>([])
const currentChapterId = ref<number>()

// ========== Tiptap 编辑器 ==========
const editor = useEditor({
  extensions: [
    StarterKit,
    Placeholder.configure({
      placeholder: '从这里开始你的故事...',
    }),
    CharacterCount,
  ],
  content: '<p>加载中...</p>',
  editorProps: {
    attributes: {
      class: 'focus:outline-none',
    },
  },
})

// ========== 计算属性 ==========
const wordCount = computed(() => {
  if (!editor.value || !editor.value.storage.characterCount) return 0
  return editor.value.storage.characterCount.characters()
})

// ========== 生命周期 ==========
onMounted(() => {
  fetchChapterList()
})

onBeforeUnmount(() => {
  editor.value?.destroy()
})

// ========== API 方法 ==========
// 获取章节列表（可追加）
const fetchChapterList = async (pageNum: number = 1, append = false) => {
  if (pageNum === 1) {
    loadingChapters.value = true
    hasMore.value = true
  } else {
    loadingMore.value = true
  }

  try {
    createBookPageRequest.value.pageNum = pageNum
    const res = await CreationBookControllerService.createBookPageByBookId(createBookPageRequest.value)
    if (res.code !== 200) {
      ElMessage.error(res.msg)
    }
    const data = res.data?.records

    if (pageNum === 1) {
      chapterList.value = data || []
    } else if (append) {
      chapterList.value.push(...data)
    }

    // 判断是否还有更多
    if (res.data?.total < pageNum) {
      hasMore.value = false
    }

    // 如果是第一页且有数据，默认加载第一个章节
    if (pageNum === 1 && data?.length > 0 && !currentChapterId.value) {
      if (data) {
        await loadChapter(data[0])
      }
    }
  } catch (err) {
    ElMessage.error(pageNum === 1 ? '加载章节失败' : '加载更多失败')
    console.error(err)
    if (pageNum === 1) chapterList.value = []
    hasMore.value = false
  } finally {
    loadingChapters.value = false
    loadingMore.value = false
  }
}

const loadChapter = async (chapter: CreateBookVo) => {
  try {
    chapterTitle.value = chapter.title || ''

    let content = chapter.content

    // 如果 content 是字符串，尝试解析为 JSON 对象
    if (typeof content === 'string') {
      try {
        content = JSON.parse(content)
      } catch (e) {
        console.warn('Content is not valid JSON, treating as plain text or HTML', content)
        // 可选：fallback 到 HTML 模式
        // content = `<p>${content}</p>`
      }
    }

    // 现在 content 应该是 { type: 'doc', content: [...] } 对象
    editor.value?.commands.setContent(content || { type: 'doc', content: [] })

    currentChapterId.value = chapter.id
  } catch (err) {
    ElMessage.error('加载章节失败')
    console.error(err)
  }
}

const addNewChapter = async () => {
  const nextNum = chapterList.value.length + 1
  let defaultTitle;
  if (chapterTitle.value !== '') {
    defaultTitle = `第${nextNum}章：${chapterTitle.value}`
  } else {
    defaultTitle = `第${nextNum}章：新章节`
  }

  try {
    const res = await CreationBookControllerService.addCreateBook({
      bookId: novelId,
      title: defaultTitle,
      number: nextNum,
      authorId: parseInt(authorId)
    })

    if (res.code !== 200) {
      ElMessage.error(res.msg)
    }

    if (res.data) {
      chapterList.value.push(res.data)
      await loadChapter(res.data)
      ElMessage.success(`已创建 ${res.data.title}`)
    }
  } catch (err) {
    ElMessage.error('创建章节失败')
    console.error(err)
  }
}

const handleSave = async () => {
  if (!currentChapterId.value || !editor.value) {
    ElMessage.warning('没有可保存的章节')
    return
  }

  saving.value = true
  try {
    const createBook: CreateBook = {
      content: editor.value.getJSON(), // ← 这是最新内容
      title: chapterTitle.value.trim() || '无标题',
      id: currentChapterId.value,
      wordCount: wordCount.value
    }

    const res = await CreationBookControllerService.updateCreateBook(createBook)

    if (res.code !== 200) {
      ElMessage.error(res.msg)
      throw new Error("保存失败")
    }

    // ✅ 关键：更新 chapterList 中对应章节的内容和标题
    const index = chapterList.value.findIndex(ch => ch.id === currentChapterId.value)
    if (index !== -1) {
      // 注意：不要直接修改对象属性（响应式问题），而是替换整个对象或使用 $patch
      chapterList.value[index] = {
        ...chapterList.value[index],
        title: createBook.title,
        content: createBook.content, // ← 更新为最新 JSON 内容
        wordCount: createBook.wordCount
      }
    }

    ElMessage.success('章节已保存！')
  } catch (err) {
    ElMessage.error('保存失败')
    console.error(err)
  } finally {
    saving.value = false
  }
}
// 工具：对象转查询字符串

function textToTiptapDoc(text: string): JSONContent {
  // 1. 移除所有因 SSE 分块产生的多余换行
  //    将连续空白（包括 \n、\r、空格）压缩为单个空格
  const cleanedText = text
    .replace(/\s+/g, ' ')   // 所有空白 → 单空格
    .trim();

  if (!cleanedText) {
    return { type: 'doc', content: [] };
  }

  // 2. 按语义分段（比如用句号、问号、感叹号 + 后续大写）
  //    或者简单起见：整个作为一段（推荐初期）
  return {
    type: 'doc',
    content: [
      {
        type: 'paragraph',
        content: [{ type: 'text', text: cleanedText }],
      },
    ],
  };
}
// ========== AI 操作（保持原逻辑） ==========
const handleAiAction = async (actionType: string) => {
  if (!editor.value) return;
  aiLoading.value = true;

  try {
    const baseRequest: AiChatRequest = {
      bookId: novelId,
      chapterId: currentChapterId.value,
      title: chapterTitle.value.trim(),
      creationType: '',
      userInput: '',
    };

    let endpoint = '';
    let requestBody: AiChatRequest;

    if (actionType === 'polish') {
      const { from, to, empty } = editor.value.state.selection;
      if (empty) {
        ElMessage.warning('请先选中您想要润色的文字。');
        return;
      }
      const selectedText = editor.value.state.doc.textBetween(from, to);
      requestBody = { ...baseRequest, creationType: '润色', userInput: selectedText };
      endpoint = '/api/book/create/ai/polish';
    } else if (actionType === 'inspire') {
      const prompt = aiPrompt.value.trim() || '给我一些创作灵感';
      requestBody = { ...baseRequest, creationType: '灵感', userInput: prompt };
      endpoint = '/api/book/create/ai/activate';
    } else if (actionType === 'continue') {
      const currentContent = editor.value.getText();
      requestBody = { ...baseRequest, creationType: '续写', userInput: currentContent };
      endpoint = '/api/book/create/ai/continue';
    } else {
      ElMessage.error('未知操作');
      return;
    }

    // ✅ 关键调用：流式 POST
    const aiResultText = await streamAiPost(endpoint, requestBody);

    // 插入结果
    if (actionType === 'inspire') {
      ElMessage.info({
        dangerouslyUseHTMLString: true,
        message: `<b>AI 灵感：</b><br>${aiResultText.replace(/\n/g, '<br>')}`,
        duration: 8000,
        showClose: true,
      });
    } else if (actionType === 'polish') {
      const { from, to } = editor.value.state.selection;
      editor.value.chain().deleteRange({ from, to }).insertContent(textToTiptapDoc(aiResultText)).run();
    } else if (actionType === 'continue') {
      editor.value.chain().focus().insertContentAt(editor.value.state.doc.content.size, textToTiptapDoc(aiResultText)).run();
    }

    ElMessage.success('AI 生成成功！');
  } catch (err: any) {
    console.error('AI 请求失败:', err);
    ElMessage.error(err.message || 'AI 生成失败，请稍后重试');
  } finally {
    aiLoading.value = false;
  }
};
</script>

<style scoped>
/* ----- 全局布局 ----- */
.writer-layout {
  height: 100vh;
  background-color: #f8f9fa;
}

/* ----- 侧边栏 ----- */
.sidebar {
  background-color: #ffffff;
  border-right: 1px solid #e9ecef;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.sidebar-header {
  padding: 1rem;
  border-bottom: 1px solid #e9ecef;
}
.sidebar-header h2 {
  font-size: 1.25rem;
  font-weight: 600;
  color: #212529;
  margin: 0;
}
.sidebar-content {
  flex-grow: 1;
}
.sidebar-padding {
  padding: 1rem;
}
.ai-tool-card,
.chapter-list-card {
  border: none !important;
}
.ai-tool-desc {
  font-size: 0.875rem;
  color: #6c757d;
  margin-bottom: 0.75rem;
}
.full-width-btn {
  width: 100%;
}
.ai-prompt-input {
  margin-bottom: 0.75rem;
}

/* 章节列表样式 */
.chapter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chapter-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.chapter-item {
  padding: 0.5rem 0;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.2s;
}
.chapter-item:hover,
.chapter-item.active {
  background-color: #e3f2fd;
}
.chapter-item.active {
  font-weight: bold;
}
.no-chapters-section {
  padding: 1.5rem 1rem;
  text-align: center;
  color: #6c757d;
  font-size: 0.875rem;
}
.hint-text {
  margin-top: 0.5rem;
  font-size: 0.75rem;
  color: #adb5bd;
}

/* ----- 主内容区 ----- */
.main-content {
  display: flex;
  flex-direction: column;
}
.editor-header {
  background-color: #ffffff;
  border-bottom: 1px solid #e9ecef;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 1.5rem;
}
.header-left {
  display: flex;
  align-items: center;
}
.chapter-title-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  font-size: 1.15rem;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}
.word-count-stat {
  --el-statistic-title-font-size: 0.8rem;
}

/* ----- 编辑器区域 ----- */
.editor-main-area {
  background-color: #f1f3f5;
  padding: 2rem;
  display: flex;
  justify-content: center;
  overflow: hidden;
}
.paper {
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  width: 100%;
  max-width: 860px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.tiptap-toolbar {
  border-bottom: 1px solid #e9ecef;
  padding: 0.5rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}
.toolbar-group {
  margin-left: 0.5rem;
}
.tiptap-editor-wrapper {
  height: 100%;
  flex-grow: 1;
}
.tiptap-editor-wrapper :deep(.ProseMirror) {
  padding: 2rem 2.5rem;
  min-height: 60vh;
  line-height: 1.8;
  font-size: 1.1rem;
  color: #333;
  font-family: "LXGW WenKai", "Source Serif Pro", Georgia, "Times New Roman", serif;
  outline: none;
}
.tiptap-editor-wrapper :deep(.ProseMirror p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  color: #adb5bd;
  pointer-events: none;
  height: 0;
}
.tiptap-editor-wrapper :deep(.ProseMirror p) {
  margin-bottom: 1.25em;
}
.tiptap-editor-wrapper :deep(.ProseMirror h1),
.tiptap-editor-wrapper :deep(.ProseMirror h2),
.tiptap-editor-wrapper :deep(.ProseMirror h3) {
  margin-top: 1em;
  margin-bottom: 0.5em;
}
.tiptap-editor-wrapper :deep(.ProseMirror h1) { font-size: 2.25em; font-weight: 700; }
.tiptap-editor-wrapper :deep(.ProseMirror h2) { font-size: 1.75em; font-weight: 600; }
.tiptap-editor-wrapper :deep(.ProseMirror h3) { font-size: 1.25em; font-weight: 600; }
.tiptap-editor-wrapper :deep(.ProseMirror strong) { font-weight: 700; }
.tiptap-editor-wrapper :deep(.ProseMirror em) { font-style: italic; }
.tiptap-editor-wrapper :deep(.ProseMirror s) { text-decoration: line-through; }
.tiptap-editor-wrapper :deep(.ProseMirror ul),
.tiptap-editor-wrapper :deep(.ProseMirror ol) {
  padding-left: 1.5rem;
  margin-bottom: 1em;
}
.tiptap-editor-wrapper :deep(.ProseMirror li) {
  margin-bottom: 0.25em;
}
</style>
