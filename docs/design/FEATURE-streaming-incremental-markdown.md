# 功能设计：流式场景下段落级增量 Markdown 渲染

## 背景

当前流式输出的 Markdown 渲染策略是 **complete 后一次性渲染**（策略 A）：

- 流式接收期间：`{{ content }}` 纯文本显示
- 流式完成后：`v-html="renderedContent"` 一次性渲染

这导致用户在前 5~10 秒内看到的是不可读的 raw markdown：`## 检查步骤1. **发动机检查**：参考**Rotax912...**

路演需要更专业的展示效果。本设计引入**段落级增量渲染**（策略 B）：已确认的段落立即渲染为漂亮格式，正在接收的段落继续纯文本累积。

## 目标

1. 已确认段落：立即渲染为 Markdown + 代码高亮，观众无需等待 complete
2. 正在接收段落：纯文本打字机效果，稳定不抖动
3. 段落切换：从纯文本到格式化 HTML 的过渡平滑、无闪烁
4. 性能可控：渲染频率从"每 token"降到"每段落"（通常 2~10 秒一次）
5. 语义分割：只在明确的 Markdown 块边界处切分（空行、代码块闭合、列表项切换、引用块边界）
6. 兜底稳定：超过 200 字符硬切，避免 buffer 无限累积
7. 流式完成后不重渲染：保持 DOM 结构稳定，避免完成瞬间的闪烁和样式突变

## 方案对比

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| A. complete 后一次性渲染 | 实现简单，绝对稳定 | 流式过程中全是 raw markdown | ❌ 当前方案，路演体验差 |
| B. 段落级增量渲染 | 段落逐步绽放，兼顾体验与稳定性 | 需要写段落边界检测状态机 | ✅ **采纳** |
| C. 逐 token 实时渲染 | 理论最实时 | DOM 剧烈抖动，性能灾难，工程不可行 | ❌ 放弃 |
| D. 隐藏中间态 | 无 raw markdown 暴露 | 失去流式核心价值，像 loading 动画 | ❌ 放弃 |

## 详细设计

### 1. 架构：双 Buffer 设计

```
┌─────────────────────────────────────────────┐
│  ChatMessage.vue                             │
│                                              │
│  ┌─────────────────────────────────────┐   │
│  │  confirmedBlocks[]                  │   │
│  │  [{ html: '<p>...</p>' },           │   │
│  │   { html: '<ol><li>...</li></ol>' }]│   │
│  └─────────────────────────────────────┘   │
│                                              │
│  ┌─────────────────────────────────────┐   │
│  │  streamingBuffer: '2. 整流罩...'    │   │
│  └─────────────────────────────────────┘   │
│                                              │
└─────────────────────────────────────────────┘
```

**数据流**：

1. 后端推送 `token` → `App.vue` 追加到当前消息的 `content`
2. `ChatMessage.vue` 的 `watch` 监听到 `content` 变化
3. 调用 `processStreamingContent(newContent)`
4. 内部维护 `confirmedBlocks` 和 `streamingBuffer`
5. 模板用 `v-for` 渲染 `confirmedBlocks`，用 `{{ streamingBuffer }}` 显示纯文本

**全局开关**：

- 默认开启增量渲染。
- `App.vue` 中配置 `useIncrementalRendering: true`。
- 设为 `false` 时，所有消息退化为 complete 后一次性渲染（`v-html="renderedContent"`）。
- 开关用于快速回滚，无需改代码。

### 2. 段落边界检测算法

核心函数 `findParagraphBoundary(text)`，返回段落分割点索引（-1 表示未找到）：

**边界优先级**（从高到低）：

1. **空行分隔**（`\n\n`）：最明确的段落边界。Markdown 中单个 `\n` 不表示段落结束（如列表项之间只有一个 `\n`），只有空行才是块级边界。
2. **代码块闭合**：``` 成对出现时，整个代码块一次性固化。
3. **列表项切换**：新的有序列表项（`\d+\.\s`）或无序列表项（`-\s`）开始前，前一个列表项已完整。
4. **引用块边界**：新的引用行（`>\s`）出现前，前一个引用块已完整。
5. **200 字符兜底**：超过 200 字符且无自然边界时，按 200 字符硬切。

> **历史调整**：早期版本曾在 170~200 字符区间使用"句末句号急切切分"策略，希望提升流式感。但实际测试发现，句末句号不是 Markdown 块边界，切分点经常落在 Inline Markdown 标记（如 `**加粗**`）中间，导致已确认段落渲染出星号残留或样式错乱。因此该策略被取消，改为只在明确的 Markdown 块边界处切分。

**代码块状态机**：

```javascript
function isInsideCodeBlock(text) {
  const fenceMatches = text.match(/^```[a-zA-Z0-9+-]*$/gm)
  return fenceMatches && fenceMatches.length % 2 === 1
}
```

### 3. 内容处理流程

```javascript
function processStreamingContent(fullContent) {
  // 1. 找到已确认内容和流式内容的分界点
  let boundary = findParagraphBoundary(fullContent)

  if (boundary === -1) {
    // 没有边界：全部内容都在 streamingBuffer
    streamingBuffer.value = fullContent
    return
  }

  // 2. 分割内容
  const completed = fullContent.slice(0, boundary).trim()
  const remaining = fullContent.slice(boundary)

  // 3. 渲染已确认段落
  if (completed) {
    const html = renderMarkdown(completed)
    confirmedBlocks.value.push({ html })
  }

  // 4. 剩余部分继续作为 streamingBuffer
  streamingBuffer.value = remaining
}
```

**流式结束时的 flush**：

当 `isStreaming` 从 `true` 变为 `false` 时，调用 `finalizeStreamingContent` 将 `streamingBuffer` 中剩余的最后一段文本强制渲染并追加到 `confirmedBlocks`，保持 DOM 结构稳定。

```javascript
function finalizeStreamingContent(streamingBuffer, confirmedBlocks) {
  const remaining = streamingBuffer.value.trim()
  if (remaining) {
    const html = renderMarkdown(remaining)
    if (html && html.trim()) {
      confirmedBlocks.value.push({ html })
    }
  }
  streamingBuffer.value = ''
}
```

### 4. ChatMessage.vue 模板改造

```vue
<template>
  <div :class="['message-row', roleClass]">
    <div class="message-bubble">
      <!-- 流式消息：已确认块 + 当前缓冲，DOM 结构在流式结束前后保持一致 -->
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
      <!-- 非流式消息：兜底一次性渲染 -->
      <div
        v-else
        class="message-content message-content-markdown"
        v-html="renderedContent"
      />
    </div>
  </div>
</template>
```

**关键设计**：

- 流式消息在 `isStreaming` 变为 `false` 后，不切换为新的容器，而是继续展示 `confirmedBlocks`。
- `hasStreamingContent` 判断：只要有已确认块或正在流式中，就使用流式结构；否则使用兜底渲染。
- 非流式消息（如历史消息、错误提示）仍使用 `renderedContent` 一次性渲染。

### 5. App.vue 调用方式调整

当前 `App.vue` 在收到 `token` 时直接追加到 `msg.content`：

```javascript
if (eventName === 'token') {
  const msg = messages.value.find(m => m.id === systemMsgId)
  if (msg) msg.content += eventData
}
```

**不需要修改**。`ChatMessage.vue` 通过 `watch(props.content)` 监听到变化，内部自动调用 `processStreamingContent`。

### 6. 样式补充

已确认段落和纯文本段落之间的过渡需要视觉衔接：

```css
.message-block {
  margin-bottom: 8px;
}

.message-block:last-child {
  margin-bottom: 0;
}

/* 已确认段落的 Markdown 样式 */
.message-block h1,
.message-block h2,
.message-block h3 {
  margin: 8px 0 6px;
  font-size: 15px;
}

.message-block ul,
.message-block ol {
  padding-left: 20px;
  margin: 6px 0;
}

/* 纯文本段落 */
.message-content-plain {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}
```

### 7. 性能优化

- **只在硬边界切分**：避免在 Inline Markdown 标记中间切分，减少渲染异常。
- **增量处理**：只处理 `content` 新增的部分（`slice(lastProcessedIndex)`），避免 O(n²) 重复扫描。
- **渲染节流**：段落边界检测只在 `content` 变化时触发，不逐 token 触发（Vue 响应式批量更新已做节流）。
- **DOM 复用**：`v-for` 的 `confirmedBlocks` 使用 `key="block-${index}"`，Vue 复用已存在的 DOM 节点。
- **buffer 上限**：`MAX_STREAMING_BUFFER_LENGTH = 200`，避免极端情况下 buffer 无限累积。
- **流式完成不重渲染**：保持 DOM 结构稳定，避免 complete 时的重排和闪烁。

## 测试计划

### 单元测试（前端）

新建或扩展 `frontend/src/utils/markdownRenderer.test.js`：

| 输入 | streamingBuffer | confirmedBlocks |
|------|----------------|-----------------|
| `第一段。\n\n第二段` | `第二段` | `[{html: '<p>第一段。</p>'}]` |
| `1. 检查步骤2. 整流罩` | `2. 整流罩` | `[{html: '<ol><li>检查步骤</li></ol>'}]` |
| ```` ```bash\necho\n``` ```` | `` | `[{html: '<pre><code...>'}]` |
| `超过200字符且无边界的长文本...` | 最后部分 | 前 200 字符被硬切 |
| `检查。2. 整流罩`（列表序号句号） | `检查。2. 整流罩` | `[]`（不在 `2.` 处误切）|
| `**加粗文本** 普通文本` | 普通文本 | `[{html: '<p><strong>加粗文本</strong></p>'}]` |

### 手动验收场景

1. **标准段落切换**
   - 提问：`CTLS 的 Rotax 912 发动机日常检查有哪些步骤？`
   - 期望：先看到纯文本打字机效果，第一个段落完成后"咔"一下变成 `<p>`，后面的段落继续纯文本累积，完成后再"咔"一下变成 `<ol><li>`

2. **代码块完整闭合后才渲染**
   - 提问：给一个 bash 命令示例
   - 期望：````` ```bash `` 期间一直是纯文本，直到 ```` ``` ```` 闭合后才变成带高亮的代码块

3. **超长不换行文本的兜底**
   - 提问一个 LLM 可能输出超长段落的问题
   - 期望：超过 200 字符后自动强制分割，已确认部分渲染为 Markdown

4. **快速短回答**
   - 提问一个简单问题（2~3 句话）
   - 期望：由于内容短，可能整个回答都在 streamingBuffer 中，complete 后通过 `finalizeStreamingContent` 追加到 confirmedBlocks

5. **流式完成后无闪烁**
   - 提问任意问题
   - 期望：complete 瞬间没有 DOM 结构切换导致的闪烁，字体和段落间距保持一致

6. **与现有 Markdown 渲染的兼容性**
   - 验证 XSS 过滤仍然生效
   - 验证代码高亮仍然生效
   - 验证表格仍然正确渲染

## 风险与回滚

| 风险 | 应对措施 |
|------|---------|
| 段落边界判断不准确 | 只在明确 Markdown 块边界切分 + 200 字符兜底；代码块使用状态机判断 |
| 已固化段落无法被修正 | 流式 LLM 通常是顺序生成，极少回头修改；出现问题可刷新页面 |
| Inline Markdown 标记被切分 | 取消句末句号急切切分，避免切在 `**`、`*` 等标记中间 |
| LLM 把标题和正文写在一行 | system prompt 禁止 `#` 标题，改用 `**加粗**` |
| 组件复杂度上升 | 保留旧的 `v-html="renderedContent"` 路径作为 fallback |
| 样式过渡不自然 | 流式完成后不重渲染，保持 DOM 结构稳定 |
| 流式感变弱 | 长段落累积时间变长，但换来更稳定的渲染效果 |

**回滚方案**：
- 在 `ChatMessage.vue` 中保留 `renderedContent` 兜底路径
- 如果增量渲染有不可接受的 bug，可以在 `App.vue` 中设置一个全局配置 `useIncrementalRendering: false`，所有消息退化为 complete 后一次性渲染

## 实施步骤

1. 写 `docs/ADR/ADR-006-incremental-markdown-rendering.md` ✅
2. 写 `docs/design/FEATURE-streaming-incremental-markdown.md` ✅
3. 修改 `ChatMessage.vue`：引入双 buffer 架构（`confirmedBlocks` + `streamingBuffer`）✅
4. 新增 `frontend/src/utils/streamingMarkdownProcessor.js`：段落边界检测算法 ✅
5. 修改 `ChatMessage.vue` 模板：支持 `v-for` 渲染已确认段落 + 纯文本累积 ✅
6. **后续调整**：取消 170~200 句末句号急切切分，避免 Inline Markdown 被切分 ✅
7. **后续调整**：流式完成后不重渲染，保持 DOM 结构稳定，解决闪烁问题 ✅
8. 新增/扩展前端单元测试
9. 启动前后端，执行手动验收场景
10. 按 `CONTRIBUTING.md` 的素材级 commit 规范提交并推送

## 关键文件

- `docs/ADR/ADR-006-incremental-markdown-rendering.md`
- `docs/design/FEATURE-streaming-incremental-markdown.md`
- `frontend/src/utils/streamingMarkdownProcessor.js`
- `frontend/src/components/ChatMessage.vue`
- `frontend/src/utils/markdownRenderer.test.js`

## 历史变更记录

| 时间 | 变更 | 原因 |
|------|------|------|
| 2026-06-12 | 取消 170~200 句末句号急切切分 | 避免切在 `**加粗**` 等 Inline Markdown 标记中间，导致星号残留和样式错乱 |
| 2026-06-12 | 流式完成后不重渲染 | 避免 `isStreaming` 切换时的 DOM 结构替换，解决闪烁和样式突变问题 |

## 相关文档

- [ADR-004：LLM 流式输出](../ADR/ADR-004-streaming-output-over-blocking.md)
- [ADR-005：Markdown 输出强制策略](../ADR/ADR-005-markdown-output-enforcement.md)
- [FEATURE-markdown-rendering](FEATURE-markdown-rendering.md)
- [FEATURE-llm-markdown-formatting](FEATURE-llm-markdown-formatting.md)
