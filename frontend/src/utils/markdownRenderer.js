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

/**
 * 轻量级 Markdown 兜底修正：处理 LLM 偶发的格式错误。
 *
 * 兜底原则：只处理"明显畸形"且"修复后无副作用"的情况，
 * 不试图重写完整的 Markdown 结构，避免误伤合法内容。
 */
function sanitizeMarkdown(raw) {
  if (!raw) return ''
  return raw
    // #标题 → # 标题（原子组模拟避免回溯误匹配）
    .replace(/^(?=(#{1,6}))\1(?=[^ #\n])/gm, '$1 ')
    // ```bash代码 → ```bash\n代码（原子组模拟避免语言标识被回溯截断）
    .replace(/^(?=(```[a-zA-Z0-9+-]+))\1(?=[^ \n])/gm, '$1\n')
    // 修复数字列表项：把 "1.检查 2.核实" 改成 "1. 检查\n2. 核实"
    // 匹配条件：数字+点+非数字非空白字符，且前面不是数字
    // 例如 "步骤1.检查" → "步骤\n\n1. 检查"
    .replace(/([^\n\d])(\d+\.)([^0-9\s]|$)/g, '$1\n\n$2 $3')
    // 数字列表项/无序列表项前如果没有换行，加空行，让 marked 识别为列表
    .replace(/([^\n])(?=\d+\.\s)/g, '$1\n\n')
    .replace(/([^\n])(?=\n- )/g, '$1\n')
}

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
  const sanitized = sanitizeMarkdown(raw)
  const html = marked.parse(sanitized, { async: false })
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOW_DATA_ATTR: false,
    // 强制所有链接在新窗口打开，并去除不安全的协议
    ALLOWED_URI_REGEXP: /^(?:(?:(?:f|t)tp(?:s)?|mailto|tel|callto|cid|xmpp|xxx):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i
  })
}
