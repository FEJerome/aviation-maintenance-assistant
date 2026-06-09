import { describe, it, expect } from 'vitest'
import { findParagraphBoundary, processStreamingContent, resetProcessor } from './streamingMarkdownProcessor.js'

describe('findParagraphBoundary', () => {
  it('returns -1 for empty or null input', () => {
    expect(findParagraphBoundary('')).toBe(-1)
    expect(findParagraphBoundary(null)).toBe(-1)
    expect(findParagraphBoundary(undefined)).toBe(-1)
  })

  it('splits on double newline', () => {
    const text = '第一段。\n\n第二段。'
    const boundary = findParagraphBoundary(text)
    expect(boundary).toBe('第一段。\n\n'.length)
    expect(text.slice(0, boundary).trim()).toBe('第一段。')
    expect(text.slice(boundary)).toBe('第二段。')
  })

  it('does not split on single newline', () => {
    const text = '段落\n- 列表项'
    // 无序列表边界会触发，不是单纯因为 \n
    const boundary = findParagraphBoundary(text)
    expect(boundary).toBeGreaterThan(0)
    // 确认是因为列表边界，而不是空行
    expect(text).not.toContain('\n\n')
  })

  it('splits on code block closure at end', () => {
    const text = '```bash\necho hello\n```'
    expect(findParagraphBoundary(text)).toBe(text.length)
  })

  it('does not split inside unclosed code block', () => {
    const text = '```bash\necho hello'
    expect(findParagraphBoundary(text)).toBe(-1)
  })

  it('splits on ordered list item boundary with space', () => {
    const text = '1. 检查步骤 2. 整流罩'
    const boundary = findParagraphBoundary(text)
    expect(boundary).toBeGreaterThan(0)
    expect(text.slice(0, boundary).trim()).toBe('1. 检查步骤')
    expect(text.slice(boundary)).toBe('2. 整流罩')
  })

  it('does not split inline ordered list without space', () => {
    const text = '1. 检查步骤2. 整流罩'
    expect(findParagraphBoundary(text)).toBe(-1)
  })

  it('splits on unordered list item boundary', () => {
    const text = '段落内容\n- 列表项'
    const boundary = findParagraphBoundary(text)
    expect(boundary).toBeGreaterThan(0)
    expect(text.slice(0, boundary).trim()).toBe('段落内容')
    expect(text.slice(boundary)).toBe('- 列表项')
  })

  it('does not trigger sentence split below 170 chars', () => {
    const text = 'a'.repeat(150) + '。'
    expect(text.length).toBe(151)
    expect(findParagraphBoundary(text)).toBe(-1)
  })

  it('triggers sentence split between 170 and 200 chars', () => {
    const prefix = 'a'.repeat(170)
    const text = prefix + '检查。' + 'b'.repeat(10)
    expect(text.length).toBe(183)
    const boundary = findParagraphBoundary(text)
    expect(boundary).toBeGreaterThan(0)
    expect(boundary).toBeLessThan(text.length)
    expect(text.slice(0, boundary).trim()).toBe(prefix + '检查。')
  })

  it('forces hard split at 200 chars when no sentence end', () => {
    const text = 'a'.repeat(220)
    expect(findParagraphBoundary(text)).toBe(200)
  })

  it('does not split on list-number period below threshold', () => {
    const text = '检查。2. 整流罩'
    expect(findParagraphBoundary(text)).toBe(-1)
  })

  it('forces hard split at 200 even with list-number period', () => {
    const text = '检查。2. 整流罩'.repeat(30)
    expect(text.length).toBe(270)
    expect(findParagraphBoundary(text)).toBe(200)
  })

  it('splits on blockquote boundary', () => {
    const text = '普通段落\n> 引用内容'
    const boundary = findParagraphBoundary(text)
    expect(boundary).toBeGreaterThan(0)
    expect(text.slice(0, boundary).trim()).toBe('普通段落')
    expect(text.slice(boundary)).toBe('> 引用内容')
  })
})

describe('processStreamingContent', () => {
  it('handles empty input', () => {
    const confirmedBlocks = { value: [] }
    const streamingBuffer = { value: 'previous' }
    processStreamingContent('', confirmedBlocks, streamingBuffer)
    expect(streamingBuffer.value).toBe('')
    expect(confirmedBlocks.value).toEqual([])
  })

  it('keeps all content in buffer when no boundary', () => {
    const confirmedBlocks = { value: [] }
    const streamingBuffer = { value: '' }
    processStreamingContent('no boundary here', confirmedBlocks, streamingBuffer)
    expect(streamingBuffer.value).toBe('no boundary here')
    expect(confirmedBlocks.value).toEqual([])
  })

  it('moves completed part to confirmedBlocks', () => {
    const confirmedBlocks = { value: [] }
    const streamingBuffer = { value: '' }
    processStreamingContent('第一段。\n\n第二段', confirmedBlocks, streamingBuffer)
    expect(confirmedBlocks.value.length).toBe(1)
    expect(confirmedBlocks.value[0].html).toContain('第一段')
    expect(streamingBuffer.value).toBe('第二段')
  })
})

describe('resetProcessor', () => {
  it('resets confirmedBlocks and streamingBuffer', () => {
    const confirmedBlocks = { value: [{ html: '<p>test</p>' }] }
    const streamingBuffer = { value: 'remaining' }
    resetProcessor(confirmedBlocks, streamingBuffer)
    expect(confirmedBlocks.value).toEqual([])
    expect(streamingBuffer.value).toBe('')
  })
})
