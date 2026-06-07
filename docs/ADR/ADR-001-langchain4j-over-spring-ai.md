# ADR-001：AI 框架选型 — LangChain4j 而非 Spring AI

- **状态**：Accepted
- **日期**：2026-06-02
- **决策者**：马昊宇（项目作者）

---

## 背景

项目需要接入大语言模型（LLM）实现 RAG 问答和 Agent 能力。Java 生态中主流的 AI 应用框架有两个选择：

1. **Spring AI** — Spring 官方推出的 AI 集成框架
2. **LangChain4j** — Java 社区最流行的 LLM 应用框架，对标 Python 的 LangChain

本决策需要在项目早期（技术栈初始化阶段）确定，直接影响后续所有 AI 相关的编码模式和依赖管理。

---

## 考虑的选项

### 选项 A：Spring AI

**优势**：
- Spring 官方出品，与 Spring Boot 生态深度整合，配置风格一致
- 长期看可能获得 Spring 团队的优先资源投入
- 对于纯 Spring 开发者，学习曲线相对平缓

**劣势**：
- **成熟度不足**：截至 2026-06，Spring AI 仍处于快速迭代期，核心 API 不稳定，1.0 尚未发布。LangChain4j 已发布 1.x 稳定版，API 契约更可靠。
- **模型支持滞后**：Spring AI 对国产模型（如 DeepSeek）和社区模型的支持速度明显慢于 LangChain4j。DeepSeek 在 Spring AI 中需要额外适配，而 LangChain4j 通过 OpenAI 兼容模式即插即用。
- **RAG 链路不完整**：Spring AI 的 RAG、Embedding、Vector Store 抽象层相对薄弱，很多功能需要自行拼接；LangChain4j 提供了从 Document Parser → Text Splitter → Embedding → Vector Store → Retrieval Augmentor 的完整链路。
- **社区规模**：LangChain4j GitHub Stars 数倍于 Spring AI，示例项目、文档、StackOverflow 问答更丰富。

### 选项 B：LangChain4j

**优势**：
- **成熟稳定**：1.14.1 为稳定版本，BOM（Bill of Materials）统一管理所有模块版本，无依赖冲突。
- **功能完备**：内置 RAG、AI Services（声明式 @AiService）、Tools（@Tool）、Memory、Streaming 等完整能力。
- **模型兼容广**：通过 `langchain4j-open-ai` 模块，DeepSeek、OpenAI、Azure OpenAI 等 OpenAI 兼容 API 即插即用；同时支持 Google Gemini、Anthropic Claude 等。
- **Spring Boot 深度集成**：提供 `langchain4j-spring-boot-starter`，配置方式与 Spring Boot 完全一致（`application.yaml` 中直接配 `langchain4j.open-ai.chat-model.api-key`）。
- **嵌入模型本地推理**：通过 `langchain4j-embeddings-all-minilm-l6-v2` 实现纯本地 ONNX 嵌入，无需外部 Embedding API。

**劣势**：
- 非 Spring 官方项目，长期生态绑定风险略高于 Spring AI（但开源协议 Apache 2.0 无虞）。
- 部分 Spring 开发者可能需要适应 LangChain4j 的抽象概念（如 `ChatMemory`、`ContentRetriever`）。

---

## 决策

**选用 LangChain4j 1.14.1 作为项目 AI 框架。**

Spring AI 虽然背靠 Spring 官方，但当前成熟度不足以支撑两周冲刺的稳定性要求。LangChain4j 在 Java AI 领域的社区活跃度、功能完备度和版本稳定性上均领先，且与 Spring Boot 3.x 的集成已非常成熟，不存在生态割裂问题。

---

## 后果

### 积极影响

- 两周内即可完成 RAG 问答和 Agent 验证层的全功能开发，无需等待框架补丁。
- DeepSeek 接入零成本：只需配置 `base-url` 和 `api-key`，`ChatLanguageModel` 接口调用方式与 OpenAI 完全一致。
- 本地 ONNX 嵌入模型直接通过 Maven 依赖引入，部署链路无外部 HTTP 依赖。

### 消极影响 /  trade-off

- 若未来 Spring AI 成熟并超越 LangChain4j，迁移成本中等（主要涉及配置和少量 API 调用调整）。
- 面试中需准备回答"为什么不用 Spring 官方的 AI 框架"——已在本 ADR 中充分准备论据。

### 相关决策

- [ADR-003](ADR-003-deepseek-over-dashscope.md)：LLM 选型 DeepSeek，通过 `langchain4j-open-ai` 模块接入。
- [ADR-002](ADR-002-chromadb-over-others.md)：向量数据库选型 ChromaDB，通过 `langchain4j-chroma` 模块接入。

---

## 相关链接

- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
- [LangChain4j Spring Boot 集成文档](https://docs.langchain4j.dev/tutorials/spring-boot-integration/)
- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [pom.xml 依赖声明](../../backend/pom.xml)
