package com.linpj.novel.create.pojo.pipeline;

/**
 * 角色生死状态（B5 结构化硬字段——供确定性规则 RC-01 判定，非自由文本）
 *
 * - ALIVE / DEAD：正文明确判定
 * - UNKNOWN：未提及 / 无状态记录 / 旧数据无该字段——UNKNOWN 时 RC-01 静默（宁可不判，不可误判）
 *
 * @author HL
 */
public enum AliveStatus {
    ALIVE, DEAD, UNKNOWN;

    /**
     * 账本容错解析（规则侧消费入口，全局红线 6 的规则化贯彻）：
     * null / 非字符串 / 非法枚举值一律归 UNKNOWN——宁可漏检，不可把脏数据放大成误判。
     */
    public static AliveStatus parse(Object raw) {
        if (raw instanceof String value) {
            try {
                return AliveStatus.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // 非法值（如"死亡"/"alive?"）归 UNKNOWN：容忍模型输出漂移
            }
        }
        return UNKNOWN;
    }
}
