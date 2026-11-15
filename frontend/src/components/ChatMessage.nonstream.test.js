import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, reactive, nextTick } from 'vue'
import ChatMessage from './ChatMessage.vue'

/**
 * Component-level regression tests for the non-streaming fallback rendering
 * (GitHub issue #11): a message that never streams must render its full
 * Markdown through the one-shot `message-content-markdown` container, and
 * that container is the one whose scoped styles pierce the v-html boundary
 * via :deep() (verified by real-browser computed-style evidence, not jsdom).
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

function mountChatMessage(propsOverrides = {}) {
  const props = reactive({
    role: 'system',
    content: '',
    isStreaming: false,
    citations: [],
    degraded: false,
    degradationReason: '',
    outcome: '',
    errorMessage: '',
    traceId: '',
    ...propsOverrides
  })

  const root = document.createElement('div')
  document.body.appendChild(root)

  const app = createApp({
    render: () => h(ChatMessage, props)
  })
  app.mount(root)

  mountedApps.push({ app, root })
  return { props, root }
}

const RICH_MARKDOWN = [
  '## 检查结果',
  '',
  '- 检查滑油量',
  '- 紧固螺栓',
  '',
  '1. 第一步',
  '2. 第二步',
  '',
  '| 项目 | 标准 |',
  '| --- | --- |',
  '| 滑油压力 | 正常 |',
  '',
  '```bash',
  'echo ok',
  '```',
  '',
  '> 注意：按手册执行。',
  '',
  '详见 [维护手册](https://example.com/manual)'
].join('\n')

describe('ChatMessage non-streaming fallback rendering', () => {
  it('renders every Markdown structure inside the one-shot markdown container', async () => {
    const { root } = mountChatMessage({ content: RICH_MARKDOWN })
    await nextTick()

    const container = root.querySelector('.message-content-markdown')
    expect(container).not.toBeNull()

    expect(container.querySelector('h2').textContent).toContain('检查结果')
    expect(container.querySelectorAll('ul li').length).toBe(2)
    expect(container.querySelectorAll('ol li').length).toBe(2)
    expect(container.querySelectorAll('table tbody tr').length).toBe(1)
    expect(container.querySelector('pre code').textContent).toContain('echo ok')
    expect(container.querySelector('blockquote').textContent).toContain('按手册执行')
    expect(container.querySelector('a').textContent).toContain('维护手册')
  })

  it('never creates streaming-only structures for a message that never streamed', async () => {
    const { root } = mountChatMessage({ content: RICH_MARKDOWN })
    await nextTick()

    expect(root.querySelectorAll('.message-block').length).toBe(0)
    expect(root.querySelector('.message-content-plain')).toBeNull()
  })

  it('re-renders the fallback content when the content prop changes', async () => {
    const { props, root } = mountChatMessage({ content: '第一版' })
    await nextTick()
    expect(root.querySelector('.message-content-markdown').textContent).toContain('第一版')

    props.content = '第二版'
    await nextTick()
    expect(root.querySelector('.message-content-markdown').textContent).toContain('第二版')
  })
})
