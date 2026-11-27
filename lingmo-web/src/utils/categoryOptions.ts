import type { BookCategoryVo } from '@/api/types'
import type { ComboboxOption } from '@/components/ui'

/**
 * 把分类库按作品方向分组的结果摊平为 Combobox 选项。
 * 只保留非空、长度不超过 work_direction(VARCHAR(20)) 的分类名，并按名称去重。
 */
export function toCategoryOptions(
  map: Record<string, BookCategoryVo[]> | null | undefined,
): ComboboxOption[] {
  const seen = new Set<string>()
  const options: ComboboxOption[] = []
  for (const category of Object.values(map ?? {}).flat()) {
    const name = category.categoryName?.trim()
    if (!name || name.length > 20 || seen.has(name)) continue
    seen.add(name)
    options.push({ label: name, value: name })
  }
  return options
}
