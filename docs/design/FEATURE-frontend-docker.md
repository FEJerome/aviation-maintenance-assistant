# 功能设计：前端 Docker 化

## 背景

当前 `docker-compose.yml` 只包含 `chromadb` 和 `backend` 两个服务。前端需要单独执行 `npm run dev` 启动，无法通过 `docker compose up -d` 一键启动完整服务。

为了云端部署和开源用户快速体验，需要将前端也纳入 Docker Compose 编排。

## 目标

1. 新增 `frontend/Dockerfile`，基于 Nginx 托管前端生产包。
2. 新增 `frontend/nginx.conf`，配置反向代理到后端。
3. 修改 `docker-compose.yml`，增加 `frontend` 服务。
4. 生产部署时，访问 `http://localhost`（或域名）即可看到完整应用。

## 方案

### 1. 前端 Dockerfile

```dockerfile
# 阶段一：构建生产包
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# 阶段二：Nginx 托管
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### 2. 前端 nginx.conf

```nginx
server {
    listen 80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 3. docker-compose.yml 修改

```yaml
services:
  chromadb:
    image: chromadb/chroma:0.5.5
    # ...（保持现有配置）

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    # ...（保持现有配置）

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: ama-frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    networks:
      - ama-network
```

## 生产部署建议

### 方案 A：docker-compose 一键启动（推荐，适合个人服务器）

```bash
docker compose up -d
```

访问 `http://服务器IP` 即可。

### 方案 B：前端单独托管（适合有域名 + HTTPS 的场景）

- 后端 + ChromaDB 用 docker-compose 启动
- 前端用 Vercel / Netlify / Cloudflare Pages 免费托管
- 优点：前端 CDN 加速、自动 HTTPS、不占用服务器资源
- 缺点：需要配置跨域或统一域名

## 本地构建验证

```bash
cd frontend
docker build -t ama-frontend .
```

## 验收方式

1. 在项目根目录执行 `docker compose up -d`。
2. 访问 `http://localhost`。
3. 看到项目介绍弹出层。
4. 关闭弹出层，发送问题，验证 SSE 流式对话正常。
5. 验证 `/api/admin/ingest` 被 Nginx 正确代理到后端（内网限制另见限流设计）。

## 风险与回滚

| 风险 | 应对 |
|------|------|
| 前端构建失败 | 确保 `npm run build` 在本地能成功 |
| Nginx 反向代理 404 | 确认 `/api` 前缀已统一，nginx.conf 与后端路径一致 |
| 端口 80 被占用 | 生产环境可改为 `8080:80` 或其他端口 |

回滚：从 `docker-compose.yml` 中移除 `frontend` 服务，恢复手动启动前端。

## 关键文件

- `frontend/Dockerfile`（新建）
- `frontend/nginx.conf`（新建）
- `docker-compose.yml`

## 相关文档

- [FEATURE-api-prefix](FEATURE-api-prefix.md)
- [FEATURE-rate-limiting](FEATURE-rate-limiting.md)
- [DEPLOY.md](../../DEPLOY.md)
