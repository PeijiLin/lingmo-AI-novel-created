/**
 * 质量治理接口（成本账本 / 人审队列 / 规则豁免）
 *
 * 路径说明：三个 Controller 均自带 /api 前缀，
 * 最终浏览器路径为 /api/api/book/...（baseUrl '/api' + 下列 path）。
 * 鉴权：全部需要 Bearer Token（走 http 单实例）。
 */
import { http } from '@/utils/http'
import type {
  CostRow,
  ExemptRequest,
  NovelRuleExemption,
  ReviewDecision,
  ReviewOverview,
  ReviewType,
} from './types'

const BASE = '/api/book'

/* ---------------- 成本账本 ---------------- */

export function getCostSummary(bookId: number): Promise<CostRow[]> {
  return http.get<CostRow[]>(`${BASE}/cost/${bookId}/summary`)
}

/** CSV 下载（后端带 UTF-8 BOM 与 Content-Disposition） */
export function exportCostCsv(bookId: number): Promise<Blob> {
  return http.get<Blob>(`${BASE}/cost/${bookId}/export`, { responseType: 'blob' })
}

/* ---------------- 人审队列 ---------------- */

export function getReviewOverview(bookId: number): Promise<ReviewOverview> {
  return http.get<ReviewOverview>(`${BASE}/review-queue/${bookId}`)
}

/** 分项清单；type ∈ conflict / assertion / safety / unmet */
export function listReviewItems<T = unknown>(bookId: number, type: ReviewType): Promise<T[]> {
  return http.get<T[]>(`${BASE}/review-queue/${bookId}/${type}`)
}

/** 四类统一裁定入口；accept=true 通过，false 驳回 */
export function resolveReviewItem(
  bookId: number,
  type: ReviewType,
  id: number,
  decision?: ReviewDecision,
): Promise<boolean> {
  return http.put<boolean>(`${BASE}/review-queue/${bookId}/${type}/${id}`, decision)
}

/* ---------------- 规则豁免 / 叙诡保护区 ---------------- */

/** 无章节区间 = 整本豁免；填 chapterFrom/chapterTo = 保护区 */
export function addExemption(request: ExemptRequest): Promise<boolean> {
  return http.post<boolean>(`${BASE}/rule-exemption`, request)
}

export function listExemptions(bookId: number): Promise<NovelRuleExemption[]> {
  return http.get<NovelRuleExemption[]>(`${BASE}/rule-exemption/${bookId}`)
}

export function listDueExemptions(bookId: number): Promise<NovelRuleExemption[]> {
  return http.get<NovelRuleExemption[]>(`${BASE}/rule-exemption/${bookId}/due`)
}

/**
 * reviewAt 为空 = 长期有效。
 * ruleId 走 query 参数而非路径变量：后端路径变量会把 %2F 解码成路径分隔符导致 400，
 * query 参数可承载 / ? & 等任意字符。
 */
export function reviewExemption(bookId: number, ruleId: string, reviewAt?: string): Promise<boolean> {
  return http.put<boolean>(
    `${BASE}/rule-exemption/${bookId}/review`,
    reviewAt ? { reviewAt } : undefined,
    { params: { ruleId } },
  )
}

export function revokeExemption(bookId: number, ruleId: string): Promise<boolean> {
  return http.delete<boolean>(`${BASE}/rule-exemption/${bookId}`, { params: { ruleId } })
}
