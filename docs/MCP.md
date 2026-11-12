# MCP Interface (Target Contract)

> 🎯 **TARGET CONTRACT — PLANNED, NOT YET IMPLEMENTED.**
> This document defines the **read-only MCP (Model Context Protocol) server** the toolkit will expose; implementation is currently **planned**.
> No MCP endpoint exists in the current code. This page is the design contract that implementation must satisfy.

---

## 1. Scope and safety posture

- The MCP server is **read-only by design**. It exposes retrieval and validation capabilities over the user's own ingested corpus.
- There is **no** `ingest`, `delete`, `update`, or any other mutation tool, and none is planned for this contract. Corpus management stays with the existing HTTP ingestion endpoint (localhost/intranet only, see [API.md](API.md) and [DEPLOY.md](../DEPLOY.md)).
- Because tools only read, an MCP client can never modify the vector store, the session store, or any configuration through this interface.

## 2. Identity model (planned deliverable)

Stable identifiers are part of the planned ingestion work:

| Identifier | Assignment | Stability rule |
|------------|-----------|----------------|
| `document_id` | Assigned at ingestion time; **deterministic content hash of the source file is the preferred scheme** | Re-ingesting byte-identical content yields the same `document_id` |
| `excerpt_id` | Assigned per chunk; **deterministic hash of the chunk content plus its `document_id` is the preferred scheme** | Stable across restarts and re-ingestions for unchanged content |

Notes:

- **One stability semantics, two allowed schemes.** Content-hash derivation is preferred. Random UUIDs are permitted **only if** they are (a) persisted with the stored data and (b) **reused when byte-identical content is re-ingested** — i.e. the implementation detects that the incoming bytes match an already-stored document/chunk and keeps the existing IDs. Either way, the observable contract is identical: identical bytes in → identical IDs out.
- **Per-page Tika parsing is NOT required** for the planned first version. IDs anchor to documents and chunks, not to pages.

## 3. Tools

### 3.1 `validate_aircraft_model`

Deterministic validation backed by the same hardcoded match table as the HTTP chat path (`AircraftValidationService`). **No LLM is involved.**

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `aircraft_model` | string | ✅ | Aircraft model as the user wrote it, e.g. `"CTLS"` |
| `engine_model` | string | ✅ | Engine model as the user wrote it, e.g. `"Rotax 912"` |

**Output:**

| Field | Type | Description |
|-------|------|-------------|
| `status` | `"MATCH" \| "MISMATCH" \| "UNKNOWN"` | Validation verdict, same semantics as the chat path |
| `normalized_aircraft_model` | string | Model after normalization (vendor-prefix stripping, sub-model recognition) |
| `normalized_engine_model` | string | Engine after normalization |
| `message` | string | Human-readable explanation, esp. for `MISMATCH`/`UNKNOWN` |

### 3.2 `search_manual`

Semantic search over the ingested corpus (ChromaDB `aviation-docs` collection).

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `query` | string | ✅ | Natural-language query; may be Chinese or English (query transformation applies as in the chat path) |
| `top_k` | integer | ❌ (default 5, max 20) | Number of excerpts to return |
| `document_id` | string | ❌ | Restrict search to a single document |

**Output:** an array of hit objects:

| Field | Type | Description |
|-------|------|-------------|
| `excerpt_id` | string | Stable excerpt identifier (see §2) |
| `document_id` | string | Owning document |
| `document_title` | string | Display title of the source document |
| `text` | string | The retrieved chunk text |
| `score` | number | Similarity score as returned by the retriever |

### 3.3 `get_manual_excerpt`

Fetch one excerpt by ID — the canonical way for a client to re-resolve a citation returned by `search_manual` or by a chat answer.

**Input:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `excerpt_id` | string | ✅ | Identifier from a previous `search_manual` hit |

**Output:**

| Field | Type | Description |
|-------|------|-------------|
| `excerpt_id` | string | Echo of the requested ID |
| `document_id` | string | Owning document |
| `document_title` | string | Display title |
| `text` | string | Full chunk text |

**Not found:** returns a structured "not found" result (not an exception), so clients can treat stale citations as a normal case.

> ⚠️ **An excerpt is not a section.** `get_manual_excerpt` returns a retrieval chunk as ingested. It carries no guarantee of aligning with manual sections, chapters, or page boundaries.

## 4. Optional supplement: `list_documents`

`list_documents` (no arguments → array of `{ document_id, document_title, ingested_at, excerpt_count }`) **may** be provided as an optional convenience so clients can discover the corpus.

It is strictly a supplement: it **must not** replace validation (`validate_aircraft_model` remains the only supported way to check aircraft/engine combinations), and clients must not infer airworthiness data from document listings.

## 5. Roadmap (explicitly out of this contract)

| Item | Status | Blocker |
|------|--------|---------|
| `get_manual_section` (section-/page-aware retrieval) | 📋 Roadmap only | Requires an ingestion evolution that preserves **page/section metadata**; current chunking does not |
| Per-page parsing in ingestion | 📋 Roadmap only | Same evolution as above; not required for the planned first version |
| Any write/mutation tool | ❌ Not planned | Read-only is a design invariant of this interface |

---

*Status: target contract (planned) · Last updated: 2026-11-12*
