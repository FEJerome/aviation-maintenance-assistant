# API

[中文版本](API.zh-CN.md)

Base URL for local development: `http://127.0.0.1:8080`.

## POST /api/chat

Runs one blocking grounded chat request.

```json
{
  "message": "CTLS Rotax 912 engine oil pressure",
  "conversationId": null
}
```

`message` is required and limited to 4000 characters. A supplied `conversationId` must be a UUID.

Normal business outcomes return HTTP 200:

```json
{
  "reply": "Synthetic answer [S1].",
  "conversationId": "0f2fb71d-e8f7-49f0-a2f2-c1a2a8df8c10",
  "outcome": "ANSWERED",
  "traceId": "f2fb71de8f749f0a2f2c1a2a8df8c100",
  "generationMode": "NON_STREAMING",
  "degraded": false,
  "degradationReason": null,
  "citations": [{
    "excerptId": "exr_example",
    "documentId": "doc_example",
    "documentTitle": "synthetic.md",
    "chunkIndex": 0,
    "score": 0.81
  }]
}
```

Outcomes are `ANSWERED`, `NO_ANSWER`, `CLARIFICATION_REQUIRED`, `VALIDATION_BLOCKED`, and `VALIDATION_UNKNOWN`. `generationMode` is `STREAMING`, `NON_STREAMING`, or `NONE`. Citations identify the excerpts actually supplied to the model; they are not sentence-level evidence verification.

## POST /api/chat/stream

Accepts the same JSON body and returns `text/event-stream`. Use a streaming `fetch` reader, not native `EventSource`, because the request is POST.

Events:

- `token`: an incremental model token, a complete configured blocking-fallback answer, or a fixed business/no-answer message.
- `complete`: final metadata except `reply`. It occurs once after successful completion.
- `error`: a safe JSON error. No `complete` event follows an error.

```javascript
const response = await fetch('/api/chat/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ message, conversationId })
});
const reader = response.body.getReader();
const decoder = new TextDecoder('utf-8');
// Feed decoded chunks to the repository's createSseParser, then flush at EOF.
```

With `AMA_LLM_STREAMING_ENABLED=false`, the server makes one blocking model call and emits one complete token. `complete` has `generationMode="NON_STREAMING"`, `degraded=true`, and `degradationReason="STREAMING_DISABLED"`. A runtime streaming failure is never retried through blocking.

When retrieval is empty, both endpoints return `NO_ANSWER`, empty citations, and no generation call. Chinese query translation may already have consumed one model attempt.

## POST /api/admin/ingest

Scans the current level of `AMA_DATA_PATH` for `.pdf`, `.md`, and `.markdown`. The endpoint is restricted to local networks and has no account system.

Example HTTP 200 report:

```json
{
  "status": "COMPLETED_WITH_ERRORS",
  "collection": "aviation-docs",
  "documents": [
    {"sourceName":"a.md","status":"UPSERTED","documentId":"doc_example","excerptCount":4,"duplicateOf":null,"errorCode":null},
    {"sourceName":"copy.md","status":"DUPLICATE_CONTENT","documentId":"doc_example","excerptCount":null,"duplicateOf":"a.md","errorCode":null},
    {"sourceName":"bad.pdf","status":"FAILED","documentId":null,"excerptCount":null,"duplicateOf":null,"errorCode":"DOCUMENT_PARSE_FAILED"}
  ],
  "ignored": ["notes.txt"]
}
```

A formable batch report uses HTTP 200 with `COMPLETED`, `COMPLETED_WITH_ERRORS`, or `FAILED`. Directory-level failures use an error status. Identical content is idempotent; changed bytes create a new version and old versions are retained.

## Errors

```json
{"code":"LLM_TIMEOUT","message":"The configured LLM endpoint timed out.","traceId":"example-trace"}
```

- 400: `INVALID_REQUEST`, `INGESTION_SOURCE_EMPTY`.
- 403: `ADMIN_FORBIDDEN`.
- 404: `NOT_FOUND`.
- 405: `METHOD_NOT_ALLOWED`.
- 415: `UNSUPPORTED_MEDIA_TYPE`.
- 429: `RATE_LIMITED`, `QUOTA_EXHAUSTED`, `LLM_UPSTREAM_RATE_LIMITED`.
- 500: `INTERNAL_ERROR`.
- 502: `LLM_UPSTREAM_REJECTED`, `LLM_PROTOCOL_ERROR`, `LLM_STREAMING_UNSUPPORTED`, `QUERY_TRANSFORM_FAILED`.
- 503: `LLM_UNAVAILABLE`, `LLM_STREAM_INTERRUPTED`, `RETRIEVAL_UNAVAILABLE`, `RETRIEVAL_METADATA_INVALID`, `QUOTA_STATE_UNAVAILABLE`, `INGESTION_SOURCE_UNAVAILABLE`.
- 504: `LLM_TIMEOUT`.

Provider response bodies, credentials, raw questions, prompts, and manual excerpts are not returned in errors. After an SSE connection is established, the same structure is sent in `event:error` even though the HTTP status may already be 200.
