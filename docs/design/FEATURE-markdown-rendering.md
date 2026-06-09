# 功能设计：Markdown 渲染与代码高亮

## 背景

SSE 流式输出已跑通，前端当前以纯文本方式显示 LLM 返回的内容。但 LLM 生成的回答包含大量格式化文本：

- 标题结构（`## 检查步骤`）
- 有序/无序列表（`1. 检查滑油量`、`- 注意事项`）
- 加粗强调（`**关键参数**`）
- 行内代码（`` `RPM` ``）和代码块（```java ... ```）
- 引用块（`> 注意：...`）
- 偶尔出现的表格（`| 参数 | 标准值 |`）

如果不做 Markdown 渲染，用户看到的是一团原始标记符号，可读性差；如果在流式过程中直接渲染不完整的 Markdown，又可能出现未闭合标签导致样式崩塌。

本设计要解决：
1. **如何在 SSE 流式输出场景下，安全、稳定地渲染 Markdown**
2. **如何为代码块添加语法高亮**
3. **如何防止 LLM 输出中的 HTML 标签带来 XSS 风险**

---

## 目标

1. 流式接收期间：前端以纯文本形式累积显示，**不实时渲染 Markdown**
2. 流式完成后：一次性将完整文本渲染为格式化的 HTML，包括代码块语法高亮
3. 样式稳定：不出现因未闭合代码块/表格/列表导致的页面样式崩坏
4. 安全可靠：过滤 LLM 输出中的危险 HTML，防止 XSS
5. 体积可控：避免引入过大的依赖包，影响首屏加载

---

## 方案对比

### Markdown 渲染库

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| **marked.js** | 轻量（~30KB），纯 JS 无依赖，API 简单，社区最活跃 | 插件生态不如 markdown-it | ✅ **采纳**。路演场景不需要复杂扩展 |
| markdown-it | 插件丰富，可扩展性强 | 体积较大，配置复杂，过度设计 | ❌ 放弃。增加不必要的复杂度 |
| vue-markdown-render | Vue 组件封装，开箱即用 | 多一层抽象，出问题难调，依赖 marked 或 markdown-it 底层 | ❌ 放弃。直接调用 marked 更可控 |

### 增量渲染策略

| 策略 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| **A：纯文本累积 + complete 后渲染** | 绝对稳定，不会有未闭合标签问题；实现简单 | 流式期间看不到 Markdown 效果 | ✅ **采纳**。路演体验已足够好 |
| B：智能缓冲渲染 | 流式期间部分内容是格式化样式 | 判断"完整 Markdown 单元"的逻辑复杂且容易出错；代码块/表格/嵌套列表的边界很难判定 | ❌ 放弃。投入产出比低 |
| C：虚拟 DOM 增量更新 | 理论上最完美 | 实现复杂，容易出现闪烁、光标跳动 | ❌ 放弃。路演前不应冒险 |

### 代码高亮库

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| **highlight.js（按需加载）** | 功能完整，主题丰富，与 marked 集成成熟 | 全语言包体积大 | ✅ **采纳**。只注册机务场景常见语言（java、bash、json、plaintext、xml） |
| PrismJS | 轻量，主题多样 | 社区活跃度低于 highlight.js，Vue 生态集成文档较少 | ❌ 放弃 |
| 不做高亮 | 零依赖 | 代码块可读性差，路演展示效果打折 | ❌ 放弃。用户明确要求高亮作为 P0 |

---

## 详细设计

### 1. 依赖引入

**marked**（Markdown 渲染）：
```bash
cd frontend && npm install marked@^15.0.0
```

**highlight.js**（代码高亮，按需引入语言）：
```bash
cd frontend && npm install highlight.js@^11.11.0
```

**DOMPurify**（XSS 防护）：
```bash
cd frontend && npm install dompurify@^3.2.0
```

> 版本选择说明：marked v15 是当前稳定版，highlight.js v11 是长期维护版本，DOMPurify v3.2 是当前活跃维护版本。

### 2. Markdown + 高亮工具模块

新建文件：`frontend/src/utils/markdownRenderer.js`

```javascript
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

// 按需注册机务场景常见语言，控制包体积
hljs.registerLanguage('java', java)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('plaintext', plaintext)
hljs.registerLanguage('python', python)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('sql', sql)

// 配置 marked：关闭危险特性，启用代码高亮
marked.use({
  renderer: {
    // 代码块高亮
    code(code, language) {
      const validLang = language && hljs.getLanguage(language) ? language : 'plaintext'
      const highlighted = hljs.highlight(code, { language: validLang }).value
      return `<pre><code class="hljs language-${validLang}">${highlighted}</code></pre>`
    }
  },
  gfm: true,           // GitHub Flavored Markdown
  breaks: true,        // 单换行转 <br>
  headerIds: false,    // 不生成 h1/h2 id，防止与页面元素冲突
  mangle: false        // 不转义邮件地址
})

/**
 * 渲染 Markdown 文本为安全的 HTML 字符串
 * @param {string} raw - 原始 Markdown 文本
 * @returns {string} - 渲染后的 HTML
 */
export function renderMarkdown(raw) {
  if (!raw) return ''
  const html = marked.parse(raw, { async: false })
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      'p', 'br', 'hr',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'ul', 'ol', 'li',
      'strong', 'b', 'em', 'i', 'del', 's',
      'a', 'code', 'pre', 'blockquote',
      'table', 'thead', 'tbody', 'tr', 'th', 'td'
    ],
    ALLOWED_ATTR: ['href', 'target', 'rel', 'class'],
    ALLOW_DATA_ATTR: false
  })
}
```

> 说明：引入 DOMPurify 作为 XSS 防护。它比手写 allowlist 更成熟，能覆盖更多边缘情况（如 SVG onload、data URI、JavaScript 伪协议等），同时配置简单明确。

### 3. 组件改造：ChatMessage.vue

当前 `ChatMessage.vue` 只接收 `role` 和 `content`，需要增加两个能力：
1. 知道当前消息是否已完成流式接收
2. 根据完成状态选择纯文本显示或 Markdown 渲染

#### Props 调整

```javascript
const props = defineProps({
  role: { type: String, required: true },
  content: { type: String, required: true },
  isStreaming: { type: Boolean, default: false }  // true = 仍在流式接收中
})
```

#### 模板调整

```vue
<template>
  <div :class="['message-row', roleClass]">
    <div class="message-bubble">
      <!-- 流式中：纯文本显示 -->
      <div v-if="isStreaming" class="message-content message-content-plain">
        {{ content }}
      </div>
      <!-- 流式完成：Markdown 渲染 -->
      <div
        v-else
        class="message-content message-content-markdown"
        v-html="renderedContent"
      />
    </div>
  </div>
</template>
```

#### 脚本调整

```javascript
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdownRenderer.js'

const props = defineProps({ ... })

const roleClass = computed(() => props.role === 'user' ? 'user' : 'system')

const renderedContent = computed(() => {
  return renderMarkdown(props.content)
})
```

### 4. App.vue 调用方式调整

当前 `App.vue` 在流式接收时会实时更新消息 `content`，但 `ChatMessage` 不知道是"流式中"还是"已完成"。

调整方案：
1. 消息对象增加 `isStreaming` 字段
2. 开始流式时推入 `isStreaming: true` 的消息
3. 收到 `complete` 事件后将 `isStreaming` 设为 `false`
4. 用户消息永远是 `isStreaming: false`（用户输入不需要 Markdown 渲染）

```javascript
// 开始流式时
messages.value.push({
  id: systemMsgId,
  role: 'system',
  content: '',
  isStreaming: true
})

// 收到 complete 事件时
const msg = messages.value.find(m => m.id === systemMsgId)
if (msg) msg.isStreaming = false
```

### 5. 样式补充

在 `ChatMessage.vue` 中增加 Markdown 渲染后的基础样式：

```css
.message-content-markdown h1,
.message-content-markdown h2,
.message-content-markdown h3 {
  margin: 12px 0 8px;
  font-size: 15px;
}

.message-content-markdown ul,
.message-content-markdown ol {
  padding-left: 20px;
  margin: 8px 0;
}

.message-content-markdown pre {
  background: #f5f5f5;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
}

.message-content-markdown code {
  font-family: 'Consolas', monospace;
  font-size: 13px;
}

.message-content-markdown p {
  margin: 8px 0;
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
```

> 为了代码高亮的主题效果，需要在 `main.js` 中引入 highlight.js 的 CSS：
> ```javascript
> import 'highlight.js/styles/github.css'
> ```

---

## 测试计划

### 单元测试（前端，可选）

验证 `renderMarkdown()` 函数：

| 输入 | 期望输出 |
|------|---------|
| `**加粗**` | `<p><strong>加粗</strong></p>` |
| `## 标题` | `<h2>标题</h2>` |
| `` `code` `` | `<p><code>code</code></p>` |
| `\| a \| b \| \n\| --- \| --- \|` | 渲染为 `<table>` |
| `<script>alert(1)</script>` | 输出为空或纯文本（script 标签被过滤） |

### 手动验收场景

1. **正常 Markdown 回答**
   - 提问：`CTLS 的 Rotax 912 发动机日常检查有哪些步骤？`
   - 期望：complete 后看到带标题、列表、加粗的格式化文本

2. **包含代码块**
   - 提问：`如何检查发动机滑油系统？`
   - 期望：如果回答包含 ```bash 命令，complete 后代码块有背景色和语法高亮

3. **流式过程中样式稳定**
   - 提问任意问题
   - 期望：流式过程中不会出现"半拉代码块"导致页面其余内容样式错乱

4. **XSS 防护**
   - 在 prompt 中诱导 LLM 输出 `<script>alert(1)</script>`
   - 期望：页面不会执行脚本，渲染为纯文本或消失

5. **首屏加载**
   - 打开聊天页
   - 期望：额外增加的 marked + highlight.js 按需语言包不会导致明显加载延迟

---

## 风险与回滚

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| marked.js 渲染结果与预期不符 | 中 | 保留 `message-content-plain` 显示模式作为兜底，配置开关可一键切回纯文本 |
| highlight.js 按需语言不够用 | 低 | 已覆盖 java/bash/json/xml/plaintext/python/yaml/sql，未来需要新语言时只加一行 `registerLanguage` |
| XSS 过滤误伤合法内容 | 低 | DOMPurify 配置明确的 allowlist，非 Markdown 常用标签会被过滤，不影响核心阅读 |
| 包体积增加导致首屏变慢 | 低 | 按需引入语言包，总增量预计在 60~80KB gzip 以内 |

**回滚方案**：如果路演前发现 Markdown 渲染有不可接受的 bug，可以在 `ChatMessage.vue` 中把 `v-html="renderedContent"` 改回 `{{ content }}`，10 分钟内回滚到纯文本模式。

---

## 相关文档

- [ADR-004：LLM 流式输出架构决策](../ADR/ADR-004-streaming-output-over-blocking.md)
- [API.md：SSE 接口规范](../API.md)
- [ARCHITECTURE.md：请求生命周期与模块职责](../ARCHITECTURE.md)
