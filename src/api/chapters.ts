/**
 * 创作章节稿（/creation/*，create_book 表）
 */
import { http } from '@/utils/http'
import type { ChapterDraft, ChapterDraftPageRequest, Page } from './types'

/** 新增章节稿 */
export function addChapter(payload: {
  bookId: number
  title: string
  number: number
  authorId?: number
  wordCount?: number
  content?: unknown
}): Promise<ChapterDraft> {
  return http.post<ChapterDraft>('/creation/add', payload)
}

/** 更新章节稿（content 为 Tiptap JSON 文档） */
export function updateChapter(payload: ChapterDraft): Promise<void> {
  return http.put<void>('/creation/update', payload)
}

/** 按 bookId 分页查询章节稿 */
export function pageChapterDrafts(req: ChapterDraftPageRequest): Promise<Page<ChapterDraft>> {
  return http.post<Page<ChapterDraft>>('/creation/page/id', req)
}
