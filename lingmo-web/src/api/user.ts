/**
 * 个人主页（/user/me、/user/update、/author/me、/author/update）
 */
import { http } from '@/utils/http'
import type {
  AuthorProfileUpdateRequest,
  AuthorProfileVo,
  UserProfileUpdateRequest,
  UserProfileVo,
} from './types'

/** 当前登录用户资料（不含密码） */
export function getMyProfile(): Promise<UserProfileVo> {
  return http.get<UserProfileVo>('/user/me')
}

/** 更新用户资料（昵称/性别；空字段不变更） */
export function updateMyProfile(payload: UserProfileUpdateRequest): Promise<UserProfileVo> {
  return http.put<UserProfileVo>('/user/update', payload)
}

/** 当前用户作者档案（未开档时后端 data 为 null） */
export function getMyAuthorProfile(): Promise<AuthorProfileVo | null> {
  return http.get<AuthorProfileVo | null>('/author/me')
}

/** 更新（或首次创建）作者档案（笔名必填） */
export function updateMyAuthorProfile(payload: AuthorProfileUpdateRequest): Promise<AuthorProfileVo> {
  return http.put<AuthorProfileVo>('/author/update', payload)
}
