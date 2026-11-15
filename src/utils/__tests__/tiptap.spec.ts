import { describe, expect, it } from 'vitest'
import { countWords, textToTiptapDoc, tiptapDocToPlainText } from '../tiptap'

describe('textToTiptapDoc 段落保留（修复旧版 \\s+ 压扁缺陷）', () => {
  it('多段落文本保留段落结构', () => {
    const doc = textToTiptapDoc('第一段开头。\n\n第二段开头。\n\n第三段开头。')
    expect(doc.type).toBe('doc')
    expect(doc.content).toHaveLength(3)
    expect(doc.content?.[0]).toEqual({
      type: 'paragraph',
      content: [{ type: 'text', text: '第一段开头。' }],
    })
    expect(doc.content?.[2]?.content?.[0]?.text).toBe('第三段开头。')
  })

  it('单换行同样视为段落分隔（AI 流式产出形态）', () => {
    const doc = textToTiptapDoc('夜色渐沉。\n他推门而入。')
    expect(doc.content).toHaveLength(2)
  })

  it('段内连续空白压为单空格（SSE 分块拼接产物）', () => {
    const doc = textToTiptapDoc('  hello   world  \n  你好   世界  ')
    expect(doc.content?.[0]?.content?.[0]?.text).toBe('hello world')
    expect(doc.content?.[1]?.content?.[0]?.text).toBe('你好 世界')
  })

  it('空文本 / 纯空白返回空文档', () => {
    expect(textToTiptapDoc('')).toEqual({ type: 'doc', content: [] })
    expect(textToTiptapDoc('  \n \n\t ')).toEqual({ type: 'doc', content: [] })
  })

  it('跨 chunk 拼接后的中间断行不被误删（回归旧缺陷场景）', () => {
    // 旧实现把整段压成单空格；新实现必须保段
    const joined = '他停下笔。\n' + '\n' + '窗外的雨还未停。'
    const doc = textToTiptapDoc(joined)
    expect(doc.content).toHaveLength(2)
    expect(doc.content?.[1]?.content?.[0]?.text).toBe('窗外的雨还未停。')
  })
})

describe('tiptapDocToPlainText', () => {
  it('段落间以空行分隔（供 AI 续写上文）', () => {
    const doc = textToTiptapDoc('第一段。\n\n第二段。')
    expect(tiptapDocToPlainText(doc)).toBe('第一段。\n\n第二段。')
  })

  it('嵌套 marks 文本正确收集', () => {
    const doc = {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            { type: 'text', text: '加粗' },
            { type: 'text', marks: [{ type: 'bold' }], text: '结尾' },
          ],
        },
      ],
    }
    expect(tiptapDocToPlainText(doc)).toBe('加粗结尾')
  })

  it('空文档返回空串', () => {
    expect(tiptapDocToPlainText(null)).toBe('')
    expect(tiptapDocToPlainText({ type: 'doc', content: [] })).toBe('')
  })
})

describe('countWords 中文字数', () => {
  it('汉字逐字计数、字母数字串计 1', () => {
    expect(countWords('你好世界')).toBe(4)
    expect(countWords('AI 小说')).toBe(3) // AI=1 + 小 说=2
    expect(countWords('')).toBe(0)
  })
})
