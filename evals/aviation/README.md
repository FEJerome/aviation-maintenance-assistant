# Aviation Golden-Set Evaluation（航空 Golden-Set 评测）

**[English](#english) | [中文](#中文)**

---

## English

### Status: planned

- ✅ **Schema ready**: [golden-set.schema.json](golden-set.schema.json) — the agreed contract for evaluation items.
- 📋 **Item body (~20 items)**: planned, not yet authored.
- 📋 **Evaluation harness**: planned; no automated runner ships in the current version.

### What this evaluation is for

The golden set provides a **reproducible regression check** for the aviation reference implementation:

1. **Retrieval quality** — do queries over the user's own corpus surface the right manual excerpts?
2. **Answer quality** — does the generated answer cover the paraphrased key points and cite sources?
3. **Control-flow correctness** — are critical-system questions routed through aircraft/engine validation, are mismatches blocked deterministically, and does the system abstain honestly when it should?

### Case types the schema can express

`expected_route` covers six outcomes, so the set can honestly include items that are **not** answerable by RAG:

| Route | Meaning |
|-------|---------|
| `rag` | Normal path: retrieve from the corpus, generate a cited answer |
| `validation_block` | Aircraft/engine mismatch — deterministic code blocks the request |
| `validation_unknown` | Combination not in the built-in table — user is informed |
| `clarify_model` | Critical-system question without aircraft/engine info — system asks for it |
| `no_answer` | The corpus legitimately has no answer — the system must say so instead of fabricating |
| `out_of_scope` | Question outside the toolkit's domain — must be declined |

For non-`rag` routes, `expected_behavior` describes the checkable expected response (e.g. what a block or refusal must convey).

### Schema overview

Each item (see [golden-set.schema.json](golden-set.schema.json) for the normative definition) contains:

| Field | Required | Meaning |
|-------|----------|---------|
| `id` | Always | Stable identifier, e.g. `AVI-001` |
| `question` | Always | Author-written question (never quoted from a manual) |
| `language` | Always | `en` or `zh`; cross-language items are first-class |
| `expected_route` | Always | One of the six routes above |
| `tags` | Always | Classification for sliced reporting (includes `no-answer`, `ambiguous`, `out-of-scope`) |
| `aircraft_model` / `engine_model` | Optional | Context for validation-path items |
| `expected_behavior` | Recommended for non-`rag` | Checkable behavior description for block/unknown/clarify/no-answer/out-of-scope items |
| `answer_keypoints` | **Required and non-empty only when `expected_route = "rag"`** | **Paraphrased** key points an acceptable answer must cover |
| `source_locator` | **Required only when `expected_route = "rag"`** | Human evidence locator (`doc_title`, optional `section_hint`/`page_hint`) |
| `required_docs` | **Required and non-empty only when `expected_route = "rag"`** | Documents that must exist in the user's own corpus |
| `notes` | Optional | Author's free-form note |

The conditional requirement is enforced by a JSON Schema `if`/`then` rule in the schema itself — a `rag` item without non-empty `answer_keypoints`, `source_locator`, and `required_docs` fails validation, while `validation_block`, `no_answer`, and other non-`rag` items validate without them.

### Authoring rules (binding for future item authors)

1. **No verbatim manual text.** `answer_keypoints` must be written in the author's own words. Questions likewise must not quote manuals.
2. **No copyrighted material is distributed.** `required_docs` only *names* the documents a user must obtain legally and ingest independently. The repository never ships the documents themselves.
3. **Locators are for humans.** `section_hint`/`page_hint` help a human reviewer find the evidence. The first-version automated harness **must not** rely on them, because ingestion does not guarantee page/section metadata in the vector store.
4. **Honest abstention is a first-class case.** `no_answer` and `out_of_scope` items exist to catch fabrication; `expected_behavior` must state what an honest abstention looks like (e.g. "states the manual set does not cover this" rather than inventing a procedure).
5. **Language balance.** The ~20 items should cover both `en` and `zh` questions, including at least a few cross-language cases (Chinese question over an English corpus).

### Scoring concept (planned)

- **Keypoint coverage** (`rag` items): fraction of `answer_keypoints` covered by the answer (LLM-as-judge or rubric-checked, exact method TBD).
- **Route correctness** (all items): did the actual control flow match `expected_route`?
- **Behavior conformance** (non-`rag` items): does the response satisfy `expected_behavior`?
- **Citation presence** (`rag` items): does the answer name a source consistent with `source_locator.doc_title`?

Results are intended to be summarized per tag, so regressions in (say) `validation` or `no-answer` behavior are visible independently of general QA quality.

---

## 中文

### 状态：planned（规划中）

- ✅ **Schema 已就绪**：[golden-set.schema.json](golden-set.schema.json)——评测条目的约定契约。
- 📋 **条目本体（约 20 条）**：规划中，尚未撰写。
- 📋 **评测执行器**：规划中；当前版本不附带自动化运行器。

### 评测目的

Golden set 为航空参考实现提供**可复现的回归检查**：

1. **检索质量**——在用户自己的语料上，查询能否召回正确的手册片段？
2. **回答质量**——生成的回答是否覆盖了改写后的要点，并标注来源？
3. **控制流正确性**——关键系统问题是否经过机型/发动机验证？不匹配组合是否被确定性拦截？系统在应当拒答时是否诚实拒答？

### Schema 能表达的病例类型

`expected_route` 覆盖六种结果，使评测集能诚实包含**无法**由 RAG 回答的条目：

| 路由 | 含义 |
|------|------|
| `rag` | 正常路径：从语料检索并生成带引用的回答 |
| `validation_block` | 机型/发动机不匹配——确定性代码拦截 |
| `validation_unknown` | 组合未收录于内置表——告知用户 |
| `clarify_model` | 关键系统问题缺少机型/发动机信息——系统追问 |
| `no_answer` | 语料中确实没有答案——系统必须如实说明，不得编造 |
| `out_of_scope` | 问题超出工具包领域——必须婉拒 |

对非 `rag` 路由，`expected_behavior` 描述可检查的预期行为（例如拦截或拒答响应必须传达什么）。

### Schema 概览

每个条目（规范性定义见 [golden-set.schema.json](golden-set.schema.json)）包含：

| 字段 | 是否必填 | 含义 |
|------|---------|------|
| `id` | 总是 | 稳定标识符，如 `AVI-001` |
| `question` | 总是 | 作者自撰的问题（绝不引用手册原文） |
| `language` | 总是 | `en` 或 `zh`；跨语言条目是一等公民 |
| `expected_route` | 总是 | 上述六种路由之一 |
| `tags` | 总是 | 用于分片报告的分类标签（含 `no-answer`、`ambiguous`、`out-of-scope`） |
| `aircraft_model` / `engine_model` | 可选 | 验证路径条目的上下文 |
| `expected_behavior` | 非 `rag` 时建议必填 | 拦截/未知/追问/无答案/越界条目的可检查行为描述 |
| `answer_keypoints` | **仅当 `expected_route = "rag"` 时必填且非空** | 可接受回答必须覆盖的**改写后**要点 |
| `source_locator` | **仅当 `expected_route = "rag"` 时必填** | 人工证据定位（`doc_title`，可选 `section_hint`/`page_hint`） |
| `required_docs` | **仅当 `expected_route = "rag"` 时必填且非空** | 用户自有语料中必须存在的文档 |
| `notes` | 可选 | 作者的自由备注 |

条件要求由 schema 内的 JSON Schema `if`/`then` 规则强制——缺少非空 `answer_keypoints`、`source_locator`、`required_docs` 的 `rag` 条目无法通过校验，而 `validation_block`、`no_answer` 等非 `rag` 条目在没有这些字段时可通过校验。

### 撰写规则（对未来的条目作者有约束力）

1. **不含手册原文。** `answer_keypoints` 必须用作者自己的话改写；问题同样不得引用手册。
2. **不分发受版权保护的材料。** `required_docs` 只是*点名*用户必须自行合法取得并摄入的文档，仓库绝不附带文档本身。
3. **定位信息仅供人工使用。** `section_hint`/`page_hint` 帮助人工评审找到证据。首版自动化评测**不得**依赖它们，因为摄入流程不保证向量库中存在页码/章节元数据。
4. **诚实拒答是一等病例。** `no_answer` 与 `out_of_scope` 条目用于捕捉编造行为；`expected_behavior` 必须写明诚实的拒答长什么样（例如"说明手册集未覆盖该内容"，而不是编造程序）。
5. **语言均衡。** 约 20 条条目应同时覆盖 `en` 和 `zh` 问题，并至少包含若干跨语言用例（中文问题检索英文语料）。

### 评分概念（规划中）

- **要点覆盖率**（`rag` 条目）：回答覆盖 `answer_keypoints` 的比例（LLM-as-judge 或 rubric 检查，具体方法待定）。
- **路由正确性**（全部条目）：实际控制流是否与 `expected_route` 一致。
- **行为符合性**（非 `rag` 条目）：响应是否满足 `expected_behavior`。
- **引用存在性**（`rag` 条目）：回答是否标注了与 `source_locator.doc_title` 一致的来源。

评测结果按 tag 汇总，使（例如）`validation` 或 `no-answer` 行为的回归能够独立于通用问答质量被观察到。
