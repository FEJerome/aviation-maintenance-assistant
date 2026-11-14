# backend — Aviation Maintenance Assistant

Spring Boot 3.5（Java 21）实现的后端服务：RAG 检索问答、机型准入验证、文档摄入、LLM 配额与限流。

> 本文件只保留后端入口信息；**权威文档以根目录与 `docs/` 为准**（见文末指针），此处不重复维护细节，避免再次过时。

## 启动与配置

- 启动方式、环境变量与部署：根目录 [DEPLOY.md](../DEPLOY.md)（中文见 [DEPLOY.zh-CN.md](../DEPLOY.zh-CN.md)）
- 全部配置键与环境变量：[../docs/CONFIGURATION.md](../docs/CONFIGURATION.md)
- 语料目录由 `AMA_DATA_PATH` 指定，默认 `./data`，**相对后端进程工作目录**；从 `backend/` 目录内启动即可命中 `backend/data/`，或以显式路径启动
- LLM 连接（generic 三件套 `AMA_LLM_BASE_URL` / `AMA_LLM_API_KEY` / `AMA_LLM_MODEL`）与 `AMA_RUNTIME_MODE=local-only` 约束：见 [../docs/CONFIGURATION.md](../docs/CONFIGURATION.md)

## 开发

```bash
./mvnw.cmd test          # 全量单元/切片测试（Windows；macOS/Linux 用 ./mvnw）
./mvnw.cmd spring-boot:run
```

loopback 联调环境（fake OpenAI ＋ 隔离 Chroma）：[../scripts/tests/README.md](../scripts/tests/README.md)

## 结构速览

```
src/main/java/cn/pandazi/aviation_maintenance_assistant/
├── chat/        # /api/chat 问答接口、机型提取与主题路由、会话、Prompt、GroundedChatService
├── document/    # /api/admin/ingest 摄入接口、内容寻址、Tika/Markdown 解析
├── rag/         # Chroma 检索、查询翻译
├── validation/  # 机型-发动机匹配验证
├── config/      # LLM 解析、local-only 端点守卫、限流、AmaProperties
└── service/     # LlmCallExecutor（唯一模型调用边界）、LlmQuotaService
```

## 接口契约

REST 接口与错误码：[../docs/API.md](../docs/API.md)（中文见 [../docs/API.zh-CN.md](../docs/API.zh-CN.md)）

## 文档指针

| 主题 | 位置 |
|------|------|
| 项目总览、演示与截图 | [根目录 README.md](../README.md) |
| 部署与运维 | [DEPLOY.md](../DEPLOY.md) / [DEPLOY.zh-CN.md](../DEPLOY.zh-CN.md) |
| 配置键全表 | [docs/CONFIGURATION.md](../docs/CONFIGURATION.md) |
| API 契约 | [docs/API.md](../docs/API.md) / [docs/API.zh-CN.md](../docs/API.zh-CN.md) |
| 架构 | [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) |
| 测试脚本 | [scripts/tests/README.md](../scripts/tests/README.md) |
