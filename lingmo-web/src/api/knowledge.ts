/**
 * 知识库（/knowledgeBase/*、/customizeKnowledgeBase/*、/api/character-card/*）
 *
 * 注意：后端 context-path 为 /api，而角色卡控制器自身又带 /api 前缀，
 * 故角色卡真实路径为 /api/api/character-card/*（与旧前端调用一致）。
 */
import { http } from '@/utils/http'
import type {
  CharacterCardVo,
  KnowledgeBaseDto,
  KnowledgeBaseVo,
  Page,
} from './types'

export interface KnowledgeBasePageRequest {
  pageNum?: number
  pageSize?: number
  bookId?: number
}

/** 分页查询知识库 */
export function pageKnowledgeBase(req: KnowledgeBasePageRequest): Promise<Page<KnowledgeBaseVo>> {
  return http.post<Page<KnowledgeBaseVo>>('/knowledgeBase/page', req)
}

/** 新增知识库 */
/** 新增知识库；后端直接返回新建 id（Long），不是 KnowledgeBaseVo */
export function addKnowledgeBase(dto: KnowledgeBaseDto): Promise<number> {
  return http.post<number>('/knowledgeBase/add', dto)
}

/** 删除知识库 */
export function removeKnowledgeBase(id: number): Promise<void> {
  return http.post<void>('/knowledgeBase/remove', { id })
}

/** 上传文件入知识库（multipart） */
export function uploadKbFile(knowledgeBaseId: number, file: File): Promise<unknown> {
  const form = new FormData()
  form.append('file', file)
  form.append('knowledgeId', String(knowledgeBaseId))
  // multipart 不走 JSON 序列化，直接用底层 fetch
  return http.request<unknown>('POST', '/customizeKnowledgeBase/upload/file', { body: form })
}

/** 上传文本内容入知识库 */
export function uploadKbContent(knowledgeBaseId: number, content: string): Promise<unknown> {
  return http.post<unknown>('/customizeKnowledgeBase/upload/content', { knowledgeId: knowledgeBaseId, content })
}

/* ---------------- 角色卡 ---------------- */

/** 书的全部角色卡 */
export function listCharacterCards(bookId: number): Promise<CharacterCardVo[]> {
  return http.get<CharacterCardVo[]>(`/api/character-card/project/${bookId}`)
}

/** 搜索角色卡 */
export function searchCharacterCards(bookId: number, keyword: string): Promise<CharacterCardVo[]> {
  return http.get<CharacterCardVo[]>(`/api/character-card/project/${bookId}/search`, {
    params: { keyword },
  })
}

/** 角色卡详情 */
export function getCharacterCard(id: number): Promise<CharacterCardVo> {
  return http.get<CharacterCardVo>(`/api/character-card/${id}`)
}

/** 新建角色卡 */
export function createCharacterCard(card: Partial<CharacterCardVo> & { bookId: number }): Promise<CharacterCardVo> {
  return http.post<CharacterCardVo>('/api/character-card/add', card)
}

/** 更新角色卡 */
export function updateCharacterCard(card: Partial<CharacterCardVo> & { id: number }): Promise<unknown> {
  return http.put<unknown>('/api/character-card/modify', card)
}

/** 删除角色卡 */
export function deleteCharacterCard(id: number): Promise<boolean> {
  return http.delete<boolean>(`/api/character-card/${id}`)
}
