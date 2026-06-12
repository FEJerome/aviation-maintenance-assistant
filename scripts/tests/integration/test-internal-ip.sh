#!/bin/bash
set -e

BASE_URL=${BASE_URL:-http://localhost:8080}

echo "测试 /api/admin/ingest 内网限制..."

# 本地访问应成功或返回业务错误（至少不是 403）
STATUS_LOCAL=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/admin/ingest")
if [ "$STATUS_LOCAL" -ne 403 ]; then
  echo "  本地访问: $STATUS_LOCAL PASS"
else
  echo "  本地访问被 403 拒绝 FAIL"
  exit 1
fi

# 模拟外网 IP 应 403
STATUS_REMOTE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/admin/ingest" \
  -H "X-Forwarded-For: 8.8.8.8")
if [ "$STATUS_REMOTE" -eq 403 ]; then
  echo "  外网 IP 被拦截: $STATUS_REMOTE PASS"
else
  echo "  外网 IP 返回: $STATUS_REMOTE, 预期 403 FAIL"
  exit 1
fi

echo "test-internal-ip.sh PASS"
