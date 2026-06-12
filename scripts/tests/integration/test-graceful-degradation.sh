#!/bin/bash
set -e

BASE_URL=${BASE_URL:-http://localhost:8080}

echo "测试优雅降级（需要配置错误 DEEPSEEK_API_KEY）..."
echo "  注意：本测试会消耗 2 次全局日限额，运行前请确保额度充足"

# 阻塞接口
RESPONSE=$(curl -s -X POST "$BASE_URL/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"What is AMM?"}')

if echo "$RESPONSE" | grep -q "当前 AI 服务暂不可用"; then
  echo "  阻塞接口降级文案正确 PASS"
else
  echo "  实际响应: $RESPONSE FAIL"
  exit 1
fi

# 流式接口
RESPONSE_STREAM=$(curl -s -N -X POST "$BASE_URL/api/chat/stream" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"message":"What is AMM?"}')

if echo "$RESPONSE_STREAM" | grep -q "当前 AI 服务暂不可用"; then
  echo "  流式接口降级文案正确 PASS"
else
  echo "  实际响应: $RESPONSE_STREAM FAIL"
  exit 1
fi

echo "test-graceful-degradation.sh PASS"
