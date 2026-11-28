/**
 * 纸墨动效总开关。
 *
 * 优先级：URL 查询参数 ?motion=0|1 > localStorage > 系统减弱动效 > 默认开。
 * 同时把结果写到 html[data-motion]，供运动类 CSS 统一降级。
 */
import { ref, watch } from 'vue'

export const MOTION_STORAGE_KEY = 'lingmo.motion'

const enabled = ref(true)
let initialized = false
let stopWatch: (() => void) | null = null
let mediaQuery: MediaQueryList | null = null

function onSystemPreferenceChange(event: MediaQueryListEvent) {
  // 用户通过 URL 或本地存储显式选择过，就不再跟随系统设置。
  if (readQuery() !== null || readStorage() !== null) return
  enabled.value = !event.matches
}

function readStorage(): boolean | null {
  try {
    const value = localStorage.getItem(MOTION_STORAGE_KEY)
    if (value === 'off' || value === '0' || value === 'false') return false
    if (value === 'on' || value === '1' || value === 'true') return true
  } catch {
    /* 隐私模式下 localStorage 不可用 */
  }
  return null
}

function readQuery(): boolean | null {
  try {
    const value = new URLSearchParams(window.location.search).get('motion')
    if (value === '0' || value === 'off' || value === 'false') return false
    if (value === '1' || value === 'on' || value === 'true') return true
  } catch {
    /* 非浏览器环境 */
  }
  return null
}

function prefersReducedMotion(): boolean {
  try {
    return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  } catch {
    return false
  }
}

function applyToDocument(value: boolean) {
  if (typeof document === 'undefined') return
  document.documentElement.dataset.motion = value ? 'on' : 'off'
}

function persist(value: boolean) {
  try {
    localStorage.setItem(MOTION_STORAGE_KEY, value ? 'on' : 'off')
  } catch {
    /* 忽略存储失败 */
  }
}

function initialValue(): boolean {
  const query = readQuery()
  if (query !== null) return query
  const stored = readStorage()
  if (stored !== null) return stored
  return !prefersReducedMotion()
}

function ensureInitialized() {
  if (initialized) return
  initialized = true
  enabled.value = initialValue()
  applyToDocument(enabled.value)
  stopWatch = watch(enabled, (value) => {
    persist(value)
    applyToDocument(value)
  })
  try {
    mediaQuery = window.matchMedia?.('(prefers-reduced-motion: reduce)') ?? null
    mediaQuery?.addEventListener?.('change', onSystemPreferenceChange)
  } catch {
    /* 环境不支持 matchMedia 时忽略 */
  }
}

/** 测试用：清掉单例状态，让下一次调用重新读取环境。 */
export function resetMotionPreference() {
  stopWatch?.()
  stopWatch = null
  mediaQuery?.removeEventListener?.('change', onSystemPreferenceChange)
  mediaQuery = null
  initialized = false
  enabled.value = true
  if (typeof document !== 'undefined') delete document.documentElement.dataset.motion
}

export function useMotionPreference() {
  ensureInitialized()

  function setEnabled(value: boolean) {
    enabled.value = value
  }

  function toggle() {
    enabled.value = !enabled.value
  }

  return { enabled, setEnabled, toggle }
}
