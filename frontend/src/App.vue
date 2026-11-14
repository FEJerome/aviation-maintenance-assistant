<template>
  <div class="chat-container">
    <div class="chat-header">
      <h2>机务维修知识助手</h2>
      <div class="header-actions">
        <button class="guide-entry-btn" @click="showGuide = true">
          📘 项目介绍
        </button>
        <span v-if="conversationId" class="conversation-id">
          会话: {{ conversationId.substring(0, 8) }}...
        </span>
        <button
          class="toggle-btn"
          @click="useIncrementalRendering = !useIncrementalRendering"
        >
          {{ useIncrementalRendering ? '增量：开' : '增量：关' }}
        </button>
        <button v-if="conversationId" class="new-chat-btn" @click="startNewChat">
          新会话
        </button>
      </div>
    </div>

    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="suggested-questions">
        <h3 class="suggested-questions-title">有什么可以帮您？</h3>
        <div class="suggested-questions-grid">
          <button
            v-for="(question, index) in suggestedQuestions"
            :key="index"
            class="suggested-question-card"
            @click="sendSuggestedQuestion(question)"
          >
            {{ question }}
          </button>
        </div>
      </div>
      <ChatMessage
        v-for="msg in messages"
        :key="msg.id"
        :role="msg.role"
        :content="msg.content"
        :is-streaming="msg.isStreaming && useIncrementalRendering"
        :citations="msg.citations"
        :degraded="msg.degraded"
        :degradation-reason="msg.degradationReason"
        :outcome="msg.outcome"
        :error-message="msg.errorMessage"
        :trace-id="msg.traceId"
      />
      <div v-if="loading" class="loading-indicator">
        <span>正在生成...</span>
      </div>
    </div>

    <div class="data-notice">
      使用云端模型时，问题和检索片段会发送给已配置的供应商。
    </div>

    <div class="chat-input-area">
      <input
        ref="inputRef"
        v-model="inputText"
        type="text"
        placeholder="输入问题..."
        :disabled="loading"
        @keyup.enter="sendMessage"
      />
      <button @click="sendMessage" :disabled="loading || !inputText.trim()">
        发送
      </button>
    </div>

    <ProjectGuide v-if="showGuide" @close="showGuide = false" />
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import ChatMessage from './components/ChatMessage.vue'
import ProjectGuide from './components/ProjectGuide.vue'
import { createSseParser } from './utils/sseParser.js'

const messages = ref([])
const inputText = ref('')
const conversationId = ref(null)
const loading = ref(false)
const messagesContainer = ref(null)
const inputRef = ref(null)
const useIncrementalRendering = ref(true)
const showGuide = ref(false)

const suggestedQuestions = [
  'CTLS 飞机 Rotax 912 发动机滑油压力标准值是多少？',
  'WT9 飞机 Rotax 912 起落架收放系统的日常检查要求是什么？',
  '飞机结构修理中，铆接修理的一般规范有哪些？',
  'B737-800 CFM56-7B 发动机滑油压力低，应该如何排故？'
]

watch(messages, (newVal) => {
  if (newVal.length === 0) {
    showGuide.value = true
  }
}, { immediate: true })

function sendSuggestedQuestion(question) {
  inputText.value = question
  sendMessage()
}

function findMessage(id) {
  return messages.value.find(message => message.id === id)
}

function parsePayload(data) {
  try {
    return JSON.parse(data)
  } catch {
    throw new Error('服务端返回了无法解析的事件数据')
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ id: Date.now(), role: 'user', content: text, isStreaming: false })
  inputText.value = ''
  loading.value = true
  scrollToBottom()

  const systemMsgId = Date.now() + 1
  messages.value.push({
    id: systemMsgId,
    role: 'system',
    content: '',
    isStreaming: true,
    citations: [],
    degraded: false,
    outcome: null,
    errorMessage: '',
    traceId: ''
  })

  let terminal = false

  function fail(message) {
    const msg = findMessage(systemMsgId)
    if (msg) {
      msg.isStreaming = false
      msg.errorMessage = message
    }
    terminal = true
  }

  try {
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message: text,
        conversationId: conversationId.value
      })
    })

    if (!response.ok) {
      const payload = await response.json().catch(() => ({}))
      fail(payload.message || `请求失败（HTTP ${response.status}）`)
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    const parser = createSseParser(({ event, data }) => {
      if (terminal) return
      const msg = findMessage(systemMsgId)
      if (!msg) return

      if (event === 'token') {
        msg.content += data
        scrollToBottom()
        return
      }

      if (event === 'complete') {
        const payload = parsePayload(data)
        conversationId.value = payload.conversationId || conversationId.value
        msg.citations = Array.isArray(payload.citations) ? payload.citations : []
        msg.degraded = payload.degraded === true
        msg.degradationReason = payload.degradationReason || ''
        msg.outcome = payload.outcome || ''
        msg.traceId = payload.traceId || ''
        msg.isStreaming = false
        terminal = true
        return
      }

      if (event === 'error') {
        const payload = parsePayload(data)
        msg.traceId = payload.traceId || ''
        fail(payload.message || '请求失败')
      }
    })

    while (!terminal) {
      const { done, value } = await reader.read()
      if (done) {
        parser.push(decoder.decode())
        parser.flush()
        if (!terminal) {
          fail('响应在完成事件到达前中断')
        }
        break
      }

      parser.push(decoder.decode(value, { stream: true }))
      if (terminal) {
        await reader.cancel()
      }
    }
  } catch (error) {
    fail(error.message || '请求失败')
  } finally {
    const msg = findMessage(systemMsgId)
    if (msg && msg.isStreaming) {
      msg.isStreaming = false
      if (!terminal) {
        msg.errorMessage = '响应在完成事件到达前中断'
      }
    }
    loading.value = false
    scrollToBottom()
    inputRef.value?.focus()
  }
}

function startNewChat() {
  conversationId.value = null
  messages.value = []
  inputText.value = ''
  showGuide.value = true
  inputRef.value?.focus()
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}
</script>

<style scoped>
.chat-container {
  max-width: 800px;
  margin: 0 auto;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: white;
}

.chat-header {
  padding: 16px 20px;
  background-color: #fff;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header h2 {
  font-size: 18px;
  color: #333;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.conversation-id {
  font-size: 11px;
  color: #999;
}

.new-chat-btn {
  padding: 4px 12px;
  background-color: transparent;
  border: 1px solid #007bff;
  color: #007bff;
  border-radius: 12px;
  cursor: pointer;
  font-size: 12px;
}

.new-chat-btn:hover {
  background-color: #007bff;
  color: white;
}

.guide-entry-btn {
  padding: 4px 12px;
  background-color: transparent;
  border: 1px solid #6c757d;
  color: #6c757d;
  border-radius: 12px;
  cursor: pointer;
  font-size: 12px;
}

.guide-entry-btn:hover {
  background-color: #6c757d;
  color: white;
}

.toggle-btn {
  padding: 4px 12px;
  background-color: transparent;
  border: 1px solid #28a745;
  color: #28a745;
  border-radius: 12px;
  cursor: pointer;
  font-size: 12px;
}

.toggle-btn:hover {
  background-color: #28a745;
  color: white;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.loading-indicator {
  text-align: center;
  color: #999;
  font-size: 13px;
  margin-top: 10px;
}

.data-notice {
  padding: 8px 20px;
  border-top: 1px solid #eee;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.4;
}

.chat-input-area {
  display: flex;
  padding: 12px 20px;
  border-top: 1px solid #e0e0e0;
  background-color: #fff;
  gap: 10px;
}

.chat-input-area input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid #ddd;
  border-radius: 20px;
  outline: none;
  font-size: 14px;
}

.chat-input-area input:focus {
  border-color: #007bff;
}

.chat-input-area button {
  padding: 10px 24px;
  background-color: #007bff;
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
}

.chat-input-area button:hover:not(:disabled) {
  background-color: #0056b3;
}

.chat-input-area button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

@media (max-width: 600px) {
  .chat-container {
    max-width: 100%;
    height: 100dvh;
  }

  .chat-header h2 {
    font-size: 16px;
  }

  .chat-input-area {
    padding: 8px 12px;
  }

  .chat-input-area button {
    padding: 8px 16px;
    font-size: 13px;
  }
}
</style>
