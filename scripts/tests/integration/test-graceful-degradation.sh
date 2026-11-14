#!/bin/bash
set -euo pipefail
BASE_URL=${BASE_URL:-http://127.0.0.1:8080}
FAKE_URL=${FAKE_URL:-http://127.0.0.1:18089}

# Corpus-hit question: with the real Chroma store and the synthetic quickstart
# corpus, zero-hit questions short-circuit to NO_ANSWER before any model call,
# so degradation assertions must use a question that actually retrieves excerpts.
QUESTION="For B737-800 with CFM56-7B, what is the oil capacity of the PWR-40S?"

curl -s -X POST "$FAKE_URL/__mode" -H "Content-Type: application/json" \
  -d '{"mode":"http400"}' >/dev/null
RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat" \
  -H "Content-Type: application/json" -d "{\"message\":\"$QUESTION\"}")
echo "$RESPONSE" | grep -q 'LLM_PROTOCOL_ERROR' || { echo "$RESPONSE"; exit 1; }

STREAM=$(curl -s -N -X POST "$BASE_URL/api/chat/stream" \
  -H "Content-Type: application/json" -d "{\"message\":\"$QUESTION\"}")
echo "$STREAM" | grep -q 'LLM_STREAMING_UNSUPPORTED' || { echo "$STREAM"; exit 1; }
if echo "$STREAM" | grep -q 'event: complete'; then
  echo "error was followed by complete"
  exit 1
fi
curl -s -X POST "$FAKE_URL/__reset" >/dev/null
echo "test-graceful-degradation.sh PASS"
