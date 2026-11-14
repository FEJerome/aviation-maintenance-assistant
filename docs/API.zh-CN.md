# API 文档

[English](API.md)

本地开发 Base URL：`http://127.0.0.1:8080`。

## POST /api/chat

执行一次阻塞式 grounded 聊天请求。

```json
{"message":"CTLS Rotax 912 发动机滑油压力","conversationId":null}
```

`message` 必填且最长 4000 字符；提供 `conversationId` 时必须是 UUID。

正常业务结果返回 HTTP 200：

```json
{
  "reply":"合成回答 [S1]。",
  "conversationId":"0f2fb71d-e8f7-49f0-a2f2-c1a2a8df8c10",
  "outcome":"ANSWERED",
  "traceId":"example-trace",
  "generationMode":"NON_STREAMING",
  "degraded":false,
  "degradationReason":null,
  "citations":[{
    "excerptId":"exr_example",
    "documentId":"doc_example",
    "documentTitle":"synthetic.md",
    "chunkIndex":0,
    "score":0.81
  }]
}
```

`outcome` 包括 `ANSWERED`、`NO_ANSWER`、`CLARIFICATION_REQUIRED`、`VALIDATION_BLOCKED`、`VALIDATION_UNKNOWN`；`generationMode` 包括 `STREAMING`、`NON_STREAMING`、`NONE`。citations 表示实际提供给模型的检索片段，不是逐句证据认证。

## POST /api/chat/stream

请求体相同，响应为 `text/event-stream`。因为是 POST，请使用 `fetch` reader，不能使用原生 `EventSource`。

- `token`：模型增量、配置式 blocking fallback 的完整正文，或固定业务/无答案提示。
- `complete`：除 `reply` 外的最终元数据，成功后只出现一次。
- `error`：安全 JSON 错误；error 后不会再发送 complete。

```javascript
const response = await fetch('/api/chat/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ message, conversationId })
});
const reader = response.body.getReader();
const decoder = new TextDecoder('utf-8');
// 将解码 chunk 交给仓库的 createSseParser，并在 EOF flush。
```

当 `AMA_LLM_STREAMING_ENABLED=false` 时，服务端只进行一次 blocking 调用并发送一个完整 token；complete 标记 `generationMode="NON_STREAMING"`、`degraded=true`、`degradationReason="STREAMING_DISABLED"`。运行中流式失败不会自动用 blocking 重试。

检索空命中时两个接口均返回 `NO_ANSWER` 和空 citations，不调用生成模型；中文查询此前可能已经发生一次翻译 attempt。

## POST /api/admin/ingest

扫描 `AMA_DATA_PATH` 当前层的 `.pdf`、`.md`、`.markdown`。接口仅限内网，不带账号体系。

HTTP 200 报告示例：

```json
{
  "status":"COMPLETED_WITH_ERRORS",
  "collection":"aviation-docs",
  "documents":[
    {"sourceName":"a.md","status":"UPSERTED","documentId":"doc_example","excerptCount":4,"duplicateOf":null,"errorCode":null},
    {"sourceName":"copy.md","status":"DUPLICATE_CONTENT","documentId":"doc_example","excerptCount":null,"duplicateOf":"a.md","errorCode":null},
    {"sourceName":"bad.pdf","status":"FAILED","documentId":null,"excerptCount":null,"duplicateOf":null,"errorCode":"DOCUMENT_PARSE_FAILED"}
  ],
  "ignored":["notes.txt"]
}
```

可形成批量报告时固定 HTTP 200，顶层为 `COMPLETED`、`COMPLETED_WITH_ERRORS` 或 `FAILED`；目录级基础设施错误才使用错误状态。相同内容幂等，改字节生成新版本，旧版本保留。

## 错误

```json
{"code":"LLM_TIMEOUT","message":"The configured LLM endpoint timed out.","traceId":"example-trace"}
```

- 400：`INVALID_REQUEST`、`INGESTION_SOURCE_EMPTY`。
- 403：`ADMIN_FORBIDDEN`。
- 404：`NOT_FOUND`。
- 405：`METHOD_NOT_ALLOWED`。
- 415：`UNSUPPORTED_MEDIA_TYPE`。
- 429：`RATE_LIMITED`、`QUOTA_EXHAUSTED`、`LLM_UPSTREAM_RATE_LIMITED`。
- 500：`INTERNAL_ERROR`。
- 502：`LLM_UPSTREAM_REJECTED`、`LLM_PROTOCOL_ERROR`、`LLM_STREAMING_UNSUPPORTED`、`QUERY_TRANSFORM_FAILED`。
- 503：`LLM_UNAVAILABLE`、`LLM_STREAM_INTERRUPTED`、`RETRIEVAL_UNAVAILABLE`、`RETRIEVAL_METADATA_INVALID`、`QUOTA_STATE_UNAVAILABLE`、`INGESTION_SOURCE_UNAVAILABLE`。
- 504：`LLM_TIMEOUT`。

错误不包含 provider body、凭据、原始问题、prompt 或手册正文。SSE 建立后即使 HTTP 已为 200，也会通过 `event:error` 发送相同结构。
