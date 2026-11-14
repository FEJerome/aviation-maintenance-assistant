# 测试脚本说明

所有默认脚本只访问 loopback fake 或确定性本地业务路径，不得配置真实模型、真实 key 或生产服务。

## 隔离环境

1. 启动隔离的本地 ChromaDB，并将 `AMA_CHROMA_BASE_URL` 指向其实例。
2. 将 `examples/quickstart/` 中的合成 Markdown 复制到独立的 `AMA_DATA_PATH`。
3. 启动 fake OpenAI-compatible server：

   ```bash
   node scripts/tests/support/fake-openai-server.mjs
   # 默认仅监听 127.0.0.1:18089
   ```

4. 启动后端时使用：

   ```bash
   export AMA_LLM_BASE_URL=http://127.0.0.1:18089/v1
   export AMA_LLM_API_KEY=test-only-credential
   export AMA_LLM_MODEL=synthetic-chat
   export AMA_LLM_DAILY_QUOTA=3
   export AMA_RATE_LIMIT_PER_IP_PER_HOUR=2
   ```

5. 先调用 `POST /api/admin/ingest`，确认合成语料报告成功。fake 的 `GET /__calls` 返回安全调用计数，`POST /__reset` 重置，`POST /__mode` 可设置 `ok`、`http400` 或 `streamError`。

## 脚本

```bash
bash scripts/tests/integration/test-api-prefix.sh
bash scripts/tests/integration/test-rate-limit.sh
bash scripts/tests/integration/test-quota.sh
bash scripts/tests/integration/test-graceful-degradation.sh
bash scripts/tests/integration/test-internal-ip.sh
bash scripts/tests/docker/test-docker-compose.sh
```

限流脚本使用本地 400 请求，不消耗模型配额。代理脚本验证伪造 XFF 不会改变 loopback 信任决策；真实 remoteAddr 与 trusted-proxy 矩阵由后端单元测试覆盖。Compose 脚本使用确定性的机型不匹配请求，不调用模型。

真实 OpenAI-compatible 供应商、真实本地大模型、公网 IP、HTTPS 和生产 Nginx 只能在单独发布验收中检查，不能用这些脚本的结果冒充覆盖。

## 视觉走查

`scripts/tests/e2e/visual-foundation.mjs` 使用本机 Chromium 系浏览器的 CDP 和 Node 内置 WebSocket，不新增依赖。启动浏览器时开放仅 loopback 的调试端口 19222，并启动前端开发服务器 127.0.0.1:5173。按场景设置 `VISUAL_SCENARIO=clarification|citations|mismatch|degraded|stream-error|mobile` 后运行：

```bash
node scripts/tests/e2e/visual-foundation.mjs
```

- `citations` / `mobile`：fake 为 `ok`，已摄入合成语料。
- `degraded`：后端设置 `AMA_LLM_STREAMING_ENABLED=false`。
- `stream-error`：fake 通过 `/__mode` 设置为 `streamError`。
- `clarification` 与 `mismatch` 不需要模型成功响应。

截图与 JSON 结果写入 `docs/assets/screenshots/foundation-*`。结果包含 fake attempt 数、来源/降级/错误状态、浏览器异常和移动端溢出检查。脚本准备不等于视觉验收完成；最终报告必须记录实际执行结果和未覆盖场景。
