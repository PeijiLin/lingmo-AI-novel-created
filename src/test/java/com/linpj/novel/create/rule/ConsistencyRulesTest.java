package com.linpj.novel.create.rule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RC-11 字数 / RC-12 POV 规则单元测试（v2.0 M1-B2：陷阱集样例——含"看似违规实则合理"的豁免方向）
 *
 * @author HL
 */
class ConsistencyRulesTest {

    private final WordCountRule wordCountRule = new WordCountRule();
    private final PovRule povRule = new PovRule();

    // ------------------------------------------------------------------
    // RC-11 字数
    // ------------------------------------------------------------------

    @Test
    void RC11_偏差超阈值违规() {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setDraft("正文".repeat(500));   // 1000 字
        ctx.setTargetWordCount(3000);       // 偏差 67%

        List<RuleViolation> violations = wordCountRule.check(ctx);

        assertEquals(1, violations.size());
        assertEquals(RuleSeverity.LOW, violations.get(0).getSeverity());
        assertTrue(violations.get(0).getEvidence().contains("偏差 67%"));
        assertTrue(violations.get(0).getEvidence().contains("不足"));
    }

    @Test
    void RC11_阈值内通过() {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setDraft("正".repeat(2500));
        ctx.setTargetWordCount(3000);       // 偏差 17%

        assertTrue(wordCountRule.check(ctx).isEmpty());
    }

    @Test
    void RC11_目标未配置静默() {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setDraft("正文".repeat(100));
        ctx.setTargetWordCount(null);

        assertTrue(wordCountRule.check(ctx).isEmpty(), "无基准不算偏离");
    }

    @Test
    void RC11_伏笔标记与空白不计字数() {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        // 2970 字正文 + 计 60 字的内联标记 + 大量空白：实际按 2970 计，偏差 1%
        ctx.setDraft("正".repeat(2970) + "\n <!--plot:山门外来人来人来了来人来了来人来了来人来了--> \n\n".repeat(2));
        ctx.setTargetWordCount(3000);

        assertTrue(wordCountRule.check(ctx).isEmpty(), "内联标记与空白不计入正文字数");
    }

    // ------------------------------------------------------------------
    // RC-12 POV
    // ------------------------------------------------------------------

    @Test
    void RC12_对白内第一人称不违规() {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setPov("第三人称限知");
        ctx.setDraft("""
                林砚望着城楼。他说：“我不想去。”
                「你们谁都别拦我。」
                风声掠过垛口。
                """);

        assertTrue(povRule.check(ctx).isEmpty(), "我/我们尽在对白引号内：合法");
    }

    @Test
    void RC12_叙述层第一人称违规() {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setPov("第三人称");
        ctx.setDraft("""
                他立于城墙之上，我心中一片冰凉。
                我们不知道的是，暗处已有刀锋出鞘。
                """);

        List<RuleViolation> violations = povRule.check(ctx);
        assertEquals(1, violations.size());
        assertEquals(RuleSeverity.MEDIUM, violations.get(0).getSeverity());
        assertTrue(violations.get(0).getEvidence().contains("2 处"));
    }

    @Test
    void RC12_复合词白名单不计() {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setPov("第三人称");
        ctx.setDraft("""
                他练剑入忘我之境，剑势自我浑然。
                师父赞他已臻无我，依然故我而剑心通明。
                """);

        assertTrue(povRule.check(ctx).isEmpty(), "忘我/自我/无我/依然故我为叙述合法复合词");
    }

    @Test
    void RC12_视角未配置或第一人称暂不校验() {
        RuleCheckContext noPov = RuleCheckContext.of(RuleStage.DRAFT);
        noPov.setDraft("我在城楼上看到我自己。我们都在。");
        assertTrue(povRule.check(noPov).isEmpty(), "未配置视角：跳过");

        RuleCheckContext firstPerson = RuleCheckContext.of(RuleStage.DRAFT);
        firstPerson.setPov("第一人称");
        firstPerson.setDraft("他站在城楼上看着我们。他转身离去。");
        assertTrue(povRule.check(firstPerson).isEmpty(), "第一人称校验待 M2 叙事模式标注扩展");
    }

    @Test
    void RC12_单次出现视为噪声不计() {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setPov("第三人称");
        // 未闭合引号残留的单处"我"（书信片段）：低于阈值不触发（残余噪声容忍）
        ctx.setDraft("他读完信。信末写着：等我回来。山风呜咽。");

        assertTrue(povRule.check(ctx).isEmpty(), "单次出现视为噪声，防书信/题记误报");
    }
}
