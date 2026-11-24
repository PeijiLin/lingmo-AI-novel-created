package com.linpj.novel.create.pipeline;

import com.linpj.novel.create.config.PipelineBudgetProperties;
import com.linpj.novel.create.pojo.pipeline.StageUsage;

/**
 * 章级预算会话（M5-F1：单次管道执行的预算感知对象）
 *
 * <p>实现取舍（零计数侵入）：直接引用管道既有的 {@link StageUsage} 累计器与任务起始时间，
 * {@link #level()} 每次调用实时折算——不引入第二套 token 计数（两处记账必然漂移）。
 * 阶段边界之间调用即得最新档位。
 *
 * <p>生效维度：token + 墙钟两维（设计 5.4 三维中的两维；成本维依赖 F4 价目配置——
 * 在单价未配置前"token × 0 单价"恒为 0，等价不触发；价目落地后此处加第三维折算即可，
 * 档位语义与接线点不变）。
 *
 * <p>时延维度在单测中的稳定性：测试注入 {@code maxElapsedMs=0} 视为"禁用该维"
 * （除零防护的显式语义），避免墙钟依赖导致用例偶发漂移。
 *
 * @author HL
 */
public final class ChapterBudget {

    private final PipelineBudgetProperties.Chapter spec;
    private final StageUsage usage;
    private final long startAt;
    /** 预算触发的降级标记（L1 关 best-of / L3 跳 Critic/Polisher 等），冒泡到任务 degraded */
    private boolean degraded;

    public ChapterBudget(PipelineBudgetProperties.Chapter spec, StageUsage usage, long startAt) {
        this.spec = spec;
        this.usage = usage;
        this.startAt = startAt;
    }

    /** 是否发生过预算降级（各 runXxx 返回值合并处消费） */
    public boolean degraded() {
        return degraded;
    }

    /** 标记预算降级（幂等；由降级动作执行处调用） */
    public void markDegraded() {
        this.degraded = true;
    }

    /** 当前档位（多维取最高；token/时延任一 ≤0 视为该维禁用） */
    public BudgetLevel level() {
        BudgetLevel tokenLevel = BudgetLevel.ofRatio(tokenRatio());
        BudgetLevel elapsedLevel = BudgetLevel.ofRatio(elapsedRatio());
        return BudgetLevel.worstOf(tokenLevel, elapsedLevel);
    }

    /**
     * L1+ 预警下的 RAG 召回上限（召回数减半，保底 1）。
     *
     * @param baseTopK 默认召回数（Spring AI VectorStoreDocumentRetriever 默认 4）
     */
    public int ragTopLimit(int baseTopK) {
        return level().atLeast(BudgetLevel.L1) ? Math.max(1, baseTopK / 2) : baseTopK;
    }

    private double tokenRatio() {
        long maxTokens = spec.getMaxTokens();
        if (maxTokens <= 0) {
            return 0;
        }
        return (double) usage.getTotalTokens() / maxTokens;
    }

    private double elapsedRatio() {
        long maxElapsedMs = spec.getMaxElapsedMs();
        if (maxElapsedMs <= 0) {
            return 0;
        }
        return (double) (System.currentTimeMillis() - startAt) / maxElapsedMs;
    }
}
