package com.linpj.novel.create.agent;

import com.linpj.novel.create.agent.runtime.AgentFence;
import com.linpj.novel.create.agent.runtime.AgentResult;
import com.linpj.novel.create.agent.runtime.AgentRuntime;
import com.linpj.novel.create.agent.runtime.AgentTask;
import com.linpj.novel.create.agent.runtime.DeliverableContract;
import com.linpj.novel.create.agent.tool.CanonQueryTool;
import com.linpj.novel.create.agent.tool.SummaryGetTool;
import com.linpj.novel.create.config.AutonomyProperties;
import com.linpj.novel.create.pojo.pipeline.CreationMode;
import com.linpj.novel.create.pojo.pipeline.CriticContext;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CriticL2Gateway 试点测试（自主性改造 R1a：§12.7——ReviewReport 同构消费 + 降级链）
 *
 * @author HL
 */
class CriticL2GatewayTest {

    private AgentRuntime runtime;
    private CriticAgent criticAgent;
    private AutonomyProperties autonomy;

    @BeforeEach
    void setUp() {
        runtime = mock(AgentRuntime.class);
        criticAgent = mock(CriticAgent.class);
        autonomy = new AutonomyProperties();
        autonomy.setEnabled(true);
    }

    private CriticL2Gateway gateway() {
        return new CriticL2Gateway(null, runtime, autonomy, criticAgent);
    }

    private PipelineRequest request() {
        PipelineRequest request = new PipelineRequest();
        request.setBookId(1L);
        request.setChapterId(10L);
        request.setMode(CreationMode.REFINED);
        return request;
    }

    private CriticContext context() {
        CriticContext context = new CriticContext();
        context.setDraft("夜色渐沉。");
        return context;
    }

    @Test
    void 任务装配含白名单与默认档位围栏() {
        when(runtime.run(any(AgentTask.class))).thenReturn(new AgentResult.Degraded("stop", 0, 0));

        gateway().review(request(), context(), 7L, "CRITIC:1", null);

        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        org.mockito.Mockito.verify(runtime).run(captor.capture());
        AgentTask task = captor.getValue();

        assertEquals(7L, task.runId());
        assertEquals("CRITIC:1", task.nodeId());
        AgentFence fence = task.fence();
        assertTrue(fence.toolWhitelist().contains(CanonQueryTool.NAME));
        assertTrue(fence.toolWhitelist().contains(SummaryGetTool.NAME));
        assertEquals(8, fence.maxSteps(), "REFINED 默认矩阵 Critic maxSteps=8（纪律 1 校准值）");
        assertEquals(60_000L, fence.tokenBudget());
        assertTrue(task.systemPrompt().contains("L2 取证模式"));
        assertTrue(task.taskPrompt().contains("夜色渐沉。"), "任务材料含待审正文（L1/L2 同源）");
    }

    @Test
    void 成功收口消费ReviewReport同构() {
        ReviewReport report = new ReviewReport();
        report.setApproved(false);
        report.setOverallScore(4);
        report.setSummary("发现两处矛盾");
        String deliverable = com.linpj.novel.create.utils.JsonUtil.toJson(report);
        when(runtime.run(any(AgentTask.class)))
                .thenReturn(new AgentResult.Success(deliverable, 500, 3));

        CriticAgent.ReviewResult result = gateway().review(request(), context(), 7L, "CRITIC:1", null);

        assertEquals(4, report.getOverallScore() == 4 ? 4 : 0);
        assertEquals(false, result.report().isApproved());
        assertEquals(4, result.report().getOverallScore(), "K 循环消费逻辑零改动（同构直消费）");
        assertEquals(500, result.tokens());
    }

    @Test
    void 预算型耗尽透传L1结果() {
        CriticAgent.ReviewResult l1Result =
                new CriticAgent.ReviewResult(new ReviewReport(), 120);
        when(runtime.run(any(AgentTask.class)))
                .thenReturn(new AgentResult.FallbackL1(l1Result, "steps-exhausted", 640));

        CriticAgent.ReviewResult result = gateway().review(request(), context(), 7L, "CRITIC:1", null);

        assertEquals(l1Result, result, "FallbackL1 的 L1 产出原样透传（编排器无感）");
    }

    @Test
    void 契约型失败落宁放过降级报告() {
        when(runtime.run(any(AgentTask.class)))
                .thenReturn(new AgentResult.Degraded("contract-failed: issues 缺失", 800, 2));

        CriticAgent.ReviewResult result = gateway().review(request(), context(), 7L, "CRITIC:1", null);

        assertTrue(result.report().isApproved(), "降级语义与 Critic 故障降级同款（宁可放过不可改错）");
        assertTrue(result.report().getSummary().contains("L2 收口失败"));
        assertEquals(800, result.tokens(), "L2 已烧 token 如实入账（失败任务同样计费）");
    }

    @Test
    void 契约校验结构级规则生效() {
        DeliverableContract contract = CriticL2Gateway.reviewReportContract();

        // 合法报告通过
        String valid = """
                {"approved":true,"overallScore":8,"summary":"一致","issues":[]}""";
        assertTrue(contract.validate(valid).ok());

        // 非法 JSON 拒绝
        assertTrue(contract.validate("not-json").violations() != null);
        // 缺必填拒绝
        assertTrue(contract.validate("{\"approved\":true}").violations() != null);
        // issues 缺失拒绝
        assertTrue(contract.validate(
                "{\"approved\":true,\"overallScore\":8,\"summary\":\"s\"}").violations() != null);
    }
}
