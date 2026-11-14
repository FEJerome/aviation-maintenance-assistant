# 真实本地 Chroma 联调报告 · 2026-11-13

> 执行：项目实现会话（2026-11-13 下午恢复）
> 环境：真实 `chromadb/chroma:0.5.5` 容器（隔离名 `ama-chroma-test`，127.0.0.1:18090，独立 volume `ama-chroma-test-data`）＋ fake OpenAI server（127.0.0.1:18089）＋ 本地后端（Java 21 / Spring Boot 3.5.14）。
> 配置：`AMA_RUNTIME_MODE=local-only`，合成语料与 state 均在 `%TEMP%` 下独立隔离目录，不触碰仓库真实数据。
> 本文只记录真实 Chroma 联调证据；真实云端／真实本地大模型不在本次范围（留待发布候选阶段单独批准）。

## 一、主链路实测（全部通过）

| # | 用例 | 结果 | 证据 |
|---|---|---|---|
| 1 | 真实摄入 4 份合成 Markdown | `COMPLETED`，全部 `UPSERTED`，共 28 excerpt | documentId 内容寻址，collection=`aviation-docs` |
| 2 | 相同文件连续摄入两次 | 报告一致；Chroma 向量总数实测 **28**（4+8+7+9），无重复 | `/api/v1/collections/{id}/count`=28 |
| 3 | 缺机型提问 | `CLARIFICATION_REQUIRED`，`generationMode=NONE`，fake 调用 **0** | "What is the oil capacity of the PWR-40S?" |
| 4 | blocking 命中问答 | `ANSWERED`／`NON_STREAMING`，**5 条真实 citations**（score 0.62~0.71），fake +1 | "For B737-800 with CFM56-7B, ..." |
| 5 | SSE 流式问答 | `token` 事件×2＋`complete` 携带同 5 条 citations，`STREAMING`，fake +1 | 原始事件流已人工核对 |
| 6 | 机型/发动机不匹配 | `VALIDATION_BLOCKED`，fake 调用 **0** | "B737-800 PW1100G engine oil" |
| 7 | 语料外提问空命中 | `NO_ANSWER`（未在手册中找到相关内容），citations 空，fake 调用 **0** | "How do I bake sourdough bread at home?" |
| 8 | blocking 显式降级（fake=http400，命中问题） | HTTP **502** `LLM_PROTOCOL_ERROR` | 手工等效验证，见"脚本偏差" |
| 9 | SSE 显式降级（fake=http400，命中问题） | `event:error` `LLM_STREAMING_UNSUPPORTED`，**无后续 complete** | 同上 |
| 10 | `test-api-prefix.sh` | **PASS**（空消息 400；退役 /chat 404；摄入状态合法） | 修复缺陷后通过 |
| 11 | `test-internal-ip.sh` | **PASS**（伪造 XFF 不改变 loopback 信任决策） | — |
| 12 | 配额熔断（独立实例 quota=3、独立 state） | 前 3 次 `ANSWERED`，第 4 次 HTTP **429** `QUOTA_EXHAUSTED`，fake 计数恰为 **3** | 手工等效验证，见"脚本偏差" |
| 13 | `test-rate-limit.sh`（独立实例 per-ip=2） | **PASS**（400 请求序列中第 3 次起 429，不耗模型配额） | — |

fake 计数全程与真实 attempt 语义一致：澄清/阻断/空命中/配额熔断均不产生模型调用。

## 二、联调发现并已修复的缺陷（1 项）

**退役路径 `/chat` 返回 500 而非 404**（由用例 10 暴露）。
- 根因：`ApiExceptionHandler` 的兜底 `@ExceptionHandler(Exception.class)` 吞掉 Spring 的 `NoResourceFoundException`，映射为 `INTERNAL_ERROR`。
- 修复：新增 `handleNotFound`，返回 404＋`NOT_FOUND`＋traceId，不含内部细节。
- 回归：`ChatControllerTest` 新增 `retiredPathReturns404`；后端全量 **107/107 PASS，BUILD SUCCESS**（昨日基线 106＋本用例 1）。

## 三、脚本环境偏差（2 项，产品行为正确，脚本需修正）

`test-graceful-degradation.sh` 与 `test-quota.sh` 均使用 `"What is AMM?"` 作为提问。该问题在**真实 Chroma** 中对合成语料零命中，按设计走 `NO_ANSWER` 短路，不进入生成路径，因此：
- 降级脚本得不到 `LLM_PROTOCOL_ERROR`（实得 `NO_ANSWER`）；
- 配额脚本永远等不到 `QUOTA_EXHAUSTED`（空命中不耗配额）。

两脚本在 in-memory/fake 检索替身下隐含"必有命中"假设，不适用于真实 Chroma。**建议修正为使用语料内命中问题**（本报告用例 8/9/12 已用命中问题手工等效验证全部通过）。此修正留待独立复审确认后施工。

## 四、资源与边界

- 联调资源全部为本次自造：容器 `ama-chroma-test`、volume `ama-chroma-test-data`、fake server、`%TEMP%` 下独立隔离目录。
- 未调用任何真实云端模型；未提交、未推送、未合并。
- 后端测试复跑与脚本证据均可独立复现：起 Chroma（18090）＋fake（18089）后按 `scripts/tests/README.md` 配置即可。

## 五、仍未覆盖

- 视觉实际验收（走查包已备，待执行＋项目维护者终审）。
- 独立复审（零上下文新眼）。
- 前端依赖漏洞处置（dompurify 优先，升级须单独批准）。
- 真实 OpenAI-compatible 供应商／真实本地大模型 smoke（留待发布候选阶段单独验收）。
