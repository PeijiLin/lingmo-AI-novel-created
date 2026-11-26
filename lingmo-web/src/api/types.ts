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

/* ---------------- 个人主页 ---------------- */

/** 用户资料（/user/me，不含密码） */
export interface UserProfileVo {
  id?: number
  username?: string
  nickName?: string
  userPhoto?: string
  /** 0-男 1-女，未设置为 null */
  userSex?: number | null
  accountBalance?: number | null
  createTime?: string | number
}

export interface UserProfileUpdateRequest {
  /** 传 null/undefined 表示不变更 */
  nickName?: string | null
  userSex?: number | null
}

/** 作者档案（/author/me，未开档时后端返回 null） */
export interface AuthorProfileVo {
  id?: number
  penName?: string
  /** 0-男频 1-女频，未设置为 null */
  workDirection?: number | null
  email?: string | null
  telPhone?: string | null
  chatAccount?: string | null
  inviteCode?: string | null
  createTime?: string | number
}

export interface AuthorProfileUpdateRequest {
  /** 笔名必填（1-20 字） */
  penName: string
  workDirection?: number | null
  email?: string | null
  telPhone?: string | null
  chatAccount?: string | null
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
  /** 本章目标字数；不传则后端 RC-11 字数规则静默 */
  targetWordCount?: number
  /** 叙事视角约束；不传则后端 RC-12 POV 规则静默 */
  pov?: string
}

/** SSE stage 事件 code（后端 StageCode） */
export type StageCode = 'PLANNING' | 'WRITING' | 'CRITIC' | 'POLISHING' | 'COMPLETED' | 'FAILED'
/** SSE stage 事件 status */
export type StageStatus = 'STARTED' | 'REVISION' | 'COMPLETED' | 'FAILED'

export interface StageUsage {
  stages: number
  totalTokens: number
  elapsedMs: number
}

/** SSE stage 事件载荷（Gson 序列化省略 null，故非判别字段均可选） */
export interface StageEvent {
  code: StageCode
  status: StageStatus
  index?: number
  total?: number
  round?: number
  degraded?: boolean
  usage?: StageUsage
  error?: string
  /** POLISHING STARTED 携带：当前为草稿态，随后 REVISION 会整稿替换 */
  draft?: boolean
}

/** 异步创作任务（creation_task） */
export interface CreationTask {
  id?: number
  bookId?: number
  chapterId?: number
  mode?: CreationMode | string
  status?: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  currentStage?: string
  degraded?: boolean
  totalTokens?: number
  elapsedMs?: number
  error?: string
  /** 模式快照 featureSnapshot 或异步任务参数，业务层按需解析 */
  params?: unknown
  createdAt?: string
  updatedAt?: string
}

/** GET /pipeline/run/{runId} 的产物概览（不含正文） */
export interface RunArtifactSummary {
  stage: string
  round: number
  tokens: number
  createdAt: string
}
export interface RunSummary {
  task: CreationTask
  succeededNodes: string[]
  artifacts: RunArtifactSummary[]
}

/* ---------------- 质量治理（成本 / 人审 / 规则豁免） ---------------- */

/** 成本汇总行：模式 × 节点 × 模型聚合 */
export interface CostRow {
  mode: string
  node: string
  model: string
  runs: number
  totalTokens: number
  cost: number
  priced: boolean
}

export type ReviewType = 'conflict' | 'assertion' | 'safety' | 'unmet'

/** 四类人审决策请求体；accept=true 为确认/通过，false 为驳回 */
export interface ReviewDecision {
  accept: boolean
  note?: string
}

export interface NovelWikiConflict {
  id?: number
  bookId?: number
  chapterId?: number
  characterId?: number
  conflictKind?: string
  oldValue?: string
  newValue?: string
  status?: string
  note?: string
  createdAt?: string
  resolvedAt?: string
}

export interface NovelPendingAssertion {
  id?: number
  bookId?: number
  chapterId?: number
  characterId?: number
  assertionType?: string
  payload?: string
  status?: string
  confirmations?: number
  expiresChapter?: number
  createdAt?: string
  resolvedAt?: string
}

export interface NovelSafetyLog {
  id?: number
  bookId?: number
  layer?: 'PRE' | 'STREAM' | 'POST'
  category?: string
  action?: string
  detail?: string
  createdAt?: string
}

export interface ReviewOverview {
  counts: Record<ReviewType, number>
  conflict: NovelWikiConflict[]
  assertion: NovelPendingAssertion[]
  safety: NovelSafetyLog[]
  unmet: CreationTask[]
}

export interface NovelRuleExemption {
  id?: number
  bookId?: number
  ruleId?: string
  reason?: string
  /** chapterFrom/chapterTo 为空表示整本豁免，填值表示叙诡保护区 */
  chapterFrom?: number | null
  chapterTo?: number | null
  reviewAt?: string | null
  createdAt?: string
}

export interface ExemptRequest {
  bookId: number
  ruleId: string
  reason?: string
  chapterFrom?: number
  chapterTo?: number
  reviewAt?: string
}
