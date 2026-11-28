import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it } from 'vitest'
import VermilionNote from '../VermilionNote.vue'
import type { VermilionNote as Note } from '@/composables/useAiCreation'

function noteWith(text: string, status: Note['status'] = 'streaming'): Note {
  return { id: 1, kind: '续写', label: '本章末尾', text, anchorPos: 0, status }
}

/** jsdom 无布局：手动桩掉正文区的滚动几何。 */
function stubGeometry(el: HTMLElement, scrollHeight: number) {
  const state = { scrollHeight, clientHeight: 200, scrollTop: 0 }
  Object.defineProperty(el, 'scrollHeight', { configurable: true, get: () => state.scrollHeight })
  Object.defineProperty(el, 'clientHeight', { configurable: true, get: () => state.clientHeight })
  Object.defineProperty(el, 'scrollTop', {
    configurable: true,
    get: () => state.scrollTop,
    set: (v: number) => {
      state.scrollTop = v
    },
  })
  return state
}

describe('VermilionNote 流式自动滚动', () => {
  it('流式正文增长时自动贴到底部', async () => {
    const wrapper = mount(VermilionNote, { props: { note: noteWith('开头') } })
    const body = wrapper.find('.vermilion-note .max-h-56').element as HTMLElement
    const geo = stubGeometry(body, 400)
    await nextTick()

    await wrapper.setProps({ note: noteWith('开头'.repeat(100)) })
    await nextTick()
    await nextTick()

    expect(geo.scrollTop).toBe(400)
    wrapper.unmount()
  })

  it('用户手动上滚后暂停贴底，回到近底部后恢复', async () => {
    const wrapper = mount(VermilionNote, { props: { note: noteWith('开头') } })
    const body = wrapper.find('.vermilion-note .max-h-56').element as HTMLElement
    const geo = stubGeometry(body, 400)
    await nextTick()

    // 用户往上滚：距底部 300px
    geo.scrollTop = 100
    body.dispatchEvent(new Event('scroll'))
    await wrapper.setProps({ note: noteWith('甲'.repeat(200)) })
    await nextTick()
    await nextTick()
    expect(geo.scrollTop).toBe(100)

    // 用户滚回近底部（800 - 200 = 600）
    geo.scrollHeight = 800
    geo.scrollTop = 600
    body.dispatchEvent(new Event('scroll'))
    await wrapper.setProps({ note: noteWith('乙'.repeat(300)) })
    await nextTick()
    await nextTick()
    expect(geo.scrollTop).toBe(800)
    wrapper.unmount()
  })

  it('REVISION 清空正文后重新贴底', async () => {
    const wrapper = mount(VermilionNote, { props: { note: noteWith('旧稿') } })
    const body = wrapper.find('.vermilion-note .max-h-56').element as HTMLElement
    const geo = stubGeometry(body, 400)
    await nextTick()

    geo.scrollTop = 0
    body.dispatchEvent(new Event('scroll'))
    await wrapper.setProps({ note: noteWith('') })
    await nextTick()
    await wrapper.setProps({ note: noteWith('新稿'.repeat(100)) })
    await nextTick()
    await nextTick()

    expect(geo.scrollTop).toBe(400)
    wrapper.unmount()
  })
})
