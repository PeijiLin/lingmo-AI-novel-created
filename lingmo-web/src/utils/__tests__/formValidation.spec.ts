import { describe, expect, it } from 'vitest'
import {
  validateAuthorForm,
  validateEmail,
  validateNickname,
  validatePenName,
  validatePhone,
  validateProfileForm,
} from '../formValidation'

describe('name validators', () => {
  it('拒绝纯空白与超长名称', () => {
    expect(validateNickname('   ')).not.toBeNull()
    expect(validatePenName('   ')).not.toBeNull()
    expect(validateNickname('一'.repeat(21))).not.toBeNull()
    expect(validatePenName('一'.repeat(21))).not.toBeNull()
  })

  it('接受 20 字以内的名称', () => {
    expect(validateNickname('一'.repeat(20))).toBeNull()
    expect(validatePenName('执墨君')).toBeNull()
  })
})

describe('optional contact validators', () => {
  it('空值视为未设置，通过校验', () => {
    expect(validateEmail('')).toBeNull()
    expect(validatePhone('')).toBeNull()
  })

  it('拒绝非法格式并接受合法格式', () => {
    expect(validateEmail('abc')).not.toBeNull()
    expect(validateEmail('a@b.com')).toBeNull()
    expect(validatePhone('123')).not.toBeNull()
    expect(validatePhone('13800138000')).toBeNull()
  })
})

describe('form validators', () => {
  it('汇总字段级错误', () => {
    expect(validateProfileForm({ nickName: '' })).toEqual([
      { field: 'nickName', message: '昵称不能为空' },
    ])
    const errors = validateAuthorForm({ penName: ' ', email: 'abc', telPhone: '123' })
    expect(errors.map((e) => e.field)).toEqual(['penName', 'email', 'telPhone'])
  })

  it('合法表单无错误', () => {
    expect(validateProfileForm({ nickName: '执墨君' })).toEqual([])
    expect(validateAuthorForm({ penName: '执墨君', email: '', telPhone: '' })).toEqual([])
  })
})
