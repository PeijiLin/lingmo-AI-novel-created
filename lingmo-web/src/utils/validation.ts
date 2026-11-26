/**
 * 表单输入校验：非法值必须显式拦截，不能静默丢弃后让用户以为已生效。
 */

/** 严格正整数：只接受十进制数字且 > 0，拒绝 0 / 负数 / 小数 / 科学计数 / 空串 */
export function parsePositiveInt(raw: string): number | undefined {
  const value = raw.trim()
  if (!/^\d+$/.test(value)) return undefined
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined
}

/** 严格日期 YYYY-MM-DD，拒绝 2026-99-99 / 2026-02-30 这类“格式对但不存在”的日期 */
export function isValidDate(raw: string): boolean {
  const value = raw.trim()
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
}
