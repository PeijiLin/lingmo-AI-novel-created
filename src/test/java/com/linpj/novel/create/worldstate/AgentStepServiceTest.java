package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.NovelAgentStep;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * AgentRuntime 步骤账本服务测试（自主性改造 R0：upsert 幂等 / 写异常上抛 / 读故障宁可重做）
 *
 * @author HL
 */
class AgentStepServiceTest {

    private AgentStepService service;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelAgentStep.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new AgentStepService());
    }

    @Test
    void 首次落账插入完整字段() {
        doReturn(List.of()).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doReturn(true).when(service).save(any(NovelAgentStep.class));

        service.recordStep(7L, "CRITIC:1", 1, "TOOL_CALL",
                Map.of("tool", "canon.query", "reason", "核对角色左臂伤势"),
                55L, AgentStepService.STATUS_DONE, 120, null);

        ArgumentCaptor<NovelAgentStep> captor = ArgumentCaptor.forClass(NovelAgentStep.class);
        verify(service).save(captor.capture());
        assertEquals(7L, captor.getValue().getRunId());
        assertEquals("CRITIC:1", captor.getValue().getNodeId());
        assertEquals(1, captor.getValue().getStepNo());
        assertEquals("TOOL_CALL", captor.getValue().getKind());
        assertEquals(55L, captor.getValue().getReceiptId());
        assertEquals(AgentStepService.STATUS_DONE, captor.getValue().getStatus());
        assertEquals(120, captor.getValue().getTokens());
    }

    @Test
    void 同键重复落账覆盖更新() {
        // 失败/崩溃步重做 = 覆盖旧行（upsert：UNIQUE(run,node,step) 命中走 update 而非 save）
        NovelAgentStep existing = new NovelAgentStep();
        existing.setId(100L);
        existing.setRunId(7L);
        existing.setNodeId("CRITIC:1");
        existing.setStepNo(2);
        existing.setStatus(AgentStepService.STATUS_FAILED);
        doReturn(List.of(existing)).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doReturn(true).when(service).updateById(any(NovelAgentStep.class));

        Long id = service.recordStep(7L, "CRITIC:1", 2, "FINISH",
                Map.of("deliverable", "..."), null, AgentStepService.STATUS_DONE, 80, "redo-after-crash");

        assertEquals(100L, id);
        verify(service).updateById(existing);
        assertEquals(AgentStepService.STATUS_DONE, existing.getStatus());
        assertEquals(80, existing.getTokens());
        assertEquals("redo-after-crash", existing.getNote());
        verify(service, never()).save(any(NovelAgentStep.class));
    }

    @Test
    void 无效参数拒绝落账() {
        assertNull(service.recordStep(null, "CRITIC:1", 1, "TOOL_CALL", null, null,
                AgentStepService.STATUS_DONE, 0, null));
        assertNull(service.recordStep(7L, " ", 1, "TOOL_CALL", null, null,
                AgentStepService.STATUS_DONE, 0, null));
        assertNull(service.recordStep(7L, "CRITIC:1", 0, "TOOL_CALL", null, null,
                AgentStepService.STATUS_DONE, 0, null), "stepNo 从 1 起");
        assertNull(service.recordStep(7L, "CRITIC:1", 1, null, null, null,
                AgentStepService.STATUS_DONE, 0, null));
        verify(service, never()).save(any(NovelAgentStep.class));
        verify(service, never()).updateById(any(NovelAgentStep.class));
    }

    @Test
    void 读故障不盲写静默返回() {
        // 预检查读故障：不盲写（写大概率同样故障），交调用方决策重试
        doThrow(new RuntimeException("库不可用")).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        assertNull(service.recordStep(7L, "CRITIC:1", 1, "TOOL_CALL", null, null,
                AgentStepService.STATUS_DONE, 0, null));
        verify(service, never()).save(any(NovelAgentStep.class));
    }

    @Test
    void 写通道异常上抛运行时决策() {
        // 步骤账本同时是恢复依据与审计账本：写失败上抛给 AgentRuntime（挂起/降级决策点），不静默吞掉
        doReturn(List.of()).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doThrow(new RuntimeException("库不可用")).when(service).save(any(NovelAgentStep.class));
        assertThrows(RuntimeException.class, () ->
                service.recordStep(7L, "CRITIC:1", 1, "TOOL_CALL", null, null,
                        AgentStepService.STATUS_DONE, 0, null));

        // update 路径同样上抛
        NovelAgentStep existing = new NovelAgentStep();
        existing.setId(100L);
        doReturn(List.of(existing)).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        doThrow(new RuntimeException("库不可用")).when(service).updateById(any(NovelAgentStep.class));
        assertThrows(RuntimeException.class, () ->
                service.recordStep(7L, "CRITIC:1", 1, "FINISH", null, null,
                        AgentStepService.STATUS_DONE, 0, null));
    }

    @Test
    void 完成判定只认DONE() {
        // 只有 DONE 才可 kill-resume 从回执重放；RUNNING/FAILED/SKIPPED 一律重做
        NovelAgentStep done = new NovelAgentStep();
        done.setStatus(AgentStepService.STATUS_DONE);
        doReturn(List.of(done)).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertTrue(service.hasDoneStep(7L, "CRITIC:1", 1));
        assertTrue(service.hasStep(7L, "CRITIC:1", 1));

        NovelAgentStep failed = new NovelAgentStep();
        failed.setStatus(AgentStepService.STATUS_FAILED);
        doReturn(List.of(failed)).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertFalse(service.hasDoneStep(7L, "CRITIC:1", 1));
        assertTrue(service.hasStep(7L, "CRITIC:1", 1), "存在性与完成性分离：失败步存在但不可重放");
    }

    @Test
    void 步骤列表按步序升序() {
        NovelAgentStep s3 = new NovelAgentStep();
        s3.setStepNo(3);
        s3.setTokens(300);
        NovelAgentStep s1 = new NovelAgentStep();
        s1.setStepNo(1);
        s1.setTokens(100);
        NovelAgentStep s2 = new NovelAgentStep();
        s2.setStepNo(2);
        s2.setTokens(200);
        doReturn(List.of(s3, s1, s2)).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        List<NovelAgentStep> steps = service.stepsOf(7L, "CRITIC:1");
        assertEquals(List.of(1, 2, 3), steps.stream().map(NovelAgentStep::getStepNo).toList());
        assertEquals(600, service.tokensUsed(7L, "CRITIC:1"), "围栏剩余额度推导的 token 已耗口径");
    }

    @Test
    void 读故障读到侧全按宁可重做降级() {
        doThrow(new RuntimeException("库不可用")).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        assertTrue(service.stepsOf(7L, "CRITIC:1").isEmpty());
        assertFalse(service.hasStep(7L, "CRITIC:1", 1));
        assertFalse(service.hasDoneStep(7L, "CRITIC:1", 1));
        assertEquals(0, service.tokensUsed(7L, "CRITIC:1"));
        assertDoesNotThrow(() -> service.stepsOf(null, null));
    }
}
