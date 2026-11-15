<template>
  <div :class="['message-row', roleClass]">
    <div class="message-bubble">
      <!-- Streaming message: confirmed blocks + current buffer; DOM structure stays stable across completion -->
      <template v-if="hasStreamingContent">
        <div
          v-for="(block, index) in confirmedBlocks"
          :key="`block-${index}`"
          class="message-block"
          v-html="block.html"
        />
        <div
          v-if="isStreaming"
          class="message-content message-content-plain"
        >
          {{ streamingBuffer }}
        </div>
      </template>
      <!-- Non-streaming message: one-shot fallback rendering -->
      <div
        v-else
        class="message-content message-content-markdown"
        v-html="renderedContent"
      />
      <div v-if="degraded" class="degraded-notice">
        非流式响应：已按配置关闭流式传输（{{ degradationReason }}）
      </div>
      <div v-if="errorMessage" class="error-notice" role="alert">
        {{ errorMessage }}
        <span v-if="traceId" class="trace-id">关联 ID：{{ traceId }}</span>
      </div>
      <div v-if="citations.length" class="citation-list">
        <div class="citation-title">检索来源</div>
        <div v-for="citation in citations" :key="citation.excerptId" class="citation-item">
          <div class="citation-name">{{ citation.documentTitle }} · 片段 {{ citation.chunkIndex + 1 }}</div>
          <div class="citation-meta">
            相似度 {{ citation.score.toFixed(3) }} · <code>{{ citation.excerptId }}</code>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { renderMarkdown } from '../utils/markdownRenderer.js'
import { findParagraphBoundary, finalizeStreamingContent } from '../utils/streamingMarkdownProcessor.js'

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
  },
  citations: {
    type: Array,
    default: () => []
  },
  degraded: {
    type: Boolean,
    default: false
  },
  degradationReason: {
    type: String,
    default: ''
  },
  outcome: {
    type: String,
    default: ''
  },
  errorMessage: {
    type: String,
    default: ''
  },
  traceId: {
    type: String,
    default: ''
  }
})

const roleClass = computed(() => props.role === 'user' ? 'user' : 'system')

/**
 * List of confirmed paragraph blocks, each shaped as { html: string }
 */
const confirmedBlocks = ref([])

/**
 * Plain-text buffer for the content currently being streamed in
 */
const streamingBuffer = ref('')

/**
 * Index of the content already processed, to avoid reprocessing
 */
let lastProcessedIndex = 0

/**
 * Full-content rendering after streaming completes (fallback path for non-streaming messages)
 */
const renderedContent = computed(() => {
  return renderMarkdown(props.content)
})

/**
 * Whether to use the streaming render structure (confirmed blocks + buffer).
 * Used whenever any block is confirmed or streaming is active; otherwise the
 * fallback rendering is used.
 */
const hasStreamingContent = computed(() => {
  return confirmedBlocks.value.length > 0 || props.isStreaming
})

/**
 * Append any content not yet consumed and confirm paragraph boundaries.
 *
 * Idempotent and consumes each byte of props.content exactly once:
 * progress is tracked via lastProcessedIndex. Safe to call from both the
 * content watcher and the completion watcher regardless of which one the
 * scheduler runs first.
 */
function consumePendingContent() {
  const newText = props.content.slice(lastProcessedIndex)
  if (!newText) return

  // Append to streamingBuffer
  streamingBuffer.value += newText

  // Try to find a paragraph boundary in streamingBuffer
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

  lastProcessedIndex = props.content.length
}

/**
 * Listen for isStreaming state transitions: starting a stream resets state,
 * finishing it drains any content that arrived in the same tick as the
 * completion event (see consumePendingContent) before flushing the buffer,
 * so trailing Markdown is never dropped.
 */
watch(() => props.isStreaming, (newVal, oldVal) => {
  if (newVal && !oldVal) {
    // Streaming starts: reset all state
    confirmedBlocks.value = []
    streamingBuffer.value = ''
    lastProcessedIndex = 0
  } else if (!newVal && oldVal) {
    // Streaming ends: consume content that arrived in the same tick first, then flush
    consumePendingContent()
    finalizeStreamingContent(streamingBuffer, confirmedBlocks)
    lastProcessedIndex = 0
  }
})

/**
 * Listen for content changes during streaming: incrementally process the
 * newly arrived text. Content that arrives in the same tick as the
 * completion event is consumed by the isStreaming watcher instead.
 */
watch(
  () => props.content,
  () => {
    if (!props.isStreaming) return
    consumePendingContent()
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
  max-width: 85%;
  padding: 10px 14px;
  border-radius: 12px;
  word-break: keep-all;
  overflow-wrap: break-word;
  line-break: strict;
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

/* Markdown styles for confirmed blocks */
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

/* Table styles: borders + zebra striping */
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

/* Markdown styles for the non-streaming fallback rendering.
 * :deep() pierces the scoped-style boundary so these rules reach nodes
 * injected via v-html, matching the confirmed-block styles above. */
.message-content-markdown :deep(h1),
.message-content-markdown :deep(h2),
.message-content-markdown :deep(h3) {
  margin: 12px 0 8px;
  font-size: 15px;
  color: #333;
}

.message-content-markdown :deep(h4),
.message-content-markdown :deep(h5),
.message-content-markdown :deep(h6) {
  margin: 10px 0 6px;
  font-size: 14px;
  color: #444;
}

.message-content-markdown :deep(ul),
.message-content-markdown :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.message-content-markdown :deep(li) {
  margin: 4px 0;
}

.message-content-markdown :deep(pre) {
  background: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 10px 0;
}

.message-content-markdown :deep(code) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
}

.message-content-markdown :deep(p) {
  margin: 8px 0;
}

.message-content-markdown :deep(blockquote) {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 4px solid #d0d7de;
  background: #f6f8fa;
  color: #57606a;
}

.message-content-markdown :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}

.message-content-markdown :deep(th),
.message-content-markdown :deep(td) {
  border: 1px solid #d0d7de;
  padding: 8px 12px;
  text-align: left;
}

.message-content-markdown :deep(th) {
  background-color: #f6f8fa;
  font-weight: 600;
}

.message-content-markdown :deep(tbody tr:nth-child(even)) {
  background-color: #f6f8fa;
}

.message-content-markdown :deep(tbody tr:hover) {
  background-color: #f3f4f6;
}

.message-content-markdown :deep(a) {
  color: #0969da;
  text-decoration: none;
}

.message-content-markdown :deep(a:hover) {
  text-decoration: underline;
}

.degraded-notice,
.error-notice {
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
}

.degraded-notice {
  background: #fff7df;
  color: #735c00;
}

.error-notice {
  background: #fff0f0;
  color: #a12020;
}

.trace-id {
  display: block;
  margin-top: 4px;
  overflow-wrap: anywhere;
}

.citation-list {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #d8d8d8;
  font-size: 12px;
}

.citation-title {
  margin-bottom: 6px;
  font-weight: 600;
}

.citation-item {
  margin-top: 8px;
  min-width: 0;
}

.citation-name {
  font-weight: 500;
  overflow-wrap: anywhere;
}

.citation-meta {
  margin-top: 3px;
  color: #666;
  overflow-wrap: anywhere;
}

.citation-meta code {
  white-space: normal;
  word-break: break-all;
}
</style>
