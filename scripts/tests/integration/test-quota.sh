#!/bin/bash
set -euo pipefail
BASE_URL=${BASE_URL:-http://127.0.0.1:8080}
FAKE_URL=${FAKE_URL:-http://127.0.0.1:18089}
DAILY_LIMIT=${DAILY_LIMIT:-3}

# Corpus-hit question: zero-hit questions short-circuit to NO_ANSWER and never
# consume quota, so the exhaustion assertion needs a question that retrieves.
QUESTION="For B737-800 with CFM56-7B, what is the oil capacity of the PWR-40S?"

curl -s -X POST "$FAKE_URL/__reset" >/dev/null
QUOTA_HIT=0
for i in $(seq 1 $((DAILY_LIMIT + 1))); do
  RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat" \
    -H "Content-Type: application/json" -d "{\"message\":\"$QUESTION\"}")
  if echo "$RESPONSE" | grep -q 'QUOTA_EXHAUSTED'; then QUOTA_HIT=$((QUOTA_HIT + 1)); fi
done
[ "$QUOTA_HIT" -ge 1 ] || { echo "Quota was not exhausted"; exit 1; }
CALLS=$(curl -s "$FAKE_URL/__calls" | grep -o '"calls":[0-9]*' | cut -d: -f2)
[ "$CALLS" -eq "$DAILY_LIMIT" ] || { echo "Expected $DAILY_LIMIT fake attempts, got $CALLS"; exit 1; }
echo "test-quota.sh PASS"
