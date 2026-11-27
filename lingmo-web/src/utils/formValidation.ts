/** 个人资料表单的字段级校验，返回错误文案或 null。 */

export interface FieldError {
  field: string
  message: string
}

const MAX_NAME_LENGTH = 20
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_RE = /^1\d{10}$/

export function validateNickname(value: string): string | null {
  const name = value?.trim() ?? ''
  if (!name) return '昵称不能为空'
  if (name.length > MAX_NAME_LENGTH) return '昵称不能超过 20 字'
  return null
}

export function validatePenName(value: string): string | null {
  const name = value?.trim() ?? ''
  if (!name) return '请填写笔名'
  if (name.length > MAX_NAME_LENGTH) return '笔名不能超过 20 字'
  return null
}

export function validateEmail(value: string): string | null {
  const email = value?.trim() ?? ''
  if (!email) return null
  return EMAIL_RE.test(email) ? null : '邮箱格式不正确'
}

export function validatePhone(value: string): string | null {
  const phone = value?.trim() ?? ''
  if (!phone) return null
  return PHONE_RE.test(phone) ? null : '电话格式不正确'
}

export function validateProfileForm(form: { nickName: string; userSex?: string }): FieldError[] {
  const errors: FieldError[] = []
  const nicknameError = validateNickname(form.nickName)
  if (nicknameError) errors.push({ field: 'nickName', message: nicknameError })
  if (form.userSex && form.userSex !== '0' && form.userSex !== '1') {
    errors.push({ field: 'userSex', message: '性别取值无效' })
  }
  return errors
}

export function validateAuthorForm(form: {
  penName: string
  email: string
  telPhone: string
}): FieldError[] {
  const errors: FieldError[] = []
  const checks: Array<[string, string | null]> = [
    ['penName', validatePenName(form.penName)],
    ['email', validateEmail(form.email)],
    ['telPhone', validatePhone(form.telPhone)],
  ]
  for (const [field, message] of checks) {
    if (message) errors.push({ field, message })
  }
  return errors
}
