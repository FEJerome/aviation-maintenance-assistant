# 前端依赖升级方案（预研审批版）— 2026-11-15

> **范围**：自托管底座前端依赖升级方案预研
> **性质**：只读预研 + 审批方案。**未执行任何升级、未安装/更新任何依赖、未修改 package.json / package-lock.json / 源码**。
> **漏洞状态**：本文档不宣称任何漏洞已修复。所有修复均待项目维护者逐项批准后另行执行。
> **前置报告**：`docs/test-reports/frontend-dependency-review-2026-11-12.md`

---

## 一、预研边界与证据基线

**本次预研执行过的只读动作**：

- `npm audit --json`（不带 `--fix`），快照时间 2026-11-15
- `npm ls dompurify vue vite postcss nanoid source-map-js undici`（依赖链确认）
- `npm view`（查询各包最新版本、peerDependencies、engines、npm 官方发布时间）
- WebFetch 查阅公开 GitHub Advisory 与 DOMPurify 官方 Releases 页面（未发送任何项目私有内容）
- 阅读源码：`frontend/src/utils/markdownRenderer.js`、`frontend/src/components/ChatMessage.vue`、`frontend/src/components/ProjectGuide.vue`、`frontend/vite.config.js`、`frontend/src/utils/markdownRenderer.test.js`

**未执行**：`npm test` / `npm run build`。理由：2026-11-12 评审报告已记录同一 lockfile（至今未变更，`git status` 证实）下测试与生产构建通过，基线有效，无需重建。本次预研**未在工作区产生任何构建产物**。

**审计快照（2026-11-15，npm audit）**：1 critical + 7 high + 3 moderate，共 11 项，与 2026-11-12 评审一致。

**运行环境**：Node v24.13.1 / npm 11.10.0（满足 vite 7/8、plugin-vue 6 的 `^20.19.0 || >=22.12.0` 要求；Docker/CI 镜像 Node 版本未核，见「仍不确定项」）。

---

## 二、Critical 项（1 项）

### C-1. dompurify 3.4.8 → 建议 3.4.18

| 维度 | 内容 |
|---|---|
| 严重级 | **critical**（直接生产依赖，`frontend/package.json` dependencies） |
| 依赖链 | `frontend`（直接）→ `node_modules/dompurify`，无传递中间层 |
| 当前版本 | 3.4.8（lockfile 精确锁定） |
| 目标版本 | **3.4.18**（npm 发布 2026-11-14）；最低可接受 3.4.17（2026-10-12） |
| 是否跨 major | **否**（3.x → 3.x，当前声明 `^3.4.8` 已覆盖目标版本，预计仅 lockfile 变更） |
| 官方来源 | GHSA-cg2g-xh64-v4q7（critical，SAFE_FOR_TEMPLATES 旁路，影响 `>=3.4.4 <=3.4.16`，**patched 3.4.17**）；另有 7 条 high/moderate/low advisory 覆盖至 3.4.16（hook 污染、IN_PLACE 子树、Trusted Types、SMIL/XHTML、CUSTOM_ELEMENT_HANDLING 等）。DOMPurify 官方 Releases 页确认 3.4.17/3.4.18 均为安全修复版本，**未声明 breaking change** |
| 影响 API 与代码路径 | 唯一调用点 `frontend/src/utils/markdownRenderer.js:83`：`DOMPurify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR, ALLOW_DATA_ATTR:false, ALLOWED_URI_REGEXP })`。下游经 `ChatMessage.vue`（`v-html="block.html"`、流式渲染）与 `ProjectGuide.vue`（`v-html`）注入 DOM。当前配置**未使用** `SAFE_FOR_TEMPLATES` / `IN_PLACE` / hooks / Trusted Types / custom elements，critical  advisory 的特定利用面（shadow root 模板表达式重组）在当前配置下不直接命中；但 DOMPurify 是本应用 XSS 边界的唯一守卫，且 8 条 advisory 中包含配置污染类旁路，**必须升级** |
| 3.4.18 行为注意点 | 官方 release notes：移除 SVG 白名单中"属性误列为元素"的条目（本应用白名单未放行 SVG，无影响）；新增 `USE_PROFILES` 与 `ALLOWED_TAGS/ATTR` 混用时的控制台警告（本应用未用 `USE_PROFILES`，不应出现警告） |

---

## 三、High 项（7 项）

> 计数说明：7 个 high 为 `npm audit` 的 7 个条目——vue、@vue/server-renderer、vite、postcss、nanoid、source-map-js、undici。其中 **vue 与 @vue/server-renderer 是两个独立条目，但同属一次 vue 升级修复**，下文合并为处理单元 H-1；vite 两个 lockfile 节点（直接 5.4.21 + vitest 嵌套 8.0.16）在 audit 中为单一条目，合并为 H-2。故 7 条目对应 6 个处理单元。

### H-1. vue 3.5.35 + @vue/server-renderer 3.5.35 → 建议 3.5.45（含 audit high 条目 ×2：vue、@vue/server-renderer）

| 维度 | 内容 |
|---|---|
| 严重级 | **high**（vue 为直接生产依赖；@vue/server-renderer 为其传递依赖） |
| 依赖链 | `frontend`（直接）→ `vue@3.5.35` → `@vue/server-renderer@3.5.35`（vue 以精确版本锁定全套 `@vue/*` 3.5.35）；`@vitejs/plugin-vue@5.2.4` 以 peer 引用 vue |
| 目标版本 | **3.5.45**（npm 发布 2026-11-05）；最低可接受 3.5.42（2026-08-27） |
| 是否跨 major | **否**（`^3.5.35` 已覆盖，预计仅 lockfile 变更） |
| 官方来源 | GHSA-g2v6-rqmx-r4w6（XSS via missing CR in attribute-name blacklist，影响 `<3.5.42`，CVSS 7.2） |
| 影响 API 与代码路径 | 应用为**客户端渲染**，无 SSR 挂载点，直接暴露面小；但 @vue/server-renderer 存在于生产依赖树（vue 默认捆绑）。本应用未直接调用其 API，影响面为 Vue 编译器/运行时整体行为，需组件级回归 |
| 联动 | `@vitejs/plugin-vue@5.2.4` 对 vue peer 要求 `^3.2.25`，3.5.45 满足，无需连带升级 |

### H-2. vite 5.4.21（直接）+ vite 8.0.16（vitest 嵌套）→ 建议 7.3.8 或 8.3.8

| 维度 | 内容 |
|---|---|
| 严重级 | **high**（直接 dev 依赖 + vitest 嵌套节点，两个 lockfile 节点同时命中） |
| 依赖链 | ① `frontend`（直接 dev）→ `vite@5.4.21` → `esbuild@^0.21.3`、`postcss@^8.4.43`、`rollup@^4.20.0`；② `frontend` → `vitest@4.1.8`（peer `vite ^6\|\|^7\|\|^8`）→ 嵌套 `vite@8.0.16` |
| 目标版本 | **方案一（推荐）：7.3.8**（npm 发布 2026-11-09）——保持 esbuild + rollup 架构；**方案二（audit 官方建议）：8.3.8**（2026-11-11）——rolldown 架构，esbuild 从依赖树中整体移除 |
| 是否跨 major | **是**（5 → 7 或 5 → 8），且 vite 7/8 要求 `@vitejs/plugin-vue` 6.x（5.2.4 仅支持 vite ^5/^6），`package.json` 必须变更 |
| 官方来源 | 12 条 advisory，影响范围 `<=6.4.3 \|\| 8.0.0–8.1.5`：high 为 GHSA-587h-7wmj-h5pm（`fs.existsSync()` 检查在 Windows 可被绕过，CWE-77）；其余为 moderate/low（路径穿越、launch-editor 命令注入、fs.deny 绕过、跨源脚本执行等）。当前 5.4.21 与嵌套 8.0.16 **双双在范围内** |
| 影响 API 与代码路径 | 仅构建/开发/测试链路。生产产物为静态文件，不运行 vite dev 中间件。`frontend/vite.config.js` 仅含 `@vitejs/plugin-vue` 插件与 `/api` 代理，无自定义插件，配置面极小，major 升级的配置迁移成本低 |
| 连带升级 | `@vitejs/plugin-vue 5.2.4 → 6.0.11`（跨 major，npm 发布 2026-11-12；peer 覆盖 vite ^5/^6/^7/^8、vue ^3.2.25）；`vitest 4.1.8 → 4.1.12`（**不跨 major**，`^4.1.8` 已覆盖；peer 覆盖 vite ^6/^7/^8，升级后嵌套 vite 8.0.16 与顶层 vite 去重合并且消失） |

### H-3. postcss 8.5.15 → 建议 8.5.33

| 维度 | 内容 |
|---|---|
| 严重级 | **high**（传递：构建链） |
| 依赖链 | `vite@5.4.21 → postcss@8.5.15`；`vue@3.5.35 → @vue/compiler-sfc → postcss@8.5.15`（去重共享同一节点） |
| 目标版本 | **8.5.33**（npm 发布 2026-11-13）；最低可接受 8.5.23 |
| 是否跨 major | **否**（传递依赖，`npm update postcss` 即可，仅 lockfile 变更） |
| 官方来源 | GHSA-r28c-9q8g-f849（sourceMappingURL 路径穿越致任意 .map 文件披露，影响 `<=8.5.17`，CVSS 7.5）；GHSA-fxqj-rqcc-2cmp（前一修复不完整，影响 `<=8.5.22`） |
| 影响 API 与代码路径 | 构建期 source map 处理，非浏览器运行时；本应用构建输入均为自有源码，利用面窄。注意 vite 7.3.8 要求 `postcss ^8.5.6`、vite 8.3.8 要求 `^8.5.32`，两路线均自然带到 8.5.33 |

### H-4. nanoid 3.3.12 → 建议 3.3.20

| 维度 | 内容 |
|---|---|
| 严重级 | **high**（传递：构建链） |
| 依赖链 | `vite@5.4.21 → postcss@8.5.15 → nanoid@3.3.12` |
| 目标版本 | **3.3.20**（postcss 8.5.33 要求 `nanoid ^3.3.20`）；最低可接受 3.3.18（npm 发布 2026-08-07） |
| 是否跨 major | **否**（`npm update nanoid` 即可，仅 lockfile 变更） |
| 官方来源 | GHSA-28wg-ghj8-5hjv（non-secure 生成器负 size 死循环，`<3.3.16`）；GHSA-2v37-7h3g-55p8（自定义生成器 size 为 0 死循环，`<3.3.18`），CVSS 5.9 |
| 影响 API 与代码路径 | 纯构建工具；应用运行时无 nanoid 调用路径，无可证实的生产可利用面 |

### H-5. source-map-js 1.2.1 → 建议 1.2.2

| 维度 | 内容 |
|---|---|
| 严重级 | **high**（传递：构建链 + 测试链） |
| 依赖链 | `vite → postcss → source-map-js@1.2.1`；`jsdom → css-tree → source-map-js@1.2.1`；`@vue/compiler-core → source-map-js@1.2.1`（去重共享） |
| 目标版本 | **1.2.2**（npm 发布 2026-09-30） |
| 是否跨 major | **否**（`npm update source-map-js` 即可，仅 lockfile 变更） |
| 官方来源 | GHSA-68fv-2mgg-jv7q（indexed source-map section offset 导致 event-loop DoS，影响 `1.0.0–1.2.1`，CVSS 7.5） |
| 影响 API 与代码路径 | 构建/测试期 source map 解析；应用不解析外部 source map，利用面限于构建/测试输入 |

### H-6. undici 7.27.2 → 建议 7.30.2

| 维度 | 内容 |
|---|---|
| 严重级 | **high**（传递：仅测试树） |
| 依赖链 | `frontend`（dev）→ `jsdom@29.1.1` → `undici@7.27.2`；jsdom 29 要求 `undici ^7.25.0` |
| 目标版本 | **7.30.2**（npm 发布 2026-11-05，满足 jsdom 29 的 `^7.25.0` 区间，`npm update undici` 即可）；最低可接受 7.30.1（2026-10-15） |
| 是否跨 major | **否**（仅 lockfile 变更，**无需升级 jsdom**） |
| 官方来源 | 27 条 advisory（代理/TLS 校验绕过、缓存/ Cookie 污染、CRLF 注入、WebSocket/EventSource DoS 等），影响范围 `7.0.0–7.30.0`，修复线 7.30.1 |
| 影响 API 与代码路径 | undici 不进浏览器静态产物，仅 vitest 的 jsdom 环境使用；vitest 配置 `environment: 'jsdom'`，测试不发起真实外网请求。建议保持 CI 无外网访问以收敛测试链风险 |

---

## 四、Moderate 项（3 项，单列不并入）

按任务纪律，moderate 不与 high 混批，但其修复路径与批次 C 重叠，此处如实标注"顺带清除"，是否随批执行由项目维护者裁夺。

### M-1. esbuild（`vite → esbuild`，影响 `<=0.24.2`，GHSA-67mh-4wv8-2f99，dev server 任意跨源读响应）

- 当前由 vite 5.4.21 拉入 `esbuild ^0.21.3`。
- 清除路径：批次 C 方案一（vite 7.3.8）带 `esbuild ^0.27.0 \|\| ^0.28.0`（出 advisory 范围）；方案二（vite 8.3.8）esbuild **从依赖树整体消失**（vite 8 改用 rolldown）。

### M-2. @vitest/mocker + vitest（GHSA-82fw-gwwq-j7x9 路径穿越，`<4.1.11`；GHSA-qxg6-89mg-rqv8 代码注入，`<4.1.12`）

- 当前 `vitest@4.1.8`（含嵌套 `@vitest/mocker@4.1.8`）。
- 清除路径：批次 C 中 `vitest → 4.1.12`（npm 发布 2026-10-15，不跨 major，覆盖两条 advisory 的修复线）。

---

## 五、建议分批顺序

| 批次 | 内容 | 跨 major | 预计变更面 | 消除项 |
|---|---|---|---|---|
| **A（运行时，最高优先）** | `dompurify → 3.4.18`、`vue → 3.5.45` | 否 | 预计仅 lockfile（semver 范围已覆盖） | 1 critical + 2 high（vue、@vue/server-renderer 两条目，同一修复批） |
| **B（构建链 minor）** | `npm update postcss nanoid source-map-js`（→ 8.5.33 / 3.3.20 / 1.2.2） | 否 | 仅 lockfile | 3 high（H-3/H-4/H-5） |
| **D（测试树）** | `npm update undici`（→ 7.30.2） | 否 | 仅 lockfile | 1 high（undici 27 条 advisory） |
| **C（构建链 major，风险最高放最后）** | `vite → 7.3.8`（推荐）或 `8.3.8`（备选）+ `@vitejs/plugin-vue → 6.0.11` + `vitest → 4.1.12` | **是** | package.json + lockfile | vite high（两个节点）+ 顺带清除 M-1/M-2 全部 moderate |

**排序理由**：先以最低风险消掉 critical 与全部"不跨 major"的 high；major 升级（vite 5→7/8、plugin-vue 5→6）独立成批，失败时回滚面清晰，不拖累已验证批次。批次 B 提前执行可为批次 C 扫清 postcss/nanoid/source-map-js，使 C 的验证面更聚焦 vite 自身。

---

## 六、每批验证命令与人工复核点（批准后执行用）

> 以下命令均为执行阶段的建议，**本预研未执行**。所有批次均在 `frontend/` 目录内操作。

### 批次 A

```bash
cd frontend
npm update dompurify vue                 # 当前 semver 范围已覆盖目标版本，仅 lockfile 变更
npm audit                                # 期望：critical 清零、vue 与 @vue/server-renderer 两条 high 清零
npm test                                 # 现行基线 43/43 全绿（含 it.each 展开），重点看 XSS 过滤用例
npm run build                            # 构建成功
```

人工复核点：
- `markdownRenderer.test.js` 的 XSS 用例（`<script>` 注入被过滤）通过；
- 浏览器冒烟：LLM 回答的 Markdown（表格/代码块/有序列表）渲染正常，链接仍在新窗口打开且 `javascript:` 协议被剥除；
- 浏览器控制台**无** DOMPurify 新警告（3.4.18 新增 USE_PROFILES 混用警告，本应用不应触发）；
- `npm ls dompurify vue` 确认版本到位。

### 批次 B

```bash
cd frontend
npm update postcss nanoid source-map-js
npm audit                                # 期望：postcss/nanoid/source-map-js high 清零
npm test && npm run build
```

人工复核点：
- `npm ls postcss nanoid source-map-js` 确认 8.5.33 / 3.3.20 / 1.2.2；
- 构建无新增警告，dist 产物 sourcemap 正常生成；
- 构建产物体积无异常膨胀。

### 批次 D

```bash
cd frontend
npm update undici
npm audit                                # 期望：undici high 清零
npm test                                 # jsdom 环境测试全绿
```

人工复核点：
- `npm ls undici` 确认 7.30.2 且仍在 jsdom 29 的 `^7.25.0` 区间内；
- 确认 jsdom 版本未被连带改动（应保持 29.1.1）；
- 此批仅测试树变化，不触生产产物。

### 批次 C（方案一：vite 7.3.8）

```bash
cd frontend
npm install -D vite@7.3.8 @vitejs/plugin-vue@6.0.11 vitest@4.1.12
npm audit                                # 期望：vite high 清零、esbuild/mocker/vitest moderate 清零
npm test && npm run build
npm run dev                              # 冒烟：5173 起服务、/api 代理到 8080 正常
```

人工复核点：
- `npm ls vite` 确认单一 vite 7.3.8（嵌套 vite 8.0.16 节点消失）、esbuild ≥0.27；
- 组件热更新正常、`vite.config.js` 的 proxy 配置行为不变；
- dist 产物与批次 A–D 后的产物人工比对（文件名哈希、体积、功能等价）；
- 若构建/测试失败，优先查 plugin-vue 6 与 vue 3.5.45 的组合（plugin-vue 6.0.10+ 的 vue peer 声明含 `^3.6.0-rc`，3.5.45 在 `^3.2.25` 内，预期无冲突，以实测为准）。

### 批次 C（方案二：vite 8.3.8，备选）

同上，版本换为 `vite@8.3.8`。额外人工复核点：
- vite 8 为 rolldown 架构（`rolldown ~1.2.18`，esbuild 不复存在），构建产物差异可能比方案一大，**必须**对 dist 产物做完整功能回归（非仅 diff）；
- 确认团队对 rolldown-vite 的接受度（项目处于自托管底座收尾期，构建链大改需谨慎）；
- 两条路线都出 advisory 影响范围，方案差异在风险取向，由项目维护者裁夺。

---

## 七、回滚方式

- **单批回滚（未提交时）**：`git checkout -- frontend/package.json frontend/package-lock.json && npm ci`，依赖树即回到上一已验证状态；
- **已提交时**：对相应批次提交执行 `git revert`；
- **执行前置条件（重要）**：当前工作分支存在大量未提交的阶段成果。执行任何升级批前，应先提交当前已验证改动，否则 `git checkout --` 回滚存在误伤未提交成果的风险；
- node_modules 层回滚始终以 lockfile 为唯一事实源：`npm ci` 严格按 lockfile 重建。

---

## 八、仍不确定项

1. **目标版本再被点名**：npm audit 只反映当前安装版本。3.4.18 / 3.5.45 / 8.5.33 / 3.3.20 / 1.2.2 / 7.30.2 / 7.3.8(或 8.3.8) / 6.0.11 / 4.1.12 自身可能已出现在更新的 advisory 中。每批执行后**必须重跑 `npm audit` 实测**，不得以本报告的"期望清零"为准。
2. **vite major 路线的最终选择**：7.3.8（esbuild+rollup，架构延续）与 8.3.8（rolldown，audit 官方建议值）均可出 advisory 范围，风险取向不同，由项目维护者裁夺。
3. **Docker/CI Node 版本未核**：vite 7/8、plugin-vue 6 要求 Node `^20.19.0 || >=22.12.0`。本机 v24.13.1 满足；若部署 Dockerfile 的 node 镜像低于此线，批次 C 需连带调整镜像（属部署范围变更，另行报批）。批次 A/B/D 无 Node 要求变化。
4. **DOMPurify 3.4.18 SVG 白名单清理**：本应用白名单未放行 SVG，理论无影响，以批次 A 的测试与冒烟实测为准。
5. **jsdom 升级未纳入本方案**：undici 修复在 jsdom 29 区间内即可完成。jsdom 29 → 31（连带 undici 8）无安全必要性，不主动扩大范围；若未来升级，undici 8 的 advisory 状态需重新核查。
6. **moderate 顺带清除的批准归属**：M-1/M-2 随批次 C 顺带清除，不单独设批；若项目维护者希望批次 C 只升 vite 不升 vitest，则 M-2 保留（不推荐，vitest 4.1.12 不跨 major、风险极低）。

---

## 九、审批清单（请项目维护者逐项批准）

```
【前端依赖升级 · 审批清单】（预研完成，未执行任何变更；详见
 docs/test-reports/frontend-dependency-upgrade-plan-2026-11-15.md）

前置条件：先提交当前已验证改动，再动依赖。

□ 批次 A（运行时，消 1 critical + 2 high〔vue、@vue/server-renderer 两条目同一修复批〕）：
  dompurify 3.4.8→3.4.18、vue 3.5.35→3.5.45；不跨 major，预计仅 lockfile 变更。
  验证：npm audit / npm test / npm run build + 浏览器 XSS 冒烟。

□ 批次 B（构建链 minor，消 3 high）：
  npm update postcss(→8.5.33) nanoid(→3.3.20) source-map-js(→1.2.2)；仅 lockfile。
  验证：npm audit / npm test / npm run build。

□ 批次 D（测试树，消 1 high / undici 27 条 advisory）：
  npm update undici(→7.30.2)；仅 lockfile，不升 jsdom。
  验证：npm audit / npm test。

□ 批次 C（构建链 major，消 vite high×2 节点 + 顺带 3 项 moderate）：
  vite 5.4.21→【7.3.8 推荐 / 8.3.8 备选，请择一】、@vitejs/plugin-vue 5.2.4→6.0.11、
  vitest 4.1.8→4.1.12；跨 major，package.json 变更。
  验证：npm audit / npm test / npm run build / npm run dev 冒烟 + dist 产物比对。
  附加裁项：□ 批准顺带清除 moderate（esbuild、@vitest/mocker、vitest）

建议执行顺序：A → B → D → C。每批执行后重跑 npm audit 实测为准。
本清单不宣称任何漏洞已修复；全部修复待批准执行后再验证确认。
```
