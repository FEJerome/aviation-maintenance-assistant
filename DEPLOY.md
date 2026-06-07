# 部署指南（DEPLOY.md）

> 本文档覆盖三种部署场景：本地开发、Docker Compose 一键启动、云服务器生产部署。

---

## 目录

- [前置条件](#前置条件)
- [场景一：Docker Compose 一键启动（推荐）](#场景一docker-compose-一键启动推荐)
- [场景二：手动启动（开发调试）](#场景二手动启动开发调试)
- [场景三：云服务器生产部署](#场景三云服务器生产部署)
- [环境变量说明](#环境变量说明)
- [常见问题（FAQ）](#常见问题faq)

---

## 前置条件

| 组件 | 版本 | 用途 | 获取地址 |
|------|------|------|---------|
| Docker | 最新版 | 运行 ChromaDB + 后端容器 | [Docker Desktop](https://www.docker.com/products/docker-desktop/) |
| Docker Compose | v2+ | 编排多容器 | 随 Docker Desktop 附带 |
| JDK 21 | Eclipse Temurin | 本地开发编译 | [Adoptium](https://adoptium.net/temurin/releases/?version=21) |
| Node.js | 20+ | 前端开发 | [nodejs.org](https://nodejs.org/) |
| DeepSeek API Key | — | LLM 调用 | [platform.deepseek.com](https://platform.deepseek.com/) |

---

## 场景一：Docker Compose 一键启动（推荐）

适合：快速体验完整功能、给面试官演示、本地验收测试。

### 1. Clone 仓库

```bash
git clone https://github.com/fejerome/aviation-maintenance-assistant.git
cd aviation-maintenance-assistant
```

### 2. 配置环境变量

```bash
# 复制模板文件
cp .env.example .env

# 编辑 .env，填入你的 DeepSeek API Key
# Windows: 用记事本/VS Code 打开 .env
# macOS/Linux:
echo "DEEPSEEK_API_KEY=sk-xxxxxxxx" > .env
```

> ⚠️ **安全提示**：`.env` 文件已被 `.gitignore` 保护，**不要**将其提交到 Git。

### 3. 启动 ChromaDB + 后端

```bash
docker compose up -d
```

首次构建会比较慢（需要下载 Maven 依赖并打包 JAR），请耐心等待。后续启动秒级完成。

验证服务状态：

```bash
# 查看容器状态
docker compose ps

# 查看后端日志（确认启动成功）
docker compose logs -f backend

# 查看 ChromaDB 健康状态
curl http://localhost:8000/api/v1/heartbeat
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问地址：
- 前端界面：**http://localhost:5173**
- 后端 API：**http://localhost:8080**

### 5. 停止服务

```bash
# 停止并删除容器（数据保留在 volume 中）
docker compose down

# 彻底清理（包括 ChromaDB 持久化数据，谨慎使用）
docker compose down -v
```

---

## 场景二：手动启动（开发调试）

适合：需要断点调试、修改后端代码后热重载、不打算使用 Docker 构建后端的场景。

### 1. 启动 ChromaDB

```bash
docker run -d --name chromadb -p 8000:8000 \
  -v ./backend/chroma-data:/chroma/chroma \
  chromadb/chroma:0.5.5
```

### 2. 启动后端

> ⚠️ **重要**：必须从 `backend/` 目录内启动，否则文档摄入的相对路径 `Paths.get("data")` 会失效。

**macOS / Linux：**

```bash
cd backend
export DEEPSEEK_API_KEY="sk-xxxxxxxx"
./mvnw spring-boot:run
```

**Windows PowerShell：**

```powershell
cd backend
$env:DEEPSEEK_API_KEY="sk-xxxxxxxx"
.\mvnw.cmd spring-boot:run
```

**Windows CMD：**

```cmd
cd backend
set DEEPSEEK_API_KEY=sk-xxxxxxxx
mvnw.cmd spring-boot:run
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

---

## 场景三：云服务器生产部署

适合：将项目部署到阿里云/腾讯云/华为云等公有云，提供在线演示地址。

### 架构建议

```
[用户] --HTTPS--> [Nginx 反向代理] --> [前端静态资源 (dist/)]
                                    --> [Spring Boot 后端 (:8080)]
                                    --> [ChromaDB (:8000，仅内网访问)]
```

### 部署步骤

1. **准备云服务器**（推荐配置：2C4G，CentOS/Ubuntu）
2. **安装 Docker + Docker Compose**
3. **Clone 仓库**并配置 `.env`
4. **构建前端生产包**：
   ```bash
   cd frontend
   npm install
   npm run build
   # 产物在 frontend/dist/ 目录
   ```
5. **修改 `docker-compose.yml`**：
   - 将 `backend` 服务的端口从 `"8080:8080"` 改为 `"127.0.0.1:8080:8080"`（仅本机访问）
   - 将 `chromadb` 的端口映射删除（仅后端内网访问）
   - 新增 `nginx` 服务托管前端 dist 并反向代理到后端
6. **配置 Nginx**（示例）：
   ```nginx
   server {
       listen 80;
       server_name ama.pandazi.cn;
       return 301 https://$server_name$request_uri;
   }
   server {
       listen 443 ssl;
       server_name ama.pandazi.cn;

       location / {
           root /usr/share/nginx/html;
           try_files $uri $uri/ /index.html;
       }

       location /api/ {
           proxy_pass http://backend:8080/;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
       }
   }
   ```
7. **启动服务**：`docker compose up -d`
8. **配置 SSL**：使用 Certbot 申请 Let's Encrypt 证书

> 💡 **简化方案**：如果服务器资源有限，也可以只部署后端 + ChromaDB，前端继续用 Vercel/Netlify 免费托管。

---

## 环境变量说明

| 变量名 | 必填 | 默认值 | 说明 |
|--------|------|--------|------|
| `DEEPSEEK_API_KEY` | ✅ | — | DeepSeek API Key，用于 LLM 调用 |
| `LANGCHAIN4J_OPEN_AI_CHAT_MODEL_API_KEY` | ❌ | `${DEEPSEEK_API_KEY}` | 显式指定 LLM API Key（一般不需要单独设置） |
| `LANGCHAIN4J_CHROMA_EMBEDDING_STORE_BASE_URL` | ❌ | `http://localhost:8000` | ChromaDB 服务地址。Docker Compose 中自动覆盖为 `http://chromadb:8000` |
| `SERVER_PORT` | ❌ | `8080` | 后端 HTTP 端口 |

---

## 常见问题（FAQ）

### Q1：`docker compose up` 后后端一直重启 / 报 `Connection refused` to ChromaDB

**原因**：后端启动比 ChromaDB 快，此时 ChromaDB 还没准备好。

**解决**：
- `docker-compose.yml` 中已配置 `depends_on` + `condition: service_healthy`，确保 ChromaDB 健康后才启动后端。
- 如果仍有问题，手动等待 10 秒后重启后端容器：
  ```bash
  docker compose restart backend
  ```

### Q2：Windows 下 Docker volume 挂载报错 `invalid mount config`

**原因**：Windows 路径分隔符或权限问题。

**解决**：
- 确保在 **PowerShell** 或 **CMD** 中执行，不要使用 MSYS/Cygwin 等模拟环境。
- 检查 `backend/chroma-data` 目录是否存在，如果不存在先手动创建：
  ```powershell
  New-Item -ItemType Directory -Force -Path backend\chroma-data
  ```
- 在 Docker Desktop → Settings → Resources → File Sharing 中确保项目目录已共享。

### Q3：PDF 摄入后重启容器，向量数据丢失了

**原因**：可能误删了 `backend/chroma-data` 目录，或没有正确挂载 volume。

**解决**：
- **不要手动删除 `backend/chroma-data/`**，这是 ChromaDB 的持久化数据目录。
- 检查 `docker-compose.yml` 中 volume 挂载是否正确：
  ```yaml
  volumes:
    - ./backend/chroma-data:/chroma/chroma
  ```
- 若数据已丢失，需重新执行 PDF 摄入：
  ```bash
  curl -X POST http://localhost:8080/admin/ingest
  ```

### Q4：`./mvnw spring-boot:run` 报 `JAVA_HOME` 错误 / 类文件版本不匹配

**原因**：系统默认 JDK 不是 21。

**解决**：
- 确认 `java -version` 输出为 `openjdk version "21"`。
- 如果不是，临时指定 JAVA_HOME：
  ```bash
  export JAVA_HOME=/path/to/jdk-21
  export PATH=$JAVA_HOME/bin:$PATH
  ```

### Q5：前端访问后端报 CORS 错误

**原因**：前端直接访问后端 IP/端口，跨域被浏览器拦截。

**解决**：
- 开发环境：前端 `vite.config.js` 中已配置代理 `/api` 到 `http://localhost:8080`，请确保前端请求路径以 `/api` 开头。
- 生产环境：通过 Nginx 反向代理，使前端和后端处于同一域名下。

### Q6：如何确认 RAG 检索是否工作正常？

```bash
# 1. 先确保 PDF 已摄入
curl -X POST http://localhost:8080/admin/ingest

# 2. 测试一个通用知识问题
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is AMM?"}'
```

如果返回的回答中包含手册引用来源（如 `来源：... Page 36`），说明 RAG 链路正常工作。

---

*最后更新：2026-06-07*
