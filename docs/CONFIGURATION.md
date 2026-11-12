# Configuration (Target Contract)

> 🎯 **TARGET CONTRACT — NOT YET IMPLEMENTED.**
> This document is the **target configuration contract** for the self-hosted toolkit; implementation is **planned** and not yet available.
> **The current code defaults to DeepSeek** via `langchain4j-open-ai` with the environment variables documented in [DEPLOY.md](../DEPLOY.md). Nothing on this page is wired into the running system yet.
>
> **[中文版本](CONFIGURATION.zh-CN.md)**

---

## 1. Goal

Let each self-hosting user point the backend at **any OpenAI-compatible chat completion endpoint** — their own provider, their own key, their own model — without code changes.

## 2. LLM connection (OpenAI-compatible)

| Planned variable | Required | Default (target) | Description |
|------------------|----------|------------------|-------------|
| `AMA_LLM_BASE_URL` | ✅ | — | Base URL of any OpenAI-compatible endpoint, e.g. `https://api.deepseek.com`, `https://api.openai.com/v1`, or a self-hosted vLLM/Ollama gateway |
| `AMA_LLM_API_KEY` | ✅ | — | API key for the endpoint above |
| `AMA_LLM_MODEL` | ✅ | `deepseek-chat` | Chat model name as the endpoint expects it |
| `AMA_LLM_TIMEOUT_SECONDS` | ❌ | `60` | Per-request timeout for LLM calls |

**Current implementation mapping (fact, not contract):**

- Today the backend reads `DEEPSEEK_API_KEY` (with `LANGCHAIN4J_OPEN_AI_CHAT_MODEL_API_KEY` as an override) and is configured against the DeepSeek endpoint in code-level configuration.
- In the planned implementation these will be re-expressed in terms of the `AMA_LLM_*` contract above, keeping DeepSeek as the default `AMA_LLM_MODEL` value so existing `.env` files continue to work through a compatibility mapping.

## 3. ChromaDB (vector store)

| Planned variable | Required | Default | Description |
|------------------|----------|---------|-------------|
| `AMA_CHROMA_BASE_URL` | ❌ | `http://localhost:8000` | ChromaDB base URL. Docker Compose overrides it to `http://chromadb:8000` internally |
| `AMA_CHROMA_COLLECTION` | ❌ | `aviation-docs` | Collection name for the ingested corpus |
| `AMA_CHROMA_DATA_PATH` | ❌ | `./backend/chroma-data` | Host path mounted as ChromaDB's persistent volume |

**Current implementation mapping (fact):** today only `LANGCHAIN4J_CHROMA_EMBEDDING_STORE_BASE_URL` is honored; collection and data path are fixed as above.

## 4. Rate limiting (optional)

| Planned variable | Required | Default | Description |
|------------------|----------|---------|-------------|
| `AMA_RATE_LIMIT_PER_IP_PER_HOUR` | ❌ | `20` | Per-IP chat request limit (Bucket4j token bucket) |
| `AMA_LLM_DAILY_QUOTA` | ❌ | `500` | Global daily cap on LLM calls, protecting the user's API budget |

**Current implementation mapping (fact):** both limits exist today as hardcoded defaults (20/hour/IP, 500/day global). The planned implementation will make them configurable without code changes.

## 5. Embeddings (fixed design decision)

Embeddings are **deliberately not part of the BYOK contract**: the toolkit uses a fully local model (all-MiniLM-L6-v2, ONNX runtime, ~20 MB) so that document content never leaves the user's machine for vectorization. No embedding API key is needed, now or planned.

## 6. Explicit non-goals for the target contract

- No multi-tenant key management or per-user key vaults — the deployment is single-tenant by design.
- No streaming-protocol changes: SSE behavior stays as documented in [docs/API.md](API.md).
- No switch away from ChromaDB as the default store (see [ADR-002](ADR/ADR-002-chromadb-over-others.md)).

---

*[中文版本](CONFIGURATION.zh-CN.md) · Status: target contract (planned)*
