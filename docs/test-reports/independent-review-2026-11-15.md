# 独立复审报告 · 2026-11-15

> 复审：独立 reviewer（零上下文新眼，不采信实施结论）
> 对象：本地提交 `e94f41579c9c7bbdc03c62d2f1936b9a51260f8c`（自托管底座），基线 `bc148f4`
> 方法：全量 diff 审读（111 文件，+6821/−2018）＋ 独立复跑 ＋ loopback live 实测 ＋ 公开红线核查
> 状态：链＝初审对象 `e94f415` → 初审报告 `400e762` → D1 返修 `5d6d2ea` → 本报告更新（复验收官）；全程未改任何产品代码

## 一、结论

**PASS（无条件通过）。** 初审为有条件放行，唯一必修项 D1 已经返修并复验清零（见 §五）；代码与测试全部独立复验通过，公开红线零命中。

- ~~必修 D1~~ → **已清零**：初审时 `docs/API.md` 与 `docs/API.zh-CN.md` 的 Errors 一节对 `404 NOT_FOUND`、`405 METHOD_NOT_ALLOWED`、`415 UNSUPPORTED_MEDIA_TYPE`、`500 INTERNAL_ERROR` 零提及（实测 grep 计数=0）；返修提交 `5d6d2ea` 在两版 Errors 节各补 4 行。

## 二、代码审查（diff 独立审读）

1. **405/415 与既有错误语义一致性 ✅** — `ApiExceptionHandler` 新增 `HttpRequestMethodNotSupportedException`→405/`METHOD_NOT_ALLOWED`、`HttpMediaTypeNotSupportedException`→415/`UNSUPPORTED_MEDIA_TYPE`，与既有 404（`NoResourceFoundException`）、400（`MethodArgumentNotValidException`+`HttpMessageNotReadableException`）、500 兜底同走统一 `response()` 通路：SCREAMING_SNAKE 语义码、含 traceId、响应体不含内部细节、log.warn 仅记 traceId/code/exceptionType。`@ExceptionHandler(Exception.class)` 兜底不再吞协议异常家族。
2. **ChatControllerTest 回归真实性 ✅** — `wrongMethodReturns405`、`unsupportedMediaTypeReturns415` 经 MockMvc 走完整 DispatcherServlet：若 advice 缺失或映射错误，Spring 默认行为返回 500/非语义体，断言 405/415＋code＋traceId 非空即证真实触达；`verifyNoInteractions(chatService)` 证协议拒绝先于业务。同文件 404/400/429 用例同构。与 §三 live 实测互证。
3. **generic LLM 解析 ✅** — `LlmSettingsResolver`：generic 三件套优先；`DEEPSEEK_API_KEY` 仅 cloud 模式 legacy 映射且在 local-only 下显式拒绝；cloud 模式缺 key 报错；apiKey 在 `toString` 中脱敏；超时/温度校验齐备。
4. **local-only ✅** — `LocalOnlyEndpointGuard`：拒绝 user-info/query/fragment、限 http/https、解析全部地址并要求全部落在 loopback/私网（拒绝 any-local/link-local/multicast）；cloud 模式非本地 LLM 端点强制 HTTPS。注释明确"配置守卫而非网络沙箱"，表述诚实。
5. **配额 attempt ✅** — `LlmQuotaService` 单进程 UTC 日配额，`acquire()` 先持久化后调用（原子替换＋fsync），失败也计一次 attempt，与"每次模型调用恰好一次预约"一致；`LlmCallExecutor` 是唯一调用边界，翻译查询（`TranslationQueryTransformer:42`）亦经其预约。
6. **摄入幂等 ✅** — `DocumentIngestionService` 内容寻址 document_id＋先 `removeAll(document_id)` 后 `addAll`，重复摄入同内容幂等；同批次重复内容记 `DUPLICATE_CONTENT`；解析失败与写入失败分类计数，报告状态机 COMPLETED/COMPLETED_WITH_ERRORS/FAILED 正确。
7. **citations ✅** — `GroundedChatService` 的 citations 来自实际送入模型的 excerpts（excerptId/documentId/title/chunkIndex/score），非句级证据验证，与 API 文档表述一致；检索为空返回 NO_ANSWER、空 citations、不消耗生成调用。
8. **SSE ✅** — NO_ANSWER 与 `STREAMING_DISABLED` 降级走"单 token＋complete"短路；运行时流式失败不重试 blocking；error 事件后无 complete；emitter 同步发送＋terminated 标志防写后错误。
9. **限流/XFF ✅** — `RateLimitFilter` 仅在 `trustProxyHeaders` 开启时取 XFF 首跳，默认 remoteAddr；admin 端点内网限制独立于此；XFF 字面量解析拒绝非法输入，伪造面由 `test-internal-ip.sh` 与单元测试覆盖。
10. **依赖与视觉 ✅** — diff 不含 `package.json`/`package-lock.json`/任何 `.css` 文件；前端样式增量仅 `data-notice` 提示条（功能文本展示，非视觉返工）；依赖升级仅为待批方案文档，未执行；构建产物 184.83 kB 与冻结基线一致。

## 三、独立复跑与 live 实测（全部 loopback，未触真实供应商/真实模型/公网）

环境：fake OpenAI（127.0.0.1:18089）、隔离 Chroma `ama-chroma-test`（127.0.0.1:18090）、独立后端 18091、独立临时语料/state 目录（复审后已删）。

| 项目 | 命令/方法 | 结果 |
|---|---|---|
| 后端全量测试 | `./mvnw.cmd test` | **Tests run: 109, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS（11.4s，当次聚合） |
| 前端单元测试 | `npm test` | **Test Files 3 passed (3)，Tests 43 passed (43)** |
| 前端生产构建 | `npm run build` | 成功，34 modules，**dist JS 184.83 kB（gzip 69.89 kB）**，与基线一致 |
| 405 | `GET /api/chat` → 18091 | **405** `{"code":"METHOD_NOT_ALLOWED",…,"traceId":…}` |
| 415 | `POST /api/chat`（Content-Type: text/plain） | **415** `{"code":"UNSUPPORTED_MEDIA_TYPE",…,"traceId":…}` |
| ingest 405 | `GET /api/admin/ingest` | **405** `METHOD_NOT_ALLOWED` |
| 退役路径 | `POST /chat` | **404** `{"code":"NOT_FOUND",…}` |
| 本地校验 | `POST /api/chat` 空 message | **400** `INVALID_REQUEST` |
| 集成脚本 | `BASE_URL=http://127.0.0.1:18091 bash scripts/tests/integration/test-api-prefix.sh` | **PASS** |
| 摄入幂等 | 连续两次 `POST /api/admin/ingest`（3 个合成 Markdown） | 两次均 COMPLETED，**documentId 完全一致**（内容寻址），excerptCount 8/7/… 稳定 |
| 非流式问答 | `POST /api/chat`（无机型关键词问题） | ANSWERED，citations 4 条字段齐全（excerptId/documentId/title/chunkIndex/score），fake 计 1 次调用 |
| 流式问答 | `POST /api/chat/stream` 同问题 | token×2＋单次 complete（citations 同构），fake 计 `stream=true` 调用；两次问答合计配额消耗=2，与"一次调用一次预约"吻合 |
| 业务短路 | 含 engine/oil 关键词问题（blocking＋stream） | 均 CLARIFICATION_REQUIRED，**不消耗模型调用**（fake 计数不变），SSE 短路形态与文档一致 |

## 四、公开红线核查

- 对 diff 全量及变更文件正文 grep：从者名、御主、内部任务编号（AMA-00x）、账号/会话标识、worktree/分支名、私人企划、真实凭据 —— **零命中**。
- `sk-your-deepseek-api-key-here`/`sk-xxxxxxxx` 为占位符，非真实凭据。
- 两份 11 月报告行文提及仓库外贡献指南（git 忽略文件）的存在与主题（如协作纪律类目），不含私人信息与内部标识；不判违规，记观察项 O3 供 maintainer 知悉。

## 五、必修清单

| # | 项 | 状态 |
|---|---|---|
| D1 | `docs/API.md`、`docs/API.zh-CN.md` Errors 节补 404/405/415/500 | **已修复并复验清零（2026-11-15）**：返修提交 `5d6d2ea`（"docs: complete API error response catalog"）仅触两文件、各 +4 行（404 `NOT_FOUND`／405 `METHOD_NOT_ALLOWED`／415 `UNSUPPORTED_MEDIA_TYPE`／500 `INTERNAL_ERROR`），中英双语语义码一致、排序正确，档实相符（与 `ApiExceptionHandler` 已验行为一致）；commit message 公开面干净；未触代码，初审已过的全量测试无需重跑 |

**必修项余额：0。**

## 六、观察项（不阻塞，留档）

- O1：根 `README.md:169` 手动启动一节仍有 "must run from inside backend/" 字样（前份实施报告已挂账，延续有效）。
- O2：`RateLimitFilter.isInternalIp` 未将 IPv4-mapped IPv6（如 `::ffff:127.0.0.1`）识别为内网；默认配置下 remoteAddr 为纯 IPv4 不受影响，反代后开启 `trustProxyHeaders` 时由部署侧注意 XFF 来源可信。
- O3：报告类文档对仓库外治理文件的类目性描述（见 §四末条）。

## 七、资源与现场状态

- 18091 后端已停止（端口确认释放，curl 返回 000）；独立临时语料/state 目录已删除。
- fake server（18089）与 Chroma 容器 `ama-chroma-test`（18090）按交接约定保留存活。
- 提交链：初审对象 `e94f415`（自托管底座）→ 初审报告 `400e762` → D1 返修 `5d6d2ea` → 本报告更新（复验收官，提交哈希以 git 记录为准，不在本文自指）。
- 工作树干净；未 clean/reset/删 worktree、未改 main、未 merge、未 push。
