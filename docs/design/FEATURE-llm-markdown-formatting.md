# 功能设计：LLM 标准 Markdown 输出格式化

## 背景

前端 Markdown 渲染与代码高亮已完成，但 LLM 实际输出的 Markdown 不够规范，导致渲染异常。例如：

- `#AMM` → 标题解析失败
- ```` ```bash#!/bin/bash ```` → 代码块语言识别失败
- 标题和正文挤在同一行：`## 检查步骤1. 打开盖子...`，整行被解析为标题内容
- 列表项、引用块前缺少换行，导致格式扁平化

路演需要稳定、美观的输出格式。本设计通过 **后端 Prompt Engineering + 前端兜底修正** 双保险，强制 LLM 输出标准 Markdown。

## 目标

1. LLM 输出 90% 以上符合标准 Markdown 语法
2. 标题、列表、代码块、表格、加粗等核心元素格式统一
3. 不破坏现有 `@AiService` 架构和流式输出链路
4. 通过单元测试验证 prompt 内容和前端渲染行为

## 方案对比

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| 前端兜底正则修正 | 成本低，可覆盖已发现的问题 | 治标不治本，复杂边界可能误伤 | ✅ 第二层防线 |
| System Prompt + 正/反示例 | 从源头规范，架构干净 | 依赖模型指令遵循能力 | ✅ 第一层防线 |
| 结构化输出（JSON） | 最严格 | 破坏流式体验，复杂度高 | ❌ 路演前不引入 |

## 详细设计

### 1. Assistant.java 重构

当前 `chat()` 和 `chatStream()` 方法上各有一份完全相同的 `@SystemMessage`，需要重构。

#### 1.1 提取 SYSTEM_PROMPT 常量

```java
public static final String SYSTEM_PROMPT = "你是一个专业的航空维修知识助手，熟悉 FAA 法规、AMM 手册体系和各类机型维护程序。"
        + "回答问题时，你会收到与问题相关的维修手册片段，请严格基于这些片段回答。"
        + "如果片段中未包含答案，请明确说明\"未在手册中找到相关内容\"。"
        + "如果涉及关键系统（如发动机、飞控），请提醒用户务必以官方手册为准。"
        + "回答时请标注引用来源（如手册章节号），保持专业、规范的语气。"
        + "\n\n【输出格式：严格遵循，否则前端无法正确渲染】"
        + "\n1. 不要使用 #、##、### 作为标题。如需表示章节标题，使用 **章节名** 加粗，并前后空行。"
        + "\n2. 回答分为 2-5 个自然段落，段落之间用一个空行分隔。"
        + "\n3. 如果有步骤，使用列表。每个列表项必须独占一行："
        + "\n   1. 打开盖子"
        + "\n   2. 检查油量"
        + "\n   禁止写成：1. 打开盖子 2. 检查油量"
        + "\n4. 加粗只用于强调关键词，格式：**关键词**。禁止嵌套，禁止用加粗包裹整个段落。"
        + "\n5. 代码块：``` 独占一行写语言标识，下一行写代码。"
        + "\n6. 每个段落、每个列表项、每个代码块都必须独占一行或多行，不要把它们连成一行。";
```

#### 1.2 两个方法复用常量

```java
@SystemMessage(SYSTEM_PROMPT)
String chat(@UserMessage String message);

@SystemMessage(SYSTEM_PROMPT)
TokenStream chatStream(@UserMessage String message);
```

### 2. Temperature 调整

将 `application.yaml` 中 `langchain4j.open-ai.chat-model.temperature` 从 `0.7` 改为 `0.2`。

理由：
- 显著降低随机性，提高 Markdown 格式规则遵循度
- 维修知识问答本身不应发散
- 通过正/反示例让模型明确模仿目标格式

### 3. 前端兜底修正

在 `frontend/src/utils/markdownRenderer.js` 中新增 `sanitizeMarkdown()`：

```javascript
function sanitizeMarkdown(raw) {
  if (!raw) return ''
  return raw
    // #标题 → # 标题（原子组模拟避免回溯误匹配）
    .replace(/^(?=(#{1,6}))\1(?=[^ #\n])/gm, '$1 ')
    // ```bash代码 → ```bash\n代码（原子组模拟避免语言标识被回溯截断）
    .replace(/^(?=(```[a-zA-Z0-9+-]+))\1(?=[^ \n])/gm, '$1\n')
    // 修复数字列表项：把 "1.检查 2.核实" 改成 "1. 检查\n2. 核实"
    .replace(/([^\n\d])(\d+\.)([^0-9\s]|$)/g, '$1\n\n$2 $3')
    // 数字列表项/无序列表项前如果没有换行，加空行，让 marked 识别为列表
    .replace(/([^\n])(?=\d+\.\s)/g, '$1\n\n')
    .replace(/([^\n])(?=\n- )/g, '$1\n')
}
```

`renderMarkdown()` 调用链：

```javascript
export function renderMarkdown(raw) {
  if (!raw) return ''
  const sanitized = sanitizeMarkdown(raw)
  const html = marked.parse(sanitized, { async: false })
  return DOMPurify.sanitize(html, { ... })
}
```

兜底规则说明：
- 标题规则：只修正行首 `#` 后缺少空格的情况（如 `#AMM` → `# AMM`）。对于"标题和正文挤在一行"的复杂情况，主要由后端 prompt 约束，兜底不过度干预，避免误伤。
- 代码块规则：修正语言标识后紧跟代码的情况（如 ```` ```bash#!/bin/bash ```` → ```` ```bash\n#!/bin/bash ````）。
- 数字列表规则：修正 LLM 把列表项连成一行的情况（如 `步骤1.检查 2.核实` → `步骤\n\n1. 检查\n2. 核实`），使 marked.js 能正确渲染为有序列表。

## 测试计划

### 后端单元测试

新建 `backend/src/test/java/.../chat/service/AssistantSystemPromptTest.java`

验证 `Assistant.SYSTEM_PROMPT` 包含：
- `不要使用 #、##、### 作为标题`
- `段落之间用一个空行分隔`
- `每个列表项必须独占一行`
- `禁止写成：1. 打开盖子 2. 检查油量`
- ```` ``` 独占一行写语言标识 ````
- `禁止用加粗包裹整个段落`

### 前端单元测试

新建 `frontend/src/utils/markdownRenderer.test.js`（使用 Vitest）

| 输入 | 期望 |
|------|------|
| `# AMM` | 渲染为 `<h1>` |
| `#AMM` | 经过兜底修正后渲染为 `<h1>` |
| ```` ```bash\necho hi\n``` ```` | 代码块含 `language-bash` 类 |
| ```` ```bashecho hi\n``` ```` | 经过兜底修正后含 `language-bash` 类 |
| `<script>alert(1)</script>` | 被 DOMPurify 过滤 |
| `\|a\|b\|\n\|---\|---\|` | 渲染为 `<table>` |
| `步骤1.检查 2.核实` | 经过兜底修正后渲染为有序列表 `<ol><li>` |
| `1.检查2.核实` | 经过兜底修正后渲染为两个列表项 |

### 手动验收

1. 问："CTLS 的 Rotax 912 发动机日常检查有哪些步骤？" → 看到多个独立段落 + 列表 + 关键词加粗
2. 问："如何检查发动机滑油系统？" → 代码块有 bash 高亮
3. 问："B737-800 的 CFM56-7B 发动机启动程序" → 结构清晰，可能含表格
4. 问无文档覆盖的问题 → 仍正确返回 "未在手册中找到相关内容"

## 风险与回滚

| 风险 | 应对措施 |
|------|---------|
| 模型仍不遵循格式 | 前端兜底覆盖最常见错误；若仍不理想，可进一步降低 temperature 或精简 prompt |
| prompt 过长分散注意力 | 当前 prompt 已精简为 6 条规则 |
| temperature 0.2 导致回答呆板 | 路演后可回调至 0.4~0.7 |
| 前端正则误伤 | 只处理行首标题和代码块两种最明确的情况 |

**回滚**：
- prompt 回滚：还原 `Assistant.java` 字符串
- 参数回滚：`application.yaml` 改回 `temperature: 0.7`
- 前端回滚：删除 `sanitizeMarkdown()` 函数体，直接返回 raw

## 实施步骤

1. 写 `docs/ADR/ADR-005-markdown-output-enforcement.md`
2. 写 `docs/design/FEATURE-llm-markdown-formatting.md`
3. 重构 `Assistant.java`：提取 `SYSTEM_PROMPT` 常量，追加 Markdown 规则
4. 修改 `application.yaml`：`temperature: 0.2`
5. 修改 `frontend/src/utils/markdownRenderer.js`：增加 `sanitizeMarkdown()` 并在 `renderMarkdown()` 中调用
6. 新增后端单元测试 `AssistantSystemPromptTest.java`
7. 新增前端单元测试 `markdownRenderer.test.js`
8. 启动前后端，执行 4 个手动验收场景
9. 按 `CONTRIBUTING.md` 的素材级 commit 规范提交并推送

## 关键文件

- `docs/ADR/ADR-005-markdown-output-enforcement.md`（新建）
- `docs/design/FEATURE-llm-markdown-formatting.md`（新建）
- `backend/src/main/java/cn/pandazi/aviation_maintenance_assistant/chat/service/Assistant.java`
- `backend/src/main/resources/application.yaml`
- `backend/src/test/java/cn/pandazi/aviation_maintenance_assistant/chat/service/AssistantSystemPromptTest.java`（新建）
- `frontend/src/utils/markdownRenderer.js`
- `frontend/src/utils/markdownRenderer.test.js`（新建）
- `frontend/package.json`
- `frontend/vite.config.js`

## 相关文档

- [ADR-005：Markdown 输出强制策略](../ADR/ADR-005-markdown-output-enforcement.md)
- [ADR-004：LLM 流式输出](../ADR/ADR-004-streaming-output-over-blocking.md)
- [FEATURE-markdown-rendering](FEATURE-markdown-rendering.md)
- [CONTRIBUTING.md](../../CONTRIBUTING.md)
