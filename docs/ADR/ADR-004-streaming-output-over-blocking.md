# ADR-004：LLM 流式输出 — TokenStream 而非阻塞式 String

- **状态**：Accepted
- **日期**：2026-06-07
- **决策者**：马昊宇（项目作者）

---

## 背景

当前系统的 LLM 调用采用阻塞式非流式模式：

1. 用户发送问题 → 后端等待 DeepSeek 生成完整回答 → 一次性返回 JSON
2. 回答可能长达数百甚至上千字，用户需要等待 3~10 秒才能看到任何内容
3. 体验上类似"传统搜索"，而非"对话式 AI"

为了提升交互体验，需要引入**流式输出（Streaming）**：LLM 每生成一个 token，后端立即通过 SSE 推送给前端，前端逐字渲染，让用户感知到"正在思考"。

本决策需要确定：在 LangChain4j 生态中，以何种方式实现流式输出，才能**既不破坏现有架构分层，又能获得流式体验**。

---

## 考虑的选项

### 选项 A：直接操作底层 `StreamingChatLanguageModel`

**实现方式**：
- 绕过 LangChain4j 的 `@AiService` 声明式接口
- 在 `MaintenanceChatService` 中直接注入 `StreamingChatLanguageModel`
- 手动拼接 System Prompt、手动调用 `RetrievalAugmentor.augment()`、手动管理 Conversation Memory
- 在 `StreamingResponseHandler.onNext()` 里将 token 推入 `SseEmitter`

**优势**：
- 完全控制 LLM 调用链的每一步，没有框架黑盒
- 可以精细控制每个 token 的处理逻辑（如拦截、改写、埋点）

**劣势**：
- **破坏架构分层**：`MaintenanceChatService`（业务编排层）需要侵入到 Prompt 构建和 RAG 检索的细节
- 原来由 `@AiService` 自动处理的 `@SystemMessage`、`RetrievalAugmentor` 自动注入、查询翻译等，全部需要手动实现
- 代码冗余：LangChain4j 已经封装好的能力，现在要重复实现
- 维护成本高：未来升级 LangChain4j 版本，手动拼接的逻辑可能需要同步调整

### 选项 B：保留 `@AiService`，方法返回 `TokenStream`

**实现方式**：
- `Assistant` 接口的方法返回值从 `String` 改为 `TokenStream`
- LangChain4j 的 `AiServices` 代理在检测到 `TokenStream` 返回类型时，自动内部调用 `StreamingChatLanguageModel`
- `MaintenanceChatService` 调用 `assistant.chat()` 获得 `TokenStream` 对象
- 注册 `.onNext()`、`.onComplete()`、`.onError()` 回调，在回调中将 token 推入 `SseEmitter`
- 调用 `.start()` 触发实际 HTTP 请求

**优势**：
- **架构分层保持清晰**：`MaintenanceChatService` 仍只负责"什么时候调、调什么"，不关心"怎么调、流式还是非流式"
- **所有 `@AiService` 自动能力保留**：`@SystemMessage` 自动注入、`RetrievalAugmentor` 自动做 RAG 检索和拼接、`TranslationQueryTransformer` 自动翻译查询
- **代码侵入性最小**：只改接口返回类型和调用处的回调注册
- **符合 LangChain4j 设计意图**：`TokenStream` 就是框架为"声明式流式"提供的官方抽象

**劣势**：
- 回调在 LangChain4j 内部线程执行，需要确认 `SseEmitter.send()` 的线程安全性（已确认：Spring 的 `SseEmitter` 使用 `ConcurrentLinkedQueue`，`send()` 是线程安全的）
- 对 `TokenStream` 生命周期理解不到位可能导致资源泄漏（需确保 `onComplete` / `onError` 中关闭 `SseEmitter`）
- 相比方式一，少了"完全控制每一步"的能力（但对于本项目当前需求，这是过度控制）

---

## 决策

**选用方式二：保留 `@AiService` 声明式接口，方法返回 `TokenStream`，外层通过 SSE 推送至前端。**

核心判断依据：**架构一致性优先于底层控制度**。

本项目的核心设计原则是"确定性代码做硬性约束，概率模型做开放生成"。`MaintenanceChatService` 负责路由、验证、会话（确定性），`Assistant` 负责 LLM 调用（概率）。方式二保持了这层边界；方式一虽然"底层"，但会让业务编排层侵入到 prompt 构建和 RAG 检索的细节，破坏分层。

---

## 后果

### 积极影响

- **用户体验显著提升**：首字延迟从秒级降至毫秒级，用户立即看到回复开始生成
- **架构分层保持完整**：业务层不感知流式实现细节，AI 层的变更（换模型、换参数）不影响业务层
- **RAG 和 SystemMessage 零改动**：LangChain4j 自动在流式调用前完成检索和 prompt 拼接
- **前后端协议标准化**：SSE 是 Web 流式的标准协议，浏览器原生支持 `EventSource`，前端接入成本低

### 消极影响 / trade-off

- **API 层协议变更**：`POST /chat` 的响应从 `application/json` 变为 `text/event-stream`，前端需要适配（原一次性渲染改为逐字追加）
- **错误处理逻辑变化**：非流式模式下，HTTP 5xx 直接返回 JSON 错误；流式模式下，错误可能在 SSE 流传输中途发生，需要设计统一的 SSE 错误事件格式
- **调试复杂度上升**：流式链路涉及异步回调、多线程、SSE 连接生命周期，排查问题比阻塞式调用更复杂
- **测试方式变化**：单元测试需要模拟 `TokenStream` 的异步回调；集成测试需要验证 SSE 事件的顺序和内容

### 相关决策

- [ADR-001](ADR-001-langchain4j-over-spring-ai.md)：LangChain4j 框架选型，提供 `TokenStream` 抽象的基础
- [ADR-003](ADR-003-deepseek-over-dashscope.md)：DeepSeek 支持 OpenAI 兼容的 `stream: true` 协议，是流式输出的前提

---

## 相关链接

- [LangChain4j Streaming 文档](https://docs.langchain4j.dev/tutorials/response-streaming/)
- [Spring SseEmitter 文档](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html)
- [API 文档：流式接口规范](../API.md)
- [ARCHITECTURE.md：请求生命周期章节](../ARCHITECTURE.md)
