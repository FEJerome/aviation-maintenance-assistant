# ADR-003：大语言模型选型 — DeepSeek 而非通义千问 / ChatGPT

- **状态**：Accepted
- **日期**：2026-06-02
- **决策者**：马昊宇（项目作者）

---

## 背景

项目需要选择一个大语言模型（LLM）作为 RAG 问答的生成引擎。核心要求是：

1. **英文能力强**：机务维修手册（FAA AC、AMM、发动机维护手册）均为英文，模型需要准确理解专业术语并生成英文语境下的回答
2. **国内访问稳定**：求职演示需要稳定可用，不能频繁出现 API 不可达
3. **成本可控**：个人开发者承担 API 费用，需要性价比高的方案
4. **Java 生态兼容**：与 LangChain4j 的集成成本要低

候选方案：

1. **通义千问（DashScope）**
2. **DeepSeek**
3. **OpenAI GPT-4 / GPT-3.5**
4. **本地开源模型（如 Qwen、Llama）**

---

## 考虑的选项

### 选项 A：通义千问（DashScope）

**优势**：
- 阿里云出品，国内访问速度快
- 中文能力顶尖，适合中文问答场景
- 与 Java 生态有官方 SDK

**劣势**：
- **英文能力弱于 DeepSeek**：机务手册为英文，通义千问在航空维修专业术语的理解和生成上表现不如 DeepSeek
- **LangChain4j 兼容性问题**：`langchain4j-dashscope` 已从核心 BOM 迁移到 `langchain4j-community` 仓库。使用 BOM 统一管理时，`dependency:tree` 会直接报 `version missing` 错误，需要手动指定版本号，破坏了 BOM 的便利性
- **额外复杂度**：需要单独申请 DashScope API Key，与 DeepSeek 不能复用
- **简历叙事热度**：2026 年 DeepSeek 在国内技术社区和招聘市场的热度明显高于通义千问

### 选项 B：DeepSeek

**优势**：
- **英文能力突出**：DeepSeek 的训练数据中英文比例高，对 FAA 手册、AMM 等专业英文文档的理解准确
- **OpenAI 兼容 API**：`base-url` 指向 `https://api.deepseek.com/v1`，LangChain4j 的 `langchain4j-open-ai` 模块即插即用，**代码层零改动**
- **成本极低**：DeepSeek API 价格约为 GPT-4 的 1/10，个人开发者负担极小
- **国内稳定**：服务器位于国内，无需代理即可稳定访问
- **简历热度**：2026 年 DeepSeek 是 AI 领域的高频关键词，面试官认知度高

**劣势**：
- 暂无官方 Embedding API（但本项目通过本地 ONNX 模型解决，不受影响）
- 模型版本迭代快，需关注 API 稳定性（目前 deepseek-chat 已非常稳定）

### 选项 C：OpenAI GPT-4 / GPT-3.5

**优势**：
- 全球最强通用模型，能力天花板最高
- LangChain4j 原生支持最完善

**劣势**：
- **需要代理**：国内访问需科学上网，演示环境不稳定
- **成本高**：GPT-4 API 费用对个人开发者不友好
- **额外 Key 管理**：需要单独申请 OpenAI API Key，增加部署复杂度
- **合规风险**：国内求职项目中使用 OpenAI 可能引发合规性质疑

### 选项 D：本地开源模型（Qwen、Llama 等）

**优势**：
- 零 API 费用
- 完全离线，数据隐私最佳

**劣势**：
- **硬件门槛高**：7B 模型在 CPU 上推理极慢，13B/70B 模型需要 GPU，与"低成本云服务器演示"的目标冲突
- **效果不如云端大模型**：本地量化模型在 RAG 长文本理解和生成质量上明显弱于 DeepSeek/GPT-4
- **部署复杂**：需要 Ollama/vLLM 等推理框架，增加运维负担

---

## 决策

**选用 DeepSeek（deepseek-chat）作为项目 LLM。**

接入方式：`langchain4j-open-ai` 模块，OpenAI 兼容模式。

配置示例（`application.yaml`）：

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: https://api.deepseek.com/v1
      api-key: ${DEEPSEEK_API_KEY}
      model-name: deepseek-chat
      temperature: 0.7
      timeout: PT60S
```

---

## 后果

### 积极影响

- 机务英文手册的 RAG 问答质量高，回答中的专业术语和引用格式准确
- 面试官几乎必问"为什么选 DeepSeek"，本 ADR 直接提供了完整的技术和商业论据
- 部署成本极低，可持续在线演示而无经济压力
- 若未来需要切换模型（如 OpenAI、Claude），只需修改 `base-url` 和 `model-name`，业务代码零改动

### 消极影响 / trade-off

- DeepSeek 暂无 Embedding API，因此必须搭配本地嵌入模型（all-MiniLM-L6-v2）或外部 Embedding 服务。本项目选择本地 ONNX 方案，增加了约 20MB 的依赖体积，但换来了零外部依赖的稳定性。
- DeepSeek 作为较新的模型厂商，长期稳定性尚需观察，但目前运行一个多月无异常。

### 相关决策

- [ADR-001](ADR-001-langchain4j-over-spring-ai.md)：LangChain4j 的 `langchain4j-open-ai` 模块为 DeepSeek 接入提供了零成本通道。
- [ADR-002](ADR-002-chromadb-over-others.md)：ChromaDB 负责向量检索，与 LLM 选型解耦。

---

## 相关链接

- [DeepSeek 官方平台](https://www.deepseek.com/)
- [DeepSeek API 文档](https://platform.deepseek.com/)
- [LangChain4j OpenAI 集成](https://docs.langchain4j.dev/integrations/language-models/open-ai/)
- [application.yaml 中 DeepSeek 配置](../../backend/src/main/resources/application.yaml)
