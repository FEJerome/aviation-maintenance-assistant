#!/bin/bash
set -euo pipefail
BASE_URL=${BASE_URL:-http://127.0.0.1:8080}

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/admin/ingest" \
  -H "X-Forwarded-For: 203.0.113.10")
if [ "$STATUS" -eq 403 ]; then
  echo "Spoofed XFF changed the loopback trust decision"; exit 1
fi
# External remoteAddr and trusted-proxy behavior are covered by local unit tests.
echo "test-internal-ip.sh PASS"
