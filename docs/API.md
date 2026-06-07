# API 文档

> 机务维修知识助手后端接口规范。
>
cn> Base URL：`http://localhost:8080`（本地开发）

---

## 目录

- [接口概览](#接口概览)
- [POST /chat — 聊天问答](#post-chat--聊天问答)
- [POST /admin/ingest — 文档批量摄入](#post-adminingest--文档批量摄入)
- [数据模型](#数据模型)
- [错误处理](#错误处理)

---

## 接口概览

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| `POST` | `/chat` | 聊天问答（支持多轮会话续聊） | 公开 |
| `POST` | `/admin/ingest` | 批量摄入 `backend/data/` 目录下所有 PDF | 公开（演示版） |

---

## POST /chat — 聊天问答

用户发送问题，系统根据路由策略返回回答。支持通过 `conversationId` 进行多轮追问。

### 请求

**Content-Type**：`application/json`

**请求体（Request Body）**：

```json
{
  "message": "CTLS的Rotax 912发动机滑油压力标准",
  "conversationId": "optional-existing-id"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | `string` | ✅ | 用户输入的问题 |
| `conversationId` | `string` | ❌ | 会话 ID。首次请求可不传，后端会生成并返回；续聊时传入同一 ID |

### 响应

**成功（HTTP 200）**：

```json
{
  "reply": "根据提供的维修手册片段，关于滑油压力...",
  "conversationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `reply` | `string` | 系统生成的回答。可能来自 LLM+RAG、拦截提示或验证失败提示 |
| `conversationId` | `string` | 当前会话 ID。续聊时需回传此值 |

### 应答类型说明

根据用户输入和会话状态，`reply` 可能有以下几种类型：

| 场景 | 示例输入 | 示例回复 |
|------|---------|---------|
| **通用知识** | `什么是AMM` | RAG 检索后由 LLM 生成的回答，包含手册引用来源 |
| **关键系统 + 未提供机型** | `发动机怎么维护` | `为了提供准确的维修信息，请提供机型（如 B737-800）和发动机型号（如 CFM56-7B）。` |
| **关键系统 + 匹配** | `CTLS的Rotax 912发动机滑油温度` | RAG 检索后由 LLM 生成的回答，自动注入机型上下文 |
| **关键系统 + 不匹配** | `B737-800的PW1100G发动机怎么维护` | `机型 B737-800 与发动机 PW1100G 不匹配。请核实后重新提供。` |
| **追问（已验证会话）** | `滑油温度呢`（带 conversationId） | 自动注入上次的机型上下文，无需重复提供 |

### curl 示例

**首次提问**：

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "CTLS的Rotax 912发动机滑油压力标准"
  }'
```

**多轮追问**（使用上一步返回的 `conversationId`）：

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "滑油温度呢",
    "conversationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }'
```

---

## POST /admin/ingest — 文档批量摄入

扫描 `backend/data/` 目录下所有 `.pdf` 文件，解析内容、分块、嵌入向量并存储到 ChromaDB。

> ⚠️ **注意**：后端必须从 `backend/` 目录启动，否则相对路径 `Paths.get("data")` 无法正确解析。

### 请求

**Content-Type**：`application/json`（请求体可为空）

**请求体**：无

### 响应

**成功（HTTP 200）**：返回纯文本描述

```text
PDF ingestion completed.
Success: AC_65-9A.pdf, CTLS-MM.pdf, FAA-AC-43.pdf, Rotax-912.pdf
Failed: corrupted-file.pdf
```

### curl 示例

```bash
curl -X POST http://localhost:8080/admin/ingest
```

### 摄入流程

```
backend/data/*.pdf
    ↓
Apache Tika 解析文本
    ↓
文本分块（按段落/大小窗口）
    ↓
all-MiniLM-L6-v2 本地 ONNX 嵌入
    ↓
存储到 ChromaDB（collection: aviation-docs）
```

---

## 数据模型

### ChatRequest

```java
public record ChatRequest(String message, String conversationId) { }
```

| 字段 | 类型 | 约束 |
|------|------|------|
| `message` | `string` | 非空，最大长度建议 2000 字符 |
| `conversationId` | `string` | 可选，UUID 格式 |

### ChatResponse

```java
public record ChatResponse(String reply, String conversationId) { }
```

| 字段 | 类型 | 约束 |
|------|------|------|
| `reply` | `string` | 系统生成的回答文本 |
| `conversationId` | `string` | 本次会话的唯一标识 |

---

## 错误处理

当前版本为演示版，错误处理以**返回 HTTP 200 + 错误信息文本**为主，未启用复杂的全局异常处理器。

### 常见错误场景

| HTTP 状态码 | 场景 | 示例 |
|-------------|------|------|
| `200` | 业务逻辑拦截（如机型不匹配） | `{ "reply": "机型 B737-800 与发动机 PW1100G 不匹配...", "conversationId": "..." }` |
| `400` | 请求体 JSON 格式错误 | Spring 默认返回 `{"error": "Bad Request"}` |
| `500` | 后端内部异常（如 ChromaDB 连接失败、DeepSeek API 超时） | Spring 默认返回 `{"error": "Internal Server Error"}` |
| `502` | ChromaDB 未启动或端口不通 | 后端服务依赖 ChromaDB，启动前请确保容器已运行 |

### 调试建议

如遇 `500` 错误，请检查后端日志：

```bash
# Docker Compose 模式
docker compose logs -f backend

# 手动启动模式（从 backend/ 目录内）
./mvnw spring-boot:run
```

重点关注：
- `DEEPSEEK_API_KEY` 是否已设置且有效
- ChromaDB 是否已启动并在 `localhost:8000` 响应正常
- `backend/data/` 目录是否存在 PDF 文件

---

*最后更新：2026-06-07*
