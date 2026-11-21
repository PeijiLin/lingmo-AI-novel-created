package com.linpj.novel.create.security;

import com.linpj.novel.create.pojo.pipeline.Issue;
import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 中置流式安全门单元测试（M4-E2）：STREAM 层只记"真正发生拦截"的事件，
 * 与 POST 层共用同一份词表与同一份修复指令（不搞两套标准）。
 *
 * @author HL
 */
class StreamSafetyGateTest {

    private final SensitiveTerms terms = new SensitiveTerms();

    @Test
    @DisplayName("扫描器复用同一份词表：跨包命中可被识别")
    void newGuardSharesWordlist() {
        StreamSafetyGate gate = new StreamSafetyGate(terms, null);
        StreamGuard guard = gate.newGuard();

        assertThrows(StreamSensitiveHitException.class,
                () -> guard.guarded(chunk -> { }).accept("制毒工艺"));
    }

    @Test
    @DisplayName("重生成落账：layer=STREAM + action=REWRITE + 定位信息（不落正文）")
    void recordRewriteLogsStreamLayer() {
        SafetyAuditService audit = mock(SafetyAuditService.class);
        StreamSafetyGate gate = new StreamSafetyGate(terms, audit);
        List<SensitiveTerms.Hit> hits = terms.scan("制毒工艺");

        gate.recordRewrite(1L, 10L, hits, 128);

        verify(audit).record(eq(1L), eq("STREAM"), eq("WORDLIST_ILLEGAL"), eq("REWRITE"),
                contains("chapterId=10"));
        verify(audit).record(eq(1L), eq("STREAM"), anyString(), anyString(), contains("forwardedLen=128"));
    }

    @Test
    @DisplayName("失败落账：重生成后仍命中/不可恢复走 REJECT（与 REWRITE 区分分子）")
    void recordRejectLogsRejectAction() {
        SafetyAuditService audit = mock(SafetyAuditService.class);
        StreamSafetyGate gate = new StreamSafetyGate(terms, audit);

        gate.recordReject(2L, null, terms.scan("制毒工艺"), 0);

        verify(audit).record(eq(2L), eq("STREAM"), eq("WORDLIST_ILLEGAL"), eq("REJECT"),
                contains("hits="));
    }

    @Test
    @DisplayName("审计通道故障不改变拦截决策（正文不会因落账失败而下发）")
    void auditFailureDoesNotBreakInterception() {
        SafetyAuditService audit = mock(SafetyAuditService.class);
        doThrow(new RuntimeException("DB down")).when(audit)
                .record(any(), anyString(), anyString(), anyString(), anyString());
        StreamSafetyGate gate = new StreamSafetyGate(terms, audit);

        assertDoesNotThrow(() -> gate.recordRewrite(1L, 10L, terms.scan("制毒工艺"), 0));
        assertDoesNotThrow(() -> gate.recordReject(1L, 10L, terms.scan("制毒工艺"), 0));
    }

    @Test
    @DisplayName("无审计通道（单测/轻量装配）不报错")
    void nullAuditIsTolerated() {
        StreamSafetyGate gate = new StreamSafetyGate(terms, null);

        assertDoesNotThrow(() -> gate.recordRewrite(1L, 1L, terms.scan("制毒工艺"), 0));
    }

    @Test
    @DisplayName("修复指令与 POST 层同源：只改表达，不动情节（同一份 repairReport）")
    void repairReportMatchesPostLayerInstruction() {
        StreamSafetyGate gate = new StreamSafetyGate(terms, null);
        List<SensitiveTerms.Hit> hits = terms.scan("制毒工艺");

        ReviewReport fromStream = gate.repairReport(hits);
        ReviewReport fromPost = FinalDraftGate.repairReport(hits);

        assertEquals(fromPost.getIssues().size(), fromStream.getIssues().size());
        Issue s = fromStream.getIssues().get(0);
        Issue p = fromPost.getIssues().get(0);
        assertEquals(p.getType(), s.getType());
        assertEquals(p.getSeverity(), s.getSeverity());
        assertEquals(p.getSuggestion(), s.getSuggestion());
        assertTrue(s.getEvidence().contains("制毒工艺"));
    }
}
