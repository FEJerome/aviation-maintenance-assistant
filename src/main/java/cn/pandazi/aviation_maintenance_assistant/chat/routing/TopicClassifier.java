package cn.pandazi.aviation_maintenance_assistant.chat.routing;

import java.util.Set;

/**
 * 路由分类器：判断用户问题是否涉及需要机型上下文的关键系统
 * <p>
 * 采用确定性关键词匹配，而非 LLM 分类，确保控制流 100% 可预期。
 */
public class TopicClassifier {

    private static final Set<String> AIRCRAFT_SPECIFIC_KEYWORDS = Set.of(
            "发动机", "engine",
            "滑油", "oil",
            "燃油", "fuel",
            "飞控", "flight control",
            "起落架", "landing gear",
            "液压", "hydraulic",
            "apu",
            "引气", "bleed"
    );

    /**
     * 判断问题是否需要机型信息
     *
     * @param message 用户原始问题
     * @return true 表示涉及关键系统，必须先验证机型
     */
    public static boolean needsAircraftInfo(String message) {
        String lower = message.toLowerCase();
        return AIRCRAFT_SPECIFIC_KEYWORDS.stream().anyMatch(lower::contains);
    }
}
