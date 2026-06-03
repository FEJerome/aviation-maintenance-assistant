package cn.pandazi.aviation_maintenance_assistant.chat.session;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内存会话存储（演示版）
 * <p>
 * 采用 LRU 策略：超过容量时自动淘汰最久未访问的会话，
 * 确保正在追问的会话不会被删除。
 * <p>
 * 生产环境建议迁移至 Redis，支持多实例共享和 TTL 过期淘汰。
 */
@Component
public class ChatSessionStore {

    private static final int MAX_SIZE = 10000;

    private final Map<String, SessionContext> store =
            Collections.synchronizedMap(
                    new LinkedHashMap<String, SessionContext>(MAX_SIZE, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, SessionContext> eldest) {
                            return size() > MAX_SIZE;
                        }
                    }
            );

    public SessionContext get(String conversationId) {
        return store.get(conversationId);
    }

    public void put(String conversationId, SessionContext context) {
        store.put(conversationId, context);
    }
}
