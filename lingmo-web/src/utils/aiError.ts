/**
 * 把后端 / AI 网关的英文报错转成可操作的中文提示。
 * 识别不了时返回 undefined，由调用方保留原始错误文本。
 */
export function explainAiError(err: unknown): string | undefined {
  const raw = err instanceof Error ? err.message : String(err ?? '')
  if (!raw) return undefined
  const lower = raw.toLowerCase()

  if (
    lower.includes('invalid_api_key') ||
    lower.includes('incorrect api key') ||
    lower.includes('api key') ||
    lower.includes('401')
  ) {
    return 'AI 服务未配置：请在后端 application-dev.yml 中填写有效的 Chat / Embedding API Key'
  }
  if (lower.includes('429') || lower.includes('rate limit') || lower.includes('quota')) {
    return 'AI 服务额度不足或请求过于频繁，请稍后重试'
  }
  if (lower.includes('timeout') || lower.includes('timed out')) {
    return 'AI 服务响应超时，请稍后重试'
  }
  if (
    lower.includes('econnrefused') ||
    lower.includes('connection') ||
    lower.includes('network')
  ) {
    return '无法连接 AI 服务，请检查网络与 base-url 配置'
  }
  return undefined
}
