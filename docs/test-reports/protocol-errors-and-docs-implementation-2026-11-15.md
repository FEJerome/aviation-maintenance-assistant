# 协议错误契约与文档修复实施报告 · 2026-11-15

> 执行：项目执行会话
> 范围：独立复审必修项——405/415 错误契约修复＋文档过时引用修复；另含项目空间文档首次建设（仓库贡献指南重写＋记忆文档建立）
> 批准：2026-11-15 经项目维护者批准按所列范围施工
> 基线：独立工作分支（基线 `bc148f4`）

## 一、结论

**两项必修缺陷全部修复并验证通过；项目空间文档建设完成。** 全部改动在独立工作分支内未提交，待确认提交范围后本地 commit；未 push、未 merge。

## 二、协议错误契约修复（405/415）

**根因**：`ApiExceptionHandler` 的 `@ExceptionHandler(Exception.class)` 兜底吞掉 Spring MVC 协议异常家族，`HttpRequestMethodNotSupportedException`（405 语义）与 `HttpMediaTypeNotSupportedException`（415 语义）均返回 500，与此前已修的退役路径 404 缺陷完全同源。

**修复**（照已修的 404 handler 模式，文件 `backend/src/main/java/.../chat/api/ApiExceptionHandler.java`）：

| 异常 | HTTP | code | message |
|------|------|------|---------|
| `HttpRequestMethodNotSupportedException` | 405 | `METHOD_NOT_ALLOWED` | The request method is not supported. |
| `HttpMediaTypeNotSupportedException` | 415 | `UNSUPPORTED_MEDIA_TYPE` | The request content type is not supported. |

错误码采用项目既有 SCREAMING_SNAKE 语义码命名（与 `INVALID_REQUEST`/`NOT_FOUND` 同族）；两个 handler 均走统一 `response()` 通路：含 traceId、log.warn 记 traceId/code/exceptionType、不含内部细节。

**回归用例**（`ChatControllerTest`，仿已登记的退役路径 404 回归写法，均 `verifyNoInteractions(chatService)`）：

- `wrongMethodReturns405`：`GET /api/chat` → 405＋`METHOD_NOT_ALLOWED`＋traceId 非空
- `unsupportedMediaTypeReturns415`：`POST /api/chat`（Content-Type: text/plain）→ 415＋`UNSUPPORTED_MEDIA_TYPE`＋traceId 非空

## 三、文档修复

1. **`backend/README.md` 重写为指针式文档**：删除全部过时引用（已删除的 AI Service 类、退役的 `/chat` 与 `/admin/ingest` 路径、已废弃的 `langchain4j.open-ai.*`/`app.rag.*` 配置键、已删除的硬编码相对路径实现细节），保留启动入口、结构速览与权威文档指针表（根 README、DEPLOY×2、CONFIGURATION、API×2、ARCHITECTURE、scripts/tests/README）。
2. **`DEPLOY.md` / `DEPLOY.zh-CN.md` 手动启动一节**：改为现行 `AMA_DATA_PATH` 语义——"语料目录由 `AMA_DATA_PATH` 指定，默认 `./data`，相对后端进程工作目录；从 `backend/` 启动即命中默认，否则显式配置"，消除对已删除实现细节的引用。
3. 两版 DEPLOY 其余 `data/` 引用逐行核对，均为现行 `AMA_DATA_PATH`/卷语义，未动。

## 四、验证（本会话实测）

| 项目 | 命令/环境 | 结果 |
|---|---|---|
| 后端全量测试 | `./mvnw.cmd test` | **Tests run: 109, Failures: 0, Errors: 0, Skipped: 0**（此前基线 107＋新增 2；ChatControllerTest 6/6） |
| 前端单元测试 | `npm test` | **Test Files 3 passed (3)，Tests 43 passed (43)** |
| 前端生产构建 | `npm run build` | **成功**，34 modules，dist JS 184.83 kB（gzip 69.89 kB，与此前基线一致） |
| loopback 新契约实测 | 后端 18091（local-only→fake 18089→Chroma 18090） | `GET /api/chat`→**405** `METHOD_NOT_ALLOWED`；`POST /api/chat` text/plain→**415** `UNSUPPORTED_MEDIA_TYPE`；`GET /api/admin/ingest`→**405**；退役 `POST /chat`→**404**（回归不破） |
| 集成脚本 | `BASE_URL=...:18091 test-api-prefix.sh` | **PASS** |

环境重建按 `scripts/tests/README.md`：`docker start ama-chroma-test`（18090）→ fake server（18089）→ 后端 env 模板；语料/状态用 `%TEMP%` 下独立隔离目录。验证后已停止本会话后端实例（18091 已释放）；Chroma 容器 `ama-chroma-test` 与 fake server 保留存活。

统计备注：`target/surefire-reports` 可能残留已删除测试类的旧报告（如 `AssistantSystemPromptTest.txt` 7 条），直接全量聚合会虚高为 116；当次运行实为 109，统计时只算当次运行文件。

## 五、项目空间文档首次建设

1. **仓库根 `CLAUDE.md` 全文重写**（git 忽略，不产生仓库 diff）：由早期版本改写为现行项目宪法——定位与路线（不跳步、不自动启动未授权任务）、仓库与工作分支现场、有效技术约束冻结表（Temurin JDK 21、Spring Boot 3.5.x、LangChain4j 1.14.1、Chroma 0.5.5 锁定、错误契约、`AMA_DATA_PATH` 语义、视觉呈现层封存、升级与真实模型验收待批准）、协作纪律（任务执行序与 git 分工、loopback-only 测试、后台会话新开不 resume、子智能体显式指定、无人值守通例）、文档分层机制。删去全部过时叙事与私人信息。
2. **项目记忆文档首批 5 个事实文件＋索引**：执行序与状态指针、测试基线数字、loopback-only 测试纪律、联调环境重建步骤、Chroma 0.5.5 锁定原因；未复制任何私人画像。

## 六、挂账与后续

1. 根 `README.md` 手动启动一节仍有"从 backend/ 目录启动"的建议字样（未引用已删实现，文档修复范围外）——未动，挂账。
2. 独立复审观察项 7 条不在本次清除范围，留档归后续里程碑。
3. 待办：零上下文新会话独立复审（本报告＋diff 为输入）；复审 PASS 后由项目维护者合并 main 并推送公开仓。
4. Chroma 容器与 fake server 存活占用 18090/18089，后续会话可直接复用。

## 七、改动文件清单（独立工作分支，全部未提交）

修改：`backend/README.md`、`DEPLOY.md`、`DEPLOY.zh-CN.md`、`backend/src/main/java/.../chat/api/ApiExceptionHandler.java`、`backend/src/test/java/.../chat/api/ChatControllerTest.java`
（API handler 与 ChatControllerTest 为本阶段既有未跟踪成果文件，本次在其上追加。）
新增：本报告 `docs/test-reports/protocol-errors-and-docs-implementation-2026-11-15.md`

项目空间（仓库外/忽略区）：仓库根 `CLAUDE.md`（重写）、项目记忆文档目录 6 文件。
