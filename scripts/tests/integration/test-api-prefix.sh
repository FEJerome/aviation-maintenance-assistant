#!/bin/bash
set -e

BASE_URL=${BASE_URL:-http://localhost:8080}

echo "测试 API 前缀映射..."

# /api/chat 应该存在（返回 200/400/429 均可接受）
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"test"}')
if [ "$STATUS" -eq 200 ] || [ "$STATUS" -eq 400 ] || [ "$STATUS" -eq 429 ]; then
  echo "  /api/chat 可达: $STATUS PASS"
else
  echo "  /api/chat 返回: $STATUS, 预期 200/400/429 FAIL"
  exit 1
fi

# /chat 应该不存在
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"test"}')
if [ "$STATUS" -eq 404 ]; then
  echo "  /chat 已废弃: $STATUS PASS"
else
  echo "  /chat 返回: $STATUS, 预期 404 FAIL"
  exit 1
fi

# /api/admin/ingest 应该存在（可能 403，但至少路径存在）
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/admin/ingest")
if [ "$STATUS" -eq 200 ] || [ "$STATUS" -eq 403 ]; then
  echo "  /api/admin/ingest 可达: $STATUS PASS"
else
  echo "  /api/admin/ingest 返回: $STATUS, 预期 200/403 FAIL"
  exit 1
fi

echo "test-api-prefix.sh PASS"
