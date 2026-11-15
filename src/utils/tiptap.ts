/**
 * 稿纸文档工具（规范 5.4）
 *
 * 修复旧版 textToTiptapDoc 段落丢失缺陷：旧实现把全部空白（含换行）压成单空格，
 * AI 产出的段落结构被压扁成一段。新实现按换行拆段、段内空白压单空格、过滤空段。
 */
import type { JSONContent } from '@tiptap/vue-3'

/** 纯文本 → Tiptap 文档（段落结构保留） */
export function textToTiptapDoc(text: string): JSONContent {
  const paragraphs = text
    .split(/\r?\n+/)
    .map((para) => para.replace(/[ \t]+/g, ' ').trim())
    .filter((para) => para.length > 0)

  if (paragraphs.length === 0) {
    return { type: 'doc', content: [] }
  }

  return {
    type: 'doc',
    content: paragraphs.map((para) => ({
      type: 'paragraph',
      content: [{ type: 'text', text: para }],
    })),
  }
}

/** Tiptap 文档 → 纯文本（段落间以空行分隔，供保存摘要 / 字数统计 / AI 续写上文） */
export function tiptapDocToPlainText(doc: JSONContent | null | undefined): string {
  if (!doc) return ''
  const paragraphs: string[] = []

  function walk(node: JSONContent) {
    if (node.type === 'paragraph' || node.type === 'heading') {
      const text = collectText(node)
      if (text.length > 0) paragraphs.push(text)
      return
    }
    if (node.type === 'text') return
    if (Array.isArray(node.content)) node.content.forEach(walk)
  }

  function collectText(node: JSONContent): string {
    if (node.type === 'text') return typeof node.text === 'string' ? node.text : ''
    if (Array.isArray(node.content)) return node.content.map(collectText).join('')
    return ''
  }

  walk(doc)
  return paragraphs.join('\n\n')
}

/** 中文字数统计（汉字逐字、字母数字连续串计 1） */
export function countWords(text: string): number {
  if (!text) return 0
  const cjk = text.match(/[\u4e00-\u9fff\u3400-\u4dbf]/g)?.length ?? 0
  const words = text.match(/[a-zA-Z0-9]+/g)?.length ?? 0
  return cjk + words
}
