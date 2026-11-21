package com.linpj.novel.create.security;

import com.linpj.novel.create.pojo.pipeline.Issue;
import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 定稿前 gate 单元测试（M4-E3 POST 层）
 *
 * 验收点：
 * - 命中转成"定向修订"清单（与 Critic Issue 同构，可直接喂 Writer 修订链路）
 * - 三类处置各自落账到 novel_safety_log（POST/PASS、POST/REWRITE、POST/MANUAL_REVIEW），
 *   拦截率（分子 REWRITE+MANUAL_REVIEW / 分母 POST 全部）口径由 action 决定
 * - 落库只写定位信息与命中摘要，不写正文（审计日志不成为二次泄露面）
 * - 审计缺失/故障不影响判定结果
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
class FinalDraftGateTest {

    @Mock private SafetyAuditService audit;

    private FinalDraftGate gate;

    @BeforeEach
    void setUp() {
        gate = new FinalDraftGate(new SensitiveTerms(), audit);
    }

    @Test
    @DisplayName("命中 → 定向修订清单：HIGH/SAFETY_TERM + 禁止改情节的约束")
    void repairReportCarriesDirectedInstructions() {
        List<SensitiveTerms.Hit> hits = gate.scan("他被肢解，随后制毒工艺流出");

        assertEquals(2, hits.size());
        ReviewReport report = gate.repairReport(hits);

        assertFalse(report.isApproved());
        assertEquals(2, report.getIssues().size());
        Issue first = report.getIssues().get(0);
        assertEquals("HIGH", first.getSeverity());
        assertEquals(FinalDraftGate.ISSUE_TYPE, first.getType());
        assertTrue(first.getProblem().contains("肢解"));
        assertTrue(first.getSuggestion().contains("保持人物、情节、场景顺序与篇幅不变"));
        assertTrue(first.getEvidence().contains("肢解"));
    }

    @Test
    @DisplayName("无命中落 PASS：为拦截率提供分母，且不写正文")
    void passIsRecordedWithLocatorOnly() {
        gate.recordPass(1L, 10L, 1200);

        verify(audit).record(eq(1L), eq("POST"), eq("CLEAN"), eq("PASS"),
                contains("chapterId=10"));
        verify(audit).record(eq(1L), eq("POST"), eq("CLEAN"), eq("PASS"),
                contains("len=1200"));
    }

    @Test
    @DisplayName("修复成功落 REWRITE：分类带命中风险类型，detail 含词与位置")
    void rewriteIsRecordedWithCategory() {
        List<SensitiveTerms.Hit> hits = gate.scan("夜色里他肢解了敌人");

        gate.recordRewrite(1L, 10L, hits, 900);

        verify(audit).record(eq(1L), eq("POST"), eq("WORDLIST_VIOLENCE"), eq("REWRITE"),
                contains("肢解(VIOLENCE)@4"));
    }

    @Test
    @DisplayName("修复后仍命中落 MANUAL_REVIEW：进入人审队列（不阻断交付）")
    void manualReviewIsRecorded() {
        List<SensitiveTerms.Hit> hits = gate.scan("开膛破肚");

        gate.recordManualReview(2L, 11L, hits, 800);

        verify(audit).record(eq(2L), eq("POST"), eq("WORDLIST_VIOLENCE"), eq("MANUAL_REVIEW"),
                contains("开膛破肚(VIOLENCE)@0"));
    }

    @Test
    @DisplayName("审计缺失：gate 仍可判定，不抛异常")
    void gateWorksWithoutAudit() {
        FinalDraftGate bare = new FinalDraftGate(new SensitiveTerms(), null);

        assertDoesNotThrow(() -> bare.recordPass(1L, 1L, 10));
        assertDoesNotThrow(() -> bare.recordRewrite(1L, 1L, bare.scan("肢解"), 10));
        assertDoesNotThrow(() -> bare.recordManualReview(1L, 1L, bare.scan("肢解"), 10));
        assertEquals(1, bare.scan("肢解").size());
    }

    @Test
    @DisplayName("词表未装配：不判定（等价现状行为）")
    void nullTermsDegradesToNoOp() {
        FinalDraftGate empty = new FinalDraftGate(null, audit);

        assertTrue(empty.scan("肢解").isEmpty());
        assertDoesNotThrow(() -> empty.recordPass(1L, 1L, 10));
        verify(audit, never()).record(any(), any(), startsWith("WORDLIST_"), any(), any());
    }

    @Test
    @DisplayName("命中摘要：无正文，只有词/分类/位置")
    void describeOmitsBodyText() {
        String described = FinalDraftGate.describe(gate.scan("他说：肢解"));

        assertEquals("[肢解(VIOLENCE)@3]", described);
        assertEquals("[]", FinalDraftGate.describe(List.of()));
        assertEquals("[]", FinalDraftGate.describe(null));
    }

    @Test
    @DisplayName("配置可追加词表：部署侧收紧立即生效（无需开关）")
    void configuredCategoriesTightenImmediately() {
        SensitiveTerms terms = new SensitiveTerms();
        terms.setCategories(Map.of("CUSTOM", List.of("禁词甲")));
        FinalDraftGate custom = new FinalDraftGate(terms, audit);

        assertEquals(1, custom.scan("这里有禁词甲").size());
    }
}
