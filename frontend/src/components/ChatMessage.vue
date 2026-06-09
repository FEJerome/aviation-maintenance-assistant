<template>
  <div :class="['message-row', roleClass]">
    <div class="message-bubble">
      <!-- 流式接收中：段落级增量渲染 -->
      <template v-if="isStreaming">
        <!-- 已确认的段落：渲染为 Markdown -->
        <div
          v-for="(block, index) in confirmedBlocks"
          :key="`block-${index}`"
          class="message-block"
          v-html="block.html"
        />
        <!-- 正在累积的段落：纯文本显示 -->
        <div class="message-content message-content-plain">
          {{ streamingBuffer }}
        </div>
      </template>
      <!-- 流式完成后：一次性渲染全部内容 -->
      <div
        v-else
        class="message-content message-content-markdown"
        v-html="renderedContent"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { renderMarkdown } from '../utils/markdownRenderer.js'
import { findParagraphBoundary } from '../utils/streamingMarkdownProcessor.js'

const props = defineProps({
  role: {
    type: String,
    required: true
  },
  content: {
    type: String,
    required: true
  },
  isStreaming: {
    type: Boolean,
    default: false
  }
})

const roleClass = computed(() => props.role === 'user' ? 'user' : 'system')

/**
 * 已确认的段落块列表，每个元素为 { html: string }
 */
const confirmedBlocks = ref([])

/**
 * 当前正在流式接收中的纯文本缓冲
 */
const streamingBuffer = ref('')

/**
 * 记录上次已处理到的 content 索引，避免重复处理
 */
let lastProcessedIndex = 0

/**
 * 流式完成后的完整渲染（兜底路径）
 */
const renderedContent = computed(() => {
  return renderMarkdown(props.content)
})

/**
 * 监听 isStreaming 状态变化：开始/结束流式时重置状态
 */
watch(() => props.isStreaming, (newVal, oldVal) => {
  if (newVal && !oldVal) {
    // 开始流式：重置所有状态
    confirmedBlocks.value = []
    streamingBuffer.value = ''
    lastProcessedIndex = 0
  } else if (!newVal && oldVal) {
    // 结束流式：重置状态，让 renderedContent 接管
    confirmedBlocks.value = []
    streamingBuffer.value = ''
    lastProcessedIndex = 0
  }
})

/**
 * 监听 content 变化：增量处理新增内容
 */
watch(
  () => props.content,
  (newContent) => {
    if (!props.isStreaming) return

    const newText = newContent.slice(lastProcessedIndex)
    if (!newText) return

    // 追加到 streamingBuffer
    streamingBuffer.value += newText

    // 尝试在 streamingBuffer 中找段落边界
    const boundary = findParagraphBoundary(streamingBuffer.value)
    if (boundary > 0) {
      const completed = streamingBuffer.value.slice(0, boundary).trim()
      const remaining = streamingBuffer.value.slice(boundary)

      if (completed) {
        const html = renderMarkdown(completed)
        if (html && html.trim()) {
          confirmedBlocks.value.push({ html })
        }
      }
      streamingBuffer.value = remaining
    }

    lastProcessedIndex = newContent.length
  },
  { flush: 'post' }
)
</script>

<style scoped>
.message-row {
  display: flex;
  margin-bottom: 12px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.system {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 12px;
  word-wrap: break-word;
}

.user .message-bubble {
  background-color: #007bff;
  color: white;
  border-bottom-right-radius: 4px;
}

.system .message-bubble {
  background-color: #f1f1f1;
  color: #333;
  border-bottom-left-radius: 4px;
}

.message-content {
  font-size: 14px;
  line-height: 1.5;
  white-space: pre-wrap;
}

/* 已确认段落的 Markdown 样式 */
.message-block {
  margin-bottom: 8px;
}

.message-block:last-child {
  margin-bottom: 0;
}

.message-block :deep(h1),
.message-block :deep(h2),
.message-block :deep(h3) {
  margin: 12px 0 8px;
  font-size: 15px;
  color: #333;
}

.message-block :deep(h4),
.message-block :deep(h5),
.message-block :deep(h6) {
  margin: 10px 0 6px;
  font-size: 14px;
  color: #444;
}

.message-block :deep(ul),
.message-block :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.message-block :deep(li) {
  margin: 4px 0;
}

.message-block :deep(pre) {
  background: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 10px 0;
}

.message-block :deep(code) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
}

.message-block :deep(p) {
  margin: 8px 0;
}

.message-block :deep(blockquote) {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 4px solid #d0d7de;
  background: #f6f8fa;
  color: #57606a;
}

/* 表格样式：边框 + 斑马线 */
.message-block :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}

.message-block :deep(th),
.message-block :deep(td) {
  border: 1px solid #d0d7de;
  padding: 8px 12px;
  text-align: left;
}

.message-block :deep(th) {
  background-color: #f6f8fa;
  font-weight: 600;
}

.message-block :deep(tbody tr:nth-child(even)) {
  background-color: #f6f8fa;
}

.message-block :deep(tbody tr:hover) {
  background-color: #f3f4f6;
}

.message-block :deep(a) {
  color: #0969da;
  text-decoration: none;
}

.message-block :deep(a:hover) {
  text-decoration: underline;
}

/* 流式完成后的 Markdown 样式（兜底路径） */
.message-content-markdown h1,
.message-content-markdown h2,
.message-content-markdown h3 {
  margin: 12px 0 8px;
  font-size: 15px;
  color: #333;
}

.message-content-markdown h4,
.message-content-markdown h5,
.message-content-markdown h6 {
  margin: 10px 0 6px;
  font-size: 14px;
  color: #444;
}

.message-content-markdown ul,
.message-content-markdown ol {
  padding-left: 20px;
  margin: 8px 0;
}

.message-content-markdown li {
  margin: 4px 0;
}

.message-content-markdown pre {
  background: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 10px 0;
}

.message-content-markdown code {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
}

.message-content-markdown p {
  margin: 8px 0;
}

.message-content-markdown blockquote {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 4px solid #d0d7de;
  background: #f6f8fa;
  color: #57606a;
}

.message-content-markdown table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}

.message-content-markdown th,
.message-content-markdown td {
  border: 1px solid #d0d7de;
  padding: 8px 12px;
  text-align: left;
}

.message-content-markdown th {
  background-color: #f6f8fa;
  font-weight: 600;
}

.message-content-markdown tbody tr:nth-child(even) {
  background-color: #f6f8fa;
}

.message-content-markdown tbody tr:hover {
  background-color: #f3f4f6;
}

.message-content-markdown a {
  color: #0969da;
  text-decoration: none;
}

.message-content-markdown a:hover {
  text-decoration: underline;
}
</style>
