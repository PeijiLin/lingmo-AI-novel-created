package com.linpj.novel.create.rule;

import com.linpj.novel.create.config.PipelineRulesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RuleChecker 骨架单元测试（v2.0 设计 4.1 分级上线 + 豁免通道 + 故障降级）
 *
 * @author HL
 */
class RuleCheckerTest {

    /** 测试用伪规则：恒产 1 条指定严重级的违规 */
    private static final class StubRule implements ConsistencyRule {
        private final String id;
        private final RuleSeverity severity;
        private final List<RuleStage> stages;

        private StubRule(String id, RuleSeverity severity, List<RuleStage> stages) {
            this.id = id;
            this.severity = severity;
            this.stages = stages;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public List<RuleStage> stages() {
            return stages;
        }

        @Override
        public RuleSeverity severity() {
            return severity;
        }

        @Override
        public String description() {
            return "测试规则";
        }

        @Override
        public List<RuleViolation> check(RuleCheckContext context) {
            return List.of(RuleViolation.of(id, severity, context.getStage(), "证据", "建议", false));
        }
    }

    /** 测试用伪规则：恒抛异常 */
    private static final class BrokenRule implements ConsistencyRule {
        @Override
        public String id() {
            return "RC-99";
        }

        @Override
        public List<RuleStage> stages() {
            return List.of(RuleStage.DRAFT);
        }

        @Override
        public RuleSeverity severity() {
            return RuleSeverity.HIGH;
        }

        @Override
        public String description() {
            return "故障规则";
        }

        @Override
        public List<RuleViolation> check(RuleCheckContext context) {
            throw new IllegalStateException("规则内部错误");
        }
    }

    private PipelineRulesProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PipelineRulesProperties();
    }

    private RuleChecker checkerOf(ConsistencyRule... rules) {
        return new RuleChecker(List.of(rules), properties);
    }

    private RuleCheckContext draftContext() {
        return RuleCheckContext.of(RuleStage.DRAFT);
    }

    @Test
    void 默认全OFF_等价现状行为() {
        properties.setStatus(new HashMap<>()); // 未配置任何规则
        RuleChecker checker = checkerOf(new StubRule("RC-01", RuleSeverity.HIGH, List.of(RuleStage.DRAFT)));

        RuleCheckResult result = checker.check(draftContext());

        assertTrue(result.getBlocking().isEmpty());
        assertTrue(result.getShadow().isEmpty());
        assertTrue(result.getExecutedRules().isEmpty(), "OFF 规则不执行");
    }

    @Test
    void SHADOW规则违规只记录不阻塞() {
        Map<String, String> status = new HashMap<>();
        status.put("RC-01", "SHADOW");
        properties.setStatus(status);
        RuleChecker checker = checkerOf(new StubRule("RC-01", RuleSeverity.HIGH, List.of(RuleStage.DRAFT)));

        RuleCheckResult result = checker.check(draftContext());

        // 影子态：即使 HIGH 违规也不阻塞（分级上线观测语义）
        assertTrue(result.getShadow().size() == 1);
        assertTrue(result.getShadow().get(0).isShadow());
        assertTrue(result.hasBlocking() == false);
        assertTrue(result.getExecutedRules().contains("RC-01"));
    }

    @Test
    void ACTIVE规则违规参与阻塞() {
        Map<String, String> status = new HashMap<>();
        status.put("RC-01", "ACTIVE");
        status.put("RC-06", "ACTIVE");
        properties.setStatus(status);
        RuleChecker checker = checkerOf(
                new StubRule("RC-01", RuleSeverity.HIGH, List.of(RuleStage.DRAFT)),
                new StubRule("RC-06", RuleSeverity.MEDIUM, List.of(RuleStage.DRAFT)));

        RuleCheckResult result = checker.check(draftContext());

        assertEquals(2, result.getBlocking().size());
        assertTrue(result.hasBlockingHigh(), "HIGH 违规触发阻塞信号");
        assertFalse(result.getBlocking().get(0).isShadow());
    }

    @Test
    void 豁免规则完全静默() {
        Map<String, String> status = new HashMap<>();
        status.put("RC-01", "ACTIVE");
        properties.setStatus(status);
        RuleChecker checker = checkerOf(new StubRule("RC-01", RuleSeverity.HIGH, List.of(RuleStage.DRAFT)));
        RuleCheckContext context = draftContext();
        context.setExemptedRules(Set.of("RC-01")); // 作者误报标记

        RuleCheckResult result = checker.check(context);

        assertTrue(result.getBlocking().isEmpty());
        assertFalse(result.getExecutedRules().contains("RC-01"), "豁免即不检");
    }

    @Test
    void 规则异常降级告警不阻塞其余规则() {
        Map<String, String> status = new HashMap<>();
        status.put("RC-01", "ACTIVE");
        status.put("RC-99", "ACTIVE");
        properties.setStatus(status);
        RuleChecker checker = checkerOf(
                new StubRule("RC-01", RuleSeverity.MEDIUM, List.of(RuleStage.DRAFT)),
                new BrokenRule());

        RuleCheckResult result = checker.check(draftContext());

        assertEquals(1, result.getBlocking().size(), "故障规则不拖垮健康规则");
        assertTrue(result.getFailedRules().contains("RC-99"));
        assertTrue(result.getExecutedRules().contains("RC-99"));
    }

    @Test
    void 时机不匹配的规则不执行() {
        Map<String, String> status = new HashMap<>();
        status.put("RC-11", "ACTIVE");
        properties.setStatus(status);
        // 只适用 DRAFT 的规则在 OUTLINE 上下文中执行
        RuleChecker checker = checkerOf(new StubRule("RC-11", RuleSeverity.LOW, List.of(RuleStage.DRAFT)));

        RuleCheckResult result = checker.check(RuleCheckContext.of(RuleStage.OUTLINE));

        assertTrue(result.getBlocking().isEmpty());
        assertTrue(result.getExecutedRules().isEmpty());
    }

    @Test
    void 状态配置非法按OFF处理() {
        Map<String, String> status = new HashMap<>();
        status.put("RC-01", "BOOSTED"); // 非法值
        properties.setStatus(status);
        List<String> warnings = new ArrayList<>();
        RuleChecker checker = checkerOf(new StubRule("RC-01", RuleSeverity.HIGH, List.of(RuleStage.DRAFT)));

        RuleCheckResult result = checker.check(draftContext());

        assertTrue(result.getBlocking().isEmpty(), "配置错误不得意外阻塞");
        assertTrue(result.getExecutedRules().isEmpty());
    }

    @Test
    void GRADUAL灰度分流_偶数书籍阻塞奇数书籍影子() {
        Map<String, String> status = new HashMap<>();
        status.put("RC-01", "GRADUAL");
        properties.setStatus(status);
        RuleChecker checker = checkerOf(new StubRule("RC-01", RuleSeverity.HIGH, List.of(RuleStage.DRAFT)));

        // 偶数书籍：阻塞生效（门槛达标观察期的一半流量）
        RuleCheckContext evenBook = RuleCheckContext.of(RuleStage.DRAFT);
        evenBook.setBookId(20L);
        RuleCheckResult evenResult = checker.check(evenBook);
        assertTrue(evenResult.hasBlockingHigh(), "偶数书籍灰度进入阻塞");

        // 奇数书籍：影子（双跑数据在线验证误报率）
        RuleCheckContext oddBook = RuleCheckContext.of(RuleStage.DRAFT);
        oddBook.setBookId(21L);
        RuleCheckResult oddResult = checker.check(oddBook);
        assertEquals(1, oddResult.getShadow().size());
        assertTrue(!oddResult.hasBlocking(), "奇数书籍灰度保持影子");

        // 无书籍上下文：保守影子（数据缺失不引入阻塞）
        RuleCheckResult noBook = checker.check(draftContext());
        assertTrue(!noBook.hasBlocking());
    }
}
