# 配置说明

[English](CONFIGURATION.md)

## 运行模式与模型连接

| 环境变量 | 默认值 | 契约 |
|---|---|---|
| `AMA_RUNTIME_MODE` | `cloud-allowed` | 可选 `cloud-allowed` 或 `local-only`。 |
| `AMA_LLM_BASE_URL` | 无 | OpenAI 兼容根地址；只去尾部 `/`，不自动补 `/v1`。 |
| `AMA_LLM_API_KEY` | 无 | 云模式必填，本地模式可选，只发往配置的端点。 |
| `AMA_LLM_MODEL` | 无 | 必填，不静默选择供应商默认模型。 |
| `AMA_LLM_TIMEOUT_SECONDS` | `60` | 阻塞与流式客户端共用的正数超时。 |
| `AMA_LLM_STREAMING_ENABLED` | `true` | 为 `false` 时 SSE 只调用一次 blocking，并标记降级。 |
| `AMA_LLM_TEMPERATURE` | 不发送 | 通用模式未配置时不发送；DeepSeek 旧配置兼容为 `0.2`。 |

云模式下三个连接变量必须完整配置。任一存在即选择通用模式，缺项会失败，绝不用 DeepSeek 值补齐，因此 YAML/Compose 不得放空占位。`LANGCHAIN4J_OPEN_AI_CHAT_MODEL_API_KEY` 不作为 key 来源。

迁移期仅配置 `DEEPSEEK_API_KEY` 时，在云模式使用 `https://api.deepseek.com/v1`、`deepseek-chat`、`0.2`；local-only 下拒绝该云回落。

## local-only 防护

允许 loopback、RFC1918 私网、IPv6 ULA，以及全部解析地址都属于这些范围的 Docker 服务名；拒绝公共地址、解析失败、unspecified、multicast 和 link-local。`localhost` 不绕过检查。云模式下非本地 LLM 必须 HTTPS，本地 HTTP 可用。

该功能只是配置防误连，不是操作系统网络沙箱；需要更强隔离时仍须配置防火墙或容器出网规则。

本地 MiniLM 只表示向量化在本地，不等于完整链路本地。使用云 LLM 时，原始问题、已验证机型上下文、翻译查询和检索手册片段可能发给供应商。

## 语料与状态

| 环境变量 | 默认值 | 契约 |
|---|---|---|
| `AMA_CHROMA_BASE_URL` | `http://localhost:8000` | Compose 覆盖为 `http://chromadb:8000`。 |
| `AMA_CHROMA_COLLECTION` | `aviation-docs` | 完整语料快照使用新 collection。 |
| `AMA_CHROMA_DATA_PATH` | `./backend/chroma-data` | 仅用于 Compose 宿主机 volume 插值。 |
| `AMA_DATA_PATH` | `./data` | 当前层 PDF/Markdown 目录，Compose 只读挂载。 |
| `AMA_STATE_PATH` | `./state` | 可写配额状态目录。 |
| `AMA_RAG_MAX_RESULTS` | `5` | 检索结果上限。 |
| `AMA_RAG_MIN_SCORE` | `0.6` | 检索分数门槛。 |
| `AMA_AVIATION_QUERY_TRANSLATION_ENABLED` | `true` | 中文翻译复用 executor，并遵守同一 local-only 端点约束。 |

`document_id` 来自原始字节 SHA-256；`excerpt_id` 来自 `document_id`、`recursive-500-50-v1`、chunk 序号和精确文本。重复摄入清理并重写同版本；改字节生成新版本且不删除旧版本。缺少这些 metadata 的旧向量必须摄入到新 collection。

配额文件为 `AMA_STATE_PATH/daily-llm-quota.json`，按 UTC 日期和单进程原子预留计数。旧 `data/daily-llm-quota.json` 不自动继承；应停止写入后核对 `date/count` 并人工迁移，或采用保守的新日限额。

## 限流、配额与代理

| 环境变量 | 默认值 | 契约 |
|---|---|---|
| `AMA_RATE_LIMIT_ENABLED` | `true` | 显式关闭后完全绕过 IP bucket。 |
| `AMA_RATE_LIMIT_PER_IP_PER_HOUR` | `20` | 每 IP 正数请求容量。 |
| `AMA_LLM_QUOTA_ENABLED` | `true` | 关闭后不读写配额状态。 |
| `AMA_LLM_DAILY_QUOTA` | `500` | 按真实模型 attempt 计数，含翻译和失败调用。 |
| `AMA_TRUST_PROXY_HEADERS` | `false` | 仅在受控代理且 backend 不可直连时启用。 |

阻塞与流式生成共用 executor。blocking client 设置 `maxRetries(0)`；当前 streaming 实现没有重试包装，fake 端点测试断言失败时只请求一次。流式失败后不会自动改用 blocking，也不会切换 provider、model 或 URL。

MCP 工具、golden-set 执行器和可下载的完整持久化 trace 仍为规划能力。
