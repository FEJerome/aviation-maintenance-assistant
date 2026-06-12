# 功能设计：请求限流

## 背景

后端 `/api/chat` 和 `/api/chat/stream` 接口直接调用 DeepSeek API。如果接口无限制地被访问，可能产生以下风险：

1. **Token 被恶意刷光**：每次对话都会消耗 DeepSeek Token，攻击者可通过高频请求快速耗尽余额。
2. **服务被拖垮**：无限制的并发请求可能导致后端线程池耗尽，影响正常用户。
3. **演示风险**：面试官或开源用户误操作反复点击，也可能造成不必要的费用。

因此需要在后端增加**多层限流保护**：

- **IP 级限流**：限制单 IP 频率，防止单个来源高频刷接口。
- **全局日限额**：限制 DeepSeek 每日总调用次数，作为跨 IP 攻击的最后防线。
- **内网限制**：文档摄入接口仅允许内网访问。

## 目标

1. 普通聊天接口按 IP 限流，默认 **20 次/小时/IP**。
2. DeepSeek 全局调用设置 **每日 500 次上限**。
3. 触发限流或日限额耗尽时返回明确提示，不影响前端交互。
4. 文档摄入接口 `/api/admin/ingest` **仅限内网访问**，防止公网被恶意触发重新索引。
5. 限流逻辑不侵入业务代码，通过 Filter 与 Service 统一处理。

## 方案对比

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| Guava RateLimiter | Google 出品，性能好 | 已停止维护，不推荐新项目使用 | ❌ 放弃 |
| Sentinel | 功能丰富，支持熔断降级 | 引入依赖重，配置复杂，杀鸡用牛刀 | ❌ 放弃 |
| 手写计数器 + ScheduledTask | 无外部依赖 | 精度差、边界条件多、难维护 | ❌ 放弃 |
| **Bucket4j** | 令牌桶算法标准实现、轻量、API 清晰、单机性能优秀 | 多实例需额外配置 Redis | ✅ **采纳** |

> 本项目采用单机 Docker Compose 部署，Bucket4j 内存版完全满足需求。未来多实例可平滑升级到 Bucket4j + Redis。

## 详细设计

### 1. 限流策略

| 层级 | 接口 | 规则 | 触发后行为 |
|------|------|------|-----------|
| IP 级 | `/api/chat`, `/api/chat/stream` | 20 次/小时/IP | HTTP 429 + `{"error":"请求过于频繁，请 1 小时后再试"}` |
| 全局日限额 | `/api/chat`, `/api/chat/stream` | 500 次/天（全站总计） | 返回固定降级文案，不调用 DeepSeek |
| 内网限制 | `/api/admin/ingest` | 仅限内网 IP | HTTP 403 + `{"error":"该接口仅限内网访问"}` |

> IP 级与全局限额**同时生效**。即使某 IP 未达到 20 次/小时，只要全局 500 次/天已用完，后续所有请求都会进入全局降级。

### 2. 获取真实 IP

生产环境通过 Nginx 反向代理，需读取 `X-Forwarded-For` 头部。获取逻辑：

```java
String ip = request.getHeader("X-Forwarded-For");
if (ip == null || ip.isBlank()) {
    ip = request.getRemoteAddr();
} else {
    // X-Forwarded-For 可能为逗号分隔的多个 IP，取第一个（最外层客户端）
    ip = ip.split(",")[0].trim();
}
```

### 3. IP 级限流：Bucket4j 配置

```java
@Bean
public Bucket chatBucket() {
    return Bucket.builder()
            .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1))))
            .build();
}
```

每个 IP 独立一个 Bucket：

```java
private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

private Bucket resolveBucket(String ip) {
    return buckets.computeIfAbsent(ip, k -> Bucket.builder()
            .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofHours(1))))
            .build());
}
```

### 4. 全局日限额：DeepSeekQuotaService

采用 Spring 单例 + `AtomicInteger` 实现，重启时从本地文件恢复计数，每天 0 点自动重置。

```java
@Service
public class DeepSeekQuotaService {

    private static final int DAILY_LIMIT = 500;
    private final AtomicInteger dailyCounter = new AtomicInteger(0);
    private final Path quotaFile = Paths.get("data", "daily-llm-quota.json");

    @PostConstruct
    public void init() {
        loadQuota();
        scheduleDailyReset();
    }

    public boolean tryAcquire() {
        int current = dailyCounter.incrementAndGet();
        saveQuota();
        return current <= DAILY_LIMIT;
    }

    public boolean isExhausted() {
        return dailyCounter.get() >= DAILY_LIMIT;
    }
}
```

计数文件格式：

```json
{
  "date": "2026-06-12",
  "count": 128
}
```

- 启动时读取文件，如果 `date` 不是今天，计数重置为 0。
- 每次 `tryAcquire()` 成功后持久化到文件，防止重启后计数丢失。
- 每天 0 点定时任务重置计数。

### 5. Filter 实现

Filter 顺序：尽早拦截，避免进入业务层消耗资源。

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private DeepSeekQuotaService quotaService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 文档摄入接口仅限内网
        if (path.startsWith("/api/admin/ingest")) {
            if (!isInternalIp(resolveClientIp(request))) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"该接口仅限内网访问\"}");
                return;
            }
        }

        // 聊天接口限流
        if (path.startsWith("/api/chat")) {
            String ip = resolveClientIp(request);
            Bucket bucket = resolveBucket(ip);
            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"请求过于频繁，请 1 小时后再试\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
```

### 6. 业务层全局限额检查

Filter 只做 IP 限流，全局日限额在 `MaintenanceChatService` 调用 DeepSeek 之前检查：

```java
public ChatResponse process(String conversationId, String message) {
    if (quotaService.isExhausted()) {
        return new ChatResponse(QUOTA_EXHAUSTED_MESSAGE, conversationId);
    }
    quotaService.tryAcquire();
    // ... 原有 RAG + LLM 逻辑
}
```

```java
public void processStream(String conversationId, String message, SseEmitter emitter) {
    if (quotaService.isExhausted()) {
        emitError(QUOTA_EXHAUSTED_MESSAGE, emitter);
        return;
    }
    quotaService.tryAcquire();
    // ... 原有 RAG + LLM 逻辑
}
```

### 7. 内网 IP 判断

```java
private boolean isInternalIp(String ip) {
    return ip.equals("127.0.0.1") || ip.startsWith("127.")
            || ip.startsWith("10.")
            || ip.startsWith("172.16.") || ip.startsWith("172.17.") || ...
            || ip.startsWith("192.168.");
}
```

更精确的做法是使用 `org.apache.commons.net.util.SubnetUtils` 或手写 CIDR 判断。本项目为了零额外依赖，采用前缀匹配。

### 8. 限流阈值可配置

为了支持测试环境快速验证，同时避免生产默认配置被误改，限流与全局限额均通过 `application.yml` 配置：

```yaml
rate-limit:
  chat:
    capacity: 20
    refill-period: 1h

deepseek:
  quota:
    daily-limit: 500
```

测试环境可创建 `application-test.yml`：

```yaml
rate-limit:
  chat:
    capacity: 2
    refill-period: 10s

deepseek:
  quota:
    daily-limit: 3
```

启动时指定 profile：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

这样测试 IP 限流无需真实等待 1 小时，测试全局日限额也无需真的发送 500 次请求。

## 降级行为

### IP 限流触发

HTTP 状态码：`429 Too Many Requests`

```json
{
  "error": "请求过于频繁，请 1 小时后再试"
}
```

### 全局日限额耗尽

不调用 DeepSeek，直接返回固定文案：

```
当前 AI 服务今日额度已用完，请明日再试。这不是维修建议，最终决策请以官方 AMM 手册为准。
```

- 阻塞接口：通过 `ChatResponse.reply` 返回。
- 流式接口：通过 SSE `error` 事件返回。

### 文档摄入越权

HTTP 状态码：`403 Forbidden`

```json
{
  "error": "该接口仅限内网访问"
}
```

## 依赖

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j_jdk17-core</artifactId>
    <version>8.18.0</version>
</dependency>
```

## 验收方式

1. 启动后端。
2. **IP 限流测试**：连续调用 21 次 `POST /api/chat/stream`。
   - 前 20 次正常进入业务逻辑。
   - 第 21 次返回 HTTP 429。
   - 等待 1 小时后再次请求，限流重置。
3. **全局日限额测试**：将 `DAILY_LIMIT` 临时改为 3，发送 4 次请求。
   - 前 3 次正常调用 DeepSeek。
   - 第 4 次返回"今日额度已用完"文案。
4. **额度持久化测试**：重启后端，验证当天已用次数从文件恢复。
5. **内网限制测试**：从公网 IP（非 127.0.0.1/内网段）访问 `/api/admin/ingest`，返回 HTTP 403。

## 风险与回滚

| 风险 | 应对 |
|------|------|
| X-Forwarded-For 伪造 | Nginx 层确保覆盖该头部，后端仅作为参考 |
| IPv6 地址判断不全 | 首日仅支持 IPv4 内网段，IPv6 一律视为外网 |
| 正常用户被误限 | 20 次/小时对面试演示足够，后续可通过配置调整 |
| 全局日限额被分布式 IP 刷完 | 应用层最后防线；如需更强防护需接入 WAF / 网关层限流 |
| 计数文件损坏 | 读取失败时重置为 0，并打印错误日志 |

回滚：
- 删除 `RateLimitFilter` 的 `@Component` 注解或移除该 Bean 即可关闭 IP 限流。
- 将 `DeepSeekQuotaService` 的 `DAILY_LIMIT` 改为 `Integer.MAX_VALUE` 即可关闭全局日限额。

## 关键文件

- `backend/pom.xml`
- `backend/src/main/java/cn/pandazi/aviation_maintenance_assistant/config/RateLimitConfig.java`
- `backend/src/main/java/cn/pandazi/aviation_maintenance_assistant/config/RateLimitFilter.java`
- `backend/src/main/java/cn/pandazi/aviation_maintenance_assistant/service/DeepSeekQuotaService.java`
- `backend/src/main/java/cn/pandazi/aviation_maintenance_assistant/chat/service/MaintenanceChatService.java`

## 相关文档

- [FEATURE-api-prefix](FEATURE-api-prefix.md)
- [FEATURE-graceful-degradation](FEATURE-graceful-degradation.md)
