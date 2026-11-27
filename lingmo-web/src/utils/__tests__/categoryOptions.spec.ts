import { describe, expect, it } from 'vitest'
import { toCategoryOptions } from '../categoryOptions'

describe('toCategoryOptions', () => {
  it('摊平各方向分组为分类选项', () => {
    expect(toCategoryOptions({
      男频: [{ id: 1, categoryName: '玄幻' }],
      女频: [{ id: 2, categoryName: '古代言情' }],
    })).toEqual([
      { label: '玄幻', value: '玄幻' },
      { label: '古代言情', value: '古代言情' },
    ])
  })

  it('空分组或缺失数据返回空列表', () => {
    expect(toCategoryOptions({})).toEqual([])
    expect(toCategoryOptions(null)).toEqual([])
    expect(toCategoryOptions(undefined)).toEqual([])
  })

  it('跨方向同名分类去重并过滤空白名称', () => {
    expect(toCategoryOptions({
      男频: [{ categoryName: '玄幻' }, { categoryName: '   ' }, { categoryName: '' }],
      女频: [{ categoryName: '玄幻' }, { categoryName: '古代言情' }],
    })).toEqual([
      { label: '玄幻', value: '玄幻' },
      { label: '古代言情', value: '古代言情' },
    ])
  })

  it('过滤超过 20 字符的分类名', () => {
    expect(toCategoryOptions({
      男频: [{ categoryName: '一'.repeat(21) }, { categoryName: '仙侠' }],
    })).toEqual([{ label: '仙侠', value: '仙侠' }])
  })
})
