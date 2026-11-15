# Golden-Set Evaluation

`run-golden-set.mjs` is a zero-dependency Node runner (built-in `fetch`, Node 18+; the
repository baseline builds with Node 24) that regression-checks the aviation reference
implementation against `evals/aviation/golden-set.json` on a fully loopback
environment. It never talks to a real model, provider, public IP, or production
service.

## Environment

Reuses the standard isolated test setup (see `scripts/tests/README.md`), with two
eval-specific additions: a dedicated Chroma collection and a dedicated data/state
directory so the evaluation never touches existing vectors or state.

1. `docker start ama-chroma-test` (Chroma on `127.0.0.1:18090`).
2. Start the fake LLM with the deterministic translation map:

   ```bash
   FAKE_TRANSLATION_MAP=scripts/tests/support/fake-translation-map.json \
     node scripts/tests/support/fake-openai-server.mjs   # 127.0.0.1:18089
   ```

3. Copy `examples/quickstart/*.md` into a dedicated data directory.
4. Start the backend on `127.0.0.1:18091`:

   ```bash
   AMA_RUNTIME_MODE=local-only \
   AMA_LLM_BASE_URL=http://127.0.0.1:18089/v1 \
   AMA_LLM_API_KEY=test-only-credential \
   AMA_LLM_MODEL=synthetic-chat \
   AMA_CHROMA_BASE_URL=http://127.0.0.1:18090 \
   AMA_CHROMA_COLLECTION=ama-eval \
   AMA_DATA_PATH=<eval-temp>/data \
   AMA_STATE_PATH=<eval-temp>/state \
   AMA_RATE_LIMIT_PER_IP_PER_HOUR=200 \
   AMA_MCP_ENABLED=true \
   AMA_MCP_AUTH_TOKEN=<long-random-token> \
   SERVER_PORT=18091 ./mvnw spring-boot:run
   ```

   The rate-limit capacity must fit the whole run: the HTTP pass issues one request
   per item, and the optional MCP pass adds an initialize handshake plus one request
   per item — all counted in the same per-IP bucket as `/api/chat`.
5. `POST /api/admin/ingest` once and confirm the three synthetic documents report
   `UPSERTED` into the `ama-eval` collection.

## Running

```bash
# HTTP pass only (default)
node scripts/eval/run-golden-set.mjs

# HTTP pass + optional second pass over the read-only MCP endpoint
EVAL_MCP_PASS=1 MCP_TOKEN=<same-token-as-backend> node scripts/eval/run-golden-set.mjs
```

Environment knobs: `EVAL_BACKEND_URL` (default `http://127.0.0.1:18091`),
`EVAL_FAKE_URL` (default `http://127.0.0.1:18089`), `EVAL_SET`,
`EVAL_OUTPUT_DIR` (default `docs/test-reports`), `EVAL_REPORT_STEM`,
`EVAL_MCP_PASS`, `MCP_TOKEN`, `EVAL_MCP_PATH` (default `/mcp`).

Reports are written as `<stem>.json` and `<stem>.md` under `EVAL_OUTPUT_DIR`.
The process exits non-zero if any gate fails. This harness is intentionally
**not** wired into required CI checks in v1: it runs locally and its report is
committed under `docs/test-reports/`.

## What is asserted

Three layers per item (see `evals/aviation/README.md` for the set contract):

- **L1 control flow** — actual outcome must equal the mapped expected outcome, and
  short-circuit routes must not call the model (fake `/__calls` delta:
  block/unknown/clarify = 0; English no_answer/out_of_scope = 0; Chinese ≤ 1
  translation call; rag items call exactly once in English, twice in Chinese).
- **L2 retrieval** — rag items must cite a `required_docs` document in the top-5
  citations (doc-level hit@5; reciprocal rank feeds MRR); no_answer and out_of_scope
  items must return empty citations; citation fields must be complete
  (`excerptId`/`documentId`/`documentTitle` non-blank, `chunkIndex` ≥ 0, `score`
  present).
- **L3 answer structure** — ANSWERED items need a non-empty reply containing an `[S`
  citation marker and `degraded=false`; NO_ANSWER items must return the fixed
  not-found notice exactly. Semantic/LLM-judged scoring is deferred to the release
  acceptance milestone.

Gates (any failure → non-zero exit): route accuracy = 100%, zero-call violations = 0,
no_answer false-answer rate = 0, rag doc-level hit@5 = 100%, citations integrity =
100%. MRR is reported without a gate.

`out_of_scope` items are evaluated against the `NO_ANSWER` outcome — a documented
mapping, not a native product route.

Results from this runner must never be presented as covering real models, real
providers, public networks, HTTPS, or production deployments.
