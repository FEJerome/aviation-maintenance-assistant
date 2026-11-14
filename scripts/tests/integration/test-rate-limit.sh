#!/bin/bash
set -euo pipefail
BASE_URL=${BASE_URL:-http://127.0.0.1:8080}
CAPACITY=${CAPACITY:-2}
LIMITED=0

for i in $(seq 1 $((CAPACITY + 2))); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/chat" \
    -H "Content-Type: application/json" -d '{"message":""}')
  if [ "$STATUS" -eq 429 ]; then LIMITED=$((LIMITED + 1));
  elif [ "$STATUS" -ne 400 ]; then echo "Unexpected status $STATUS"; exit 1; fi
done
[ "$LIMITED" -ge 1 ] || { echo "Rate limit was not reached"; exit 1; }
echo "test-rate-limit.sh PASS"
