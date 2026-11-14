# ADR-008: Self-hosted foundation

## Status

Accepted.

## Context

The toolkit needs provider-neutral OpenAI-compatible configuration without silently leaking documents to unintended endpoints. It also needs reproducible ingestion, inspectable retrieval sources, and one explicit streaming contract.

## Decision

- Use `AMA_LLM_*` connection settings with strict generic-first resolution and a limited `DEEPSEEK_API_KEY` migration path.
- Validate local-only endpoints at startup, excluding link-local, and document that this is not a network sandbox.
- Keep a single Spring Boot application with a generic model/retrieval core and an aviation composition layer.
- Hash source bytes for document IDs and versioned chunk content for excerpt IDs. Re-ingest identical versions idempotently and use new collections for complete snapshots.
- Make retrieval explicit, return citations for the excerpts injected into the prompt, and skip generation when retrieval is empty.
- Route every model attempt through one executor. Configure blocking fallback only before a request; never retry a failed stream automatically.
- Keep optional local rate/quota controls, safe trace IDs, and no request/response body logging.

## Consequences

Older vector stores must be re-ingested to obtain stable metadata. API clients receive additive chat fields but structured infrastructure errors and JSON ingestion reports. Single-instance session and quota state are not distributed systems. MCP, evaluation execution, and complete persistent traces remain separate milestones.
