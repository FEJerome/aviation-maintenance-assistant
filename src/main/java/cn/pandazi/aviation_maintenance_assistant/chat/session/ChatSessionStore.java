package cn.pandazi.aviation_maintenance_assistant.chat.session;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话存储（演示版）
 * <p>
 * 生产环境建议迁移至 Redis，支持多实例共享和 TTL 过期淘汰。
 */
@Component
public class ChatSessionStore {

    private final ConcurrentHashMap<String, SessionContext> store = new ConcurrentHashMap<>();

    public SessionContext get(String conversationId) {
        return store.get(conversationId);
    }

    public void put(String conversationId, SessionContext context) {
        store.put(conversationId, context);
    }
}
