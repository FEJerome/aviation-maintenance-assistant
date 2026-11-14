package cn.pandazi.aviation_maintenance_assistant.chat.routing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministically extracts explicitly supplied aircraft and engine identifiers.
 */
public class AircraftInfoExtractor {

    private static final Pattern MODEL_PATTERN = Pattern.compile(
            "\\b((?:CTLS|WT9|FLIGHTDESIGN|AEROSPOOL)?[\\s-]*(?:[AB]\\d{3}(?:-\\d{3,4})?(?:[A-Z]{2,3})?|CTLS|WT9))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ENGINE_PATTERN = Pattern.compile(
            "\\b((?:CFM56(?:-[A-Z0-9]+)?|LEAP-1[AB]|PW\\s*1100G|V2500|GE\\s*90(?:-[A-Z0-9]+)?|ROTAX\\s*912(?:\\s*(?:ULS|S|iS))?))\\b",
            Pattern.CASE_INSENSITIVE);

    private AircraftInfoExtractor() {
    }

    public static ExtractedAircraftInfo extract(String message) {
        return new ExtractedAircraftInfo(
                extractWithPattern(MODEL_PATTERN, message),
                extractWithPattern(ENGINE_PATTERN, message));
    }

    private static String extractWithPattern(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1).trim().toUpperCase() : null;
    }

    public record ExtractedAircraftInfo(String model, String engine) {
        public boolean isComplete() {
            return model != null && engine != null;
        }
    }
}
