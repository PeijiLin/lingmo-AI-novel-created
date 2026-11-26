package com.linpj.novel.create.agent;

import com.linpj.novel.create.agent.runtime.AgentFence;
import com.linpj.novel.create.agent.runtime.AgentResult;
import com.linpj.novel.create.agent.runtime.AgentRuntime;
import com.linpj.novel.create.agent.runtime.AgentTask;
import com.linpj.novel.create.agent.tool.CanonQueryTool;
import com.linpj.novel.create.agent.tool.RuleCheckTool;
import com.linpj.novel.create.agent.tool.SummaryGetTool;
import com.linpj.novel.create.config.AutonomyProperties;
import com.linpj.novel.create.pojo.pipeline.CreationMode;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.PlannerContext;
import com.linpj.novel.create.utils.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Planner L2 网关测试（自主性改造 R2b：PlanResult 同构契约 / rulecheck 白名单 / 终态消费与安全网）
 *
 * @author HL
 */
class PlannerL2GatewayTest {

    private AgentRuntime runtime;
    private PlannerAgent plannerAgent;
    private AutonomyProperties autonomy;
    private com.linpj.novel.create.worldstate.PendingAssertionService pendingAssertionService;

    @BeforeEach
    void setUp() {
        runtime = mock(AgentRuntime.class);
        plannerAgent = mock(PlannerAgent.class);
        autonomy = new AutonomyProperties();
        autonomy.setEnabled(true);
        pendingAssertionService = mock(com.linpj.novel.create.worldstate.PendingAssertionService.class);
    }

    private PlannerL2Gateway gateway() {
        return new PlannerL2Gateway(null, runtime, autonomy, plannerAgent, pendingAssertionService);
    }

    private PipelineRequest request() {
        PipelineRequest request = new PipelineRequest();
        request.setBookId(10L);
        request.setChapterId(999L);
        request.setMode(CreationMode.REFINED);
        request.setUserInput("规划钢厂对峙章");
        return request;
    }

    @Test
    void 任务装配含规则校验工具与默认档位围栏() {
        when(runtime.run(any(AgentTask.class))).thenReturn(new AgentResult.Degraded("stop", 0, 0));

        gateway().plan(request(), new PlannerContext(), 7L, "PLANNING", null);

        ArgumentCaptor<AgentTask> captor = ArgumentCaptor.forClass(AgentTask.class);
        verify(runtime).run(captor.capture());
        AgentTask task = captor.getValue();

        assertEquals("PLANNING", task.nodeId());
        AgentFence fence = task.fence();
        assertTrue(fence.toolWhitelist().contains(RuleCheckTool.NAME),
                "rulecheck.run 在白名单——大纲可行性靠确定性规则回执（判定确定化）");
        assertTrue(fence.toolWhitelist().contains(CanonQueryTool.NAME));
        assertTrue(fence.toolWhitelist().contains(SummaryGetTool.NAME));
        assertEquals(4, fence.maxSteps(), "REFINED 默认矩阵 Planner maxSteps=4");
        assertEquals(30_000L, fence.tokenBudget());
    }

    @Test
    void 成功收口消费PlanResult同构() {
        String deliverable = JsonUtil.toJson(Map.of(
                "chapterTitle", "钢厂对峙",
                "scenes", List.of(Map.of(
                        "location", "废弃钢厂", "conflict", "对峙", "outcome", "对方现身"))));
        when(runtime.run(any(AgentTask.class)))
                .thenReturn(new AgentResult.Success(deliverable, 600, 3));

        PlannerAgent.PlanResult result = gateway().plan(request(), new PlannerContext(), 7L, "PLANNING", null);

        assertNotNull(result);
        assertEquals("钢厂对峙", result.outline().getChapterTitle());
        assertEquals(600, result.tokens());
    }

    @Test
    void 契约型失败与预算型异常都返null走安全网() {
        when(runtime.run(any(AgentTask.class)))
                .thenReturn(new AgentResult.Degraded("contract-failed: scenes 为空", 300, 2));
        assertNull(gateway().plan(request(), new PlannerContext(), 7L, "PLANNING", null));

        when(runtime.run(any(AgentTask.class)))
                .thenReturn(new AgentResult.FallbackL1("非 PlanResult 类型", "steps-exhausted", 200));
        assertNull(gateway().plan(request(), new PlannerContext(), 7L, "PLANNING", null));
    }

    @Test
    void 契约校验大纲结构与场景三要素() {
        com.linpj.novel.create.agent.runtime.DeliverableContract contract = gateway().outlineContract();

        // 合法大纲
        String valid = JsonUtil.toJson(Map.of(
                "chapterTitle", "第13章",
                "scenes", List.of(Map.of(
                        "location", "钢厂", "conflict", "对峙", "outcome", "现身"))));
        assertTrue(contract.validate(valid).ok());

        // 缺标题 / 场景空 / 场景缺要素
        assertTrue(contract.validate(JsonUtil.toJson(Map.of(
                "scenes", List.of(Map.of("location", "x", "conflict", "y", "outcome", "z")))))
                .violations() != null, "缺 chapterTitle");
        assertTrue(contract.validate(JsonUtil.toJson(Map.of(
                "chapterTitle", "t", "scenes", List.of())))
                .violations() != null, "scenes 空");
        assertTrue(contract.validate(JsonUtil.toJson(Map.of(
                "chapterTitle", "t",
                "scenes", List.of(Map.of("location", "x", "conflict", "y")))))
                .violations() != null, "场景缺 outcome（状态变化要素）");
    }
}
