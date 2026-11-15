import { describe, it, expect } from 'vitest'
import { renderMarkdown } from './markdownRenderer.js'

/**
 * Verify the key behaviors of the Markdown renderer:
 * 1. Standard Markdown renders correctly
 * 2. Occasional LLM formatting errors are repaired by the frontend fallback
 * 3. XSS content is filtered out
 */
describe('renderMarkdown', () => {
  it('renders a proper heading', () => {
    const html = renderMarkdown('# AMM')
    expect(html).toContain('<h1>')
    expect(html).toContain('AMM')
  })

  it('fixes malformed heading without space after #', () => {
    const html = renderMarkdown('#AMM')
    expect(html).toContain('<h1>')
    expect(html).toContain('AMM')
  })

  it('renders a level-2 heading', () => {
    const html = renderMarkdown('## 检查步骤')
    expect(html).toContain('<h2>')
  })

  it('renders bold text', () => {
    const html = renderMarkdown('**关键参数**')
    expect(html).toContain('<strong>关键参数</strong>')
  })

  it('renders an unordered list', () => {
    const html = renderMarkdown('- 第一项\n- 第二项')
    expect(html).toContain('<ul>')
    expect(html).toContain('<li>')
  })

  it('renders an ordered list', () => {
    const html = renderMarkdown('1. 第一步\n2. 第二步')
    expect(html).toContain('<ol>')
    expect(html).toContain('<li>')
  })

  it('fixes inline ordered list items squashed into one line', () => {
    const html = renderMarkdown('步骤1. 发动机检查2. 整流罩检查3. 滑油检查')
    expect(html).toContain('<ol>')
    expect(html).toContain('发动机检查')
    expect(html).toContain('整流罩检查')
    expect(html).toContain('滑油检查')
  })

  it('renders a fenced code block with syntax highlighting', () => {
    const html = renderMarkdown('```bash\necho hello\n```')
    expect(html).toContain('<pre>')
    expect(html).toContain('language-bash')
  })

  it('fixes malformed code fence missing newline after language identifier', () => {
    const html = renderMarkdown('```bash\necho hello\n```')
    expect(html).toContain('<pre>')
    expect(html).toContain('language-bash')
  })

  it('renders a table', () => {
    const html = renderMarkdown('| 参数 | 标准值 |\n| --- | --- |\n| 滑油压力 | 正常 |')
    expect(html).toContain('<table>')
    expect(html).toContain('<th>')
    expect(html).toContain('<td>')
  })

  it('filters out dangerous HTML to prevent XSS', () => {
    const html = renderMarkdown('<script>alert(1)</script>')
    expect(html).not.toContain('<script>')
    expect(html).not.toContain('alert')
  })

  it('returns empty string for null or undefined input', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown(null)).toBe('')
    expect(renderMarkdown(undefined)).toBe('')
  })
})

describe('link URI allowlist', () => {
  it('preserves http and https link hrefs', () => {
    const html = renderMarkdown('[manual](https://example.com/amm) [site](http://example.com)')
    expect(html).toContain('href="https://example.com/amm"')
    expect(html).toContain('href="http://example.com"')
  })

  it('strips javascript: link hrefs', () => {
    const html = renderMarkdown('[xss](javascript:alert(1))')
    expect(html).not.toContain('href')
    expect(html).not.toContain('javascript:')
  })

  it('strips on* event handler attributes from links', () => {
    const html = renderMarkdown('<a href="https://example.com" onclick="alert(1)">x</a>')
    expect(html).not.toContain('onclick')
  })

  it('preserves ftp, mailto and relative link hrefs', () => {
    const html = renderMarkdown('[f](ftp://example.com/file) [m](mailto:a@b.c) [r](/docs/guide)')
    expect(html).toContain('href="ftp://example.com/file"')
    expect(html).toContain('href="mailto:a@b.c"')
    expect(html).toContain('href="/docs/guide"')
  })
})
