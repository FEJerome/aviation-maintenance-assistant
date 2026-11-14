# 架构说明

## 单体内边界

HTTP/Vue 应用壳进入机务 composition layer，再进入通用 grounded 内核。通用内核不 import 机型、发动机或机务会话类；不采用插件注册、动态领域加载或独立 Maven 模块。

```text
ChatController
  -> MaintenanceChatService
     - 每轮确定性路由、显式信息提取、机型/发动机重验、会话
     - AviationPrompt
     -> GroundedChatService
        -> RetrievalService
           -> QueryTransformer（机务中文翻译）
           -> ChromaDB + 本地 MiniLM
        -> LlmCallExecutor
           -> OpenAI-compatible blocking / streaming clients
```

## 配置与模型调用

`AmaProperties` 绑定 AMA 配置。`LlmSettingsResolver` 只解析一次，通用三连接项优先且缺项不混用，旧 `DEEPSEEK_API_KEY` 只在云模式提供迁移映射。`LocalOnlyEndpointGuard` 在启动时检查 LLM 与 Chroma 的全部解析地址，link-local 默认拒绝；它不是网络沙箱。

`OpenAiConfig` 用同一 resolved settings 创建 blocking/streaming client，不自动补 `/v1`，不发送未配置的 temperature，blocking 设置 `maxRetries(0)`。`LlmCallExecutor` 是聊天、流式和翻译的唯一调用边界，每次 attempt 在调用前预留配额。当前 streaming 实现直接执行请求，fake 失败测试验证只调用一次；运行中失败不切 provider 或 blocking 重试。

## 摄入与稳定 ID

`DocumentIngestionService` 使用 `SourceDocumentParser` 明确区分 PDF/Tika 与 Markdown/UTF-8。只扫描当前层，按文件名排序，同内容别名只处理第一份。

- `document_id = doc_ + SHA-256(raw bytes)`。
- `excerpt_id = exr_ + SHA-256(document_id + chunker version + chunk index + exact text)`。
- `embedding_id = excerpt_id`。
- chunker 固定为 `recursive-500-50-v1`。

每段保存 document/excerpt/title/source/hash/media/chunk/version/time metadata，不保存绝对路径，也不承诺 page/section。写入前按 `document_id` 清理同版本，再按稳定 ID 全量写入。Chroma 写入不是事务，失败报告后，下一次同版本清理可收敛。改字节产生新版本，旧版本默认保留；完整替换使用新 collection。

## 检索、引用与无答案

`RetrievalService` 显式执行 query transform 和 retrieve，返回稳定 ID、文档名、chunk 序号、score 与文本。缺失稳定 metadata 是明确错误，不随机生成 citation。

`GroundedChatService` 将片段按 `[S#]` 注入 prompt，并返回实际注入的 citations。citations 不是逐句事实验证。空命中直接 `NO_ANSWER`，不调用生成模型；中文查询此前可能已发生一次翻译 attempt。

流式关闭时只进行一次 blocking 调用并标记 `STREAMING_DISABLED`。流式运行中失败发送安全 error，不自动重试。

## 机务准入

`MaintenanceChatService.prepare` 每轮先路由。关键问题合并本轮显式值与另一方已验证会话值，再重新 validate；MATCH 才更新会话，MISMATCH/UNKNOWN 阻断且不覆盖旧会话。缺少信息直接追问，不默认猜发动机。通用问题不注入旧机型上下文。blocking 与 streaming 使用同一 prepare 结果。

## API、安全与状态

正常业务返回 200 和 additive ChatResponse；基础设施错误使用安全 HTTP/SSE error。每个请求有随机 traceId，日志只允许 trace/outcome/errorCode/耗时/数量/稳定 ID 等安全字段，不记录 key、原始问题、prompt、手册正文或 provider body。

限流和配额可显式关闭。默认不信任 forwarded headers；只有受控代理且 backend 不可直连时才启用。会话为单实例内存 LRU，配额为单实例 UTC 文件状态，不支持多副本。

MCP、golden-set 执行器和完整持久化 trace 仍为后续能力。
