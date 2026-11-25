package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.pojo.entity.NovelCanonProposal;
import com.linpj.novel.create.pojo.entity.NovelStateFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 正典提案提交门测试（自主性改造 R3：软字段 ADOPTED 入典 / 死亡回退 REJECTED+冲突账 / 幂等与容错）
 *
 * @author HL
 */
class ProposalCommitGateTest {

    private CanonProposalService proposalService;
    private StateHistoryService stateHistoryService;
    private WikiConflictService wikiConflictService;
    private ProposalCommitGate gate;

    @BeforeEach
    void setUp() {
        proposalService = mock(CanonProposalService.class);
        stateHistoryService = mock(StateHistoryService.class);
        wikiConflictService = mock(WikiConflictService.class);
        gate = new ProposalCommitGate(proposalService, stateHistoryService, wikiConflictService);
    }

    private NovelCanonProposal pending(long id, Object proposal) {
        NovelCanonProposal p = new NovelCanonProposal();
        p.setId(id);
        p.setRunId(7L);
        p.setNodeId("WRITING:1");
        p.setStatus(CanonProposalService.STATUS_PENDING);
        p.setProposal(proposal);
        return p;
    }

    private NovelStateFact fact(Map<String, Object> state) {
        NovelStateFact fact = new NovelStateFact();
        fact.setState(state);
        return fact;
    }

    @Test
    void 软字段提案采纳并写入正典() {
        when(proposalService.proposalsOf(7L, "WRITING:1")).thenReturn(List.of(
                pending(1L, Map.of("type", "STATE", "characterId", 1001,
                        "state", Map.of("伤势", "左臂基本痊愈")))));
        when(stateHistoryService.solveLatest(10L, 1001L)).thenReturn(fact(Map.of("伤势", "左臂中弹")));
        when(proposalService.markResolved(1L, CanonProposalService.STATUS_ADOPTED)).thenReturn(true);

        ProposalCommitGate.SettleSummary summary = gate.settle(7L, "WRITING:1", 10L, 999L);

        assertEquals(1, summary.adopted());
        assertEquals(0, summary.rejected());
        verify(stateHistoryService).appendFact(eq(10L), eq(1001L), eq(999L),
                eq(Map.of("伤势", "左臂基本痊愈")));
        verify(wikiConflictService, never()).record(anyLong(), anyLong(), anyLong(),
                any(), any(), any(), any(), any());
    }

    @Test
    void 死亡回退硬冲突拒绝并落仲裁账() {
        when(proposalService.proposalsOf(7L, "WRITING:1")).thenReturn(List.of(
                pending(2L, Map.of("type", "STATE", "characterId", 1001,
                        "state", Map.of("aliveStatus", "ALIVE")))));
        when(stateHistoryService.solveLatest(10L, 1001L))
                .thenReturn(fact(Map.of("aliveStatus", "DEAD")));
        when(proposalService.markResolved(2L, CanonProposalService.STATUS_REJECTED)).thenReturn(true);

        ProposalCommitGate.SettleSummary summary = gate.settle(7L, "WRITING:1", 10L, 999L);

        assertEquals(0, summary.adopted());
        assertEquals(1, summary.rejected(), "死亡回退待作者仲裁——不静默写'复活'进正典");
        verify(stateHistoryService, never()).appendFact(anyLong(), anyLong(), anyLong(), any());
        ArgumentCaptor<String> noteCaptor = ArgumentCaptor.forClass(String.class);
        verify(wikiConflictService).record(eq(10L), eq(999L), eq(1001L),
                eq("PROPOSAL_CONFLICT"), eq("DEAD"), any(), eq("PENDING_REVIEW"),
                noteCaptor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(noteCaptor.getValue().contains("生死硬冲突"));
    }

    @Test
    void 结构不完整拒绝() {
        when(proposalService.proposalsOf(7L, "WRITING:1")).thenReturn(List.of(
                pending(3L, Map.of("type", "STATE")))); // 缺 characterId/state
        when(proposalService.markResolved(3L, CanonProposalService.STATUS_REJECTED)).thenReturn(true);

        ProposalCommitGate.SettleSummary summary = gate.settle(7L, "WRITING:1", 10L, 999L);

        assertEquals(0, summary.adopted());
        assertEquals(1, summary.rejected());
        verify(stateHistoryService, never()).appendFact(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void 已裁定与入账失败不改判() {
        // 已 ADOPTED 的提案跳过（幂等：不二次消费）
        NovelCanonProposal adopted = pending(4L, Map.of("type", "STATE", "characterId", 1,
                "state", Map.of("x", "y")));
        adopted.setStatus(CanonProposalService.STATUS_ADOPTED);
        when(proposalService.proposalsOf(7L, "WRITING:1")).thenReturn(List.of(adopted));
        gate.settle(7L, "WRITING:1", 10L, 999L);
        verify(proposalService, never()).markResolved(anyLong(), any());

        // 入账失败：保持 PENDING（不虚报 ADOPTED）
        when(proposalService.proposalsOf(7L, "WRITING:1")).thenReturn(List.of(
                pending(5L, Map.of("type", "STATE", "characterId", 1001,
                        "state", Map.of("伤势", "康复")))));
        when(stateHistoryService.solveLatest(10L, 1001L)).thenReturn(null);
        org.mockito.Mockito.doThrow(new RuntimeException("库不可用"))
                .when(stateHistoryService).appendFact(anyLong(), anyLong(), anyLong(), any());

        ProposalCommitGate.SettleSummary summary = gate.settle(7L, "WRITING:1", 10L, 999L);

        assertEquals(0, summary.adopted(), "入账失败不虚报 ADOPTED");
        assertEquals(0, summary.rejected());
        verify(proposalService, never()).markResolved(5L, CanonProposalService.STATUS_ADOPTED);
    }

    @Test
    void 无提案零开销() {
        when(proposalService.proposalsOf(7L, "WRITING:1")).thenReturn(List.of());

        ProposalCommitGate.SettleSummary summary = gate.settle(7L, "WRITING:1", 10L, 999L);

        assertEquals(0, summary.adopted());
        assertEquals(0, summary.rejected());
        verify(stateHistoryService, never()).solveLatest(anyLong(), anyLong());
    }
}
