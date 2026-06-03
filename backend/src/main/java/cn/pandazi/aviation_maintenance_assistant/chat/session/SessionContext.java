package cn.pandazi.aviation_maintenance_assistant.chat.session;

/**
 * 聊天会话上下文，记录已验证的机型信息
 */
public record SessionContext(String confirmedModel, String confirmedEngine, boolean validated) {
}
