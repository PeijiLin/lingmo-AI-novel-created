package com.linpj.novel.create.worldstate;

/**
 * 时间归一化（v2.0 设计 2.2 + 公理 P3 分工：LLM 只识别语义出结构化偏移，代码做算术）
 *
 * LLM 契约（抽取侧输出）：
 * {@link Offset} 三选一——
 * - DAY_OFFSET(days)：相对表述，"三天后" → days=3（解析不了的不要填这里）
 * - FUZZY(days)：模糊表述的估计值，"半月已过" → days=15
 * - ABSOLUTE_LABEL(label)：架空历法原文，"青云历 314 年春" → 原样保留（不换算）
 * 无时间信息 → null 偏移（UNKNOWN，绝不猜值）
 *
 * 代码端运算（本类）：offset + 参照事件 dayIndex → 归一化 day。
 * 参照缺失（前序事件天数为 null）时 DAY_OFFSET/FUZZY 均不可解析 → UNKNOWN。
 *
 * @author HL
 */
final class TimeNormalization {

    private TimeNormalization() {
    }

    /** LLM 输出的结构化时间偏移（sealed 语义：kind 匹配后其余字段可空） */
    record Offset(String kind, Integer days, String label) {

        public static Offset dayOffset(int days) {
            return new Offset("DAY_OFFSET", days, null);
        }

        public static Offset fuzzy(int days) {
            return new Offset("FUZZY", days, null);
        }

        public static Offset absoluteLabel(String label) {
            return new Offset("ABSOLUTE_LABEL", null, label);
        }
    }

    /** 归一化结果：分型 + 天数（EXACT/RELATIVE_RESOLVED/FUZZY 才有值） */
    record Resolved(StoryTimeKind kind, Integer dayIndex) {

        static final Resolved UNKNOWN = new Resolved(StoryTimeKind.UNKNOWN, null);
    }

    /**
     * 归一化入口（纯函数）。
     *
     * @param offset           LLM 结构化偏移；null = 无时间信息 → UNKNOWN
     * @param referenceDayIndex 参照事件天数；null = 前序不可定位
     * @return 归一化结果（绝不猜值：解析链断在任何一环都返回 UNKNOWN）
     */
    static Resolved resolve(Offset offset, Integer referenceDayIndex) {
        if (offset == null || offset.kind() == null) {
            return Resolved.UNKNOWN;
        }
        switch (offset.kind()) {
            case "DAY_OFFSET" -> {
                if (referenceDayIndex == null) {
                    // 参照链断裂：相对偏移无处锚定（不猜锚点）
                    return Resolved.UNKNOWN;
                }
                Integer days = offset.days();
                if (days == null) {
                    return Resolved.UNKNOWN;
                }
                return new Resolved(StoryTimeKind.RELATIVE_RESOLVED, referenceDayIndex + days);
            }
            case "FUZZY" -> {
                if (referenceDayIndex == null || offset.days() == null) {
                    return Resolved.UNKNOWN;
                }
                // 模糊估计按中点落天数轴，分型保留 FUZZY（消费端置信度降级使用）
                return new Resolved(StoryTimeKind.FUZZY, referenceDayIndex + offset.days());
            }
            case "ABSOLUTE_LABEL" -> {
                // 架空历法：保留原文标签（存 raw_time_text），天数不可比 → 偏序降级
                return new Resolved(StoryTimeKind.ABSOLUTE_LABEL, null);
            }
            default -> {
                return Resolved.UNKNOWN;
            }
        }
    }
}
