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

/** 小说类型（作品方向 → 分类列表） */
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
 * 新建作品（book_info 落库）。
 * 后端 POST /book/create 已就绪：authorId 缺省时从登录态自动解析/创建作者档案。
 */
export function createBook(payload: {
  bookName: string
  bookDesc?: string
  workDirection?: string | null
  authorId?: number
}): Promise<BookInfoVo> {
  return http.post<BookInfoVo>('/book/create', payload)
}

/**
 * 发布作品（create_book 草稿 → book_chapter + book_content，幂等）。
 * 后端 PUT /book/publish 已就绪。
 */
export function publishBook(bookId: number): Promise<unknown> {
  return http.put<unknown>('/book/publish', { bookId })
}

/**
 * 阅读量 +1 上报（阅读器打开章节时）。
 * 后端 POST /book/read/increment 已就绪，失败仍静默降级（不阻塞阅读）。
 */
export function reportChapterRead(chapterId: number, bookId?: number): Promise<unknown> {
  return http.post<unknown>('/book/read/increment', { chapterId, bookId })
}

/**
 * 轻量榜单（按阅读量排序）。
 * 后端 GET /book/ranking 已就绪，直接使用服务端排序。
 */
export function listRanking(limit?: number): Promise<BookInfoVo[]> {
  return http.get<BookInfoVo[]>('/book/ranking', { params: { limit } })
}
