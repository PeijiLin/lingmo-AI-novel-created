package com.linpj.novel.create.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.mapper.novel.CreationStageArtifactMapper;
import com.linpj.novel.create.pojo.entity.CreationStageArtifact;
import com.linpj.novel.create.pojo.entity.CreationTask;
import com.linpj.novel.create.pipeline.CreationPipelineService;
import com.linpj.novel.create.security.InputGuard;
import com.linpj.novel.create.security.SafetyAuditService;
import com.linpj.novel.create.service.CreationTaskService;
import com.linpj.novel.create.worldstate.NodeCheckpointService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * run 汇总端点测试（M5-F2：断点续跑观测入口——任务+checkpoint+产物一屏）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreationPipelineRunSummaryTest {

    @Mock private CreationPipelineService pipelineService;
    @Mock private CreationTaskService creationTaskService;
    @Mock private InputGuard inputGuard;
    @Mock private SafetyAuditService safetyAuditService;
    @Mock private NodeCheckpointService checkpointService;
    @Mock private CreationStageArtifactMapper artifactMapper;

    private CreationPipelineController controller;

    @BeforeAll
    static void initEntityMetadata() {
        // Lambda 条件构造需要实体 TableInfo 元数据（controller 内构建 Wrapper）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                CreationStageArtifact.class);
    }

    @BeforeEach
    void setUp() {
        controller = new CreationPipelineController(pipelineService, creationTaskService,
                inputGuard, safetyAuditService, checkpointService, artifactMapper);
    }

    private CreationTask task(Long id, String status, String stage) {
        CreationTask task = new CreationTask();
        task.setId(id);
        task.setBookId(1L);
        task.setStatus(status);
        task.setCurrentStage(stage);
        return task;
    }

    @Test
    void run汇总含任务节点与产物() {
        when(creationTaskService.getById(9L)).thenReturn(task(9L, "FAILED", "CRITIC"));
        when(checkpointService.succeededNodes(9L)).thenReturn(Set.of("PLANNING", "WRITING:1"));
        CreationStageArtifact artifact = new CreationStageArtifact();
        artifact.setStage("WRITING");
        artifact.setRound(1);
        artifact.setTokens(1200);
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact));

        BaseResponse<Map<String, Object>> response = controller.queryRun(9L);

        assertNotNull(response);
        assertTrue(response.getData().containsKey("task"));
        assertEquals(Set.of("PLANNING", "WRITING:1"), response.getData().get("succeededNodes"),
                "断点续跑预检：哪些节点已成功可恢复");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) response.getData().get("artifacts");
        assertEquals("WRITING", artifacts.get(0).get("stage"));
        assertEquals(1, artifacts.get(0).get("round"));
    }

    @Test
    void 任务不存在抛404语义() {
        when(creationTaskService.getById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> controller.queryRun(404L));
    }

    @Test
    void 非法参数直接拒绝() {
        assertThrows(BusinessException.class, () -> controller.queryRun(null));
        assertThrows(BusinessException.class, () -> controller.queryRun(0L));
    }
}
