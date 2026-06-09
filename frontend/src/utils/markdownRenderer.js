import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import plaintext from 'highlight.js/lib/languages/plaintext'
import python from 'highlight.js/lib/languages/python'
import yaml from 'highlight.js/lib/languages/yaml'
import sql from 'highlight.js/lib/languages/sql'

// 按需注册机务场景常见语言，控制包体积
hljs.registerLanguage('java', java)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('plaintext', plaintext)
hljs.registerLanguage('python', python)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('sql', sql)

// 配置 marked：关闭危险特性，启用代码高亮
marked.use({
  renderer: {
    // 代码块高亮
    // marked v15 的 renderer API：code({ text, lang, escaped })
    code({ text, lang }) {
      const validLang = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
      const highlighted = hljs.highlight(text, { language: validLang }).value
      return `<pre><code class="hljs language-${validLang}">${highlighted}</code></pre>`
    }
  },
  gfm: true,           // GitHub Flavored Markdown
  breaks: true,        // 单换行转 <br>
  headerIds: false,    // 不生成 h1/h2 id，防止与页面元素冲突
  mangle: false        // 不转义邮件地址
})

const ALLOWED_TAGS = [
  'p', 'br', 'hr',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'ul', 'ol', 'li',
  'strong', 'b', 'em', 'i', 'del', 's',
  'a', 'code', 'pre', 'blockquote',
  'table', 'thead', 'tbody', 'tr', 'th', 'td'
]

const ALLOWED_ATTR = ['href', 'target', 'rel', 'class']

/**
 * 渲染 Markdown 文本为安全的 HTML 字符串
 * @param {string} raw - 原始 Markdown 文本
 * @returns {string} - 渲染后的 HTML
 */
export function renderMarkdown(raw) {
  if (!raw) return ''
  const html = marked.parse(raw, { async: false })
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOW_DATA_ATTR: false,
    // 强制所有链接在新窗口打开，并去除不安全的协议
    ALLOWED_URI_REGEXP: /^(?:(?:(?:f|t)tp(?:s)?|mailto|tel|callto|cid|xmpp|xxx):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i
  })
}
