# Aviation Reference Implementation（航空参考实现说明）

**[English](#english) | [中文](#中文)**

---

## English

This note describes what the **aviation maintenance reference implementation** in this repository demonstrates, and what it deliberately does **not** contain.

> ⚠️ **No real manual text is included in this repository.** All example questions and behaviors below are described abstractly. The corpus must be supplied by you, from legally obtained documents.

### Capability 1: Aircraft/engine model validation

- Before answering questions about **critical systems** (engine, flight controls, landing gear, hydraulics), the backend requires an aircraft model and an engine model.
- A **hardcoded match table** (`AircraftValidationService`) checks the combination deterministically:
  - **MATCH** → the question proceeds to RAG with the aircraft context injected.
  - **MISMATCH** → the request is blocked by code; the LLM is never called.
  - **UNKNOWN** → the user is told the combination is not in the built-in table.
- Design principle: hard constraints live in deterministic code; only open-ended generation is delegated to the probabilistic model.

### Capability 2: Cross-language retrieval

- The reference corpus used during development was **English** (FAA advisory circulars, light-aircraft maintenance manuals).
- Users can ask questions in **Chinese or English**. A query transformation step (`TranslationQueryTransformer`) normalizes/translates queries before vector retrieval, so a Chinese question can retrieve English manual excerpts, and the LLM answers in the language of the question.
- The local embedding model (all-MiniLM-L6-v2) runs fully offline — no external embedding API is called.

### Capability 3: Manual source locating

- Answers generated through the RAG pipeline carry **source citations** (document name and locator information as available, e.g. page numbers when present in the ingested text).
- The system is designed to cite, not to replace: final maintenance decisions must always be made by qualified engineers against the authoritative manuals (AMM/FIM/IPC) and applicable regulations.

### What you need to bring

| Item | Why |
|------|-----|
| Legally obtained manuals (PDF, current version) | Nothing is shipped; you ingest your own corpus into ChromaDB |
| An OpenAI-compatible LLM API key | Current code defaults to DeepSeek; arbitrary endpoints are a planned target contract ([docs/CONFIGURATION.md](../../docs/CONFIGURATION.md)) |

---

## 中文

本文档说明本仓库中**机务维修参考实现**所展示的能力，以及它刻意**不**包含的内容。

> ⚠️ **本仓库不包含任何真实手册原文。** 下面的示例问题与行为均为抽象描述。语料库必须由你自行提供，且须为合法获取的文档。

### 能力一：机型/发动机匹配验证

- 在回答**关键系统**（发动机、飞控、起落架、液压）相关问题前，后端要求用户提供机型与发动机型号。
- **硬编码匹配表**（`AircraftValidationService`）以确定性方式校验组合：
  - **MATCH（匹配）** → 问题携带机型上下文进入 RAG 流程。
  - **MISMATCH（不匹配）** → 代码层直接拦截，不调用 LLM。
  - **UNKNOWN（未知）** → 告知用户该组合未收录于内置表。
- 设计原则：硬性约束由确定性代码承担，只有开放式生成才交给概率模型。

### 能力二：跨语言检索

- 开发期使用的参考语料为**英文**（FAA 咨询通告、轻型飞机维修手册）。
- 用户可用**中文或英文**提问。查询转换组件（`TranslationQueryTransformer`）在向量检索前对查询进行归一化/翻译，中文问题也能召回英文手册片段，LLM 按提问语言作答。
- 本地嵌入模型（all-MiniLM-L6-v2）完全离线运行，不调用任何外部 Embedding API。

### 能力三：手册来源定位

- 经 RAG 流水线生成的回答携带**来源引用**（文档名称，以及可获得时的定位信息，例如被摄入文本中存在的页码）。
- 系统的设计定位是"引用而非替代"：最终维修决策必须由具备资质的工程师依据权威手册（AMM/FIM/IPC）和适用法规做出。

### 你需要自行准备的内容

| 项目 | 原因 |
|------|------|
| 合法获取的手册（当前版本为 PDF） | 仓库不附带任何语料，由你自行摄入 ChromaDB |
| OpenAI 兼容 LLM 的 API Key | 当前代码默认 DeepSeek；任意端点接入是规划中的目标契约（[docs/CONFIGURATION.zh-CN.md](../../docs/CONFIGURATION.zh-CN.md)） |
