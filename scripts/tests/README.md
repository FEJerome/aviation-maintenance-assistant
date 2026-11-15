# Test Scripts

All default scripts only hit the loopback fake or deterministic local business paths. Never point them at a real model, real API keys, or production services.

## Isolated Environment

1. Start an isolated local ChromaDB and point `AMA_CHROMA_BASE_URL` at its instance.
2. Copy the synthetic Markdown from `examples/quickstart/` into a dedicated `AMA_DATA_PATH`.
3. Start the fake OpenAI-compatible server:

   ```bash
   node scripts/tests/support/fake-openai-server.mjs
   # listens on 127.0.0.1:18089 by default
   ```

4. Start the backend with:

   ```bash
   export AMA_LLM_BASE_URL=http://127.0.0.1:18089/v1
   export AMA_LLM_API_KEY=test-only-credential
   export AMA_LLM_MODEL=synthetic-chat
   export AMA_LLM_DAILY_QUOTA=3
   export AMA_RATE_LIMIT_PER_IP_PER_HOUR=2
   ```

5. Call `POST /api/admin/ingest` first and confirm the synthetic corpus reports success. On the fake, `GET /__calls` returns the safe call counter, `POST /__reset` resets it, and `POST /__mode` can set `ok`, `http400`, or `streamError`.

## Scripts

```bash
bash scripts/tests/integration/test-api-prefix.sh
bash scripts/tests/integration/test-rate-limit.sh
bash scripts/tests/integration/test-quota.sh
bash scripts/tests/integration/test-graceful-degradation.sh
bash scripts/tests/integration/test-internal-ip.sh
bash scripts/tests/docker/test-docker-compose.sh
```

The rate-limit script uses local 400 responses and consumes no model quota. The proxy script verifies that a forged `X-Forwarded-For` does not change loopback trust decisions; the real remote-Addr × trusted-proxy matrix is covered by backend unit tests. The compose script uses deterministic model-mismatch requests and never calls the model.

Real OpenAI-compatible providers, real local LLMs, public IPs, HTTPS, and production Nginx are validated only in a separate release acceptance — results from these scripts must never be presented as covering them.

## Visual Walkthrough

`scripts/tests/e2e/visual-foundation.mjs` drives a local Chromium-family browser over CDP using Node's built-in WebSocket client, with no added dependencies. It opens a loopback-only debug port 19222 and starts the frontend dev server on 127.0.0.1:5173. Set the scenario via `VISUAL_SCENARIO=clarification|citations|mismatch|degraded|stream-error|mobile`, then run:

```bash
node scripts/tests/e2e/visual-foundation.mjs
```

- `citations` / `mobile`: fake in `ok` mode, synthetic corpus already ingested.
- `degraded`: backend started with `AMA_LLM_STREAMING_ENABLED=false`.
- `stream-error`: fake switched to `streamError` via `/__mode`.
- `clarification` and `mismatch` need no successful model response.

Screenshots and JSON results are written under `docs/assets/screenshots/foundation-*`. Results include the fake attempt count, citation/degradation/error states, browser anomalies, and a mobile overflow check. Script setup alone does not count as visual acceptance complete; the final report must record the actual execution results and any uncovered scenarios.
