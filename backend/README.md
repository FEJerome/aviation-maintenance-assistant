# backend — 机务维修知识助手（后端服务）

Spring Boot 3.5 + LangChain4j 构建的 RAG 问答与 Agent 验证服务。

> **⚠️ 重要**：本项目必须从 `backend/` 目录内启动，否则文档摄入的相对路径 `Paths.get("data")` 会失效。

---

## 前置条件

- JDK 21（Eclipse Temurin）
- Maven（Wrapper 已包含：`./mvnw`）
- Docker（用于 ChromaDB）
- DeepSeek API Key（环境变量）

---

## 快速启动

### 1. 启动 ChromaDB（向量数据库）

```bash
docker run -d --name chromadb -p 8000:8000 \
  -v ./chroma-data:/chroma/chroma \
  chromadb/chroma:0.5.5
```

### 2. 设置环境变量

```bash
export DEEPSEEK_API_KEY="sk-xxxxxxxx"
```

Windows PowerShell：
```powershell
$env:DEEPSEEK_API_KEY="sk-xxxxxxxx"
```

### 3. 编译并运行

```bash
./mvnw clean compile
./mvnw spring-boot:run
```

服务启动后访问：http://localhost:8080

---

## 项目结构

```
backend/
├── src/main/java/cn/pandazi/aviation_maintenance_assistant/
│   ├── AviationMaintenanceAssistantApplication.java   # 启动类
│   ├── config/
│   │   ├── OpenAiConfig.java                          # DeepSeek LLM 配置
│   │   └── RagConfig.java                             # ChromaDB + RAG 配置
│   ├── chat/
│   │   ├── api/ChatController.java                    # /chat 接口
│   │   ├── dto/ChatRequest.java / ChatResponse.java   # 请求/响应
│   │   ├── routing/
│   │   │   ├── AircraftInfoExtractor.java             # 机型/发动机提取
│   │   │   └── TopicClassifier.java                   # 关键词路由
│   │   ├── service/
│   │   │   ├── Assistant.java                         # LangChain4j AI Service
│   │   │   └── MaintenanceChatService.java            # 聊天业务核心
│   │   └── session/
│   │       ├── ChatSessionStore.java                  # 内存 LRU 会话
│   │       └── SessionContext.java                    # 会话上下文
│   ├── document/
│   │   ├── api/DocumentIngestionController.java       # /admin/ingest
│   │   └── service/DocumentIngestionService.java      # PDF 解析与嵌入
│   ├── rag/
│   │   └── TranslationQueryTransformer.java           # 查询翻译器
│   └── validation/
│       ├── dto/ValidationResult.java
│       └── service/AircraftValidationService.java     # 机型-发动机匹配
├── src/main/resources/
│   └── application.yaml                               # 主配置
├── src/test/
├── data/                                              # PDF 源文档
├── chroma-data/                                       # ChromaDB 持久化数据
└── pom.xml
```

---

## 核心接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat` | 聊天问答（支持 `conversationId` 续聊） |
| POST | `/admin/ingest` | 批量摄入 `data/` 目录下所有 PDF |

### /chat 请求示例

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "CTLS with Rotax 912 engine, what is the oil pressure range?",
    "conversationId": "optional-existing-id"
  }'
```

**响应**：
```json
{
  "reply": "根据手册...",
  "conversationId": "uuid-here"
}
```

---

## 关键配置（application.yaml）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | `8080` | HTTP 端口 |
| `langchain4j.open-ai.chat-model.api-key` | `${DEEPSEEK_API_KEY}` | DeepSeek API Key（环境变量） |
| `langchain4j.open-ai.chat-model.base-url` | `https://api.deepseek.com/v1` | DeepSeek 接口地址 |
| `langchain4j.chroma.embedding-store.base-url` | `http://localhost:8000` | ChromaDB 服务地址 |
| `langchain4j.chroma.embedding-store.collection-name` | `aviation-docs` | 向量集合名 |
| `app.rag.retrieval.max-results` | `5` | 单次检索返回片段数 |
| `app.rag.retrieval.min-score` | `0.6` | 向量相似度阈值 |

---

## 重要目录说明

| 目录 | 用途 | 是否提交 Git |
|------|------|-------------|
| `data/` | PDF 源文档，运行 `/admin/ingest` 进行向量嵌入 | ❌ `.gitignore` 忽略 |
| `chroma-data/` | ChromaDB 持久化数据（sqlite + collection） | ❌ `.gitignore` 忽略 |
| `target/` | Maven 构建输出 | ❌ `.gitignore` 忽略 |

> **不要手动删除 `chroma-data/`**，否则所有向量数据需重新摄入。

---

## 技术栈

- JDK 21（Virtual Threads）
- Spring Boot 3.5.14
- LangChain4j 1.14.1
- DeepSeek（OpenAI 兼容 API）
- ChromaDB 0.5.5（外部 Docker 服务）
- all-MiniLM-L6-v2（本地 ONNX 嵌入模型）
- Apache Tika（PDF 解析）

---

## 相关文档

- [根目录 README](../README.md) — 项目总览与演示说明
- [CLAUDE.md](CLAUDE.md) — 智能体上下文与架构决策
