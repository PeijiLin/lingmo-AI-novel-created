package com.linpj.novel.create.pipeline;

import com.linpj.novel.create.config.PipelineBudgetProperties;
import com.linpj.novel.create.pojo.pipeline.StageUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预算档位纯函数与章级会话测试（M5-F1 / M4-E6：L1–L4 阈值边界 + 多维取最高 + 降级标记）
 *
 * 阈值契约（TC-30 锚点）：≤0.8 → L0｜>0.8 → L1｜>0.9 → L2｜=1.0 → L3｜≤1.2 → L3｜>1.2 → L4
 *
 * @author HL
 */
class ChapterBudgetTest {

    private static PipelineBudgetProperties.Chapter spec(long maxTokens, long maxElapsedMs) {
        PipelineBudgetProperties properties = new PipelineBudgetProperties();
        properties.getChapter().setMaxTokens(maxTokens);
        properties.getChapter().setMaxElapsedMs(maxElapsedMs);
        return properties.getChapter();
    }

    // ------------------------------------------------------------------
    // BudgetLevel 纯函数（阈值边界）
    // ------------------------------------------------------------------

    @Test
    void 比例阈值边界() {
        assertEquals(BudgetLevel.L0, BudgetLevel.ofRatio(0.0));
        assertEquals(BudgetLevel.L0, BudgetLevel.ofRatio(0.79));
        assertEquals(BudgetLevel.L0, BudgetLevel.ofRatio(0.8), "=0.8 仍在预算内（>0.8 才预警）");
        assertEquals(BudgetLevel.L1, BudgetLevel.ofRatio(0.81));
        assertEquals(BudgetLevel.L1, BudgetLevel.ofRatio(0.9), "=0.9 仍 L1（>0.9 才紧缩）");
        assertEquals(BudgetLevel.L2, BudgetLevel.ofRatio(0.91));
        assertEquals(BudgetLevel.L3, BudgetLevel.ofRatio(1.0), "=1.0 触顶（>= 判定）");
        assertEquals(BudgetLevel.L3, BudgetLevel.ofRatio(1.2), "=1.2 仍在缓冲内");
        assertEquals(BudgetLevel.L4, BudgetLevel.ofRatio(1.21), ">1.2 止损");
    }

    @Test
    void 多维取最高档() {
        assertEquals(BudgetLevel.L3, BudgetLevel.worstOf(BudgetLevel.L1, BudgetLevel.L3));
        assertEquals(BudgetLevel.L3, BudgetLevel.worstOf(BudgetLevel.L3, BudgetLevel.L0));
        assertEquals(BudgetLevel.L2, BudgetLevel.worstOf(BudgetLevel.L2, BudgetLevel.L2));
    }

    @Test
    void 档位比较atLeast() {
        assertTrue(BudgetLevel.L4.atLeast(BudgetLevel.L3));
        assertTrue(BudgetLevel.L3.atLeast(BudgetLevel.L3));
        assertFalse(BudgetLevel.L1.atLeast(BudgetLevel.L2));
    }

    // ------------------------------------------------------------------
    // ChapterBudget 会话（usage 引用实时折算 + 维度禁用 + 降级标记）
    // ------------------------------------------------------------------

    @Test
    void token维度实时折算随usage更新() {
        StageUsage usage = new StageUsage();
        ChapterBudget budget = new ChapterBudget(spec(100, 0), usage, System.currentTimeMillis());

        assertEquals(BudgetLevel.L0, budget.level());
        usage.addTokens(85);
        assertEquals(BudgetLevel.L1, budget.level(), "85/100 > 0.8 → 预警");
        usage.addTokens(15);
        assertEquals(BudgetLevel.L3, budget.level(), "100/100 = 1.0 → 触顶");
    }

    @Test
    void 预算置零视为该维禁用() {
        StageUsage usage = new StageUsage();
        usage.addTokens(1_000_000);
        ChapterBudget budget = new ChapterBudget(spec(0, 0), usage, System.currentTimeMillis());

        assertEquals(BudgetLevel.L0, budget.level(), "maxTokens=0/maxElapsedMs=0 均为禁用语义（查询/单测确定性）");
    }

    @Test
    void 时延维度独立触发() {
        StageUsage usage = new StageUsage(); // token 维度恒 0
        ChapterBudget budget = new ChapterBudget(spec(100, 1), usage, System.currentTimeMillis() - 10);

        assertEquals(BudgetLevel.L4, budget.level(), "10ms/1ms = 10 倍超限 → 止损（时延维独立生效）");
    }

    @Test
    void RAG召回减半保底为1() {
        StageUsage usage = new StageUsage();
        ChapterBudget normal = new ChapterBudget(spec(1000, 0), usage, 0);
        assertEquals(4, normal.ragTopLimit(4), "L0 正常档：默认召回数");

        usage.addTokens(900); // 0.9 > 0.8 → L1
        ChapterBudget alert = new ChapterBudget(spec(1000, 0), usage, 0);
        assertEquals(2, alert.ragTopLimit(4), "L1 预警：召回减半");
        assertEquals(1, alert.ragTopLimit(1), "减半保底 1（不为 0）");
    }

    @Test
    void 降级标记幂等冒泡() {
        ChapterBudget budget = new ChapterBudget(spec(100, 0), new StageUsage(), 0);
        assertFalse(budget.degraded());
        budget.markDegraded();
        budget.markDegraded();
        assertTrue(budget.degraded());
    }
}
