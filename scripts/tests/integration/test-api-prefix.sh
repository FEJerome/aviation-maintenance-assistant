#!/bin/bash
set -euo pipefail
BASE_URL=${BASE_URL:-http://127.0.0.1:8080}

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/chat" \
  -H "Content-Type: application/json" -d '{"message":""}')
[ "$STATUS" -eq 400 ] || { echo "Expected local validation 400, got $STATUS"; exit 1; }

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/chat" \
  -H "Content-Type: application/json" -d '{"message":"test"}')
[ "$STATUS" -eq 404 ] || { echo "Expected retired path 404, got $STATUS"; exit 1; }

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/admin/ingest")
case "$STATUS" in 200|400|403|503) ;; *) echo "Unexpected ingestion status $STATUS"; exit 1;; esac
echo "test-api-prefix.sh PASS"
