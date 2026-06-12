#!/bin/bash
set -e

BASE_URL=${BASE_URL:-http://localhost:8080}
CAPACITY=${CAPACITY:-20}

echo "测试 IP 限流（容量: $CAPACITY）..."
echo "  注意：运行前请确保全局日限额未耗尽，否则可能拿不到预期成功次数"

SUCCESS=0
RATE_LIMITED=0

for i in $(seq 1 $((CAPACITY + 3))); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/chat" \
    -H "Content-Type: application/json" \
    -d '{"message":"test"}')
  if [ "$STATUS" -eq 200 ] || [ "$STATUS" -eq 400 ]; then
    SUCCESS=$((SUCCESS + 1))
  elif [ "$STATUS" -eq 429 ]; then
    RATE_LIMITED=$((RATE_LIMITED + 1))
  else
    echo "  第 $i 次返回意外状态: $STATUS FAIL"
    exit 1
  fi
done

echo "  成功: $SUCCESS, 限流: $RATE_LIMITED"

if [ "$SUCCESS" -ge "$CAPACITY" ] && [ "$RATE_LIMITED" -ge 1 ]; then
  echo "test-rate-limit.sh PASS"
else
  echo "test-rate-limit.sh FAIL"
  exit 1
fi
