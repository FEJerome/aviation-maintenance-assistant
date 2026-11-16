# Release-Acceptance Preparation — Self-Check Report

- Date: 2026-11-16
- Scope: real-model smoke harness (`scripts/eval/run-real-smoke.mjs`), version bump to `0.1.0`,
  `SECURITY.md`, stale documentation fixes, and the legacy DeepSeek model-alias update
  (`deepseek-chat` → `deepseek-flash`).
- All automated verification below ran on the loopback fake/local environment only
  (fake OpenAI-compatible server `127.0.0.1:18089`, isolated Chroma `127.0.0.1:18090`,
  backend `127.0.0.1:18091`). No real model, provider, public network, or production
  service was involved. The real-model smoke itself is **not** executed here; it waits
  for a separately approved window.

## Backend

- `./mvnw.cmd test`: **157/157 PASS** (surefire report directory cleaned before the run
  to exclude stale reports). No regressions from the version bump or the legacy
  model-alias change; `LlmSettingsResolverTest` was updated to assert the new
  `deepseek-flash` mapping. No new test cases were required — behavior is unchanged.

## Frontend

- `npm test`: **89/89 PASS** (5 test files, vitest 4.1.12) — unchanged baseline.
- `npm run build`: success — `dist` JS **259.36 kB** (gzip 94.45 kB), CSS 12.87 kB,
  index.html 0.40 kB — unchanged baseline.
- `npm ci` audit: **0 vulnerabilities**.

## Smoke-runner self-check (loopback fake, judge off)

Equivalence check of `run-real-smoke.mjs` against `run-golden-set.mjs` on the same
loopback environment (dedicated `ama-eval` collection, dedicated data/state
directories):

- `run-golden-set.mjs` (HTTP + MCP passes): all gates PASS, MRR 1.0000 — regression
  baseline intact; the runner was not modified.
- `run-real-smoke.mjs` (HTTP + MCP passes, judge disabled): all gates PASS with the
  same five gates, MRR 1.0000. Model-call accounting read from the backend quota
  ledger (`daily-llm-quota.json` delta): 30 calls consumed for the two passes,
  matching the golden run's fake-counter total exactly (quota file ended at 60 for
  both runs combined, 30 + 30).
- Negative check 1: running without `EVAL_STATE_PATH` fails fast with exit 1.
- Negative check 2: `EVAL_JUDGE=1` against the fake (whose responses are not judge
  JSON) fails closed — unusable verdicts fail the `judge keypoint coverage = 100%`
  gate and the process exits 1. The judge gate appears in reports only when the
  judge is enabled.

## Notes

- The legacy alias change touches `LlmSettingsResolver`, its test, `.env.example`,
  `docs/CONFIGURATION.md` / `.zh-CN.md`, and ADR-003 (with a dated update note).
  ADR-005's mention of `deepseek-chat` is a historical observation and is
  intentionally left unchanged.
- Real-model smoke execution (deepseek-flash over the generic `AMA_LLM_*` triple,
  dedicated `ama-eval-real` collection, quota 120, MCP enabled) remains pending a
  separately approved execution window; credentials are injected as server-side
  environment variables only.
