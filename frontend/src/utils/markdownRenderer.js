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
 * Lightweight Markdown fallback fixes for occasional LLM formatting errors.
 *
 * Principle: only repair "obviously malformed" cases where the fix has no
 * side effects. Never attempt to rewrite whole Markdown structures, so
 * legitimate content is never harmed.
 */
function sanitizeMarkdown(raw) {
  if (!raw) return ''
  return raw
    // "#heading" -> "# heading" (atomic-group simulation avoids backtracking mismatches)
    .replace(/^(?=(#{1,6}))\1(?=[^ #\n])/gm, '$1 ')
    // "```bashcode" -> "```bash\ncode" (atomic-group simulation keeps the language identifier intact)
    .replace(/^(?=(```[a-zA-Z0-9+-]+))\1(?=[^ \n])/gm, '$1\n')
    // Repair numbered list items squashed into one line: "1.check 2.verify"
    // Match condition: digits + dot + non-digit non-whitespace, not preceded by a digit
    // e.g. "step1.check" -> "step\n\n1. check"
    .replace(/([^\n\d])(\d+\.)([^0-9\s]|$)/g, '$1\n\n$2 $3')
    // Insert a blank line before numbered/unordered list items not preceded by
    // a newline, so marked recognizes them as lists
    .replace(/([^\n])(?=\d+\.\s)/g, '$1\n\n')
    .replace(/([^\n])(?=\n- )/g, '$1\n')
}

// Register on-demand the languages common in maintenance scenarios to control bundle size
hljs.registerLanguage('java', java)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('plaintext', plaintext)
hljs.registerLanguage('python', python)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('sql', sql)

// Configure marked: disable risky features, enable code highlighting
marked.use({
  renderer: {
    // Code block highlighting
    // marked v15 renderer API: code({ text, lang, escaped })
    code({ text, lang }) {
      const validLang = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
      const highlighted = hljs.highlight(text, { language: validLang }).value
      return `<pre><code class="hljs language-${validLang}">${highlighted}</code></pre>`
    }
  },
  gfm: true,           // GitHub Flavored Markdown
  breaks: true,        // single newline becomes <br>
  headerIds: false,    // no h1/h2 id generation, avoid clashing with page elements
  mangle: false        // do not escape email addresses
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
 * Render Markdown text into a safe HTML string
 * @param {string} raw - raw Markdown text
 * @returns {string} - rendered HTML
 */
export function renderMarkdown(raw) {
  if (!raw) return ''
  const sanitized = sanitizeMarkdown(raw)
  const html = marked.parse(sanitized, { async: false })
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOW_DATA_ATTR: false,
    // URI allowlist aligned with the DOMPurify default: http/https/ftp/mailto
    // and relatives pass; javascript: and other unknown schemes are stripped
    ALLOWED_URI_REGEXP: /^(?:(?:(?:f|ht)tps?|mailto|tel|callto|cid|xmpp|xxx):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i
  })
}
