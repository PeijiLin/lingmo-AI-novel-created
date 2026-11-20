package com.linpj.novel.create.pojo.pipeline;

/**
 * 断言可信度分级（v2.0 设计 2.3 ★：角色说的 ≠ 真的发生了）
 *
 * - FACT：叙述直陈事实 -> 进正典权威层
 * - HYPOTHETICAL：角色推测/假设（"也许/想必是…"）-> 待证实区
 * - HEARSAY：转述/传闻（"据说/听人说…"）-> 待证实区
 * - DREAM：梦境/幻觉场景内容 -> 待证实区（与 C4 叙事模式联动）
 * - LIE：谎言。不做实时识别（评审 N3 抽取端只见局部正文，判谎需要真相侧信息——
 *   全知悖论）；入口是作者标注 + 事后降格（真相揭穿时将既有 FACT 标记），
 *   enum 保留 LIE 供作者标注通道使用
 *
 * 升格：后续章节 >=2 个独立锚点证实，或作者确认；降格：不删除（可追溯"读者在第几章被误导"）。
 *
 * @author HL
 */
public enum AssertionType {
    FACT, HYPOTHETICAL, HEARSAY, DREAM, LIE;

    /**
     * 容错解析（LLM 输出侧统一入口）：null/非法值归 FACT。
     * 归 FACT 的理由：快照主体位置/心态等字段的错误入账风险低于整章账本跳过
     * （single bad value 只影响该角色分级，不炸整次抽取——宁可分级失真不可管道中断）；
     * 影子数据（S6 探针）会度量漂移率再定是否收紧。
     */
    public static AssertionType parse(Object raw) {
        if (raw instanceof String value) {
            try {
                return AssertionType.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // 模型漂移输出（如"事实"/"probably"）：归 FACT 并由探针观测
            }
        }
        return FACT;
    }

    /** 是否应进待证实区（非 FACT 且非 null 语义）——LLM 输出经 parse 后 FACT 已兜底 */
    public static boolean quarantined(AssertionType type) {
        return type != FACT;
    }
}
