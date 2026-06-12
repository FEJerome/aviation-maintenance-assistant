# 功能设计：统一 API 前缀 `/api`

## 背景

当前后端接口路径为：
- `POST /chat`
- `POST /chat/stream`
- `POST /admin/ingest`

前端请求路径为：
- `/api/chat/stream`

开发环境通过 `vite.config.js` 的 proxy rewrite 规则，把 `/api/chat/stream` 改写成 `/chat/stream` 再转发到后端：

```javascript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
    rewrite: (path) => path.replace(/^\/api/, '')
  }
}
```

## 问题

1. **Nginx 生产配置复杂**：生产环境需要在 Nginx 中做同样的 rewrite：
   ```nginx
   location /api/ {
       proxy_pass http://backend:8080/;  # 注意末尾斜杠，容易配错
   }
   ```
   多一个斜杠或少一个斜杠都会导致 404。

2. **不够规范**：开源项目和生产系统中，统一使用 `/api` 前缀是更标准的做法。前后端路径一一对应，不需要 rewrite。

## 目标

后端所有接口统一加 `/api` 前缀，Nginx/Vite 直接转发，无需 rewrite。

## 变更清单

### 后端

1. `ChatController.java`
   ```java
   @RestController
   @RequestMapping("/api/chat")
   public class ChatController { ... }
   ```

2. `DocumentIngestionController.java`
   ```java
   @RestController
   @RequestMapping("/api/admin/ingest")
   public class DocumentIngestionController { ... }
   ```

### 前端

1. `vite.config.js`
   去掉 rewrite：
   ```javascript
   proxy: {
     '/api': {
       target: 'http://localhost:8080',
       changeOrigin: true
     }
   }
   ```

2. `App.vue`
   确认前端请求路径保持 `/api/chat/stream`，无需修改。

### 文档

1. `DEPLOY.md` 更新 Nginx 配置示例。
2. `README.md` 如有接口示例，同步更新。

## Nginx 配置示例（生产）

```nginx
server {
    listen 80;
    server_name ama.pandazi.cn;

    location / {
        root /usr/share/nginx/html;
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

## 验收方式

1. 开发环境：`npm run dev` + `./mvnw spring-boot:run`，前端能正常对话。
2. Docker 环境：`docker compose up -d`，访问 `http://localhost`，前端能正常对话。
3. 直接 curl 测试：
   ```bash
   curl -X POST http://localhost:8080/api/chat \
     -H "Content-Type: application/json" \
     -d '{"message":"What is AMM?"}'
   ```

## 风险与回滚

| 风险 | 应对 |
|------|------|
| 前端旧路径缓存 | 清理浏览器缓存，Vite dev server 重启后自动生效 |
| Nginx 配置遗漏 | 更新 DEPLOY.md，按文档逐步配置 |
| 其他接口遗漏 | 全文搜索 `@RequestMapping` 和 `@PostMapping`，确保全部加上 `/api` |

回滚：去掉 Controller 上的 `/api` 前缀，恢复 Vite rewrite 即可。

## 关键文件

- `backend/src/main/java/cn/pandazi/aviation_maintenance_assistant/chat/api/ChatController.java`
- `backend/src/main/java/cn/pandazi/aviation_maintenance_assistant/document/api/DocumentIngestionController.java`
- `frontend/vite.config.js`
- `DEPLOY.md`

## 相关文档

- [FEATURE-rate-limiting](FEATURE-rate-limiting.md)
- [FEATURE-frontend-docker](FEATURE-frontend-docker.md)
