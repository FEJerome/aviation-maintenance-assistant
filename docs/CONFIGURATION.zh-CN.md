# 配置（目标契约）

> 🎯 **目标契约——尚未实现。**
> 本文档是自托管工具包的**目标配置契约**，实现状态为**规划中（尚未落地）**。
> **当前代码默认使用 DeepSeek**，通过 `langchain4j-open-ai` 接入，环境变量以 [DEPLOY.zh-CN.md](../DEPLOY.zh-CN.md) 中记录的为准。本页内容尚未接入运行中的系统。
>
> **[English version](CONFIGURATION.md)**

---

## 1. 目标

让每位自托管用户都能把后端指向**任意 OpenAI 兼容的聊天补全端点**——自己的提供方、自己的密钥、自己的模型——无需修改代码。

## 2. LLM 连接（OpenAI 兼容）

| 规划变量 | 必填 | 默认值（目标） | 说明 |
|---------|------|---------------|------|
| `AMA_LLM_BASE_URL` | ✅ | — | 任意 OpenAI 兼容端点的 Base URL，如 `https://api.deepseek.com`、`https://api.openai.com/v1`，或自托管 vLLM/Ollama 网关 |
| `AMA_LLM_API_KEY` | ✅ | — | 上述端点的 API Key |
| `AMA_LLM_MODEL` | ✅ | `deepseek-chat` | 端点所要求的聊天模型名称 |
| `AMA_LLM_TIMEOUT_SECONDS` | ❌ | `60` | LLM 调用的单次请求超时 |

**当前实现映射（事实，非契约）：**

- 当前后端读取 `DEEPSEEK_API_KEY`（可用 `LANGCHAIN4J_OPEN_AI_CHAT_MODEL_API_KEY` 覆盖），并在代码级配置中对接 DeepSeek 端点。
- 规划实现会将上述配置归一到 `AMA_LLM_*` 契约，同时保留 DeepSeek 作为 `AMA_LLM_MODEL` 的默认值，通过兼容映射让现有 `.env` 文件继续可用。

## 3. ChromaDB（向量存储）

| 规划变量 | 必填 | 默认值 | 说明 |
|---------|------|--------|------|
| `AMA_CHROMA_BASE_URL` | ❌ | `http://localhost:8000` | ChromaDB 服务地址。Docker Compose 内部自动覆盖为 `http://chromadb:8000` |
| `AMA_CHROMA_COLLECTION` | ❌ | `aviation-docs` | 摄入语料所用的集合名 |
| `AMA_CHROMA_DATA_PATH` | ❌ | `./backend/chroma-data` | 挂载为 ChromaDB 持久化卷的主机路径 |

**当前实现映射（事实）：** 当前仅 `LANGCHAIN4J_CHROMA_EMBEDDING_STORE_BASE_URL` 生效；集合名与数据路径如上固定。

## 4. 限流（可选）

| 规划变量 | 必填 | 默认值 | 说明 |
|---------|------|--------|------|
| `AMA_RATE_LIMIT_PER_IP_PER_HOUR` | ❌ | `20` | 每 IP 每小时聊天请求上限（Bucket4j 令牌桶） |
| `AMA_LLM_DAILY_QUOTA` | ❌ | `500` | LLM 调用全局每日上限，保护用户的 API 预算 |

**当前实现映射（事实）：** 两项限额当前以硬编码默认值存在（20 次/小时/IP，500 次/天全局）。规划实现将使其无需改代码即可配置。

## 5. 嵌入模型（固定的设计决策）

嵌入**刻意不纳入 BYOK 契约**：工具包使用完全本地的模型（all-MiniLM-L6-v2，ONNX 运行时，约 20MB），保证文档内容在向量化环节不离开用户机器。现在和规划中都不需要任何 Embedding API Key。

## 6. 目标契约明确不做的事

- 不做多租户密钥管理或按用户密钥库——部署按设计为单租户。
- 不改流式协议：SSE 行为保持 [docs/API.md](API.md) 中的文档约定。
- 不把默认向量存储从 ChromaDB 换掉（见 [ADR-002](ADR/ADR-002-chromadb-over-others.md)）。

---

*[English version](CONFIGURATION.md) · 状态：目标契约（规划中）*
