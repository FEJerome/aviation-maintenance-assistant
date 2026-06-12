package cn.pandazi.aviation_maintenance_assistant.chat.routing;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从用户问题中提取机型和发动机型号
 * <p>
 * 两周冲刺采用简单正则，覆盖常见写法即可，不求穷尽。
 * 对已知单一动力配置的轻型飞机（CTLS/WT9），若用户未提供发动机型号，
 * 自动补全默认值 Rotax 912，以提升无专业背景用户的首次体验。
 */
public class AircraftInfoExtractor {

    private static final Pattern MODEL_PATTERN = Pattern.compile(
            "\\b((?:CTLS|WT9|FLIGHTDESIGN|AEROSPOOL)?[\\s-]*(?:[AB]\\d{3}(?:-\\d{3,4})?(?:[A-Z]{2,3})?|CTLS|WT9))\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ENGINE_PATTERN = Pattern.compile(
            "\\b((?:CFM56(?:-[A-Z0-9]+)?|LEAP-1[AB]|PW\\s*1100G|V2500|GE\\s*90(?:-[A-Z0-9]+)?|ROTAX\\s*912(?:\\s*(?:ULS|S|iS))?))\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 单一动力配置机型的默认发动机型号映射。
     * Key 为归一化后的机型名称。
     */
    private static final Map<String, String> DEFAULT_ENGINES = Map.of(
            "CTLS", "Rotax 912",
            "WT9", "Rotax 912"
    );

    /**
     * 从用户问题中提取机型和发动机信息
     *
     * @param message 用户原始问题
     * @return 提取结果，model 或 engine 可能为 null
     */
    public static ExtractedAircraftInfo extract(String message) {
        String model = extractWithPattern(MODEL_PATTERN, message);
        String engine = extractWithPattern(ENGINE_PATTERN, message);

        // 对已知单一动力配置机型，若未提供发动机型号则自动补全
        if (model != null && engine == null) {
            String normalizedModel = normalizeModel(model);
            String defaultEngine = DEFAULT_ENGINES.get(normalizedModel);
            if (defaultEngine != null) {
                engine = defaultEngine;
            }
        }

        return new ExtractedAircraftInfo(model, engine);
    }

    private static String extractWithPattern(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim().toUpperCase();
        }
        return null;
    }

    /**
     * 将机型名称归一化，用于默认发动机型号查找。
     * 与 {@link cn.pandazi.aviation_maintenance_assistant.validation.service.AircraftValidationService}
     * 中的归一化逻辑保持一致。
     */
    private static String normalizeModel(String input) {
        String normalized = input.toUpperCase().replaceAll("[\\s-]", "");
        normalized = normalized.replaceAll("^(FLIGHTDESIGN|AEROSPOOL)", "");
        return normalized;
    }

    public record ExtractedAircraftInfo(String model, String engine) {
        public boolean isComplete() {
            return model != null && engine != null;
        }
    }
}
