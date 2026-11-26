import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeAll, describe, expect, it, vi } from 'vitest'
import DraftPaper from '../DraftPaper.vue'

beforeAll(() => {
  // jsdom 没有 ResizeObserver；DraftPaper 仅用它做朱批重定位
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  )
})

describe('DraftPaper 稿纸挂载', () => {
  it('默认空文档仍渲染 ProseMirror（Tiptap 3.11 挂载守卫回归）', async () => {
    const wrapper = mount(DraftPaper, { props: { notes: [] }, attachTo: document.body })

    await vi.waitFor(() => expect(wrapper.find('.paper-editor .ProseMirror').exists()).toBe(true))
    expect(wrapper.find('.paper-editor p').exists()).toBe(true)
    wrapper.unmount()
  })

  it('加载空 doc 后仍保留可编辑段落', async () => {
    const wrapper = mount(DraftPaper, { props: { notes: [] }, attachTo: document.body })
    await nextTick()

    wrapper.vm.setDoc({ type: 'doc', content: [] })
    await nextTick()

    expect(wrapper.find('.paper-editor .ProseMirror').exists()).toBe(true)
    expect(wrapper.find('.paper-editor p').exists()).toBe(true)
    expect(wrapper.vm.getPlainText()).toBe('')
    wrapper.unmount()
  })

  it('加载有正文的 doc 后正文可读', async () => {
    const wrapper = mount(DraftPaper, { props: { notes: [] }, attachTo: document.body })
    await nextTick()

    wrapper.vm.setDoc({
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: '暮色四合' }] }],
    })
    await nextTick()

    expect(wrapper.vm.getPlainText()).toContain('暮色四合')
    wrapper.unmount()
  })
})
