# Quickstart Examples（快速开始示例）

**[English](#english) | [中文](#中文)**

## English

This directory contains three fully synthetic, self-written Markdown documents. Every value and procedure is invented and must not be used for real maintenance.

### Native PDF and Markdown ingestion

Place the `.md` files in the directory configured by `AMA_DATA_PATH` (`./data` when running from `backend/`), then call:

```bash
curl -X POST http://127.0.0.1:8080/api/admin/ingest
```

The endpoint scans only the current directory, supports `.pdf`, `.md`, and `.markdown`, and returns a JSON report. PDF parsing uses Tika; Markdown is read as UTF-8. The samples can be ingested directly without conversion.

Ingestion assigns deterministic `document_id` and `excerpt_id` values. Re-ingesting identical bytes rewrites the same version without duplicate vectors. Changed bytes create a new document version; use a fresh `AMA_CHROMA_COLLECTION` for a complete corpus replacement so old and new versions are not retrieved together.

The response contains stable retrieval citations, not page-level or sentence-level proof. These samples contain no real manual content.

## 中文

本目录包含三份完全自写的合成 Markdown 文档。所有数值和程序均为虚构，严禁用于真实维修。

### 原生 PDF 与 Markdown 摄入

将 `.md` 文件放入 `AMA_DATA_PATH` 配置的目录（从 `backend/` 启动时默认为 `./data`），然后调用：

```bash
curl -X POST http://127.0.0.1:8080/api/admin/ingest
```

接口只扫描当前目录，支持 `.pdf`、`.md`、`.markdown`，返回 JSON 报告。PDF 使用 Tika 解析，Markdown 按 UTF-8 读取，示例无需转换即可摄入。

摄入会生成确定性的 `document_id` 与 `excerpt_id`。相同字节重复摄入会清理并重写同一版本，不产生重复向量；字节变化会生成新文档版本。完整替换语料时请使用新的 `AMA_CHROMA_COLLECTION`，避免新旧版本混合召回。

响应中的引用表示实际检索来源，不是页码级或逐句证据认证。示例不包含任何真实手册内容。
