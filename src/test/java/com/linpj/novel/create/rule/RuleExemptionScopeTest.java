package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.entity.NovelRuleExemption;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保护区作用范围单元测试（v2.0 M4-E5：区间声明 + 到期复核的边界语义）
 *
 * @author HL
 */
class RuleExemptionScopeTest {

    private static final Date NOW = new Date(1_700_000_000_000L);

    private static Date daysAgo(int days) {
        return new Date(NOW.getTime() - days * 86_400_000L);
    }

    private static Date daysLater(int days) {
        return new Date(NOW.getTime() + days * 86_400_000L);
    }

    @Test
    void 无区间即整本豁免() {
        RuleExemptionScope scope = new RuleExemptionScope("RC-01", null, null, null);

        assertTrue(scope.coversWholeBook());
        assertFalse(scope.isChapterRange());
        // 整本豁免对任意章节（含未知章节）都生效
        assertTrue(scope.protects(5L, NOW));
        assertTrue(scope.protects(null, NOW));
    }

    @Test
    void 闭区间覆盖两端不覆盖区间外() {
        RuleExemptionScope scope = new RuleExemptionScope("RC-01", 10L, 12L, null);

        assertFalse(scope.coversWholeBook());
        assertTrue(scope.coversChapter(10L));
        assertTrue(scope.coversChapter(11L));
        assertTrue(scope.coversChapter(12L), "结束章属于保护区（闭区间）");
        assertFalse(scope.coversChapter(9L));
        assertFalse(scope.coversChapter(13L), "区间外必须恢复拦截");
    }

    @Test
    void 单端缺省表示单方向无界() {
        RuleExemptionScope fromOnly = new RuleExemptionScope("RC-01", 10L, null, null);
        assertTrue(fromOnly.coversChapter(10L));
        assertTrue(fromOnly.coversChapter(999L));
        assertFalse(fromOnly.coversChapter(9L));

        RuleExemptionScope toOnly = new RuleExemptionScope("RC-01", null, 12L, null);
        assertTrue(toOnly.coversChapter(1L));
        assertTrue(toOnly.coversChapter(12L));
        assertFalse(toOnly.coversChapter(13L));
    }

    @Test
    void 章节号缺失不做区间保护() {
        RuleExemptionScope scope = new RuleExemptionScope("RC-01", 10L, 12L, null);

        assertFalse(scope.coversChapter(null), "未知章节不能断言覆盖");
        assertFalse(scope.protects(null, NOW), "宁可拦截也不默认豁免");
    }

    @Test
    void 到期后保护区失效_未到期仍生效() {
        RuleExemptionScope future = new RuleExemptionScope("RC-01", 10L, 12L, daysLater(3));
        assertFalse(future.isExpired(NOW));
        assertTrue(future.protects(11L, NOW));

        RuleExemptionScope expired = new RuleExemptionScope("RC-01", 10L, 12L, daysAgo(1));
        assertTrue(expired.isExpired(NOW));
        assertFalse(expired.protects(11L, NOW), "过期即失效，规则恢复拦截");
    }

    @Test
    void 整本豁免过期同样失效() {
        RuleExemptionScope expired = new RuleExemptionScope("RC-01", null, null, daysAgo(1));

        assertTrue(expired.coversWholeBook());
        assertFalse(expired.protects(3L, NOW), "整本豁免也受复核期约束");
    }

    @Test
    void 无复核时间视为长期有效() {
        RuleExemptionScope scope = new RuleExemptionScope("RC-01", 10L, 12L, null);

        assertFalse(scope.isExpired(NOW));
        assertTrue(scope.protects(11L, NOW));
    }

    @Test
    void 实体还原保留区间与复核时间() {
        NovelRuleExemption exemption = new NovelRuleExemption();
        exemption.setRuleId("RC-05");
        exemption.setChapterFrom(3L);
        exemption.setChapterTo(8L);
        exemption.setReviewAt(daysLater(1));

        RuleExemptionScope scope = RuleExemptionScope.from(exemption);

        assertEquals("RC-05", scope.ruleId());
        assertEquals(3L, scope.chapterFrom());
        assertEquals(8L, scope.chapterTo());
        assertTrue(scope.protects(5L, NOW));

        assertNull(RuleExemptionScope.from(null), "空行返回 null 由调用方跳过");
    }
}
