package com.linpj.novel.create.worldstate;

/**
 * 故事时间（T_story）归一化结果分型（v2.0 设计 2.2 时间归一化）
 *
 * - EXACT：绝对表述成功锚定到天数轴（罕见：需要书内历法换算规则，首期基本不出现）
 * - RELATIVE_RESOLVED：LLM 出相对偏移 + 代码运算成功解析（主路径："三天后" → 上事件 day + 3）
 * - FUZZY：模糊表述的区间估计中点（"半月已过" → 15 天，confidence 降级使用）
 * - ABSOLUTE_LABEL：架空历法原文（"青云历 314 年春"）——无法换算天数轴，保留原文标签，偏序降级
 * - UNKNOWN：无时间信息——红线 6：绝不猜值
 *
 * EXACT/RELATIVE_RESOLVED/FUZZY 携带可运算的 day_index；
 * ABSOLUTE_LABEL/UNKNOWN 的 day_index 为 null，时序校验退化为偏序（章号 + 场景序）。
 *
 * @author HL
 */
public enum StoryTimeKind {
    EXACT,
    RELATIVE_RESOLVED,
    FUZZY,
    ABSOLUTE_LABEL,
    UNKNOWN
}
