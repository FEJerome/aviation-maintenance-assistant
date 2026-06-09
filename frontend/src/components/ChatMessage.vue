<template>
  <div :class="['message-row', roleClass]">
    <div class="message-bubble">
      <!-- 流式接收中：纯文本显示，避免未闭合 Markdown 崩样式 -->
      <div v-if="isStreaming" class="message-content message-content-plain">
        {{ content }}
      </div>
      <!-- 流式完成后：渲染 Markdown + 代码高亮 -->
      <div
        v-else
        class="message-content message-content-markdown"
        v-html="renderedContent"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { renderMarkdown } from '../utils/markdownRenderer.js'

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

const renderedContent = computed(() => {
  return renderMarkdown(props.content)
})
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

/* Markdown 渲染完成后的样式 */
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

/* 表格样式：边框 + 斑马线 */
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
