import { renderMarkdown } from './markdownRenderer.js'

/**
 * 流式缓冲的最大长度。
 * 超过此长度且无自然边界时，触发强制硬切。
 */
const MAX_STREAMING_BUFFER_LENGTH = 200

/**
 * 判断当前文本是否处于未闭合的代码块内。
 * @param {string} text
 * @returns {boolean}
 */
function isInsideCodeBlock(text) {
  const fenceMatches = text.match(/^```[a-zA-Z0-9+-]*$/gm)
  return fenceMatches && fenceMatches.length % 2 === 1
}

/**
 * 查找段落边界，返回已确认内容的结束位置索引。
 *
 * 边界优先级（从高到低）：
 * 1. 空行分隔（\n\n）
 * 2. 代码块闭合（``` 成对）
 * 3. 列表项切换（新的 1. / - ）
 * 4. 引用块边界（新的 > ）
 * 5. 200 字符兜底硬切
 *
 * 注意：不再使用"句末句号急切切分"策略，以避免切在 Markdown Inline
 * 标记（如 **加粗 **、*斜体*）中间，导致已确认块渲染样式错乱。
 *
 * @param {string} text - 当前 streamingBuffer
 * @returns {number} - 边界索引（-1 表示未找到）
 */
export function findParagraphBoundary(text) {
  if (!text || text.length === 0) return -1

  // 状态 1：代码块
  // 在未闭合的代码块内：必须等待闭合
  if (isInsideCodeBlock(text)) {
    return -1
  }
  // 文本以闭合的代码块 fence 结尾：整个文本可以固化
  if (/\n```\s*$/.test(text)) {
    return text.length
  }

  // 状态 2：空行分隔（最明确的段落边界）
  // Markdown 中单个 \n 不表示段落结束（如列表项之间只有一个 \n），
  // 只有空行（\n\n）才是块级边界。
  const doubleNewline = text.lastIndexOf('\n\n')
  if (doubleNewline > 0) {
    return doubleNewline + 2
  }

  // 状态 3：有序列表项边界
  // 匹配 "1. xxx 2. " 中 "1. xxx " 和 "2. " 之间的位置
  const listBoundary = text.match(/(?<=\S)\s+(?=\d+\.\s)/)
  if (listBoundary) {
    return listBoundary.index + 1
  }

  // 状态 4：无序列表项边界
  const ulBoundary = text.match(/(?<=[^\n])\n(?=-\s)/)
  if (ulBoundary) {
    return ulBoundary.index + 1
  }

  // 状态 5：引用块边界
  const blockquoteBoundary = text.match(/(?<=[^\n])\n(?=>\s)/)
  if (blockquoteBoundary) {
    return blockquoteBoundary.index + 1
  }

  // 状态 6：200 字符兜底硬切
  // 仅在无自然边界时触发，避免缓冲无限累积。
  if (text.length >= MAX_STREAMING_BUFFER_LENGTH) {
    return MAX_STREAMING_BUFFER_LENGTH
  }

  return -1
}

/**
 * 处理流式内容，将已确认的段落渲染为 HTML，剩余部分保留为纯文本。
 *
 * 注意：本函数对 fullContent 只做单次切分。如果内容包含多个可切分边界，
 * 调用者需自行循环调用，或在每次 content 变化时调用一次（Vue watch 模式）。
 *
 * @param {string} fullContent - 当前完整内容（包含已确认 + 未确认）
 * @param {Array<{html: string}>} confirmedBlocks - 已确认的 HTML 块数组（会被修改）
 * @param {Ref<string>} streamingBuffer - 当前流式缓冲 ref（会被修改）
 * @param {Function} [renderFn] - 渲染函数，默认 renderMarkdown
 */
export function processStreamingContent(
  fullContent,
  confirmedBlocks,
  streamingBuffer,
  renderFn = renderMarkdown
) {
  if (!fullContent) {
    streamingBuffer.value = ''
    return
  }

  const boundary = findParagraphBoundary(fullContent)

  if (boundary === -1) {
    streamingBuffer.value = fullContent
    return
  }

  const completed = fullContent.slice(0, boundary).trim()
  const remaining = fullContent.slice(boundary)

  if (completed) {
    const html = renderFn(completed)
    if (html && html.trim()) {
      confirmedBlocks.value.push({ html })
    }
  }

  streamingBuffer.value = remaining
}

/**
 * 流式结束时，将剩余缓冲强制渲染并追加到已确认块。
 *
 * @param {Ref<string>} streamingBuffer - 当前流式缓冲 ref（会被清空）
 * @param {Array<{html: string}>} confirmedBlocks - 已确认的 HTML 块数组（会被修改）
 * @param {Function} [renderFn] - 渲染函数，默认 renderMarkdown
 */
export function finalizeStreamingContent(
  streamingBuffer,
  confirmedBlocks,
  renderFn = renderMarkdown
) {
  const remaining = streamingBuffer.value.trim()
  if (remaining) {
    const html = renderFn(remaining)
    if (html && html.trim()) {
      confirmedBlocks.value.push({ html })
    }
  }
  streamingBuffer.value = ''
}

/**
 * 重置处理器状态（用于新会话）。
 *
 * @param {Ref<Array>} confirmedBlocks
 * @param {Ref<string>} streamingBuffer
 */
export function resetProcessor(confirmedBlocks, streamingBuffer) {
  confirmedBlocks.value = []
  streamingBuffer.value = ''
}
