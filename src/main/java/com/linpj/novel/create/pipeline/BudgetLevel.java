package com.linpj.novel.create.pipeline;

/**
 * 预算降级档位（v2.0 设计 5.4 / M5-F1 + M4-E6：不可配置关闭的 L1–L4 阶梯）
 *
 * <p>档位 → 动作（常量表，语义细节见 CreationPipelineService 接线处）：
 * <ul>
 *   <li>L0 正常：全量链路</li>
 *   <li>L1 预警（&gt;80% 预算）：关 best-of（PRECISE 降单稿链路）、Critic 降中档模型（B2-3）、
 *       RAG 召回数减半、书级 80% 水位告警留痕</li>
 *   <li>L2 紧缩（&gt;90%）：K=1 且仅修 HIGH 问题</li>
 *   <li>L3 触顶（≥100%）：跳过 Critic 与 Polisher，**保留 RuleChecker 与两层安全防线**
 *       ——安全与硬一致性校验永不为成本让步（P6，TC-30 用例锁死）</li>
 *   <li>L4 止损（&gt;120%）：停止后续生成节点，任务失败——已完成产物保留（artifact 落库），
 *       未完成报告随 failTask.error 转人工</li>
 * </ul>
 *
 * <p>比例阈值（0.8/0.9/1.0/1.2）为编译期常量：不属于配置域，防止"调阈值=关降级"的后门。
 * 多维预算（token/时延/成本）取各维算出的最高档。
 *
 * @author HL
 */
public enum BudgetLevel {

    L0, L1, L2, L3, L4;

    /** L1 触发比例（预算占用 > 80%） */
    public static final double ALERT_RATIO = 0.8;

    /** L2 触发比例（预算占用 > 90%） */
    public static final double TIGHTEN_RATIO = 0.9;

    /** L3 触发比例（预算占用 >= 100%：触顶） */
    public static final double EXHAUST_RATIO = 1.0;

    /** L4 触发比例（预算占用 > 120%：超限缓冲，允许当前节点收尾止损） */
    public static final double SEVERE_RATIO = 1.2;

    /** 是否达到指定档位或更高 */
    public boolean atLeast(BudgetLevel other) {
        return other != null && ordinal() >= other.ordinal();
    }

    /** 预算占用比例 → 档位（纯函数；边界：=1.0 触顶 L3，1.2 以内缓冲，>1.2 止损 L4） */
    public static BudgetLevel ofRatio(double ratio) {
        if (ratio > SEVERE_RATIO) {
            return L4;
        }
        if (ratio >= EXHAUST_RATIO) {
            return L3;
        }
        if (ratio > TIGHTEN_RATIO) {
            return L2;
        }
        if (ratio > ALERT_RATIO) {
            return L1;
        }
        return L0;
    }

    /** 多维取最高档（token/时延/成本各维独立判定后取 max） */
    public static BudgetLevel worstOf(BudgetLevel left, BudgetLevel right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
