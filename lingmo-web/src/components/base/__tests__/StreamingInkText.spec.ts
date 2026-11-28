import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import StreamingInkText from '../StreamingInkText.vue'

describe('StreamingInkText', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('禁用时直接渲染纯文本，不产生动画片段', () => {
    const wrapper = mount(StreamingInkText, { props: { text: '暮色四合', disabled: true } })

    expect(wrapper.find('.ink-plain').text()).toBe('暮色四合')
    expect(wrapper.findAll('.motion-ink-reveal')).toHaveLength(0)
  })

  it('新增内容只追加为新墨团，不重绘旧片段', async () => {
    const wrapper = mount(StreamingInkText, { props: { text: '暮色', minChunkChars: 2 } })
    expect(wrapper.findAll('.motion-ink-reveal').map((n) => n.text())).toEqual(['暮色'])

    await wrapper.setProps({ text: '暮色四合' })
    await nextTick()

    expect(wrapper.findAll('.motion-ink-reveal').map((n) => n.text())).toEqual(['暮色', '四合'])
  })

  it('过小的增量先攒着，最多等 maxWaitMs 再补齐', async () => {
    const wrapper = mount(StreamingInkText, { props: { text: '甲' } })

    await wrapper.setProps({ text: '甲乙' })
    vi.advanceTimersByTime(60)
    await nextTick()
    expect(wrapper.findAll('.motion-ink-reveal')).toHaveLength(1)

    vi.advanceTimersByTime(160)
    await nextTick()
    expect(wrapper.findAll('.motion-ink-reveal').map((n) => n.text())).toEqual(['甲', '乙'])
  })

  it('整稿替换（先清空再流式）后只保留新文本', async () => {
    const wrapper = mount(StreamingInkText, { props: { text: '旧稿' } })

    await wrapper.setProps({ text: '' })
    vi.advanceTimersByTime(60)
    await nextTick()

    await wrapper.setProps({ text: '新稿全文' })
    vi.advanceTimersByTime(60)
    await nextTick()

    expect(wrapper.text()).toBe('新稿全文')
    expect(wrapper.findAll('.motion-ink-reveal')).toHaveLength(1)
  })

  it('超长文本降级为纯文本', () => {
    const wrapper = mount(StreamingInkText, { props: { text: '字'.repeat(4000) } })

    expect(wrapper.find('.ink-plain').exists()).toBe(true)
    expect(wrapper.findAll('.motion-ink-reveal')).toHaveLength(0)
  })
})
