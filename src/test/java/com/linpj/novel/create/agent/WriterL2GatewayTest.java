package com.linpj.novel.create.agent;

import com.linpj.novel.create.agent.runtime.AgentFence;
import com.linpj.novel.create.agent.runtime.AgentResult;
import com.linpj.novel.create.agent.runtime.AgentRuntime;
import com.linpj.novel.create.agent.runtime.AgentTask;
import com.linpj.novel.create.agent.tool.CanonQueryTool;
import com.linpj.novel.create.agent.tool.ProposalEmitTool;
import com.linpj.novel.create.agent.tool.SummaryGetTool;
import com.linpj.novel.create.config.AutonomyProperties;
import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import com.linpj.novel.create.pojo.pipeline.CreationMode;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.WriterContext;
import com.linpj.novel.create.utils.JsonUtil;
import com.linpj.novel.create.worldstate.CitationLinkService;
import com.linpj.novel.create.worldstate.ToolReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Writer L2 网关测试（自主性改造 R2a：WriteResult 同构契约 / claims[] 引文校验 / 终态消费与安全网）
 *
 * @author HL
 */
class WriterL2GatewayTest {

    private AgentRuntime runtime;
    private WriterAgent writerAgent;
    private AutonomyProperties autonomy;
    private ToolReceiptService receiptService;
    private CitationLinkService citationLinkService;

    @BeforeEach
    void setUp() {
        runtime = mock(AgentRuntime.class);
        writerAgent = mock(WriterAgent.class);
        autonomy = new AutonomyProperties();
        autonomy.setEnabled(true);
        receiptService = mock(ToolReceiptService.class);
        citationLinkService = mock(CitationLinkService.class);
    }

    private WriterL2Gateway gateway() {
        return new WriterL2Gateway(null, runtime, autonomy, writerAgent,
                receiptService, citationLinkService);
    }

    private PipelineRequest request() {
        PipelineRequest request = new PipelineRequest();
        request.setBookId(10L);
        request.setChapterId(999L);
        request.setMode(CreationMode.REFINED);
        request.setUserInput("写林岸出院后的对峙");
        return request;
    }

    private WriterContext context() {
        return new WriterContext();
    }

    @Test
    void 任务装配含三工具白名单与默认档位围栏() {
        when(runtime.run(any(AgentTask.class))).thenReturn(new AgentResult.Degraded("stop", 0, 0));

        gateway().write(request(), context(), 7L, "WRITING:1", null);

        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(runtime).run(captor.capture());
        AgentTask task = captor.getValue();

        assertEquals("WRITING:1", task.nodeId());
        AgentFence fence = task.fence();
        assertTrue(fence.toolWhitelist().contains(CanonQueryTool.NAME));
        assertTrue(fence.toolWhitelist().contains(SummaryGetTool.NAME));
        assertTrue(fence.toolWhitelist().contains(ProposalEmitTool.NAME), "提案暂存工具在白名单（R2a）");
        assertEquals(5, fence.maxSteps(), "REFINED 默认矩阵 Writer maxSteps=5");
        assertEquals(60_000L, fence.tokenBudget());
        assertTrue(task.systemPrompt().contains("proposal.emit"));
    }

    @Test
    void 成功收口消费WriteResult同构并一次性下发() {
        String deliverable = JsonUtil.toJson(Map.of(
                "text", "夜色渐沉，林岸站在钢厂的阴影里……（正文省略，实际长度达标）",
                "report", Map.of("written", "完成对峙场景", "proposalsStored", java.util.List.of("P1"))));
        when(runtime.run(any(AgentTask.class)))
                .thenReturn(new AgentResult.Success(deliverable, 800, 3));

        WriterAgent.WriteResult result = gateway().write(request(), context(), 7L, "WRITING:1", null);

        assertNotNull(result);
        assertTrue(result.text().contains("夜色渐沉"));
        assertEquals(800, result.tokens());
    }

    @Test
    void 契约型失败返回null走分发处安全网() {
        when(runtime.run(any(AgentTask.class)))
                .thenReturn(new AgentResult.Degraded("contract-failed: text 缺失", 500, 2));

        // Degraded 不抛异常（Writer 哲学：L2 故障安全网回 L1——分发处 writeFirstDraft 兜底）
        assertNull(gateway().write(request(), context(), 7L, "WRITING:1", null));
    }

    @Test
    void 预算型耗尽透传L1兜底产出() {
        WriterAgent.WriteResult l1Result = new WriterAgent.WriteResult("L1 正文", 300L);
        when(runtime.run(any(AgentTask.class)))
                .thenReturn(new AgentResult.FallbackL1(l1Result, "steps-exhausted", 600));

        WriterAgent.WriteResult result = gateway().write(request(), context(), 7L, "WRITING:1", null);

        assertEquals("L1 正文", result.text());
    }

    @Test
    void 契约校验正文必填与长度下限() {
        WriterL2Gateway gateway = gateway();
        com.linpj.novel.create.agent.runtime.DeliverableContract contract =
                gateway.writerContract(7L, "WRITING:1");

        // 合法交付通过（正文≥下限）
        String valid = JsonUtil.toJson(Map.of(
                "text", "x".repeat(WriterL2Gateway.MIN_DRAFT_LENGTH),
                "report", Map.of("written", "场景完成")));
        assertTrue(contract.validate(valid).ok());

        // 缺 text / 缺 report / 正文空 / 过短 / 缺 written
        assertTrue(contract.validate(JsonUtil.toJson(Map.of("report", Map.of()))).violations() != null);
        assertTrue(contract.validate(JsonUtil.toJson(Map.of("text", "正文", "report", Map.of()))).violations() != null,
                "正文过短拦截");
        assertTrue(contract.validate(JsonUtil.toJson(Map.of(
                "text", "x".repeat(WriterL2Gateway.MIN_DRAFT_LENGTH),
                "report", Map.of()))).violations() != null, "汇报缺 written");
    }

    @Test
    void claims引文校验与落账() {
        NovelToolReceipt receipt = new NovelToolReceipt();
        receipt.setId(30L);
        receipt.setResult(JsonUtil.toJson(java.util.List.of(
                Map.of("text", "林岸左臂中弹，恢复期"))));
        when(receiptService.getById(30L)).thenReturn(receipt);

        com.linpj.novel.create.agent.runtime.DeliverableContract contract =
                gateway().writerContract(7L, "WRITING:1");

        // 合法 claims：通过 + 引文账落账
        String cited = JsonUtil.toJson(Map.of(
                "text", "正文……",
                "report", Map.of("written", "s"),
                "claims", java.util.List.of(
                        Map.of("text", "林岸左臂中弹尚未痊愈", "evidenceId", "ev:30#1"))));
        com.linpj.novel.create.agent.runtime.DeliverableContract.CitationOutcome ok =
                contract.verifyCitations(cited, 3);
        assertTrue(ok.ok());
        assertEquals(1, ok.total());
        assertEquals(1, ok.valid());
        verify(citationLinkService).record(eq(7L), eq("WRITING:1"), eq(3),
                contains("左臂"), eq("ev:30#1"), eq(30L), eq(1), eq(true), isNull());

        // 幻觉 claims：拦截 + 失败绑定留痕
        String hallucinated = JsonUtil.toJson(Map.of(
                "text", "正文……",
                "report", Map.of("written", "s"),
                "claims", java.util.List.of(
                        Map.of("text", "右臂完全健康无伤", "evidenceId", "ev:30#1"))));
        com.linpj.novel.create.agent.runtime.DeliverableContract.CitationOutcome bad =
                contract.verifyCitations(hallucinated, 3);
        assertFalse(bad.ok());

        // claims 空/缺失不强制（Writer 引文对象是前文证据，可选）
        String noClaims = JsonUtil.toJson(Map.of(
                "text", "正文……", "report", Map.of("written", "s")));
        assertTrue(contract.verifyCitations(noClaims, 3).ok());
    }
}
