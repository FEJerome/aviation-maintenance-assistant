import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN.js'
import en from './locales/en.js'

/**
 * Custom message compiler: messages are treated as literal text with simple
 * `{name}` interpolation. The default intlify compiler parses `|` as a
 * plural-form separator, which breaks the Markdown table pipes embedded in
 * the guide content (e.g. `| 层级 | 技术选型 |`). Plural and linked-message
 * syntax are intentionally unsupported — the resources do not use them.
 */
export function literalMessageCompiler(message) {
  return (ctx) =>
    String(message).replace(/\{(\w+)\}/g, (match, key) => {
      const named = ctx.named
      const value = typeof named === 'function' ? named(key) : named?.[key]
      return value === undefined || value === null ? match : String(value)
    })
}

/**
 * Shared i18n instance. zh-CN is the default and fallback locale: with no
 * language-switching control in the UI, the interface renders exactly the
 * copy that was previously hard-coded in the components.
 */
export const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messageCompiler: literalMessageCompiler,
  messages: {
    'zh-CN': zhCN,
    en
  }
})

export default i18n
