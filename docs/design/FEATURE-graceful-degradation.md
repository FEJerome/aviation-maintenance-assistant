# 功能设计：优雅降级

## 背景

系统依赖 DeepSeek API 生成回答。在实际运行中，可能遇到以下异常：

- DeepSeek API 超时
- API Key 无效或余额不足
- 网络波动导致连接失败
- DeepSeek 服务端临时不可用
- 后端内部异常（如 ChromaDB 连接失败）
- 全局限额已耗尽（见 [FEATURE-rate-limiting](FEATURE-rate-limiting.md)）

如果不对这些异常做处理，前端会显示空白、SSE 断开或 raw error，体验差，也不安全。

## 目标

1. 任何异常都不应导致前端崩溃或显示堆栈信息。
2. 用户应收到明确、专业的提示，告知当前无法生成回答。
3. 提示中必须强调"最终以官方 AMM 手册为准"，符合航空维修领域的安全合规要求。
4. 流式接口通过 SSE `error` 事件返回；阻塞接口通过正常 `ChatResponse` 返回。
5. 全局限额耗尽属于**已知受控状态**，使用专属文案，与 DeepSeek 异常区分。

## 降级文案

### 1. DeepSeek 异常 / 网络异常 / API Key 问题

统一使用以下文案：

```
当前 AI 服务暂不可用，请稍后重试。这不是维修建议，最终决策请以官方 AMM 手册为准。
```

### 2. 全局限额耗尽

使用专属文案，明确告知用户是额度问题：

```
当前 AI 服务今日额度已用完，请明日再试。这不是维修建议，最终决策请以官方 AMM 手册为准。
```

## 实现位置

### 1. 阻塞式接口 `MaintenanceChatService.process()`

在方法外层增加 try-catch，捕获所有异常后返回固定文案：

```java
public ChatResponse process(String conversationId, String message) {
    try {
        // 原有逻辑
    } catch (Exception e) {
        return new ChatResponse(FALLBACK_MESSAGE, conversationId);
    }
}
```

### 2. 流式接口 `MaintenanceChatService.processStream()`

在方法外层增加 try-catch，通过 SSE 发送 `error` 事件：

```java
public void processStream(String conversationId, String message, SseEmitter emitter) {
    try {
        // 原有逻辑
    } catch (Exception e) {
        emitError(FALLBACK_MESSAGE, emitter);
    }
}
```

### 3. 全局限额耗尽

在 `MaintenanceChatService` 调用 DeepSeek 前检查 `DeepSeekQuotaService`：

```java
private static final String QUOTA_EXHAUSTED_MESSAGE =
    "当前 AI 服务今日额度已用完，请明日再试。这不是维修建议，最终决策请以官方 AMM 手册为准。";

public ChatResponse process(String conversationId, String message) {
    if (quotaService.isExhausted()) {
        return new ChatResponse(QUOTA_EXHAUSTED_MESSAGE, conversationId);
    }
    quotaService.tryAcquire();
    // ... 原有逻辑
}

public void processStream(String conversationId, String message, SseEmitter emitter) {
    if (quotaService.isExhausted()) {
        emitError(QUOTA_EXHAUSTED_MESSAGE, emitter);
        return;
    }
    quotaService.tryAcquire();
    // ... 原有逻辑
}
```

### 4. `streamAssistantReply()`

`Assistant.chatStream()` 的 `onError` 回调中也要调用 `emitError`：

```java
.onError(error -> emitError(FALLBACK_MESSAGE, emitter))
```

## 错误信息隔离

- 对外返回的文案固定，不包含堆栈、异常类名、API Key 状态等敏感信息。
- 内部异常详情记录到服务端日志（通过 SLF4J），便于排查。

```java
private static final Logger log = LoggerFactory.getLogger(MaintenanceChatService.class);

catch (Exception e) {
    log.error("Failed to process chat request", e);
    return new ChatResponse(FALLBACK_MESSAGE, conversationId);
}
```

## 验收方式

1. 配置错误的 DeepSeek API Key。
2. 发送问题，验证：
   - 阻塞接口返回固定降级文案。
   - 流式接口通过 SSE `error` 事件返回固定降级文案。
3. 将全局限额临时调低并耗尽，验证：
   - 阻塞接口返回"今日额度已用完"文案。
   - 流式接口通过 SSE `error` 事件返回"今日额度已用完"文案。
4. 前端显示：聊天区域出现对应的系统消息。

## 风险与回滚

| 风险 | 应对 |
|------|------|
| 异常被吞掉无法排查 | 服务端打印完整日志 |
| 用户反复重试仍失败 | 文案建议"稍后重试"，结合限流避免刷接口 |
| 前端未处理 SSE error 事件 | 检查 App.vue 的 SSE 解析逻辑，已支持 error 事件 |
| 全局限额文案与异常文案混淆 | 使用独立常量，分别测试 |

回滚：去掉 try-catch，恢复原始抛出逻辑；或将 `DAILY_LIMIT` 设为极大值关闭全局限额。

## 关键文件

- `backend/src/main/java/cn/pandazi/aviation_maintenance_assistant/chat/service/MaintenanceChatService.java`
- `backend/src/main/java/cn/pandazi/aviation_maintenance_assistant/service/DeepSeekQuotaService.java`

## 相关文档

- [FEATURE-rate-limiting](FEATURE-rate-limiting.md)
- [FEATURE-api-prefix](FEATURE-api-prefix.md)
