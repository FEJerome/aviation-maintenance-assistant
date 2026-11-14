# 自托管底座 · 独立复审报告 · 2026-11-13

> 复审：零上下文新眼独立会话（不携带实施侧上下文，全部数字为本会话实测）
> 对象：独立工作分支（基线 `main=bc148f4`），全部改动未提交
> 方法：全量 diff 逐文件审读＋独立复跑＋真实环境（loopback fake/Chroma）脚本验证
> 说明：本会话到场时本文件已存在一份同名复审产物（前次复审会话所写）。本会话未采信其结论，独立重做全部审查与复跑后重写本文件；两份报告结论趋同是同一对象、同一方法下的自然结果，其中 M1 沿用前次登记并经本会话独立实测确认与证据扩展，M2 为本会话新发现。

## 一、复审结论

**有条件放行。** 架构与代码质量达到合入标准；两个已登记脚本偏差确认属实，修复已存在于工作区且方向正确，本会话独立复跑双 PASS。必修项 **2 项**：

- **M1**（沿用前次登记，本会话实测确认并扩展证据）：`ApiExceptionHandler` 兜底吞掉 Spring MVC 协议异常家族，错误方法（405 语义）与错误 Content-Type（415 语义）均返回 **500 INTERNAL_ERROR**。与今日已修的 404 缺陷完全同源。
- **M2**（本会话新发现）：`backend/README.md` 全面过时（引用已删除的 `Assistant.java`、已退役的 `/chat` 路径、已废弃的 `langchain4j.open-ai.*` 配置键），且 `DEPLOY.md:127`／`DEPLOY.zh-CN.md:127` 引用已删除的 `Paths.get("data")` 实现细节。方案 §9.6 文档清单遗漏了 `backend/README.md`。

两项修复成本均低，建议合入前处理。其余为观察项，不阻塞。

## 二、独立复跑证据（本会话实测）

| 项目 | 命令 | 结果 |
|---|---|---|
| 后端全量测试 | `cd backend && ./mvnw.cmd test` | **Tests run: 107, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS**（10.43s，12:11 实测） |
| 前端单元测试 | `cd frontend && npm test` | **Test Files 3 passed (3)，Tests 43 passed (43)**（vitest 4.1.8，12:13 实测） |
| 前端生产构建 | `npm run build` | **成功**，vite 5.4.21，34 modules，dist JS 184.83 kB（gzip 69.89 kB） |
| Compose 语法（dev） | `docker compose config --quiet` | **OK** |
| Compose 语法（prod 模板） | `docker compose -f docker-compose.prod.yml.example config --quiet` | **OK** |
| Chroma 语料 | `GET /api/v1/collections/{id}/count` @18090 | **28 向量**（与联调报告一致，重复摄入无重复向量） |
| fake 最近请求 | `GET /__calls` @18089 | `model=synthetic-chat`、`stream=false`、`hasTemperature=false`——佐证指定 model 进入请求、blocking 不带 stream、generic 模式不发 temperature |

## 三、两个已登记脚本偏差的确认与复跑（授权范围内，仅此两处）

**偏差确认**：两脚本原使用 `"What is AMM?"`，在真实 Chroma（合成语料 28 向量）下零命中，按设计走 `NO_ANSWER` 短路、不进生成路径——降级断言（`LLM_PROTOCOL_ERROR`/`LLM_STREAMING_UNSUPPORTED`）与配额断言（`QUOTA_EXHAUSTED`、fake 计数）永不成立。偏差定性属实：产品行为正确、脚本假设错误。

**修复现状**：本会话到场时修复已存在于工作区——两脚本提问均已改为语料内命中问题 `"For B737-800 with CFM56-7B, what is the oil capacity of the PWR-40S?"`（与联调报告用例 8/9/12 一致），并附注释说明零命中短路是设计行为、断言必须使用命中问题。本会话审读确认修复方向正确，未再做任何代码改动。

**独立复跑验证**（本会话自起独立后端实例，127.0.0.1:18091，`local-only`，LLM 指向 fake 18089，Chroma 指向 18090，state 用 `%TEMP%` 下独立隔离目录，quota=3、限流关闭；验证后已停止实例并删除临时目录）：

| 脚本 | 结果 | 本会话实测行为 |
|---|---|---|
| `test-graceful-degradation.sh` | **PASS** | fake=http400 下 blocking 得 `LLM_PROTOCOL_ERROR`；SSE 得 `LLM_STREAMING_UNSUPPORTED` 且无后续 `complete` |
| `test-quota.sh` | **PASS** | 前 3 次放行、第 4 次 `QUOTA_EXHAUSTED`；fake `__calls` 恰为 **3**（本会话直接核实 `{"calls":3}`），超限请求不产生模型调用 |

注：两脚本不能在同一 quota=3 实例上连跑（降级脚本的 2 次真实 attempt 会消耗配额），本会话按"降级 → 清 state 重启 → 配额"顺序执行。

## 四、分领域审查记录

### 1. 通用 OpenAI-compatible 配置与 local-only 校验 —— 通过

- `LlmSettingsResolver`：generic 三连接项（base-url/api-key/model）任一见即选 generic 且缺项 fail-fast，不用 legacy 值补齐；legacy `DEEPSEEK_API_KEY` 仅 cloud 模式映射，local-only 直接拒绝（符合 D4）；`ResolvedLlmSettings.toString()` 脱敏 apiKey。
- `LocalOnlyEndpointGuard`：解析全部 DNS 地址逐一判定；loopback/RFC1918/ULA（含 IPv4-mapped）放行，**link-local、multicast、any-local、未解析一律拒绝**（符合 D6 拍板，比方案 §4.3 更严）；cloud 模式非本地 LLM 端点强制 HTTPS；拒绝 userinfo/query/fragment；不自动补 `/v1`。
- `OpenAiConfig`：temperature 未配置不发送（fake `hasTemperature=false` 实测佐证）；`logRequests/Responses=false`；blocking 显式 `maxRetries(0)`，保证 1 acquire = 1 HTTP attempt。

### 2. executor 配额按真实 attempt —— 通过

- `LlmCallExecutor` 是唯一模型调用边界（聊天/流式/翻译共用），调用前 `acquire()`，失败也计；streaming 只 acquire 一次；错误归一为固定安全文案，不带 provider body；日志仅 traceId/code/异常类型/httpStatus。
- `LlmQuotaService`：UTC 日期、单进程 synchronized、临时文件文件＋fsync＋ATOMIC_MOVE；状态损坏/不可写 fail-closed（`QUOTA_STATE_UNAVAILABLE`）；禁用时不读写状态文件；dailyLimit≤0 启动即失败。
- 中文查询翻译经 executor 计 1 次 attempt；零命中短路不 acquire 生成配额（联调报告与本会话 fake 计数一致）。

### 3. 摄入幂等与内容寻址 —— 通过

- `DocumentIdentity` 与方案 §5.2 逐字一致：`document_id=doc_+SHA-256(raw bytes)`；`excerpt_id` 绑定 `recursive-500-50-v1`＋chunk 序号＋精确文本；`embedding_id=excerpt_id`；完整 64 位 hex。
- 只扫顶层目录、文件名排序；同次扫描同内容别名 `DUPLICATE_CONTENT` 只处理首份；写入前按 `document_id` removeAll 再全量 addAll（非事务、重跑收敛的语义已在文档声明）；实测 28 向量无重复。
- `SourceDocumentParser`：PDF 走 Tika、Markdown 严格 UTF-8（malformed 即失败）；空文本拒绝；扩展名白名单（pdf/md/markdown），其余进 `ignored`。

### 4. citations 与零命中短路 —— 通过

- citations 直接来自实际注入 prompt 的 `RetrievedExcerpt`；metadata 缺失抛 `RETRIEVAL_METADATA_INVALID` 而非编造；`AviationPrompt` 明示 "Never invent page numbers, chapter numbers, document titles, or excerpt IDs"。
- 零命中三路（blocking/streaming/streaming-disabled fallback）均短路返回 `NO_ANSWER`，不调用生成模型；检索在翻译之后（设计语义：零命中可能有一次翻译 attempt、无生成 attempt，文档已如实表述）。
- 翻译失败抛 `QUERY_TRANSFORM_FAILED`，不悄悄用原查询继续；配额异常透传。

### 5. SSE 解析与 EOF 中断 —— 通过

- `sseParser.js` 按 SSE 规范实现：CRLF 跨 chunk 半对等待、仅去冒号后一个空格（不 trim token）、多行 data 拼接、冒号注释行、`flush()` 收尾派发。
- `App.vue`：reader `done` 且未收到 `complete`/`error` 时 fail"响应在完成事件到达前中断"；terminal 后 cancel reader；error 后不再处理后续事件。
- 后端 emit 路径 `terminated` 原子防护，error 后绝不发 complete（降级脚本断言覆盖）。
- nginx dev/prod 均有 `location = /api/chat/stream`＋`proxy_buffering off`＋`proxy_read_timeout 300s`。

### 6. 限流与 XFF 不信任 —— 通过

- `trustProxyHeaders` 默认 **false**，默认用 `getRemoteAddr()`；开启时仅取 XFF 首跳。
- admin 摄入门禁 `isInternalIp` 只接受**字面量 IP**（逐段校验，不 DNS 解析）；伪造 XFF 不改变默认模式下的信任决策。
- prod nginx 对 `/api/admin/ingest` 另有 IP allowlist 双保险；prod compose backend 无 host 端口映射，`AMA_TRUST_PROXY_HEADERS=true` 与注释前提（backend 不可直连）绑定。

### 7. Compose loopback / data:ro / state 独立 —— 通过

- dev compose 三服务端口全绑 `127.0.0.1`；`./backend/data:/app/data:ro` 只读；`./backend/state:/app/state` 独立可写；chroma 数据卷由 `AMA_CHROMA_DATA_PATH` 插值；`state/` 已入 `backend/.gitignore`。
- `.env.example` 全部为占位符；generic 三行注释要求"同时取消注释"；local-only 示例指向 loopback。

### 8. 机务准入（附加核对） —— 通过

- `AircraftInfoExtractor` 的 `DEFAULT_ENGINES` 已删除，CTLS/WT9 不再自动补 Rotax 912。
- `MaintenanceChatService` 统一 `prepare`：每轮 `TopicClassifier`；显式值优先、会话已验证值补齐（D7 合并规则）；MISMATCH/UNKNOWN 不写 session；MATCH 才写；blocking 与 streaming 消费同一 prepare 结果。

### 9. 公开仓文档 —— 通过（除 M2）

- 本次改动文档（README×2、DEPLOY×2、API×2、ARCHITECTURE、CONFIGURATION×2、MCP、ADR-008、examples×2、scripts/README、.env.example）关键词扫描（任务编号/内部称谓/handoff 路径/协作黑话/真实凭据/邮箱/真实域名）：**无内部黑话、无私人敏感信息、无真实 key**（`sk-your-...`、`sk-xxxxxxxx` 均为占位符）。
- main 基线已有内容说明：ADR-001～007 的"决策者：马昊宇（项目作者）"与 `docs/design/FEATURE-api-prefix.md` 的 `ama.pandazi.cn` 均为基线历史内容，本次未改动（逐一核对 git diff 确认），不属本次范围。
- 输入校验实测：空 message／超 4000 字／非法 UUID conversationId 均正确返回 **400**。

## 五、必修清单（合入前处理）

| # | 缺陷 | 证据（本会话实测） | 建议 |
|---|---|---|---|
| M1 | `ApiExceptionHandler` 兜底 `@ExceptionHandler(Exception.class)` 吞掉 Spring MVC 协议异常家族：`HttpRequestMethodNotSupportedException`（405 语义）与 `HttpMediaTypeNotSupportedException`（415 语义）均返回 **500 INTERNAL_ERROR**。与今日已修的 `NoResourceFoundException`→404 完全同源 | `curl -X GET /api/chat` → 500；`curl -X GET /api/admin/ingest` → 500；`POST /api/chat` with `Content-Type: text/plain` → 500（对照：退役 `/chat` 正确 404，三种非法请求体正确 400） | 照 404 模式补 `@ExceptionHandler(HttpRequestMethodNotSupportedException.class)` → 405＋`METHOD_NOT_ALLOWED`；可一并考虑 415；在 `ChatControllerTest` 加回归用例（仿 `retiredPathReturns404`） |
| M2 | `backend/README.md` 全面过时：引用已删除的 `Assistant.java`（第 66 行）、已退役的 `/chat` 与 `/admin/ingest` 路径（第 93-94 行）、已废弃的 `langchain4j.open-ai.*`／`app.rag.*` 配置键（第 122-127 行）、已删除的 `Paths.get("data")`（第 5 行）；`DEPLOY.md:127`／`DEPLOY.zh-CN.md:127` 同样引用 `Paths.get("data")` | 本会话逐行核对：`Assistant.java` 与 `ingestDocumentSafe` 已不存在，`application.yaml` 已无 `langchain4j.open-ai.*` | 方案 §9.6 文档清单遗漏了该文件。建议：`backend/README.md` 重写为简短指针（指向根 README＋docs/CONFIGURATION）或同步更新；DEPLOY 双版第 127 行改为 `AMA_DATA_PATH` 语义（默认 `./data` 相对工作目录，故仍建议从 `backend/` 启动或显式配置） |

## 六、观察项（不阻塞，供后续里程碑参考）

1. `RateLimitFilter.parsePeriod`（第 70 行）与 `RateLimitProperties.validate` 使用无参 `String.toUpperCase()`：土耳其语等 locale 下含 `i` 的自定义周期值（如 `30min`）会产出 `30MİN` 致 `Duration.parse` 启动失败。建议 `toUpperCase(Locale.ROOT)`。（默认 `1h` 不受影响。）
2. `RateLimitFilter.buckets` 为无界 `ConcurrentHashMap`（每 IP 一桶，永不回收）。单实例演示语义已在文档声明；公网长期运行需定期清理或带 TTL 的实现。
3. `MaintenanceChatService.processStream`（第 57 行）prepare 阶段抛异常时以新随机 UUID 作 traceId 上报，无法与内部 trace 关联（prepare 内部 traceId 未暴露）。影响仅可观测性。
4. `App.vue` 的 `watch(messages)` 只在空列表时打开项目引导，无 else 分支——发送首条消息后引导不会自动关闭（需用户手动关）。UX 取舍，非缺陷。
5. `docs/test-reports/frontend-dependency-review-2026-11-12.md` 登记 1 critical（dompurify 3.4.8）＋7 high 依赖漏洞，处置须单独批准升级——维持原登记，不在本次范围。
6. README Quick Start 以 `DEEPSEEK_API_KEY` 为最短路径（README.md:151/171），与 generic-first 叙事存在轻微张力；迁移路径已明确文档化，可接受。
7. 历史设计/决策文档（ADR-001/005、FEATURE-llm-markdown-formatting、FEATURE-api-prefix、FEATURE-testing-strategy 等）仍引用 `Assistant.java`／旧路径／旧配置键。作为按实现时点冻结的历史快照可接受（本次已对三篇 FEATURE 加 Superseded 提示）；如需更严格，可在 docs 索引统一声明"设计文档反映实现时点状态"。

## 七、边界确认

- 本次复审全程仅 loopback fake/local：fake OpenAI（127.0.0.1:18089）、隔离 Chroma 容器 `ama-chroma-test`（127.0.0.1:18090）。未触真实云端模型、真实 key、生产服务。
- 未执行 commit/push/merge/clean/reset；本会话对仓库**零代码改动**（两个授权脚本的修复在到场时已存在，本会话仅审读确认＋独立复跑）。
- 自造资源已全部清理：18091 后端实例×2 已停止（端口确认释放）、`%TEMP%` 下独立隔离目录已删除；**Chroma 容器 `ama-chroma-test` 与 fake server 按要求保留**（核实存活）。
- 未覆盖：视觉实际验收（走查包待执行）、真实 OpenAI-compatible/真实本地大模型 smoke（留待发布候选阶段单独验收）、依赖升级（待批准）。
- 未使用子智能体。
