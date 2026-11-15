import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, reactive, nextTick } from 'vue'
import ChatMessage from './ChatMessage.vue'

/**
 * Component-level regression tests for the incremental streaming race
 * (GitHub issue #7): when the final SSE `token` events and the `complete`
 * event are dispatched from the same network chunk, both props mutate in
 * the same tick. The `isStreaming` watcher (pre-flush) then runs before the
 * `content` watcher (flush: 'post'), so the trailing Markdown used to be
 * dropped and never confirmed into the DOM.
 *
 * These tests drive the real Vue scheduler in jsdom: no stubs, no mocks.
 */

const mountedApps = []

afterEach(() => {
  while (mountedApps.length) {
    const { app, root } = mountedApps.pop()
    app.unmount()
    root.remove()
  }
})

function mountChatMessage() {
  const props = reactive({
    role: 'system',
    content: '',
    isStreaming: true,
    citations: [],
    degraded: false,
    degradationReason: '',
    outcome: '',
    errorMessage: '',
    traceId: ''
  })

  const root = document.createElement('div')
  document.body.appendChild(root)

  // Re-render the child whenever any prop changes, mimicking the parent
  // (App.vue) updating message props during streaming.
  const app = createApp({
    render: () => h(ChatMessage, props)
  })
  app.mount(root)

  mountedApps.push({ app, root })
  return { props, root }
}

describe('ChatMessage incremental streaming completion race', () => {
  it('preserves a trailing code block when the last tokens and complete arrive in the same tick', async () => {
    const { props, root } = mountChatMessage()

    // Mid-stream: a paragraph boundary confirms the first block; the code
    // fence stays open in the streaming buffer.
    props.content = '说明段落。\n\n```bash\n'
    await nextTick()

    // Same tick: final tokens plus the completion flip.
    props.content += 'echo done\n```'
    props.isStreaming = false
    await nextTick()

    expect(root.querySelectorAll('pre').length).toBe(1)
    expect(root.querySelector('pre').textContent).toContain('echo done')
    expect(root.textContent).toContain('说明段落')
  })

  it('preserves a trailing ordered-list item when the last tokens and complete arrive in the same tick', async () => {
    const { props, root } = mountChatMessage()

    props.content = '前段。\n\n1. 检查'
    await nextTick()

    // Same tick: the second list item plus the completion flip.
    props.content += ' 2. 紧固'
    props.isStreaming = false
    await nextTick()

    const items = root.querySelectorAll('ol li')
    expect(items.length).toBe(2)
    expect(items[0].textContent).toContain('检查')
    expect(items[1].textContent).toContain('紧固')
  })

  it('leaves no empty confirmed block or empty paragraph when content ends with a blank line', async () => {
    const { props, root } = mountChatMessage()

    props.content = '第一段。\n\n'
    await nextTick()

    // Same tick: trailing paragraph ending with a blank line, then complete.
    props.content += '第二段。\n\n'
    props.isStreaming = false
    await nextTick()

    const blocks = root.querySelectorAll('.message-block')
    expect(blocks.length).toBe(2)
    blocks.forEach(block => {
      expect(block.innerHTML.trim()).not.toBe('')
    })
    expect(root.innerHTML).not.toContain('<p></p>')
    // The transient plain streaming buffer must be gone after completion.
    expect(root.querySelector('.message-content-plain')).toBeNull()
  })

  it('keeps normal multi-chunk streaming behaviour when complete arrives in a later tick', async () => {
    const { props, root } = mountChatMessage()

    props.content = '第一段。\n\n'
    await nextTick()

    props.content += '第二段'
    await nextTick()

    props.isStreaming = false
    await nextTick()

    const blocks = root.querySelectorAll('.message-block')
    expect(blocks.length).toBe(2)
    expect(blocks[0].textContent).toContain('第一段')
    expect(blocks[1].textContent).toContain('第二段')
    expect(root.querySelector('.message-content-plain')).toBeNull()
  })
})
