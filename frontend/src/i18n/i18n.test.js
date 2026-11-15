import { describe, it, expect, afterEach } from 'vitest'
import { createApp, h, reactive, nextTick } from 'vue'
import { createI18n } from 'vue-i18n'
import { i18n, literalMessageCompiler } from './index.js'
import zhCN from './locales/zh-CN.js'
import en from './locales/en.js'
import App from '../App.vue'
import ChatMessage from '../components/ChatMessage.vue'

/**
 * Regression net for the vue-i18n extraction (GitHub issue #13):
 * - every zh-CN resource value must equal the interface copy that was
 *   previously hard-coded in the components (no rewording allowed);
 * - the en resource tree must mirror the zh-CN key tree one-to-one, so a
 *   missing English key falls back to zh-CN visibly in review;
 * - with the default locale, App/ChatMessage render the same Chinese text
 *   as before the extraction.
 */

const mountedApps = []

afterEach(() => {
  while (mountedApps.length) {
    const { app, root } = mountedApps.pop()
    app.unmount()
    root.remove()
  }
  i18n.global.locale.value = 'zh-CN'
})

function mountWithI18n(component, props = {}, instance = i18n) {
  const reactiveProps = reactive(props)
  const root = document.createElement('div')
  document.body.appendChild(root)
  const app = createApp({
    render: () => h(component, reactiveProps)
  })
  app.use(instance)
  app.mount(root)
  mountedApps.push({ app, root })
  return { props: reactiveProps, root }
}

function collectKeyPaths(node, prefix = '') {
  const paths = []
  for (const [key, value] of Object.entries(node)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (value && typeof value === 'object') {
      paths.push(...collectKeyPaths(value, path))
    } else {
      paths.push(path)
    }
  }
  return paths
}

describe('zh-CN resources preserve the exact previous interface copy', () => {
  const cases = [
    ['app.title', {}, '机务维修知识助手'],
    ['app.guideButton', {}, '📘 项目介绍'],
    ['app.conversationPrefix', {}, '会话: '],
    ['app.incrementalOn', {}, '增量：开'],
    ['app.incrementalOff', {}, '增量：关'],
    ['app.newChat', {}, '新会话'],
    ['app.suggestedTitle', {}, '有什么可以帮您？'],
    ['app.loading', {}, '正在生成...'],
    ['app.dataNotice', {}, '使用云端模型时，问题和检索片段会发送给已配置的供应商。'],
    ['app.inputPlaceholder', {}, '输入问题...'],
    ['app.send', {}, '发送'],
    ['app.suggestedQuestions.ctls', {}, 'CTLS 飞机 Rotax 912 发动机滑油压力标准值是多少？'],
    ['app.suggestedQuestions.wt9', {}, 'WT9 飞机 Rotax 912 起落架收放系统的日常检查要求是什么？'],
    ['app.suggestedQuestions.structure', {}, '飞机结构修理中，铆接修理的一般规范有哪些？'],
    ['app.suggestedQuestions.b737', {}, 'B737-800 CFM56-7B 发动机滑油压力低，应该如何排故？'],
    ['app.errors.parseEvent', {}, '服务端返回了无法解析的事件数据'],
    ['app.errors.requestFailedWithStatus', { status: 429 }, '请求失败（HTTP 429）'],
    ['app.errors.requestFailed', {}, '请求失败'],
    ['app.errors.streamInterrupted', {}, '响应在完成事件到达前中断'],
    ['chat.degradedNotice', { reason: 'streaming-disabled' }, '非流式响应：已按配置关闭流式传输（streaming-disabled）'],
    ['chat.traceIdLabel', { traceId: 'abc-123' }, '关联 ID：abc-123'],
    ['chat.citationsTitle', {}, '检索来源'],
    ['chat.citationName', { title: 'AMM 25-10-01', index: 2 }, 'AMM 25-10-01 · 片段 2'],
    ['chat.citationMetaPrefix', { score: '0.812' }, '相似度 0.812 · '],
    ['guide.title', {}, '航空维修智能助手 · 项目介绍'],
    ['guide.close', {}, '关闭'],
    ['guide.start', {}, '开始对话 →'],
    ['guide.sections.background.title', {}, '项目背景'],
    ['guide.sections.architecture.title', {}, '技术架构'],
    ['guide.sections.challenges.title', {}, '核心难点'],
    ['guide.sections.role.title', {}, '我的角色'],
    ['guide.sections.comparison.title', {}, '与 ChatGPT 的区别']
  ]

  it.each(cases)('t(%j, %j) === %j', (key, params, expected) => {
    expect(i18n.global.t(key, params)).toBe(expected)
  })

  it('guide section bodies keep the original Markdown opening lines', () => {
    expect(zhCN.guide.sections.background.content.startsWith('**为什么做这个项目？**')).toBe(true)
    expect(zhCN.guide.sections.comparison.content).toContain('| 维度 | ChatGPT | 本项目 |')
  })
})

describe('en resources mirror the zh-CN key tree', () => {
  it('every zh-CN key path exists in en and every en key path exists in zh-CN', () => {
    const zhPaths = collectKeyPaths(zhCN).sort()
    const enPaths = collectKeyPaths(en).sort()
    expect(enPaths).toEqual(zhPaths)
  })

  it('en resources render English interface text', () => {
    const enI18n = createI18n({
      legacy: false,
      locale: 'en',
      fallbackLocale: 'zh-CN',
      messageCompiler: literalMessageCompiler,
      messages: { 'zh-CN': zhCN, en }
    })
    expect(enI18n.global.t('app.title')).toBe('Aviation Maintenance Knowledge Assistant')
    expect(enI18n.global.t('app.send')).toBe('Send')
    expect(enI18n.global.t('guide.title')).toBe('Aviation Maintenance Assistant · Project Guide')
    expect(enI18n.global.t('chat.citationsTitle')).toBe('Retrieved Sources')
    expect(enI18n.global.t('app.errors.requestFailedWithStatus', { status: 500 })).toBe('Request failed (HTTP 500)')
  })
})

describe('default zh-CN rendering after extraction', () => {
  it('App renders the previous Chinese interface text', () => {
    const { root } = mountWithI18n(App)
    expect(root.querySelector('.chat-header h2').textContent).toBe('机务维修知识助手')
    expect(root.querySelector('.guide-entry-btn').textContent.trim()).toBe('📘 项目介绍')
    expect(root.querySelector('.suggested-questions-title').textContent).toBe('有什么可以帮您？')
    const cards = root.querySelectorAll('.suggested-question-card')
    expect(cards).toHaveLength(4)
    expect(cards[0].textContent.trim()).toBe('CTLS 飞机 Rotax 912 发动机滑油压力标准值是多少？')
    expect(root.querySelector('.chat-input-area input').placeholder).toBe('输入问题...')
    expect(root.querySelector('.chat-input-area button').textContent.trim()).toBe('发送')
    expect(root.querySelector('.toggle-btn').textContent.trim()).toBe('增量：开')
    expect(root.querySelector('.data-notice').textContent.trim()).toBe('使用云端模型时，问题和检索片段会发送给已配置的供应商。')
  })

  it('ProjectGuide renders all five localized section titles and footer', async () => {
    const { root } = mountWithI18n(App)
    const navTexts = [...root.querySelectorAll('.guide-nav-text')].map(el => el.textContent.trim())
    expect(navTexts).toEqual(['项目背景', '技术架构', '核心难点', '我的角色', '与 ChatGPT 的区别'])
    expect(root.querySelector('.guide-header h3').textContent).toBe('航空维修智能助手 · 项目介绍')
    expect(root.querySelector('.guide-close').getAttribute('aria-label')).toBe('关闭')
    expect(root.querySelector('.guide-start-btn').textContent.trim()).toBe('开始对话 →')
    // Guide body renders the localized Markdown of the active section.
    await nextTick()
    expect(root.querySelector('.guide-content-inner').innerHTML).toContain('为什么做这个项目？')
  })

  it('ChatMessage renders localized notices, trace ID, and citations', () => {
    const { root } = mountWithI18n(ChatMessage, {
      role: 'system',
      content: '',
      isStreaming: false,
      citations: [{ excerptId: 'amm-25-10-01', documentTitle: 'AMM 25-10-01', chunkIndex: 1, score: 0.8123 }],
      degraded: true,
      degradationReason: 'streaming-disabled',
      outcome: '',
      errorMessage: '请求失败',
      traceId: 'trace-1'
    })
    expect(root.querySelector('.degraded-notice').textContent).toContain('非流式响应：已按配置关闭流式传输（streaming-disabled）')
    expect(root.querySelector('.trace-id').textContent).toBe('关联 ID：trace-1')
    expect(root.querySelector('.citation-title').textContent).toBe('检索来源')
    expect(root.querySelector('.citation-name').textContent).toBe('AMM 25-10-01 · 片段 2')
    expect(root.querySelector('.citation-meta').textContent).toContain('相似度 0.812 · amm-25-10-01')
  })
})
