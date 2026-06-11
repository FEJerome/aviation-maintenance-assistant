export const guideSections = [
  {
    id: 'background',
    icon: '🎯',
    title: '项目背景',
    content: `**为什么做这个项目？**

航空机务维修依赖**官方维修手册（AMM）**和**法规文件（FAA）**，单机型资料动辄数十万页。机务人员遇到故障时，用传统关键词搜索经常"搜不到"或"搜不准"——因为手册是英文的，而机务用中文描述故障。

**这个系统解决什么问题？**

- 上传 **AMM / FAA 法规** PDF → 自动分块、向量化、存入 ChromaDB 向量数据库
- 机务用中文自然语言提问 → **查询翻译层自动转英文** → 检索相关知识 → 用中文生成回答
- 回答引用具体手册章节号，可溯源、可验证`
  },
  {
    id: 'architecture',
    icon: '🏗️',
    title: '技术架构',
    content: `| 层级 | 技术选型 |
|------|---------|
| 前端 | Vue 3 + 自研增量渲染引擎（SSE 流式输出 + Markdown 实时解析） |
| 后端 | Spring Boot 3.5 + LangChain4j 1.14 + JDK 21 |
| 向量数据库 | ChromaDB（Docker 本地部署） |
| 嵌入模型 | all-MiniLM-L6-v2（本地 ONNX，20MB 轻量） |
| 大模型 | DeepSeek Chat API |
| 部署 | Docker + docker-compose 一键启动 |

**查询翻译层**：已实现的 Query Transformation 组件，将中文问题翻译成英文后再检索英文向量库，解决跨语言语义鸿沟。`
  },
  {
    id: 'challenges',
    icon: '⚡',
    title: '核心难点',
    content: `**难点 1：查询翻译层（中英文跨语言检索）**

- 用户用中文提问"液压系统压力低"，手册里是"Hydraulic System Pressure Low"
- 直接用中文检索英文向量数据库，相似度极低，会漏掉正确答案
- 解决方案：DeepSeek 翻译层做 Query Transformation，把中文问题先翻译成英文再检索

**难点 2：增量渲染（SSE 流式输出 + Markdown 实时解析）**

- LLM 生成是流式的，如果等全部生成完再显示，用户要等待 5-10 秒
- 直接逐字追加会导致 Markdown 语法断裂（如 \`\`\*\*加粗\`\`\` 还没闭合就渲染）
- 解决方案：双 buffer 架构——未确认段落用纯文本 buffer，完整段落切到 HTML buffer 渲染

**难点 3：RAG 分块策略调优**

- 分块太大 → 检索精度下降；分块太小 → 上下文断裂
- 需要在 chunk size 和 overlap 之间找到平衡点，保证手册语义单元完整`
  },
  {
    id: 'role',
    icon: '👨‍💻',
    title: '我的角色',
    content: `**全栈独立开发**

**后端：**

- 从 0 搭建 Spring Boot 项目骨架
- 设计并实现 RAG 核心流程：Load → Split → Embed → Store → Retrieve → Generate
- 开发查询翻译层（Query Transformation）
- ChromaDB 向量数据库接入与调优
- Agent 机型验证层架构设计（路由层 + 验证服务 + 会话状态）

**前端：**

- Vue 3 单页应用架构设计
- 自研增量渲染引擎（双 buffer + 段落级确认机制）
- Markdown 渲染器（代码块高亮、表格、列表支持）
- SSE 流式数据接收与状态管理

**AI 工程：**

- DeepSeek API 接入与 Prompt Engineering
- RAG 分块策略调优（chunk size / overlap）
- 查询翻译层设计与实现`
  },
  {
    id: 'comparison',
    icon: '🆚',
    title: '与 ChatGPT 的区别',
    content: `| 维度 | ChatGPT | 本项目 |
|------|---------|--------|
| **知识来源** | 通用互联网数据（可能过时、不可信） | **AMM 维修手册 / FAA 法规**（权威、可溯源） |
| **幻觉控制** | 无领域约束，可能瞎编机型数据 | 机型验证层架构设计（硬编码约束，阻断错配） |
| **回答格式** | 自由文本，无结构化引用 | 引用具体手册章节号，可追溯原始出处 |
| **语言支持** | 中英文混合回答，质量不稳定 | 查询翻译层保证跨语言检索精度 |
| **部署方式** | 云端 SaaS，数据外泄风险 | 本地化 Docker 部署，数据自主可控 |`
  }
]
