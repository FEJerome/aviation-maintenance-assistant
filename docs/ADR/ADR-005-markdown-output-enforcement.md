# ADR-005：强制 LLM 输出标准 Markdown 格式

- **状态**：Accepted
- **日期**：2026-06-09
- **决策者**：马昊宇（项目作者）

---

## 背景

前端 Markdown 渲染与代码高亮功能已经完成（marked.js v15 + highlight.js + DOMPurify）。但在实际测试中发现，LLM（DeepSeek deepseek-chat）输出的 Markdown 并不总是符合标准语法，例如：

- `#AMM` —— `#` 后缺少空格，marked.js 不会识别为 `<h1>`
- ```` ```bash#!/bin/bash ```` —— 代码块语言标识后缺少换行，highlight.js 无法识别语言
- 标题和正文挤在同一行：`## 检查步骤1. 打开盖子`，整行被解析为标题内容
- 列表项符号与内容之间偶有没有空格的情况

路演需要稳定、美观的格式化输出。如果仅依赖前端渲染器，这些不合法的 Markdown 会直接影响展示效果。因此需要在系统层面决定：如何确保 LLM 生成符合规范的 Markdown。

## 考虑的选项

### 选项 A：前端兜底正则修正

**实现方式**：
- 在 `markdownRenderer.js` 中渲染前增加 `sanitizeMarkdown()`
- 将 `#标题` 修正为 `# 标题`
- 将 ```` ```bash代码 ```` 修正为 ```` ```bash\n代码 ````

**优势**：
- 实现成本低，30 分钟内可完成
- 对已有错误输出 100% 兜底
- 不需要调整模型调用链路

**劣势**：
- 治标不治本，LLM 可能长期依赖兜底
- 正则规则难以覆盖所有 Markdown 语法边界情况
- 如果规则写得太宽，可能误伤合法内容

### 选项 B：System Prompt + Few-shot 示例

**实现方式**：
- 在 `Assistant.java` 的 `@SystemMessage` 中追加 Markdown 格式规则
- 追加 1~2 组「用户问题 → 正确 Markdown 格式」的 few-shot 示例
- 将 `temperature` 从 0.7 降至 0.2，提高指令遵循度
- 实测发现 DeepSeek 难以稳定做到"标题独占一行"，因此直接**禁止 # 标题**，改用段落 + 列表组织内容，降低格式复杂度

**优势**：
- 从源头规范输出，架构干净
- DeepSeek 模型对 few-shot 示例的模仿能力强
- 不增加前后端协议复杂度

**劣势**：
- 依赖模型的指令遵循能力，不能保证 100%
- few-shot 示例会增加每次请求的 token 消耗（约 200~400 token）
- 如果示例设计不当，可能让模型过度结构化简单回答

### 选项 C：结构化输出（JSON Schema）

**实现方式**：
- 要求 LLM 以 JSON 格式返回，字段预先定义，如 `title`、`steps`、`codeBlocks`
- 前端按 JSON 结构渲染，不再依赖 Markdown 解析

**优势**：
- 最严格，格式 100% 可控
- 天然支持多段内容混排

**劣势**：
- 破坏流式输出体验（JSON 必须等完整输出后才能解析）
- 需要重新定义前后端协议，增加复杂度
- 不适合路演前短期交付

## 决策

**采用选项 B 为主、选项 A 为辅的双保险策略。**

核心判断依据：路演前需要最小侵入、最快见效的方案。System Prompt + Few-shot 能在不改变架构的情况下大幅提升格式规范性；前端兜底修正作为第二层防线，覆盖模型偶尔仍犯的格式错误。结构化输出虽然最严格，但会牺牲流式体验且工程量大，路演前不引入。

同时，将 `temperature` 从 0.7 调整至 0.2，以提升格式遵循度和维修知识回答的确定性；并因模型难以稳定做到"标题独占一行"，改为禁止标题，章节用 `**章节名**` 加粗表示。

## 后果

### 积极影响

- LLM 输出 Markdown 格式规范性显著提升，路演展示效果更稳定
- 前端渲染器工作量减少，不再需要处理大量畸形 Markdown
- 双保险策略确保即使模型偶尔出错，用户侧仍能看到正确渲染
- temperature 降低后，维修知识回答的幻觉风险也有所下降

### 消极影响 / trade-off

- prompt 长度增加，单次请求成本略有上升
- 对非常简短的回答，模型可能也会尝试加入 Markdown 结构，显得冗余
- 前端正则兜底需要维护，规则扩展时需谨慎避免误伤

### 相关决策

- [ADR-004](ADR-004-streaming-output-over-blocking.md)：流式输出架构，本决策需与之兼容
- [FEATURE-markdown-rendering](../design/FEATURE-markdown-rendering.md)：前端 Markdown 渲染实现

## 相关链接

- [LangChain4j AiService SystemMessage 文档](https://docs.langchain4j.dev/tutorials/ai-services/#system-message)
- [DeepSeek API 参数说明](https://api-docs.deepseek.com/)
- [FEATURE-llm-markdown-formatting.md](../design/FEATURE-llm-markdown-formatting.md)
