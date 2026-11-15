/**
 * 我的书架（/bookshelf/*）
 */
import { http } from '@/utils/http'
import type { BookShelfAddRequest } from './types'

/** 加入书架 */
export function addToBookshelf(payload: BookShelfAddRequest): Promise<unknown> {
  return http.post<unknown>('/bookshelf/add', payload)
}
