package cn.pandazi.aviation_maintenance_assistant.validation.dto;

/**
 * 机型-发动机验证结果
 */
public enum ValidationResult {
    MATCH,      // 机型与发动机匹配且已收录
    MISMATCH,   // 机型与发动机不匹配
    UNKNOWN     // 机型或发动机未在系统中收录
}
