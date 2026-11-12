# Deployment Guide (DEPLOY.md)

> This guide covers three deployment scenarios: local development, Docker Compose one-command startup, and self-hosted server deployment.
>
> **[中文版 (Chinese version)](DEPLOY.zh-CN.md)**

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Scenario 1: Docker Compose (recommended)](#scenario-1-docker-compose-recommended)
- [Scenario 2: Manual startup (development)](#scenario-2-manual-startup-development)
- [Scenario 3: Self-hosted server deployment](#scenario-3-self-hosted-server-deployment)
- [Environment Variables](#environment-variables)
- [FAQ](#faq)

---

## Prerequisites

| Component | Version | Purpose | Get it |
|-----------|---------|---------|--------|
| Docker | Latest | Run ChromaDB + backend containers | [Docker Desktop](https://www.docker.com/products/docker-desktop/) |
| Docker Compose | v2+ | Multi-container orchestration | Bundled with Docker Desktop |
| JDK 21 | Eclipse Temurin | Local development & compilation | [Adoptium](https://adoptium.net/temurin/releases/?version=21) |
| Node.js | 20+ | Frontend development | [nodejs.org](https://nodejs.org/) |
| DeepSeek API Key | — | LLM calls (current default) | [platform.deepseek.com](https://platform.deepseek.com/) |

> 🎯 **BYOK note**: connecting an arbitrary OpenAI-compatible endpoint (custom `base_url` / `api_key` / `model`) is a **planned target contract** — see [docs/CONFIGURATION.md](docs/CONFIGURATION.md). It is **not supported by the current code**, which defaults to DeepSeek. The variable names below reflect the current implementation.

---

## Scenario 1: Docker Compose (recommended)

Best for: quickly running the full stack, local acceptance testing, and self-hosted server deployment.

### 1. Clone the repository

```bash
git clone https://github.com/fejerome/aviation-maintenance-assistant.git
cd aviation-maintenance-assistant
```

### 2. Configure environment variables

```bash
# Copy the template
cp .env.example .env

# Edit .env and fill in your DeepSeek API key
# Windows: open .env with Notepad / VS Code
# macOS/Linux:
echo "DEEPSEEK_API_KEY=sk-xxxxxxxx" > .env
```

> ⚠️ **Security**: `.env` is protected by `.gitignore`. **Never** commit it to Git.

### 3. Start all services

```bash
docker compose up -d
```

The first build is slow (Maven and Node dependencies are downloaded and packaged). Subsequent startups are near-instant.

Verify service status:

```bash
# Container status
docker compose ps

# Backend logs (confirm successful startup)
docker compose logs -f backend

# ChromaDB health
curl http://localhost:8000/api/v1/heartbeat

# Test the backend API
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"What is AMM?"}'
```

### 4. Access the application

- Frontend: **http://localhost** (or your server's address)
- Backend API: **http://localhost:8080/api/chat**
- ChromaDB: **http://localhost:8000**

### 5. Document ingestion (localhost / intranet only)

Place your legally obtained PDF manuals into `backend/data/`, then:

```bash
# Should only be executed from the host itself; restrict public access at the
# reverse-proxy layer (see Scenario 3) — the endpoint itself has no auth in the current version.
curl -X POST http://localhost:8080/api/admin/ingest
```

### 6. Stop services

```bash
# Stop and remove containers (data persists in volumes)
docker compose down

# Full cleanup (including ChromaDB persistent data — use with caution)
docker compose down -v
```

---

## Scenario 2: Manual startup (development)

Best for: breakpoint debugging, hot reload after backend changes, or when you don't want to build the backend with Docker.

### 1. Start ChromaDB

```bash
docker run -d --name chromadb -p 8000:8000 \
  -v ./backend/chroma-data:/chroma/chroma \
  chromadb/chroma:0.5.5
```

### 2. Start the backend

> ⚠️ **Important**: must be started from inside `backend/`, otherwise the ingestion relative path `Paths.get("data")` will not resolve.

**macOS / Linux:**

```bash
cd backend
export DEEPSEEK_API_KEY="sk-xxxxxxxx"
./mvnw spring-boot:run
```

**Windows PowerShell:**

```powershell
cd backend
$env:DEEPSEEK_API_KEY="sk-xxxxxxxx"
.\mvnw.cmd spring-boot:run
```

**Windows CMD:**

```cmd
cd backend
set DEEPSEEK_API_KEY=sk-xxxxxxxx
mvnw.cmd spring-boot:run
```

### 3. Start the frontend

```bash
cd ../frontend
npm install
npm run dev
```

Access:
- Frontend: **http://localhost:5173**
- Backend API: **http://localhost:8080/api/chat**

> Note: in development, the Vite dev server proxies `/api` to `http://localhost:8080`; no Nginx rewrite is needed.

---

## Scenario 3: Self-hosted server deployment

Best for: running your own instance on a cloud VM or a home/lab server, for yourself or your team — **as a private, self-hosted deployment**, not a public demo service.

### Suggested architecture

```
[You / your team] --HTTP/HTTPS--> [Nginx reverse proxy] --/api/*--> [Spring Boot backend (:8080)]
                                              |
                                              +--/     --> [Frontend static assets (Nginx :80)]
                                              +--internal--> [ChromaDB (:8000, backend only)]
```

### Deployment steps

1. **Prepare a server** (recommended: 2C4G, CentOS/Ubuntu)
2. **Install Docker + Docker Compose**
3. **Clone the repository** and configure `.env`
4. **Adjust `docker-compose.yml`**:
   - Change the `backend` port mapping from `"8080:8080"` to `"127.0.0.1:8080:8080"` (loopback only)
   - Remove the `chromadb` port mapping entirely (internal access from backend only)
   - Keep the `frontend` service exposed on port 80
5. **Configure Nginx** (example — replace `ama.example.com` with your own domain or IP):

   ```nginx
   server {
       listen 80;
       server_name ama.example.com;
       return 301 https://$server_name$request_uri;
   }

   server {
       listen 443 ssl;
       server_name ama.example.com;

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

6. **Restrict `/api/admin/ingest` to the intranet** (the endpoint has no built-in auth in the current version — this restriction is **required**, not optional):

   ```nginx
   location /api/admin/ingest {
       allow 127.0.0.1;
       allow 10.0.0.0/8;
       allow 172.16.0.0/12;
       allow 192.168.0.0/16;
       deny all;
       proxy_pass http://backend:8080/api/admin/ingest;
   }
   ```

7. **Start services**: `docker compose up -d`
8. **Configure SSL**: use Certbot to obtain a Let's Encrypt certificate

> 💡 **Lightweight option**: if server resources are limited, deploy only backend + ChromaDB and serve the frontend build from any static host.

---

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DEEPSEEK_API_KEY` | ✅ | — | DeepSeek API key for LLM calls (current default provider) |
| `LANGCHAIN4J_OPEN_AI_CHAT_MODEL_API_KEY` | ❌ | `${DEEPSEEK_API_KEY}` | Explicitly set the LLM API key (usually not needed separately) |
| `LANGCHAIN4J_CHROMA_EMBEDDING_STORE_BASE_URL` | ❌ | `http://localhost:8000` | ChromaDB base URL. Automatically overridden to `http://chromadb:8000` in Docker Compose |
| `SERVER_PORT` | ❌ | `8080` | Backend HTTP port |

> 🎯 Additional variables for arbitrary OpenAI-compatible endpoints (`base_url`, `model`) are part of a **planned target contract** — see [docs/CONFIGURATION.md](docs/CONFIGURATION.md). They are not wired into the current code.

---

## FAQ

### Q1: After `docker compose up`, the backend keeps restarting / reports `Connection refused` to ChromaDB

**Cause**: the backend starts faster than ChromaDB, which isn't ready yet.

**Fix**:
- `docker-compose.yml` already configures `depends_on` + `condition: service_healthy`, so the backend only starts after ChromaDB is healthy.
- If it still happens, wait 10 seconds and restart the backend container:
  ```bash
  docker compose restart backend
  ```

### Q2: Docker volume mount fails on Windows with `invalid mount config`

**Cause**: Windows path separators or permission issues.

**Fix**:
- Run commands in **PowerShell** or **CMD**, not in MSYS/Cygwin emulation layers.
- Make sure `backend/chroma-data` exists; create it if missing:
  ```powershell
  New-Item -ItemType Directory -Force -Path backend\chroma-data
  ```
- In Docker Desktop → Settings → Resources → File Sharing, ensure the project directory is shared.

### Q3: Vector data lost after restarting containers

**Cause**: `backend/chroma-data` may have been deleted, or the volume is not mounted correctly.

**Fix**:
- **Do not manually delete `backend/chroma-data/`** — it is ChromaDB's persistent data directory.
- Check the volume mount in `docker-compose.yml`:
  ```yaml
  volumes:
    - ./backend/chroma-data:/chroma/chroma
  ```
- If data is lost, re-run PDF ingestion (localhost/intranet only):
  ```bash
  curl -X POST http://localhost:8080/api/admin/ingest
  ```

### Q4: `./mvnw spring-boot:run` reports `JAVA_HOME` errors / class file version mismatch

**Cause**: the system default JDK is not 21.

**Fix**:
- Confirm `java -version` prints `openjdk version "21"`.
- If not, temporarily set JAVA_HOME:
  ```bash
  export JAVA_HOME=/path/to/jdk-21
  export PATH=$JAVA_HOME/bin:$PATH
  ```

### Q5: Frontend hits CORS errors calling the backend

**Cause**: Nginx is not proxying `/api` correctly in production.

**Fix**:
- Confirm the `frontend` service in `docker-compose.yml` is running.
- Confirm `/api/` `proxy_pass` in `frontend/nginx.conf` points to the right target.
- In development, `vite.config.js` already proxies `/api` to `http://localhost:8080` — make sure frontend requests start with `/api`.

### Q6: How to verify RAG retrieval works?

```bash
# 1. Make sure PDFs are ingested (localhost/intranet only)
curl -X POST http://localhost:8080/api/admin/ingest

# 2. Ask a general-knowledge question
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is AMM?"}'
```

If the reply contains manual citations (e.g. `Source: ... Page 36`), the RAG pipeline is working.

### Q7: API returns 429 Too Many Requests

**Cause**: rate limiting was triggered:

- Per-IP limit: 20 requests/hour/IP by default.
- Global daily quota: 500 total LLM calls/day.

**Fix**:

- Per-IP limit: wait an hour and retry.
- Daily quota exhausted: it resets the next day; to adjust temporarily, change `DAILY_LIMIT` in `DeepSeekQuotaService`.

---

## Post-deployment verification checklist

The following items **cannot** be fully simulated locally; verify them after your server + Nginx setup is complete:

| Item | How to verify | Expected result |
|------|---------------|-----------------|
| Real-IP rate limiting | Hit `/api/chat` from different public IPs or proxies | Each IP is counted independently; 429 after limit |
| Nginx intranet restriction | Access `/api/admin/ingest` from a public network | Nginx returns 403 directly; request never reaches the backend |
| HTTPS redirect | Visit `http://<your-domain>` | Automatic 301 to `https://<your-domain>` |
| DNS resolution | Access frontend and backend via your domain | Both work |
| ChromaDB not exposed | Access `http://<server-ip>:8000` from outside | Connection timeout or refusal |
| Security group / firewall | Only ports 80 and 443 open | All other ports unreachable |

> 💡 Check each item off on first deployment and keep screenshots as your acceptance record.

---

*[中文版本](DEPLOY.zh-CN.md) · Last updated: 2026-11-12 (self-hosted repositioning)*
