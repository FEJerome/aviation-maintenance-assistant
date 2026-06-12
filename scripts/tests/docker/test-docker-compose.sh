#!/bin/bash
set -e

echo "测试 Docker Compose 一键启动..."
echo "  注意：首次构建较慢，请等待 docker compose up -d 完全就绪后再执行"

# 确保服务已启动
docker compose ps | grep -E "backend|frontend|chromadb" > /dev/null || {
  echo "  docker compose 服务未运行 FAIL"
  exit 1
}

# 验证前端可访问
STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost)
if [ "$STATUS" -eq 200 ]; then
  echo "  前端访问: $STATUS PASS"
else
  echo "  前端访问: $STATUS FAIL"
  exit 1
fi

# 验证后端反向代理
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"test"}')
if [ "$STATUS" -eq 200 ] || [ "$STATUS" -eq 400 ] || [ "$STATUS" -eq 429 ]; then
  echo "  /api/chat 代理: $STATUS PASS"
else
  echo "  /api/chat 代理: $STATUS FAIL"
  exit 1
fi

echo "test-docker-compose.sh PASS"
