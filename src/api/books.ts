/**
 * 书籍（展示侧 /book/* 与创作侧书籍信息 /creation/*）
 */
import { http } from '@/utils/http'
import type {
  BookCategoryVo,
  BookChapterVo,
  BookContentVo,
  BookInfoVo,
  BookPageRequest,
  ChapterPageRequest,
  Page,
} from './types'

/** 分类书目（发现页） */
export function pageBooks(req: BookPageRequest): Promise<Page<BookInfoVo>> {
  return http.post<Page<BookInfoVo>>('/book/page', req)
}

/** 小说类型（男频/女频 → 分类列表） */
export function listCategories(): Promise<Record<string, BookCategoryVo[]>> {
  return http.get<Record<string, BookCategoryVo[]>>('/book/category/list')
}

/** 书籍分页章节（阅读器目录 / 作品详情） */
export function pageChapters(req: ChapterPageRequest): Promise<Page<BookChapterVo>> {
  return http.post<Page<BookChapterVo>>('/book/chapter/page', req)
}

/** 章节内容（阅读器） */
export function getChapterContent(chapterId: number): Promise<BookContentVo> {
  return http.get<BookContentVo>('/book/content', { params: { chapterId } })
}

/** 某作者的作品列表（我的作品 / 创作台入口） */
export function listAuthorBooks(userId: number): Promise<BookInfoVo[]> {
  return http.get<BookInfoVo[]>('/creation/book/author', { params: { userId } })
}

/** 书籍详情 */
export function getBookInfo(bookId: number): Promise<BookInfoVo> {
  return http.get<BookInfoVo>('/creation/bookInfo/id', { params: { bookId } })
}

/**
 * 发布作品（草稿 → 发布二态）。
 * 预留端点：后端规范 6 演进项尚未实现，调用失败由调用方降级提示，不阻塞创作主线。
 */
export function publishBook(bookId: number): Promise<unknown> {
  return http.put<unknown>('/book/publish', { bookId })
}

/**
 * 阅读量 +1 上报（阅读器打开章节时）。
 * 预留端点：后端规范 6 演进项尚未实现，失败静默降级。
 */
export function reportChapterRead(chapterId: number, bookId?: number): Promise<unknown> {
  return http.post<unknown>('/book/read/increment', { chapterId, bookId })
}

/**
 * 轻量榜单（按阅读量排序）。
 * 预留端点：后端未实现时由调用方回退 pageBooks 客户端排序（规范 6：可先由 pageBookList 兜底）。
 */
export function listRanking(limit?: number): Promise<BookInfoVo[]> {
  return http.get<BookInfoVo[]>('/book/ranking', { params: { limit } })
}
