# 功能设计：测试策略

## 背景

项目进入最后一天冲刺阶段，需要在有限时间内完成：编码、本地验证、Docker 集成验证、生产部署验证。为了系统化管理测试活动，避免遗漏，需要明确：

1. 测试脚本放哪里。
2. 哪些功能在开发环境可验证，哪些必须到 Nginx/生产环境验证。
3. 如何降低测试成本（如避免真实等待 1 小时才能验证 IP 限流）。

## 目标

1. 建立统一的测试脚本目录 `scripts/tests/`。
2. 区分开发环境测试、Docker Compose 集成测试、生产环境补充验证。
3. 所有核心功能都有明确的验收脚本或手动步骤。
4. 编码时预留可配置、可测试的扩展点。

## 测试脚本目录结构

```
scripts/
└── tests/
    ├── README.md                    # 测试脚本使用说明
    ├── integration/                 # 后端集成测试脚本
    │   ├── test-api-prefix.sh
    │   ├── test-rate-limit.sh
    │   ├── test-quota.sh
    │   ├── test-graceful-degradation.sh
    │   └── test-internal-ip.sh
    ├── e2e/                         # 端到端测试脚本
    │   ├── test-chat-flow.sh
    │   └── test-guide-modal.sh
    └── docker/                      # Docker Compose 部署验证
        └── test-docker-compose.sh
```

### 目录说明

| 目录 | 用途 | 执行环境 |
|------|------|---------|
| `integration/` | 验证后端接口、限流、降级、内网限制等 | 本地开发环境（Spring Boot + ChromaDB）|
| `e2e/` | 验证完整用户交互链路 | 浏览器 + 本地服务 |
| `docker/` | 验证 Docker Compose 一键启动 | Docker Desktop / Linux |

### 与自动化测试框架的边界

- `scripts/tests/`：手动/半自动验收脚本，用 `curl`、浏览器、Shell 完成，适合冲刺阶段快速验证。
- `backend/src/test/`：JUnit 单元测试和 `@SpringBootTest` 集成测试（未来逐步补充）。
- `frontend/src/test/` 或 `frontend/e2e/`：Vitest / Playwright 自动化测试（未来逐步补充）。

## 三层验收策略

### 第一层：开发环境逻辑验证

**环境**：本地启动后端（`./mvnw spring-boot:run`）+ ChromaDB Docker + 前端 `npm run dev`。

| 功能 | 验证方式 | 可测性 |
|------|---------|--------|
| `/api` 前缀 | curl `/api/chat` 200，`/chat` 404 | ✅ 完全可测 |
| IP 限流 | 循环请求 21 次，第 21 次返回 429 | ✅ 可测（需配置测试阈值）|
| 全局日限额 | 临时调低 `DAILY_LIMIT`，验证超额后降级 | ✅ 完全可测 |
| 优雅降级 | 配置错误 API Key，验证返回固定文案 | ✅ 完全可测 |
| `/admin/ingest` 后端白名单 | 用 `X-Forwarded-For: 8.8.8.8` 模拟外网 IP | ✅ 完全可测 |
| 项目介绍模式 | 浏览器手动验证弹窗显示/关闭 | ✅ 完全可测 |

### 第二层：Docker Compose 集成验证

**环境**：`docker compose up -d` 启动全部服务。

| 功能 | 验证方式 | 可测性 |
|------|---------|--------|
| 前端 Nginx 托管 | 访问 `http://localhost` 看到前端页面 | ✅ 完全可测 |
| 前端反向代理 `/api` | 访问 `http://localhost/api/chat` 被代理到后端 | ✅ 完全可测 |
| 前端 SPA 路由回退 | 直接访问子路由后刷新，Nginx `try_files` 回退到 `index.html` | ✅ 完全可测 |
| 服务编排 | `docker compose ps` 三个服务均 healthy | ✅ 完全可测 |

### 第三层：生产环境补充验证

**环境**：云服务器 + Nginx + 域名 + HTTPS。

| 功能 | 验证方式 | 可测性 |
|------|---------|--------|
| 真实 IP 透传 | 通过多个公网 IP 访问，验证限流按真实客户端 IP 生效 | ⚠️ 必须生产环境 |
| Nginx 层 `/admin/ingest` 限制 | 从公网访问 `/api/admin/ingest`，Nginx 直接 403 | ⚠️ 必须生产环境 |
| HTTPS 跳转 | 访问 80 端口自动 301 到 443 | ⚠️ 必须生产环境 |
| 域名解析 | 通过域名访问完整链路 | ⚠️ 必须生产环境 |
| 防火墙 + 安全组 | ChromaDB 不对外暴露 | ⚠️ 必须生产环境 |

## 降低测试成本的设计

### IP 限流阈值可配置

生产默认：

```yaml
rate-limit:
  chat:
    capacity: 20
    refill-period: 1h
```

测试环境/手动测试时可调低：

```yaml
# application-test.yml 或临时修改
rate-limit:
  chat:
    capacity: 2
    refill-period: 10s
```

这样测试时不必真实等待 1 小时。

### 全局日限额可配置

```yaml
deepseek:
  quota:
    daily-limit: 500
```

测试时临时改为 3，可快速验证额度耗尽降级。

### 测试脚本执行顺序与隔离

部分脚本会消耗全局日限额或触发 IP 限流，建议按以下顺序执行：

1. `test-api-prefix.sh`
2. `test-internal-ip.sh`
3. `test-graceful-degradation.sh`（需错误 API Key，消耗 2 次配额）
4. `test-quota.sh`（需正确 API Key，临时调低日限额）
5. `test-rate-limit.sh`（需确保配额未耗尽）

如果一次测试失败导致状态污染，可手动删除 `backend/data/daily-llm-quota.json` 重置全局日限额。

### 测试脚本可独立执行

每份脚本输出 `PASS` / `FAIL`，并打印关键结果。例如：

```bash
./scripts/tests/integration/test-rate-limit.sh
# 输出：
# 第 1 次：200
# ...
# 第 21 次：429
# IP 限流测试 PASS
```

## 验收方式

1. 所有 `scripts/tests/integration/` 脚本在开发环境执行通过。
2. 所有 `scripts/tests/docker/` 脚本在 Docker Compose 环境执行通过。
3. 生产部署后，手动完成 `DEPLOY.md` 中的"生产环境补充验证"清单。

## 风险与回滚

| 风险 | 应对 |
|------|------|
| 测试脚本与代码不同步 | 每次代码变更后同步更新脚本；脚本作为文档的一部分 |
| 开发环境无法模拟真实 IP | 明确区分开发/生产可测项，不在开发环境硬测 Nginx 行为 |
| 测试阈值配置遗漏提交 | 测试阈值仅放在 `application-test.yml` 或脚本注释中，不修改生产默认配置 |

## 关键文件

- `scripts/tests/README.md`
- `scripts/tests/integration/*.sh`
- `scripts/tests/docker/*.sh`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-test.yml`（可选）

## 相关文档

- [FEATURE-rate-limiting](FEATURE-rate-limiting.md)
- [FEATURE-graceful-degradation](FEATURE-graceful-degradation.md)
- [FEATURE-frontend-docker](FEATURE-frontend-docker.md)
- [DEPLOY.md](../../DEPLOY.md)
