/**
 * 章节稿管理（按 bookId 加载/新增/保存/换章）
 */
import { ref } from 'vue'
import type { ChapterDraft } from '@/api/types'
import { addChapter, pageChapterDrafts, updateChapter } from '@/api/chapters'

export function useChapters(bookId: number) {
  const chapters = ref<ChapterDraft[]>([])
  const currentChapter = ref<ChapterDraft | null>(null)
  const loading = ref(false)
  const saving = ref(false)

  async function loadAll() {
    loading.value = true
    try {
      // 拉全量章节（创作台场景章节数有限，pageSize 取大页）
      const page = await pageChapterDrafts({ bookId, pageNum: 1, pageSize: 200 })
      // 后端按 number 升序未保证，前端稳定排序
      chapters.value = [...(page.records ?? [])].sort((a, b) => (a.number ?? 0) - (b.number ?? 0))
      if (!currentChapter.value && chapters.value.length > 0) {
        currentChapter.value = chapters.value[0]
      }
      return chapters.value
    } finally {
      loading.value = false
    }
  }

  function select(chapter: ChapterDraft) {
    currentChapter.value = chapter
    return chapter
  }

  async function create(title: string, authorId?: number): Promise<ChapterDraft> {
    const number = chapters.value.length + 1
    const draft = await addChapter({
      bookId,
      title,
      number,
      authorId,
      wordCount: 0,
    })
    chapters.value.push(draft)
    currentChapter.value = draft
    return draft
  }

  async function save(payload: { id: number; title: string; wordCount: number; content: unknown }): Promise<void> {
    saving.value = true
    try {
      await updateChapter({ id: payload.id, title: payload.title, wordCount: payload.wordCount, content: payload.content })
      // 同步本地列表
      const idx = chapters.value.findIndex((c) => c.id === payload.id)
      if (idx !== -1) {
        chapters.value[idx] = {
          ...chapters.value[idx],
          title: payload.title,
          wordCount: payload.wordCount,
          content: payload.content,
        }
      }
      if (currentChapter.value?.id === payload.id) {
        // 必须带上 content：CreatorView 监听 currentChapter 并调用 setDoc，
        // 若这里丢了 content，保存后稿纸会被空文档覆盖。
        currentChapter.value = {
          ...currentChapter.value,
          title: payload.title,
          wordCount: payload.wordCount,
          content: payload.content,
        }
      }
    } finally {
      saving.value = false
    }
  }

  return { chapters, currentChapter, loading, saving, loadAll, select, create, save }
}
