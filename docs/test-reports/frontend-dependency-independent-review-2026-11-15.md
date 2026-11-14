# 前端依赖升级独立复审报告 — 2026-11-15

> **复审对象**：前端依赖升级提交链 `73e590d..2f0afa0`（5 个提交，最终 HEAD `2f0afa0`）
> **复审方式**：零上下文独立复核——不采信实施报告结论，全部数字由本复审独立复跑/独立观测得出
> **结论**：**PASS（通过）**——变更面、版本、安全、测试、构建、真实浏览器行为全部核验一致；发现的 2 处异常行为经基线对照实验证明为升级前已存在的产品行为，非本次升级回归，按挂账登记（见 §六）
> **环境**：Node v24.13.1 / npm 11.10.0 / Microsoft Edge 131.0.2903.51（Chromium，headless）；Windows 10

---

## 一、变更面核验（git diff 73e590d..2f0afa0）

| 核验项 | 结果 |
|---|---|
| 变更文件清单 | 仅 3 个：`frontend/package.json`、`frontend/package-lock.json`、`docs/test-reports/frontend-dependency-upgrade-execution-2026-11-15.md`（新增）✅ |
| 产品源码 / 视觉呈现 / 配置 | 零改动 ✅ |
| 提交数与主题 | 5 个提交：runtime packages → test-tree http client → build tooling to Vite 7 → 执行报告 → 报告措辞中立化，与实施报告声明一致 ✅ |
| 提交作者 | 与仓库既有配置一致 ✅ |
| 公开红线 | 全量 diff 扫描：无从者名/内部称谓/账号会话/内部任务编号/分支名/真实凭据 ✅ |

## 二、版本核验（直接读取 lockfile，逐提交比对）

### 2.1 最终 lockfile（HEAD）

| 包 | 目标版本 | lockfile 实测 | 结论 |
|---|---|---|---|
| dompurify | 3.4.18 | 3.4.18 | ✅ |
| vue | 3.5.45 | 3.5.45 | ✅ |
| postcss | 8.5.33 | 8.5.33 | ✅ |
| nanoid | 3.3.20 | 3.3.20 | ✅ |
| source-map-js | 1.2.2 | 1.2.2 | ✅ |
| undici | 7.30.2 | 7.30.2 | ✅ |
| vite | 7.3.8 | 7.3.8（全树**单一**节点） | ✅ |
| @vitejs/plugin-vue | 6.0.11 | 6.0.11 | ✅ |
| vitest | 4.1.12 | 4.1.12 | ✅ |
| jsdom | 保持 29.1.1 | 29.1.1（未连带变动） | ✅ |
| esbuild | ≥0.27 | 0.28.2 | ✅ |
| lockfileVersion | — | 3 | ✅ |

### 2.2 「批 B 无独立 commit」独立验证

对链上每个提交的 lockfile 逐一扫描版本：

| 提交 | postcss | nanoid | source-map-js |
|---|---|---|---|
| 基线 73e590d | 8.5.15 | 3.3.12 | 1.2.1 |
| 批 A（9748c87） | **8.5.33** | **3.3.20** | **1.2.2** |

证实：批 A（`npm update dompurify vue`）刷新 vue 3.5.45 传递依赖树时，已将 postcss/nanoid/source-map-js 带入目标版本；批 B 为 no-op、无需独立 commit 的声明**属实**（由 lockfile 证据独立推出，非采信报告）。

### 2.3 vite 去重核验

- 基线 lockfile 存在**双 vite 节点**：5.4.21（顶层）+ 8.0.16（vitest 嵌套）。
- HEAD lockfile 为**单一 vite 7.3.8** 节点，嵌套 8.0.16 已消除。✅

### 2.4 lockfile 与 package.json 一致性

`npm ci` 严格按 lockfile 重建成功（若二者不同步 `npm ci` 会直接报错），一致性成立。✅

## 三、独立复跑（全部命令亲跑，未用 audit fix、未升级任何依赖）

在 `frontend/` 下按序执行：

| 步骤 | 命令 | 实测结果 |
|---|---|---|
| 重建 | `npm ci` | 成功；安装即报 `found 0 vulnerabilities` |
| 审计 | `npm audit` | **found 0 vulnerabilities** ✅（与实施报告「11 → 0」的终点一致） |
| 测试 | `npm test` | vitest 4.1.12：**Test Files 3 passed (3)，Tests 43 passed (43)** ✅ |
| 构建 | `npm run build` | vite v7.3.8，35 modules，`✓ built in 668ms` ✅ |

dist 实测（与实施报告数字逐项一致）：

| 产物 | 实施报告 | 复审实测 |
|---|---|---|
| index.html | 0.40 kB | 0.40 kB ✅ |
| CSS | 12.87 kB | 12.87 kB（gzip 2.82）✅ |
| JS | 193.12 kB | 193.12 kB（193,120 B，gzip 72.11）✅ |

## 四、真实浏览器补验（实施方仅做 jsdom，本复审补真实浏览器）

### 4.1 方法与隔离

- 浏览器：本机 Microsoft Edge 131.0.2903.51（Chromium 内核，headless），经 playwright-core 驱动（驱动库装于复审隔离目录，未进入项目依赖树）。
- 服务拓扑（`netstat` 实测绑定证据，**全部 loopback**）：
  - vite dev server：`TCP 127.0.0.1:5173`（升级版，HEAD）
  - vite dev server：`TCP 127.0.0.1:5174`（基线对照组，从基线提交独立解包 + 独立 `npm ci`，装于隔离目录）
  - 桩后端：`TCP 127.0.0.1:8080` 与 `TCP [::1]:8080`（提供 `GET /api/health` 与 `POST /api/chat/stream` SSE）
  - dist 静态服务：`TCP 127.0.0.1:5180`（生产构建产物冒烟）
- 未触任何真实模型、真实后端或外网页面；SSE 载荷为本地桩构造。验证结束后全部进程终止、端口已释放（复测 `netstat` 无监听残留）。

### 4.2 检查项与结果（升级版，13/13 通过）

| # | 检查项 | 结果 |
|---|---|---|
| 1 | 应用真实浏览器启动 | ✅ |
| 2 | 结构消息渲染完成、无错误提示 | ✅ |
| 3 | Markdown 表格渲染（表头/单元格文本正确） | ✅ |
| 4 | 表格样式与升级前基线一致（见 §六-2，computed 值双侧相同） | ✅ |
| 5 | 代码块渲染且带 `hljs language-bash` 高亮类 | ✅ |
| 6 | 有序列表 3 项完整渲染 | ✅ |
| 7 | `<script>` 载荷在渲染产物中零存活 | ✅ |
| 8 | `onerror` 等 `on*` 事件属性零存活 | ✅ |
| 9 | `javascript:` href 零存活 | ✅ |
| 10 | XSS 无副作用执行（4 个探针全局标记均未被置位，渲染后追加等待复测） | ✅ |
| 11 | 控制台无任何 DOMPurify 相关消息/警告 | ✅ |
| 12 | 无页面级 JS 错误 | ✅ |
| 13 | `/api/health` 经 dev server 代理正确转发至 loopback 桩并返回预期 JSON（证明 `/api` 代理 loopback 行为不变；聊天 POST 亦全程经 5173 同源代理） | ✅ |

其他观测：

- 控制台仅有一条 `Failed to load resource: 404`——经响应监听核对，页面自身资源零 404，该条为浏览器默认请求 `/favicon.ico` 所致（`index.html` 未声明 favicon，基线与升级版表现相同，良性）。
- 生产构建产物冒烟（dist，127.0.0.1:5180）：应用启动正常、项目介绍浮层渲染正常、控制台零错误、零页面错误 → **DIST SMOKE PASS**。
- 截图人工复核：结构消息（表格/代码块/列表）、XSS 载荷（剥除后仅剩无害文本）、dist 启动页——版式、配色、间距与既有视觉一致，**未发现肉眼可见呈现变化**。

### 4.3 链接行为抽验（双侧一致）

经页面内 `import` 真实 `markdownRenderer` 模块渲染同一组链接载荷：

| 载荷 | 基线 | 升级版 |
|---|---|---|
| `https://` / `http://` 链接 | href 被剥除（渲染为纯文本） | href 被剥除（**一致**） |
| `ftp://` / `mailto:` / 相对路径 | href 保留 | href 保留（**一致**） |
| `javascript:` | href 被剥除 | href 被剥除（**一致**） |

独立证实实施报告「剩余风险 2」：http/https href 剥除为 `markdownRenderer` 自定义 URI 白名单的既有行为，**升级前后逐条一致，非回归**。

## 五、基线对照实验（回归归因的关键证据）

为判定 §六两处异常是否为升级引入，将基线提交（`73e590d`）的 `frontend/` 解包至隔离目录、独立 `npm ci`（vue 3.5.35 / dompurify 3.4.8 / vite 5.4.21），用**同一浏览器、同一桩、同一脚本**对照：

| 场景 | 基线（73e590d） | 升级版（HEAD） |
|---|---|---|
| 流式渲染（增量开）：表格/代码块/有序列表 | hasTable=true / hasHljs=**false** / ol=**0** | hasTable=true / hasHljs=**false** / ol=**0**（**逐字节一致**） |
| 非流式渲染：表格 computed `border-collapse` | `separate` | `separate`（一致；`td` 边框、外边距亦一致） |
| 链接处理（§4.3） | 同上 | 逐条一致 |

**结论：两处异常在升级前即可复现且表现完全相同，均为既有产品行为，非本次依赖升级引入的回归。**

## 六、挂账登记（既有问题，与本次升级无关；修复需动产品源码，超出本批次授权）

1. **流式增量渲染尾部丢失（既有竞态）**：当 SSE 末批 token 与 `complete` 事件在同一事件循环帧到达时，增量渲染路径（增量：开，为默认）会丢失尚未固化的尾部段落（实测丢失代码块与有序列表，渲染残留 `<p>``</p>`）。非流式兜底路径（增量：关）渲染完整。基线与升级版行为一致。建议登记为后续产品缺陷单（watcher 时序/flush 顺序），不在本批次处理。
2. **非流式兜底路径 scoped CSS 不触达 v-html 内容（既有）**：`.message-content-markdown` 下的表格等样式未使用 `:deep()`，`v-html` 注入的节点不带 scoped 属性，导致表格无边框（computed `border-collapse: separate`）。流式路径使用 `:deep()` 不受影响。基线与升级版表现一致，登记备查。
3. http/https 链接 href 剥除：实施报告已登记（其剩余风险 2），本复审独立复测确认一致，不重复开立。

## 七、公开红线复核

- 全量 diff 与执行报告文本扫描：无从者名/内部称谓、无账号或会话标识、无内部任务编号、无开发分支/worktree 名称、无真实凭据（API Key/token）。
- 本报告同样遵循上述口径。

## 八、结论

**PASS**。前端依赖升级提交链（`73e590d..2f0afa0`）在变更面、目标版本、lockfile 一致性、安全审计（0 漏洞）、自动化测试（43/43）、生产构建（dist 数字一致）、真实浏览器渲染/XSS 剥除/代理行为各维度全部通过独立核验；「批 B 无独立 commit」声明经 lockfile 逐提交证据独立证实；发现的 2 处行为异常经基线对照实验证明为既有产品行为、非升级回归，已按挂账登记。建议进入合并流程，并将 §六-1/§六-2 纳入后续缺陷跟踪。
