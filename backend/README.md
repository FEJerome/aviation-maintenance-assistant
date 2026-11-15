# backend — Aviation Maintenance Assistant

Spring Boot 3.5 (Java 21) backend service: RAG retrieval Q&A, aircraft-model admission validation, document ingestion, LLM quota and rate limiting.

> This file keeps only backend entry points. **The authoritative documentation lives in the repository root and in `docs/`** (see the pointers at the end); implementation details are intentionally not duplicated here to avoid going stale.

## Getting Started and Configuration

- Startup, environment variables, and deployment: root [DEPLOY.md](../DEPLOY.md) (Chinese: [DEPLOY.zh-CN.md](../DEPLOY.zh-CN.md))
- Full configuration keys and environment variables: [../docs/CONFIGURATION.md](../docs/CONFIGURATION.md)
- The corpus directory is set by `AMA_DATA_PATH`, defaulting to `./data` **relative to the backend process working directory**; starting from within `backend/` hits `backend/data/`, or start with an explicit path
- LLM connection (generic OpenAI-compatible triple `AMA_LLM_BASE_URL` / `AMA_LLM_API_KEY` / `AMA_LLM_MODEL`) and the `AMA_RUNTIME_MODE=local-only` constraint: see [../docs/CONFIGURATION.md](../docs/CONFIGURATION.md)

## Development

```bash
./mvnw.cmd test          # full unit/slice test suite (Windows; use ./mvnw on macOS/Linux)
./mvnw.cmd spring-boot:run
```

Loopback integration environment (fake OpenAI + isolated Chroma): [../scripts/tests/README.md](../scripts/tests/README.md)

## Structure Overview

```
src/main/java/cn/pandazi/aviation_maintenance_assistant/
├── chat/        # /api/chat Q&A endpoint, model extraction and topic routing, sessions, prompts, GroundedChatService
├── document/    # /api/admin/ingest ingestion endpoint, content addressing, Tika/Markdown parsing
├── rag/         # Chroma retrieval, query translation
├── validation/  # aircraft-model/engine match validation
├── config/      # LLM settings resolution, local-only endpoint guard, rate limiting, AmaProperties
└── service/     # LlmCallExecutor (the only model-call boundary), LlmQuotaService
```

## API Contract

REST endpoints and error codes: [../docs/API.md](../docs/API.md) (Chinese: [../docs/API.zh-CN.md](../docs/API.zh-CN.md))

## Documentation Pointers

| Topic | Location |
|-------|----------|
| Project overview, demo, screenshots | [root README.md](../README.md) |
| Deployment and operations | [DEPLOY.md](../DEPLOY.md) / [DEPLOY.zh-CN.md](../DEPLOY.zh-CN.md) |
| Full configuration key table | [docs/CONFIGURATION.md](../docs/CONFIGURATION.md) |
| API contract | [docs/API.md](../docs/API.md) / [docs/API.zh-CN.md](../docs/API.zh-CN.md) |
| Architecture | [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) |
| Test scripts | [scripts/tests/README.md](../scripts/tests/README.md) |
