/**
 * 我的书架（/bookshelf/*）
 */
import { http } from '@/utils/http'
import type { BookInfoVo, BookShelfAddRequest, Page } from './types'

/** 加入书架 */
export function addToBookshelf(payload: BookShelfAddRequest): Promise<unknown> {
  return http.post<unknown>('/bookshelf/add', payload)
}

/** 我的书架分页列表（需登录，后端只认当前登录用户） */
export function listShelfBooks(pageNum = 1, pageSize = 24): Promise<Page<BookInfoVo>> {
  return http.get<Page<BookInfoVo>>('/bookshelf/list', {
    params: { pageNum, pageSize },
  })
}
