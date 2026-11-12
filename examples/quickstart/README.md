# Quickstart Examples（快速开始示例）

**[English](#english) | [中文](#中文)**

---

## English

This directory contains **three fully synthetic, self-written Markdown sample documents** for trying out the Aviation Maintenance Assistant document format.

> ⚠️ **Every sample file starts with `SYNTHETIC SAMPLE — NOT FOR REAL MAINTENANCE USE`.**
> These documents were written from scratch for this repository. They contain **no content copied from any real manual**, and their procedures, values, and part numbers are invented. **Never** use them for actual maintenance decisions.

### Files

| File | Simulated document type | Topics covered |
|------|------------------------|----------------|
| `sample-daily-inspection.md` | Light aircraft daily inspection | Walk-around checks, fluid levels, control surfaces |
| `sample-engine-oil-check.md` | Piston engine oil servicing | Oil level check, top-up procedure, leak inspection |
| `sample-tire-inspection.md` | Landing gear tire inspection | Tread wear, pressure checks, replacement criteria |

### ⚠️ Important: current ingestion accepts PDF only

The current ingestion controller (`POST /api/admin/ingest`) **only scans `backend/data/` for `.pdf` files**. **End-to-end Markdown ingestion is planned and not available yet.**

What you can do **today**:

1. Read these files to understand the document style the toolkit is designed for (short sections, explicit procedures, safety warnings).
2. To actually ingest content into the current system, place your **legally obtained PDF** manuals into `backend/data/` and run:
   ```bash
   curl -X POST http://localhost:8080/api/admin/ingest
   ```
3. If you want to experiment with these Markdown samples anyway, you may convert them to PDF yourself (e.g. with Pandoc: `pandoc sample-engine-oil-check.md -o sample-engine-oil-check.pdf`) and place the resulting PDFs in `backend/data/`. This is a manual workaround, not a supported pipeline.

What is **planned**:

- Native Markdown ingestion alongside PDF, via the same `backend/data/` drop-in workflow.
- Stable `document_id` / `excerpt_id` assignment during ingestion (see [docs/MCP.md](../../docs/MCP.md)).

---

## 中文

本目录包含**三份完全自写的合成 Markdown 示例文档**，用于体验 Aviation Maintenance Assistant 的文档格式。

> ⚠️ **每份示例文件首行都标注了 `SYNTHETIC SAMPLE — NOT FOR REAL MAINTENANCE USE`。**
> 这些文档是本仓库从零撰写的，**不包含任何真实手册的内容**，其中的程序、数值、件号均为虚构。**严禁**将其用于真实维修决策。

### 文件清单

| 文件 | 模拟文档类型 | 覆盖主题 |
|------|-------------|---------|
| `sample-daily-inspection.md` | 轻型飞机每日检查 | 绕机检查、液面检查、操纵面 |
| `sample-engine-oil-check.md` | 活塞发动机滑油勤务 | 滑油量检查、加注程序、渗漏检查 |
| `sample-tire-inspection.md` | 起落架轮胎检查 | 胎面磨损、气压检查、更换标准 |

### ⚠️ 重要：当前摄入仅支持 PDF

当前摄入控制器（`POST /api/admin/ingest`）**只扫描 `backend/data/` 目录下的 `.pdf` 文件**。**Markdown 端到端摄入为规划能力，当前尚不可用。**

**今天你可以做的事**：

1. 阅读这些文件，了解本工具包所面向的文档风格（短章节、明确程序、安全警示）。
2. 如需真正摄入内容，将你**合法获取的 PDF** 手册放入 `backend/data/`，然后执行：
   ```bash
   curl -X POST http://localhost:8080/api/admin/ingest
   ```
3. 如果你仍想体验这些 Markdown 示例，可以自行转换为 PDF（例如用 Pandoc：`pandoc sample-engine-oil-check.md -o sample-engine-oil-check.pdf`），再放入 `backend/data/`。这只是手动变通方法，不是受支持的流水线。

**规划中**：

- 原生 Markdown 摄入，与 PDF 共用 `backend/data/` 投放工作流。
- 摄入时分配稳定的 `document_id` / `excerpt_id`（见 [docs/MCP.md](../../docs/MCP.md)）。
