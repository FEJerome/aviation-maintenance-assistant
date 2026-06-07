<template>
  <div class="chat-container">
    <div class="chat-header">
      <h2>机务维修知识助手</h2>
      <div class="header-actions">
        <span v-if="conversationId" class="conversation-id">
          会话: {{ conversationId.substring(0, 8) }}...
        </span>
        <button v-if="conversationId" class="new-chat-btn" @click="startNewChat">
          新会话
        </button>
      </div>
    </div>

    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="empty-state">
        请输入问题，例如：B737-800 发动机滑油压力
      </div>
      <ChatMessage
        v-for="msg in messages"
        :key="msg.id"
        :role="msg.role"
        :content="msg.content"
      />
      <div v-if="loading" class="loading-indicator">
        <span>正在生成...</span>
      </div>
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
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import ChatMessage from './components/ChatMessage.vue'

const messages = ref([])
const inputText = ref('')
const conversationId = ref(null)
const loading = ref(false)
const messagesContainer = ref(null)
const inputRef = ref(null)

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ id: Date.now(), role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  scrollToBottom()

  // 预占一个系统消息位，用于流式填充
  const systemMsgId = Date.now() + 1
  messages.value.push({ id: systemMsgId, role: 'system', content: '' })

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
      const errData = await response.json().catch(() => ({}))
      throw new Error(errData.error || errData.message || `HTTP ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // SSE 格式解析：按双换行分割事件
      const lines = buffer.split('\n')
      let eventName = ''
      let eventData = ''

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i]
        if (line.startsWith('event:')) {
          eventName = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          eventData = line.substring(5).trim()
        } else if (line === '' && eventName) {
          // 一个完整事件
          if (eventName === 'token') {
            const msg = messages.value.find(m => m.id === systemMsgId)
            if (msg) msg.content += eventData
            scrollToBottom()
          } else if (eventName === 'complete') {
            try {
              const payload = JSON.parse(eventData)
              if (payload.conversationId) {
                conversationId.value = payload.conversationId
              }
            } catch (e) {
              // ignore parse error
            }
          } else if (eventName === 'error') {
            const msg = messages.value.find(m => m.id === systemMsgId)
            if (msg) msg.content = '请求失败：' + eventData
          }
          // 重置
          eventName = ''
          eventData = ''
        }
      }
    }
  } catch (error) {
    const msg = messages.value.find(m => m.id === systemMsgId)
    if (msg) {
      msg.content = '请求失败：' + error.message
    } else {
      messages.value.push({
        id: Date.now(),
        role: 'system',
        content: '请求失败：' + error.message
      })
    }
  } finally {
    loading.value = false
    scrollToBottom()
    inputRef.value?.focus()
  }
}

function startNewChat() {
  conversationId.value = null
  messages.value = []
  inputText.value = ''
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

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.empty-state {
  text-align: center;
  color: #999;
  margin-top: 40px;
  font-size: 14px;
}

.loading-indicator {
  text-align: center;
  color: #999;
  font-size: 13px;
  margin-top: 10px;
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
