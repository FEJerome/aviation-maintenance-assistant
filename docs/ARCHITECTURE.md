# Architecture

## Monolith Internal Boundaries

The HTTP/Vue application shell enters the aviation-maintenance composition layer, which in turn drives the generic grounded kernel. The generic kernel never imports aircraft, engine, or maintenance-session classes; there is no plugin registry, dynamic domain loading, or separate Maven module.

```text
ChatController
  -> MaintenanceChatService
     - deterministic per-turn routing, explicit information extraction,
       aircraft/engine re-validation, sessions
     - AviationPrompt
     -> GroundedChatService
        -> RetrievalService
           -> QueryTransformer (aviation Chinese-to-English translation)
           -> ChromaDB + local MiniLM
        -> LlmCallExecutor
           -> OpenAI-compatible blocking / streaming clients
```

## Configuration and Model Invocation

`AmaProperties` binds all AMA configuration. `LlmSettingsResolver` resolves once, giving the generic connection triple precedence without mixing partial fallbacks; the legacy `DEEPSEEK_API_KEY` is only mapped as a migration path in cloud mode. `LocalOnlyEndpointGuard` checks every resolved LLM and Chroma address at startup, rejecting link-local addresses by default; it is not a network sandbox.

`OpenAiConfig` builds the blocking/streaming clients from the same resolved settings: it never auto-appends `/v1`, never sends an unconfigured temperature, and sets `maxRetries(0)` on the blocking client. `LlmCallExecutor` is the single call boundary for chat, streaming, and translation; it reserves quota before every attempt. The current streaming implementation executes the request directly — the fake-failure tests verify it is called exactly once — and a mid-run failure neither switches provider nor falls back to blocking retry.

## Ingestion and Stable IDs

`DocumentIngestionService` uses `SourceDocumentParser` to keep PDF/Tika parsing strictly separate from Markdown/UTF-8 parsing. It scans only the current level, sorts by file name, and processes only the first of any content-identical aliases.

- `document_id = doc_ + SHA-256(raw bytes)`
- `excerpt_id = exr_ + SHA-256(document_id + chunker version + chunk index + exact text)`
- `embedding_id = excerpt_id`
- The chunker is pinned to `recursive-500-50-v1`.

Every chunk stores document/excerpt/title/source/hash/media/chunk/version/time metadata — never absolute paths — and makes no promises about page or section granularity. Before writing, the same-version chunks are cleared by `document_id`, then everything is written wholesale under stable IDs. Chroma writes are not transactional; after a failure is reported, the next same-version cleanup converges. Changing bytes produces a new version while old versions are retained by default; full replacement uses a new collection.

## Retrieval, Citations, and No-Answer

`RetrievalService` explicitly performs query transformation and retrieval, returning stable IDs, document names, chunk indices, scores, and text. Missing stable metadata is a hard error — citations are never generated at random.

`GroundedChatService` injects the excerpts into the prompt tagged `[S#]` and returns the citations actually injected. Citations are not per-sentence fact verification. Empty hits short-circuit straight to `NO_ANSWER` without invoking the generation model; for Chinese queries, one translation attempt may already have occurred by then.

When streaming is disabled, exactly one blocking call is made and marked `STREAMING_DISABLED`. A mid-run streaming failure sends a safe error and is not retried automatically.

## Aviation Admission Control

`MaintenanceChatService.prepare` routes every turn first. For key questions it merges this turn's explicit values with the other party's already-validated session values, then re-validates: only MATCH updates the session; MISMATCH/UNKNOWN blocks the answer and never overwrites the old session state. Missing information triggers a follow-up question — the engine is never guessed by default. Generic questions do not inject stale model context. Blocking and streaming share the same prepare result.

## API, Security, and State

Normal business responses return 200 with an additive ChatResponse; infrastructure failures use a safe HTTP/SSE error. Every request carries a random traceId, and logs may contain only safe fields — trace/outcome/errorCode/duration/counts/stable IDs — never keys, raw questions, prompts, manual text, or provider bodies.

Rate limiting and quota enforcement can be explicitly disabled. Forwarded headers are distrusted by default; enable them only behind a controlled proxy with no direct backend access. Sessions are a single-instance in-memory LRU, and quota state is a single-instance UTC file — neither supports multi-replica deployment.

## Read-Only MCP

The MCP layer is a thin adapter, not a second pipeline. `McpMaintenanceTool` (Spring AI 1.1.x
annotation-scanned `@McpTool`, streamable HTTP transport on `AMA_MCP_PATH`) delegates to
`MaintenanceChatService.processWithExcerpts`, which runs the same `prepare` control flow as
the HTTP path and calls the same blocking grounding path (`GroundedChatService.respond`).
`GroundedResult` already carries the retrieved `RetrievedExcerpt` list, so MCP citations —
same fields as HTTP citations plus the original `text` — are built from the same source in
the same order; nothing is re-retrieved. The tool maps the five business outcomes into a
`ChatResponse`-isomorphic result, short-circuits without any model call exactly like HTTP,
and converts model/retrieval failures into safe `isError` tool results carrying only a
semantic code and traceId. Quota accounting, session reuse, and degradation semantics are
inherited unchanged from the shared executor and services.

Endpoint security is a small filter chain in front of the transport: `McpAuthFilter`
(loopback-only by remote address, forwarded headers permanently ignored, constant-time
bearer check against `AMA_MCP_AUTH_TOKEN`) runs before `RateLimitFilter`, which includes the
MCP path in the same per-IP bucket as `/api/chat`. `McpStartupValidator` fails startup when
the endpoint is enabled without a sufficiently long token. The endpoint is disabled by
default; when disabled the transport autoconfiguration backs off entirely and the path
returns the standard 404. Contract: [MCP.md](MCP.md).

The golden-set executor and full persistent tracing remain future capabilities.
