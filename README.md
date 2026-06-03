# 机务维修知识助手（Aviation Maintenance Assistant）

基于 RAG + Agent 技术的航空维修知识库智能问答系统。

> **项目定位**：Java + AI 交叉方向的求职作品，结合 4 年深航机务维修经验，构建垂类领域 Agent。

---

## 项目结构

```
.
├── backend/          Spring Boot 3.5 + LangChain4j + Java 21
├── frontend/         Vue 3 + Vite（待初始化）
└── README.md         本文件
```

---

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| JDK | Eclipse Temurin | 21 LTS |
| 后端框架 | Spring Boot | 3.5.14 |
| AI 框架 | LangChain4j | 1.14.1 |
| LLM | DeepSeek | deepseek-chat |
| 向量数据库 | ChromaDB | 0.5.5（Docker）|
| 嵌入模型 | all-MiniLM-L6-v2 | 本地 ONNX |
| 前端 | Vue 3 | 3.4.x（Vite）|

---

## 快速启动

### 前置条件

- JDK 21（Eclipse Temurin）
- Maven（Wrapper 已包含）
- Docker（用于 ChromaDB）
- Node.js 20+（前端开发时）

### 1. 启动 ChromaDB（向量数据库）

```bash
docker run -d --name chromadb -p 8000:8000 \
  -v ./backend/chroma-data:/chroma/chroma \
  chromadb/chroma:0.5.5
```

### 2. 启动后端

⚠️ **必须从 `backend/` 目录内启动**，否则文档摄入的相对路径会失效。

```bash
cd backend
./mvnw spring-boot:run
```

后端服务将运行在 http://localhost:8080

### 3. 初始化前端（待补充）

```bash
cd frontend
npm install
npm run dev
```

---

## 核心功能

- **RAG 问答**：基于 FAA AC、AMM 等公开维修手册的检索增强生成
- **Agent 机型验证**：对话前强制校验飞机型号与发动机型号匹配
- **确定性路由**：发动机/飞控/起落架等关键系统走硬性代码路由，不走 LLM 自路由
- **会话管理**：内存 LRU 会话存储（演示版），支持 conversationId 续聊

---

## 数据来源

| 资料 | 类型 | 用途 |
|------|------|------|
| FAA AC 65-9A | 美国政府公开教材 | 机务基础知识库 |
| Flight Design CTLS AMM | 轻型飞机公开维修手册 | 具体维护程序演示 |
| Rotax 912 Line Maintenance | 发动机维护手册 | 发动机系统演示 |

---

## 注意事项

- `backend/data/` 存放 PDF 源文档，运行 `/admin/ingest` 接口进行向量嵌入
- `backend/chroma-data/` 是 ChromaDB 持久化数据，**不要手动删除**
- 后端启动前请确保 ChromaDB 容器已运行（端口 8000）
