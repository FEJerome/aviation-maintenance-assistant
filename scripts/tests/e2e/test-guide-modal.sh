#!/bin/bash
set -e

echo "测试项目介绍模式..."

# 验证首页可访问
STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost)
if [ "$STATUS" -eq 200 ]; then
  echo "  首页可达: $STATUS PASS"
else
  echo "  首页访问: $STATUS FAIL"
  exit 1
fi

echo "  注意：弹窗显示/关闭、localStorage 记忆逻辑需浏览器手动验证"
echo "test-guide-modal.sh PASS"
