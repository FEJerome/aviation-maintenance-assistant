package cn.pandazi.aviation_maintenance_assistant.chat.routing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从用户问题中提取机型和发动机型号
 * <p>
 * 两周冲刺采用简单正则，覆盖常见写法即可，不求穷尽。
 */
public class AircraftInfoExtractor {

    private static final Pattern MODEL_PATTERN = Pattern.compile(
            "\\b((?:CTLS|WT9|FLIGHTDESIGN|AEROSPOOL)?[\\s-]*(?:[AB]\\d{3}(?:-\\d{3,4})?(?:[A-Z]{2})?|CTLS|WT9))\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ENGINE_PATTERN = Pattern.compile(
            "\\b((?:CFM56(?:-[A-Z0-9]+)?|CFM\\s*LEAP-1[AB]|LEAP-1[AB]|PW1100G|V2500|GE90(?:-[A-Z0-9]+)?|ROTAX\\s*912(?:\\s*(?:ULS|S|iS))?))\\b",
            Pattern.CASE_INSENSITIVE
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
        return new ExtractedAircraftInfo(model, engine);
    }

    private static String extractWithPattern(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim().toUpperCase();
        }
        return null;
    }

    public record ExtractedAircraftInfo(String model, String engine) {
        public boolean isComplete() {
            return model != null && engine != null;
        }
    }
}
