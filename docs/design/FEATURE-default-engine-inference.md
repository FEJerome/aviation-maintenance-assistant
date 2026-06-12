# 功能设计：默认发动机型号推断

## 背景

在前端推荐问题气泡的测试过程中，发现以下问题：

- 用户点击「WT9 飞机起落架收放系统的日常检查要求是什么？」后，系统返回：「为了提供准确的维修信息，请提供机型（如 B737-800）和发动机型号（如 CFM56-7B）。」
- 但 `AircraftValidationService` 的硬编码匹配表中已经收录了 `WT9 → Rotax 912`。

根因在于：`MaintenanceChatService` 对关键系统问题要求同时提取到机型和发动机型号，而用户问题中只提供了机型。对于没有机务背景的用户，期望他们记住 WT9 配什么发动机是不现实的。

## 目标

- 对已知单一动力配置的轻型飞机，如果用户只提供了机型、未提供发动机型号，后端自动补全默认发动机型号。
- 保证向量数据库中确实有数据的内容可以通过验证层，返回有价值回答。
- 对存在多种发动机选项的机型（如 B737-800），保持严格验证，要求用户补充发动机型号。

## 方案

在 `AircraftInfoExtractor.extract()` 中增加默认发动机型号映射：

| 机型 | 默认发动机型号 | 理由 |
|------|---------------|------|
| CTLS | Rotax 912 | 向量库中 CTLS 只关联 Rotax 912 |
| WT9 | Rotax 912 | 向量库中 WT9 只关联 Rotax 912 |

实现逻辑：

1. 从用户问题中提取机型和发动机型号。
2. 如果机型已提取、发动机未提取，且该机型在默认映射表中，则自动补全发动机型号。
3. 后续验证流程保持不变。

## 为什么不把所有机型都加默认映射？

B737-800 等民航客机有多个发动机选项（CFM56-7B / LEAP-1B），不同发动机的维护程序和手册不同。如果默认指定某一种，可能给出错误答案。因此保持严格验证，要求用户补充发动机型号。

## 改动范围

### 后端

1. **`backend/src/main/java/cn/pandazi/aviation_maintenance_assistant/chat/routing/AircraftInfoExtractor.java`**
   - 新增 `DEFAULT_ENGINES` 映射（CTLS → Rotax 912，WT9 → Rotax 912）。
   - 新增 `normalizeModel()` 方法，用于默认映射查找时的机型名称归一化。
   - 在 `extract()` 返回结果前，对单一动力配置机型自动补全发动机型号。

### 不修改的文件

- `AircraftValidationService.java`：验证逻辑保持不变，只补充默认映射的提取。
- `MaintenanceChatService.java`：控制流保持不变。

## 代码示例

```java
private static final Map<String, String> DEFAULT_ENGINES = Map.of(
    "CTLS", "Rotax 912",
    "WT9", "Rotax 912"
);

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
```

## 调整后预期效果

| 问题 | 修改后结果 |
|------|-----------|
| CTLS 飞机 Rotax 912 发动机滑油压力标准值是多少？ | ✅ MATCH，命中 CTLS-MM / Rotax 手册 |
| WT9 飞机起落架收放系统的日常检查要求是什么？ | ✅ MATCH（自动补全 Rotax 912），命中 WT9-AMM |
| 飞机结构修理中，铆接修理的一般规范有哪些？ | ✅ 通用知识路由，命中 FAA-AC-43-13-1B |
| B737-800 发动机滑油压力低，应该如何排故？ | ⚠️ 仍要求补充发动机型号（合理，B737-800 有多个发动机选项） |

## 验收标准

- [ ] 点击「CTLS 飞机 Rotax 912...」能正常通过验证并返回答案。
- [ ] 点击「WT9 飞机起落架...」能自动补全 Rotax 912，通过验证并命中 WT9-AMM 返回答案。
- [ ] 点击「飞机结构修理中...」走通用知识路由，正常返回答案。
- [ ] 点击「B737-800 发动机滑油压力低...」仍提示需要补充发动机型号。
- [ ] 后端默认发动机补全不影响已有带发动机型号的提问行为。

## 后续可扩展方向

1. **数据库化默认映射**：将机型-默认发动机映射从硬编码迁移到配置文件或数据库，便于扩展。
2. **基于向量库的默认推断**：根据向量库中该机型的文档关联的发动机型号，自动推断默认发动机。
3. **用户提示优化**：当需要补充发动机型号时，给出该机型的可选发动机列表（如「B737-800 可选 CFM56-7B 或 LEAP-1B」）。

## 关键文件

- `backend/src/main/java/cn/pandazi/aviation_maintenance_assistant/chat/routing/AircraftInfoExtractor.java`

## 相关文档

- [FEATURE-suggested-questions](FEATURE-suggested-questions.md)
- [FEATURE-rate-limiting](FEATURE-rate-limiting.md)
- [FEATURE-graceful-degradation](FEATURE-graceful-degradation.md)
