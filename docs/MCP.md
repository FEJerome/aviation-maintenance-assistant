# MCP Interface (Read-Only Question Answering)

> ✅ **IMPLEMENTED.** This document is the actual contract of the read-only MCP server shipped
> in this repository. It supersedes the earlier multi-tool target contract: only
> `ask_maintenance_question` is exposed, and no additional MCP tools are planned in this scope.

---

## 1. Scope and safety posture

- The MCP server is **read-only by design**. It exposes the existing maintenance
  question-answering pipeline over the user's own ingested corpus and nothing else.
- There is **no** `ingest`, `delete`, `update`, search-only, or administration tool. Corpus
  management stays with the existing HTTP ingestion endpoint (localhost/intranet only, see
  [API.md](API.md) and [DEPLOY.md](../DEPLOY.md)).
- "Read-only" describes *what a client can change*: nothing. The tool still participates in
  the normal product behavior — it keeps the per-conversation validated aircraft context
  (via `conversationId`) and consumes daily model quota exactly like the HTTP chat path.
- Model credentials are read only from server-side configuration
  (`AMA_LLM_BASE_URL` / `AMA_LLM_API_KEY` / `AMA_LLM_MODEL`). MCP requests cannot carry or
  override them, and questions, manual excerpts, and tokens are never written to logs.

## 2. Endpoint, transport, and enablement

| Aspect | Value |
|--------|-------|
| Endpoint | `POST {AMA_MCP_PATH}` (default `/mcp`) |
| Protocol | MCP **streamable HTTP** (`spring.ai.mcp.server.protocol=STREAMABLE`) |
| Disabled by default | `AMA_MCP_ENABLED=false` → the transport is not registered; the path answers **404** |
| Fail-closed startup | enabling without `AMA_MCP_AUTH_TOKEN` (or with a token shorter than 16 characters) fails application startup |
| Network binding | authorization uses only the servlet remote address; it must be a loopback address (`127.0.0.0/8`, `::1`) |
| Forwarded headers | `X-Forwarded-For` / `Forwarded` are **permanently ignored** by the MCP auth filter, even when `AMA_TRUST_PROXY_HEADERS=true` |
| Authentication | `Authorization: Bearer <AMA_MCP_AUTH_TOKEN>`, compared in constant time |
| Failures before dispatch | `401 UNAUTHORIZED` (missing/invalid token), `403 FORBIDDEN` (non-loopback caller); both return the safe JSON contract `{code, message, traceId}` |
| Rate limiting | `/mcp` joins the same per-IP bucket as `/api/chat`, governed by `AMA_RATE_LIMIT_ENABLED` / `AMA_RATE_LIMIT_PER_IP_PER_HOUR` |
| Auth filter order | runs before rate limiting, so rejected credentials do not consume quota tokens |

> Deploying behind a proxy or in a container network changes the observed remote address.
> Non-loopback callers are always rejected; expose the endpoint only via local port
> forwarding if you need remote access.

## 3. Configuration

| Environment variable | Default | Meaning |
|----------------------|---------|---------|
| `AMA_MCP_ENABLED` | `false` | Master switch for both the transport and the guard chain |
| `AMA_MCP_AUTH_TOKEN` | *(empty)* | Bearer token required when enabled; ≥ 16 characters |
| `AMA_MCP_PATH` | `/mcp` | Endpoint path; must start with `/` |

## 4. Tool: `ask_maintenance_question`

Blocking, request-response only (no streaming). It reuses the exact HTTP chat pipeline:
aircraft/engine extraction and validation, session context, query transformation, Chroma
retrieval, model invocation, quota accounting, and citations.

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `question` | string | ✅ | Maintenance question, 1–4000 characters |
| `conversationId` | string (UUID) | ❌ | Reuses the validated aircraft/engine context of an earlier exchange; a new UUID is generated when omitted |

Invalid input (blank or over-long `question`, malformed `conversationId`) returns an
`isError` tool result with a safe message and never reaches the pipeline.

**Output** (JSON text content, isomorphic to the HTTP `ChatResponse` with excerpts added):

| Field | Type | Description |
|-------|------|-------------|
| `reply` | string | Answer text, or the safe clarification/blocking message |
| `conversationId` | string | Effective conversation id (echo or generated) |
| `outcome` | enum | `ANSWERED`, `NO_ANSWER`, `CLARIFICATION_REQUIRED`, `VALIDATION_BLOCKED`, `VALIDATION_UNKNOWN` |
| `traceId` | string | Request-scoped trace id |
| `generationMode` | enum | `NON_STREAMING` on success; `NONE` for short-circuits |
| `degraded` | boolean | Same semantics as the HTTP contract (always `false` for MCP today) |
| `degradationReason` | string \| null | e.g. `STREAMING_DISABLED` in the HTTP path; unused by MCP |
| `citations[]` | array | `excerptId`, `documentId`, `documentTitle`, `chunkIndex`, `score`, plus **`text`** — the original manual excerpt used as evidence |

**Outcome semantics** mirror `/api/chat`: missing aircraft information asks for
clarification without calling the model; unknown or mismatched model/engine pairs are
blocked without calling the model; zero retrieval hits answer with `NO_ANSWER` without
calling the model. `QUOTA_EXHAUSTED` is deliberately **not** a normal outcome: the daily
quota is reserved before the model call, and exhausting it fails the call, so it surfaces
as `isError: true` with code `QUOTA_EXHAUSTED` (mirroring the HTTP 429 error contract).

**Model and infrastructure failures** return `isError: true` whose text carries only a safe
semantic code (e.g. `QUOTA_EXHAUSTED`, `LLM_TIMEOUT`, `LLM_UNAVAILABLE`) plus `traceId` —
never upstream provider details, questions, or excerpts.

**Rate limiting** follows §2: an over-bucket call is rejected with the standard `429`
`RATE_LIMITED` JSON contract before JSON-RPC dispatch.

## 5. Example

```bash
curl -s -X POST http://127.0.0.1:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Authorization: Bearer $AMA_MCP_AUTH_TOKEN" \
  -d '{
    "jsonrpc": "2.0", "id": 3, "method": "tools/call",
    "params": {
      "name": "ask_maintenance_question",
      "arguments": {
        "question": "For B737-800 with CFM56-7B, what is the oil capacity of the PWR-40S?"
      }
    }
  }'
```

The response is streamable HTTP (SSE `data:` frames or plain JSON); the `result.content[0].text`
field holds the output JSON described in §4.

## 6. Testing

Loopback-only coverage lives in the backend test suite (`mcp` package: tool mapping, auth
matrix including `::1` and spoofed forwarding headers, startup fail-closed, per-IP bucket
sharing, streamable `initialize`/`tools/list`/`tools/call` against the fake server, disabled
404) and in `scripts/tests/integration/test-mcp.sh` (see `scripts/tests/README.md`).

---

*Status: implemented · Contract: single tool, streamable HTTP · Last updated: 2026-11-15*
