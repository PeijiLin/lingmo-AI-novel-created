/**
 * 类型化客户端 · 公共类型（对齐后端 VO/DTO 实况）
 */

/** 后端统一信封（utils/http.ts 已解包，业务层拿到的就是 T） */
export interface BaseResponse<T> {
  code: number
  msg?: string
  data?: T
}

/** MyBatis-Plus 分页结构 */
export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages?: number
}

/* ---------------- 用户与认证 ---------------- */

export interface LoginResult {
  access_token: string
  refresh_token: string
  userId: number
}

/* ---------------- 书籍（展示侧 book_info） ---------------- */

export interface BookInfoVo {
  id?: number
  workDirection?: number
  categoryId?: number
  categoryName?: string
  picUrl?: string
  bookName: string
  authorId?: number
  authorName?: string
  bookDesc?: string
  score?: number
  bookStatus?: number
  visitCount?: number
  wordCount?: number
  commentCount?: number
  lastChapterId?: number
  lastChapterName?: string
  lastChapterUpdateTime?: string
  isVip?: number
  createTime?: string
  tags?: string[]
}

export interface BookCategoryVo {
  id?: number
  categoryName?: string
  workDirection?: number
}

export interface BookPageRequest {
  pageNum?: number
  pageSize?: number
  categoryId?: number
  workDirection?: number
}

export interface ChapterPageRequest {
  pageNum?: number
  pageSize?: number
  sort?: boolean
  bookInfoId?: number
}

export interface BookChapterVo {
  id?: number
  bookId?: number
  chapterNum?: number
  chapterName?: string
  wordCount?: number
  isVip?: number
  createTime?: string
}

export interface BookContentVo {
  id?: number
  chapterId?: number
  content?: string
  createTime?: string
}

/* ---------------- 创作侧（create_book 章节稿） ---------------- */

/** 章节稿（content 为 Tiptap JSON 文档，或其 JSON 字符串） */
export interface ChapterDraft {
  id?: number
  bookId?: number
  title?: string
  number?: number
  wordCount?: number
  content?: unknown
  authorId?: number
  createTime?: string
  updateTime?: string
}

export interface ChapterDraftPageRequest {
  pageNum?: number
  pageSize?: number
  bookId?: number
}

/* ---------------- 知识库 ---------------- */

export interface KnowledgeBaseVo {
  id?: number
  bookId?: number
  userId?: number
  name?: string
  description?: string
  createAt?: string
  updateAt?: string
}

export interface KnowledgeBaseDto {
  id?: number
  bookId?: number
  userId?: number
  name?: string
  description?: string
}

export interface CharacterRelationship {
  id?: number
  characterId?: number
  relatedCharacterId?: number
  relationType?: string
  description?: string
}

export interface CharacterCardVo {
  id?: number
  bookId?: number
  name?: string
  description?: string
  personality?: string
  background?: string
  appearance?: string
  age?: number
  gender?: string
  occupation?: string
  abilities?: string
  weaknesses?: string
  goals?: string
  relationships?: CharacterRelationship[]
  createdAt?: string
  updatedAt?: string
}

/* ---------------- 书架 ---------------- */

export interface BookShelfAddRequest {
  bookId?: number
  userId?: number
}

/* ---------------- AI 创作流水线 ---------------- */

/** 四档创作模式（后端 CreationMode） */
export type CreationMode = 'FAST' | 'STANDARD' | 'REFINED' | 'PRECISE'

export interface AiChatRequest {
  bookId: number
  userInput: string
  chapterId?: number
  creationType?: string
  title?: string
  worldSetting?: string
  style?: string
  /** 创作模式，不传即 FAST（兼容旧端点） */
  mode?: CreationMode
  /** Critic 重写轮数上限（默认 2，硬上限 3），仅 STANDARD/REFINED/PRECISE 生效 */
  maxRewriteRounds?: number
}

/** SSE stage 事件载荷（utils/sse.ts StageEventPayload 的 api 层视角） */
export interface StageEvent {
  code: string
  status: string
  index?: number
  total?: number
  round?: number
  degraded?: boolean
  usage?: { stages: number; totalTokens: number; elapsedMs: number }
  error?: string
}

/** 异步创作任务（creation_task） */
export interface CreationTask {
  id?: number
  bookId?: number
  chapterId?: number
  mode?: string
  status?: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  currentStage?: string
  degraded?: boolean
  totalTokens?: number
  elapsedMs?: number
  error?: string
  createdAt?: string
  updatedAt?: string
}
