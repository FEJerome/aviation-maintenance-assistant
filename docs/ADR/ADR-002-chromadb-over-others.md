# ADR-002：向量数据库选型 — ChromaDB（Docker 持久化）

- **状态**：Accepted
- **日期**：2026-06-02
- **决策者**：马昊宇（项目作者）

---

## 背景

RAG 系统的核心组件之一是向量数据库（Vector Database），用于存储文档嵌入向量并支持相似性检索。项目需要选择一个轻量、可持久化、易于部署的向量数据库方案。

候选方案包括：

1. **InMemoryEmbeddingStore**（LangChain4j 内置）
2. **Redis**（通过 Redis Stack 的向量能力）
3. **Milvus / Qdrant / Weaviate**（专用向量数据库）
4. **ChromaDB**（轻量级开源向量数据库）

---

## 考虑的选项

### 选项 A：InMemoryEmbeddingStore（LangChain4j 内置）

**优势**：
- 零外部依赖，无需启动额外服务
- 开发和测试最简单

**劣势**：
- **重启丢数据**：应用重启后所有向量数据丢失，必须重新执行 PDF 摄入
- **无法多实例共享**：内存存储无法被多个后端实例共享，不具备水平扩展能力
- **面试叙事薄弱**：在求职场景中，"内存存储"显得不够正式，无法体现生产级部署能力
- **云部署困难**：云服务器重启后数据全失，不符合"可在线演示"的目标

**结论**：仅适合原型验证，不适合作为求职作品的正式方案。

### 选项 B：Redis（Redis Stack）

**优势**：
- 企业级缓存/存储方案，生产环境广泛部署
- 支持向量相似性搜索（RediSearch 模块）
- 可作为会话存储的共享后端（本项目目前用内存 LRU，未来可能迁移到 Redis）

**劣势**：
- **引入过重**：项目当前仅需向量存储，Redis Stack 的向量模块配置相对复杂
- **资源占用**：Redis 本身需要额外内存和运维成本
- **过度设计**：两周冲刺期间，Redis 的额外复杂度会分散核心功能的开发精力

### 选项 C：Milvus / Qdrant / Weaviate

**优势**：
- 专用向量数据库，性能最强，功能最全
- Milvus 在云原生和大规模场景下表现优异

**劣势**：
- **部署复杂**：Milvus 需要多个组件（etcd、MinIO、query node 等），启动和运维成本高
- **资源需求大**：不适合低成本云服务器（2C4G 难以流畅运行 Milvus）
- **学习曲线陡峭**：对于求职项目而言，面试官更关注业务逻辑而非向量数据库调参

### 选项 D：ChromaDB（Docker 持久化）

**优势**：
- **轻量单容器**：一个 Docker 容器即可运行，资源占用极低
- **本地持久化**：通过 volume 挂载 `./chroma-data` 到宿主机，重启不丢数据
- **零环境差异**：本地 Docker Desktop 与云服务器使用完全相同的 `docker-compose.yml`，零部署差异
- **LangChain4j 原生支持**：通过 `langchain4j-chroma` 模块直接接入，配置简洁
- **迁移路径清晰**：未来若需切换到 Milvus/Redis，只需替换 `EmbeddingStore` 的实现类，上层业务代码零改动

**劣势**：
- 高并发写入性能不如 Milvus（但本项目为只读查询为主，写入仅在 PDF 摄入时触发）
- 分布式扩展能力有限（但求职项目单机演示足矣）

---

## 决策

**选用 ChromaDB 0.5.5（Docker 持久化模式）作为向量数据库。**

具体部署方式：
- Docker 镜像：`chromadb/chroma:0.5.5`（锁定版本，避免 latest 不兼容风险）
- 持久化：`-v ./backend/chroma-data:/chroma/chroma`
- LangChain4j 接入：`langchain4j-chroma` 模块 + `application.yaml` 配置

---

## 后果

### 积极影响

- 5 分钟内完成向量数据库的部署和初始化，与前后端启动完全解耦
- 26,708 条文档嵌入后的数据持久化到本地，重启后无需重新摄入
- `docker-compose.yml` 在云服务器上完全一致，部署链路零修改

### 消极影响 / trade-off

- 当前锁定 0.5.5 版本，未来升级需验证 API 兼容性（ChromaDB 版本间存在 API 变更历史）
- 单机模式下不具备水平扩展能力，但求职项目的叙事空间已足够

### 相关决策

- [ADR-001](ADR-001-langchain4j-over-spring-ai.md)：LangChain4j 通过 `langchain4j-chroma` 模块提供 ChromaDB 的即插即用支持。

---

## 相关链接

- [ChromaDB 官方文档](https://docs.trychroma.com/)
- [LangChain4j ChromaDB 集成](https://docs.langchain4j.dev/integrations/embedding-stores/chroma/)
- [docker-compose.yml 中 ChromaDB 服务定义](../../docker-compose.yml)
