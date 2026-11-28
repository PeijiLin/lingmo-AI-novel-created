import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

function setSearch(search: string) {
  window.history.replaceState({}, '', `/${search}`)
}

describe('useMotionPreference', () => {
  beforeEach(async () => {
    setSearch('')
    localStorage.clear()
    delete document.documentElement.dataset.motion
    vi.stubGlobal('matchMedia', undefined)
    const mod = await import('../useMotionPreference')
    mod.resetMotionPreference()
  })

  it('默认开启动效，并在 html 上标记 on', async () => {
    const { useMotionPreference } = await import('../useMotionPreference')
    const motion = useMotionPreference()

    expect(motion.enabled.value).toBe(true)
    expect(document.documentElement.dataset.motion).toBe('on')
  })

  it('查询参数 ?motion=0 关闭动效', async () => {
    setSearch('?motion=0')
    const { useMotionPreference } = await import('../useMotionPreference')

    expect(useMotionPreference().enabled.value).toBe(false)
  })

  it('本地存储 off 关闭动效', async () => {
    localStorage.setItem('lingmo.motion', 'off')
    const { useMotionPreference } = await import('../useMotionPreference')

    expect(useMotionPreference().enabled.value).toBe(false)
  })

  it('系统减弱动效时默认关闭', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })))
    const { useMotionPreference } = await import('../useMotionPreference')

    expect(useMotionPreference().enabled.value).toBe(false)
  })

  it('setEnabled(false) 持久化并切换到 off', async () => {
    const { useMotionPreference } = await import('../useMotionPreference')
    const motion = useMotionPreference()
    motion.setEnabled(false)
    await nextTick()

    expect(localStorage.getItem('lingmo.motion')).toBe('off')
    expect(document.documentElement.dataset.motion).toBe('off')
    expect(motion.enabled.value).toBe(false)
  })
})
