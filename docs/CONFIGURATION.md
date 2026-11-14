# Configuration

[中文版本](CONFIGURATION.zh-CN.md)

## Runtime and model connection

| Variable | Default | Contract |
|---|---|---|
| `AMA_RUNTIME_MODE` | `cloud-allowed` | `cloud-allowed` or `local-only`. |
| `AMA_LLM_BASE_URL` | none | OpenAI-compatible root URL. Only trailing slashes are removed; `/v1` is never guessed. |
| `AMA_LLM_API_KEY` | none | Required in cloud mode, optional for a local endpoint. Sent only to the configured endpoint. |
| `AMA_LLM_MODEL` | none | Required; there is no silent provider default. |
| `AMA_LLM_TIMEOUT_SECONDS` | `60` | Positive timeout shared by blocking and streaming clients. |
| `AMA_LLM_STREAMING_ENABLED` | `true` | When false, SSE uses one blocking call and marks the response as degraded. |
| `AMA_LLM_TEMPERATURE` | unset | Generic mode omits the parameter when unset. Legacy DeepSeek compatibility uses `0.2`. |

All three connection variables must be present together in cloud mode. If any one is present, generic mode is selected and missing values fail validation; DeepSeek values never fill the gaps. Empty YAML or Compose placeholders therefore must not be used. `LANGCHAIN4J_OPEN_AI_CHAT_MODEL_API_KEY` is not a supported key source.

For migration, `DEEPSEEK_API_KEY` alone selects `https://api.deepseek.com/v1`, `deepseek-chat`, and temperature `0.2` in cloud mode. It is rejected as a fallback in local-only mode.

## Local-only protection

`local-only` accepts loopback, RFC1918 private addresses, IPv6 ULA, and Docker service names resolving entirely to those ranges. Public, unresolved, unspecified, multicast, and link-local addresses are rejected. `localhost` receives no bypass. Cloud mode requires HTTPS for non-local LLM endpoints; local HTTP is allowed.

This guard prevents configuration mistakes; it is not an operating-system network sandbox. Use firewall or container egress rules when stronger isolation is required.

Local MiniLM embeddings keep vectorization local, but that alone does not make the whole chain local. With a cloud LLM, the original question, validated aircraft context, translated query, and retrieved manual excerpts may be sent to the configured provider.

## Corpus and state

| Variable | Default | Contract |
|---|---|---|
| `AMA_CHROMA_BASE_URL` | `http://localhost:8000` | Compose overrides this to `http://chromadb:8000`. |
| `AMA_CHROMA_COLLECTION` | `aviation-docs` | Use a new collection for a complete corpus snapshot. |
| `AMA_CHROMA_DATA_PATH` | `./backend/chroma-data` | Host path used only by Compose volume interpolation. |
| `AMA_DATA_PATH` | `./data` | Current-level PDF/Markdown source directory; mounted read-only in Compose. |
| `AMA_STATE_PATH` | `./state` | Writable quota state directory. |
| `AMA_RAG_MAX_RESULTS` | `5` | Maximum retrieval results. |
| `AMA_RAG_MIN_SCORE` | `0.6` | Retrieval score threshold. |
| `AMA_AVIATION_QUERY_TRANSLATION_ENABLED` | `true` | Chinese query translation through the same executor and local-only endpoint rules. |

Document IDs hash the raw source bytes. Excerpt IDs hash `document_id`, `recursive-500-50-v1`, chunk index, and exact chunk text. Re-ingestion removes and rewrites the same `document_id`; changed bytes create a new version and do not delete the old one. Existing stores without this metadata must be re-ingested into a new collection.

The quota file is `AMA_STATE_PATH/daily-llm-quota.json`. It uses UTC dates and single-process atomic reservations. The legacy `data/daily-llm-quota.json` is not inherited automatically; stop writes, verify its `date` and `count`, and migrate it deliberately or choose a conservative new daily limit.

## Limits and proxy trust

| Variable | Default | Contract |
|---|---|---|
| `AMA_RATE_LIMIT_ENABLED` | `true` | Explicitly disable to bypass IP buckets. |
| `AMA_RATE_LIMIT_PER_IP_PER_HOUR` | `20` | Positive per-IP request capacity. |
| `AMA_LLM_QUOTA_ENABLED` | `true` | Disable to avoid all quota state reads/writes. |
| `AMA_LLM_DAILY_QUOTA` | `500` | Counts actual model attempts, including translation and failed attempts. |
| `AMA_TRUST_PROXY_HEADERS` | `false` | Read forwarded headers only behind a controlled proxy with no direct backend access. |

Blocking and streaming generation use the same executor. The blocking client sets `maxRetries(0)`; the current streaming implementation has no retry wrapper, and fake endpoint tests assert one request on failure. Neither path falls back to another provider, model, URL, or blocking request after a streaming failure.

MCP tools, golden-set execution, and downloadable persistent traces remain planned capabilities.
