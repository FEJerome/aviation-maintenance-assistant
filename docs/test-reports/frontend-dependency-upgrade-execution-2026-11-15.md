# 前端依赖升级执行报告 — 2026-11-15

> **范围**：前端依赖安全升级，按预研方案 `docs/test-reports/frontend-dependency-upgrade-plan-2026-11-15.md` 执行
> **基线**：73e590d（worktree 分支 `ama-002-dependency-upgrades`）
> **执行序**：A（运行时）→ B（构建链 minor）→ D（测试树）→ C（构建链 major，Vite 7.3.8 路线）
> **总结果**：`npm audit` 由 11 项（1 critical + 7 high + 3 moderate）降为 **0 项**；测试 43/43 全程通过；生产构建与开发服务器冒烟均通过
> **声明**：本次未修改任何业务源码、视觉呈现与文档（本报告除外）；未触真实云端/本地大模型；全部验证为 loopback。

---

## 一、环境

- 本机 Node v24.13.1 / npm 11.10.0（满足 vite 7 / plugin-vue 6 的 `^20.19.0 || >=22.12.0`）
- 施工前已 `npm ci` 按 lockfile 严格重建依赖树
- 执行期 advisory 复查：九个目标版本均命中各自修复线（vite 7.3.8 为 2026-11-10 新 critical GHSA-p4f5-76q4-ffq7 的补丁版本；undici 7.30.2、vitest 4.1.12 同为各自修复线；vue 3.5.45、dompurify 3.4.18 出清全部在册 advisory）

## 二、逐批执行记录

### 批 A — 运行时依赖（commit `9748c87`）

| 项 | 内容 |
|---|---|
| 命令 | `npm update dompurify vue` |
| 版本 | dompurify 3.4.8 → **3.4.18**；vue 3.5.35 → **3.5.45**（含 @vue/server-renderer 3.5.45） |
| 变更面 | 仅 lockfile（package.json semver 范围未动） |
| audit | 11 → **5**（critical 1→0；vue 双条目 high 清零；postcss/nanoid/source-map-js 三个传递 high 一并随依赖树刷新清零，详见批 B 说明） |
| 测试 | 43/43 通过 |
| 构建 | 通过；dist JS 184.83 → 192.05 kB（安全补丁所致，+3.9%） |
| 冒烟 | jsdom loopback 9/9：表格/代码块/有序列表/标题渲染正常；`<script>`、`onerror`、`javascript:` 载荷均被剥除；**无 DOMPurify 新警告** |
| 备注 | 与 3.4.8 行为比对确认链接处理零变化（见「剩余风险 2」） |

### 批 B — 构建链 minor（无独立 commit，验证性通过）

| 项 | 内容 |
|---|---|
| 命令 | `npm update postcss nanoid source-map-js` |
| 版本 | 目标 8.5.33 / 3.3.20 / 1.2.2 —— **批 A 执行时 vue 3.5.45 的传递依赖刷新已将其带入目标版本**，本批 npm update 为 no-op，lockfile 无变化，故无独立 commit |
| audit | 确认 postcss / nanoid / source-map-js high 条目清零（计数归并于批 A commit） |
| 测试 / 构建 | 43/43 通过；构建正常，sourcemap 产物无异常，体积无异常膨胀 |

### 批 D — 测试树（commit `892b225`）

| 项 | 内容 |
|---|---|
| 命令 | `npm update undici` |
| 版本 | undici 7.27.2 → **7.30.2**；jsdom 保持 29.1.1（在 `^7.25.0` 区间内，未连带变动） |
| 变更面 | 仅 lockfile（6 行） |
| audit | 5 → **4**（undici high 清零，27+ 条 advisory 出范围；新查到的数条 advisory 修复线亦止于 7.30.2） |
| 测试 | 43/43 通过（jsdom 环境全绿） |

### 批 C — 构建链 major（commit `5602235`）

| 项 | 内容 |
|---|---|
| 命令 | `npm install -D vite@7.3.8 @vitejs/plugin-vue@6.0.11 vitest@4.1.12` |
| 版本 | vite 5.4.21 → **7.3.8**（vitest 嵌套 vite 8.0.16 去重消失，全树单一 vite 节点）；@vitejs/plugin-vue 5.2.4 → **6.0.11**；vitest 4.1.8 → **4.1.12**（@vitest/mocker 4.1.12）；esbuild 0.21.5 → **0.28.2** |
| 变更面 | package.json（3 个 devDependencies）+ lockfile |
| audit | 4 → **0**（vite high 清零；顺带 moderate 三项全部清零：esbuild、@vitest/mocker 两条目、vitest） |
| 测试 | 43/43 通过 |
| 构建 | 通过；dist JS 193.12 kB / CSS 12.87 kB |
| 冒烟 | dev 服务器 loopback 5/5：5173 正常起服务；`main.js` 模块转换正常；`ChatMessage.vue` SFC 由 plugin-vue 6 编译正常（含 HMR 上下文）；`/api/health` 经代理转发至 8080 stub 并返回预期 JSON（代理行为不变） |

## 三、产物对比（dist）

| 阶段 | index.html | CSS | JS |
|---|---|---|---|
| 基线（73e590d） | 0.38 kB | 13.15 kB | 184.83 kB（gzip 69.89） |
| 批 A 后 | 0.38 kB | 13.15 kB | 192.05 kB（gzip 72.51） |
| 批 C 后 | 0.40 kB | 12.87 kB | 193.12 kB（gzip 72.11） |

JS 较基线 +8.29 kB（+4.5%），来源为 dompurify/vue 安全补丁本体及打包器换代，无功能代码变更；CSS 差异为压缩器版本行为，属正常波动。

## 四、冒烟脚本留档

- 批 A：jsdom loopback 渲染冒烟（Markdown 结构、XSS 剥除、链接策略、DOMPurify 控制台警告捕获），9 项断言全过。
- 批 C：8080 端口 loopback stub 后端 + vite dev 服务器，curl 断言 5 项全过；结束后端口已释放。

## 五、剩余风险与挂账（不清零、不自行扩大）

1. **`node:20-alpine` 已 EOL**：该 tag 冻结于 Node v20.20.2（≥20.19.0，满足本次 vite 7 构建下限，故未改镜像、不阻塞本批）；但镜像不再重建，无法获得基底安全更新。镜像升级（如 node:22/24）属部署范围变更，登记待裁。
2. **预研文档复核点与现场的一处出入**：`markdownRenderer.js:88` 的自定义 `ALLOWED_URI_REGEXP` 中 `(f|t)tp(?:s)?` 疑为官方默认值 `(f|ht)tps?` 的转写偏差，导致 Markdown 内 http/https 链接的 `href` 在基线上即被剥除（渲染为不可点击文本），且应用并未对链接强制 `target=_blank`。已用 dompurify 3.4.8 与 3.4.18 双向实测确认**升级前后行为完全一致、非本次回归**。修复需动业务源码，超出本任务授权，登记待裁。
3. **vite dev server critical（GHSA-p4f5-76q4-ffq7）利用前提**为 dev server 暴露于网络（`--host`/`server.host`）。本项目 dev 默认绑定 localhost，生产为 Nginx 托管静态产物、不运行 vite，暴露面不成立；保持现状即可，登记备查。
4. **构建基线数字**：宪法约束表「dist JS ≈184.8 kB」自本批起被安全补丁突破（现 193.12 kB），建议正典同步更新该基线。
5. **jsdom 29 未升级**：undici 修复在其区间内完成，jsdom 29→31 无安全必要性，不主动扩大；未来若升级，undici 8 的 advisory 状态需重新核查。
6. **目标版本再被点名的固有风险**：执行末期复查时 nine 个目标版本仍均为各自修复线，但 advisory 随时可能更新；后续常规复测以 `npm audit` 实测为准。

## 六、commit 清单（分支 `ama-002-dependency-upgrades`，未 push）

| commit | 内容 |
|---|---|
| `9748c87` | chore(deps): update runtime packages（批 A） |
| `892b225` | chore(deps): update test-tree http client（批 D） |
| `5602235` | chore(deps): update build tooling to Vite 7（批 C） |
| （本报告） | docs: 前端依赖升级执行报告 |

## 七、验证数字汇总

- `npm audit`：**11 → 0**（critical 1→0，high 7→0，moderate 3→0）
- `npm test`：**43/43**（基线、每批后均全绿，含 XSS 过滤用例）
- `npm run build`：基线、批 A、批 C 后均通过
- 冒烟：批 A 9/9；批 C 5/5；全部 loopback，未触真实模型
