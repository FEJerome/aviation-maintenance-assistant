# 测试脚本说明

本目录存放手动/半自动验收脚本，用于最后一天冲刺阶段的快速验证。

## 目录结构

```
scripts/tests/
├── integration/     # 后端集成测试
├── e2e/             # 端到端测试
└── docker/          # Docker Compose 部署验证
```

## 前置条件

- 后端已启动：`cd backend && ./mvnw spring-boot:run`
- ChromaDB 已启动：`docker run -d --name chromadb -p 8000:8000 chromadb/chroma:0.5.5`
- 前端已启动：`cd frontend && npm run dev`
- 或直接使用：`docker compose up -d`

## 执行方式

### Linux / macOS / Git Bash

```bash
./scripts/tests/integration/test-api-prefix.sh
./scripts/tests/integration/test-rate-limit.sh
./scripts/tests/integration/test-quota.sh
./scripts/tests/integration/test-graceful-degradation.sh
./scripts/tests/integration/test-internal-ip.sh
```

### Windows PowerShell

```powershell
bash ./scripts/tests/integration/test-api-prefix.sh
```

## 测试环境配置

为避免真实等待 1 小时验证 IP 限流，测试时可使用 `application-test.yml`：

```yaml
rate-limit:
  chat:
    capacity: 2
    refill-period: 10s

deepseek:
  quota:
    daily-limit: 3
```

启动时指定 profile：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

## 生产环境补充验证

以下项目**无法**在开发环境完全模拟，需部署到生产后验证：

- 真实公网 IP 限流
- Nginx 层 `/api/admin/ingest` 内网限制
- HTTPS 跳转
- 域名访问
- ChromaDB 不对外暴露

详见 [DEPLOY.md](../../DEPLOY.md) 生产环境章节。

## 脚本编写规范

1. 每份脚本以 `#!/bin/bash` 开头。
2. 设置 `set -e`，遇到错误立即退出。
3. 使用 `BASE_URL` 变量，默认 `http://localhost:8080`。
4. 每个测试输出 `PASS` / `FAIL`。
5. 失败时打印预期值与实际值。
