#!/bin/bash
set -e

BASE_URL=${BASE_URL:-http://localhost:8080}
DAILY_LIMIT=${DAILY_LIMIT:-3}

echo "测试全局日限额（限制: $DAILY_LIMIT）..."
echo "  注意：运行前请确保 IP 限流和优雅降级未触发，且日限额未耗尽"

SUCCESS=0
QUOTA_HIT=0

for i in $(seq 1 $((DAILY_LIMIT + 2))); do
  RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d '{"message":"What is AMM?"}')
  if echo "$RESPONSE" | grep -q "额度已用完"; then
    QUOTA_HIT=$((QUOTA_HIT + 1))
  elif echo "$RESPONSE" | grep -q "暂不可用"; then
    echo "  第 $i 次触发优雅降级，非预期（请检查 API Key 是否正确）FAIL"
    exit 1
  else
    SUCCESS=$((SUCCESS + 1))
  fi
done

echo "  成功: $SUCCESS, 额度耗尽: $QUOTA_HIT"

if [ "$SUCCESS" -ge "$DAILY_LIMIT" ] && [ "$QUOTA_HIT" -ge 1 ]; then
  echo "test-quota.sh PASS"
else
  echo "test-quota.sh FAIL"
  exit 1
fi
