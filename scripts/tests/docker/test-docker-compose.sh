#!/bin/bash
set -euo pipefail

STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1)
[ "$STATUS" -eq 200 ] || { echo "Frontend status $STATUS"; exit 1; }

# This request is deterministically blocked and never reaches a model.
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://127.0.0.1/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"B737-800 PW1100G engine oil"}')
[ "$STATUS" -eq 200 ] || { echo "Chat proxy status $STATUS"; exit 1; }
echo "test-docker-compose.sh PASS"
