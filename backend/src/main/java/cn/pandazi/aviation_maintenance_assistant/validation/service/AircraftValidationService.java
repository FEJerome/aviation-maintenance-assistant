package cn.pandazi.aviation_maintenance_assistant.validation.service;

import cn.pandazi.aviation_maintenance_assistant.validation.dto.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 机型-发动机验证服务
 * <p>
 * 演示版采用硬编码匹配表。两周冲刺不求覆盖全部机型，
 * 收录 4 组主流组合足够验证机制跑通。
 * <p>
 * 生产环境可扩展为数据库查询或对接航空公司机型管理系统。
 */
@Service
public class AircraftValidationService {

    private static final Map<String, Set<String>> VALID_COMBINATIONS = Map.of(
            // 民航客机（基于 FAA 通用教材回答）
            "B737800", Set.of("CFM567B", "LEAP1B"),
            "A320CEO", Set.of("CFM565B", "V2500"),
            "A320NEO", Set.of("PW1100G", "LEAP1A"),
            "B777300ER", Set.of("GE90115B"),
            // 轻型飞机（有完整 AMM 手册）
            "CTLS", Set.of("ROTAX912"),
            "WT9", Set.of("ROTAX912")
    );

    /**
     * 验证机型与发动机是否匹配
     *
     * @param model  飞机型号，如 B737-800
     * @param engine 发动机型号，如 CFM56-7B
     * @return MATCH（匹配）、MISMATCH（不匹配）、UNKNOWN（未收录）
     */
    public ValidationResult validate(String model, String engine) {
        String normalizedModel = normalize(model);
        String normalizedEngine = normalize(engine);

        Set<String> engines = VALID_COMBINATIONS.get(normalizedModel);
        if (engines == null) {
            return ValidationResult.UNKNOWN;
        }
        return engines.contains(normalizedEngine) ? ValidationResult.MATCH : ValidationResult.MISMATCH;
    }

    private String normalize(String input) {
        String normalized = input.toUpperCase().replaceAll("[\\s-]", "");
        // 去掉厂商前缀：Flight Design CTLS → CTLS，Aerospool WT9 → WT9
        normalized = normalized.replaceAll("^(FLIGHTDESIGN|AEROSPOOL)", "");
        // Rotax 912 子型号归一化：912ULS / 912S / 912iS → 912
        normalized = normalized.replaceAll("912(ULS|S|IS)$", "912");
        return normalized;
    }
}
