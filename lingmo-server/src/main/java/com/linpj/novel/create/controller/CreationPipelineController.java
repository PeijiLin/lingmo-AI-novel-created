package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.AiChatRequest;
import com.linpj.novel.create.pojo.entity.CreationStageArtifact;
import com.linpj.novel.create.pojo.entity.CreationTask;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pipeline.CreationPipelineService;
import com.linpj.novel.create.security.InputGuard;
import com.linpj.novel.create.security.PrecheckVerdict;
import com.linpj.novel.create.security.SafetyAuditService;
import com.linpj.novel.create.service.CreationTaskService;
import com.linpj.novel.create.worldstate.NodeCheckpointService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linpj.novel.create.mapper.novel.CreationStageArtifactMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 多智能体创作管道入口（设计第二节）
 *
 * POST /api/book/create/ai/pipeline            同步模式（SSE stage + content 混合事件流）
 * POST /api/book/create/ai/pipeline/async      异步模式（返回 taskId，进度轮询）
 * GET  /api/book/create/ai/pipeline/task/{id}   异步任务进度查询（轮询）
 *
 * 请求体在 AiChatRequest 基础上增加 mode（FAST/STANDARD/REFINED/PRECISE，默认 FAST）
 * 与 maxRewriteRounds（默认 2，硬上限 3）。
 *
 * 兼容性：现有五个端点（/polish /create /continue /dialogue /activate）不动，老前端零改动。
 *
 * @author HL
 */
@RestController
@RequestMapping("/api/book/create/ai")
public class CreationPipelineController {

    private final CreationPipelineService creationPipelineService;
    private final CreationTaskService creationTaskService;
    private final InputGuard inputGuard;
    private final SafetyAuditService safetyAuditService;
    private final NodeCheckpointService checkpointService;
    private final CreationStageArtifactMapper artifactMapper;

    public CreationPipelineController(CreationPipelineService creationPipelineService,
                                        CreationTaskService creationTaskService,
                                        InputGuard inputGuard,
                                        SafetyAuditService safetyAuditService,
                                        NodeCheckpointService checkpointService,
                                        CreationStageArtifactMapper artifactMapper) {
        this.creationPipelineService = creationPipelineService;
        this.creationTaskService = creationTaskService;
        this.inputGuard = inputGuard;
        this.safetyAuditService = safetyAuditService;
        this.checkpointService = checkpointService;
        this.artifactMapper = artifactMapper;
    }

    /**
     * 运行创作管道（同步，SSE 混合事件流：stage + content）
     */
    @PostMapping(value = "/pipeline", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> runPipeline(@RequestBody AiChatRequest request) {
        validate(request);
        return creationPipelineService.run(PipelineRequest.from(request));
    }

    /**
     * 提交异步创作任务（整章/批量生成）：返回 taskId，前端轮询任务进度
     */
    @PostMapping("/pipeline/async")
    public BaseResponse<Long> submitAsyncPipeline(@RequestBody AiChatRequest request) {
        validate(request);
        return ResultUtils.success(creationPipelineService.submitAsyncTask(PipelineRequest.from(request)));
    }

    /**
     * 查询异步任务进度与用量统计（状态机：PENDING/RUNNING/COMPLETED/FAILED + token 埋点）
     */
    @GetMapping("/pipeline/task/{id}")
    public BaseResponse<CreationTask> queryTask(@PathVariable("id") Long id) {
        ThrowsUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        CreationTask task = creationTaskService.getById(id);
        ThrowsUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        return ResultUtils.success(task);
    }

    /**
     * run 级执行汇总（M5-F2：断点续跑观测入口）——任务 + 节点 checkpoint + 阶段产物清单一屏。
     * 续跑前先看这里：哪些节点已成功（succeededNodes）、卡在哪个阶段（currentStage）、
     * 产物落到了哪一轮（artifacts）。
     */
    @GetMapping("/pipeline/run/{runId}")
    public BaseResponse<java.util.Map<String, Object>> queryRun(@PathVariable("runId") Long runId) {
        ThrowsUtils.throwIf(runId == null || runId <= 0, ErrorCode.PARAMS_ERROR);
        CreationTask task = creationTaskService.getById(runId);
        ThrowsUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");

        java.util.Map<String, Object> run = new java.util.LinkedHashMap<>();
        run.put("task", task);
        run.put("succeededNodes", checkpointService.succeededNodes(runId));
        run.put("artifacts", listRunArtifacts(runId));
        return ResultUtils.success(run);
    }

    /** run 内全部阶段产物清单（stage/round/tokens 概览——产物本体去 task 详情或 artifact 表按 ID 取） */
    private java.util.List<java.util.Map<String, Object>> listRunArtifacts(Long runId) {
        java.util.List<CreationStageArtifact> records = artifactMapper.selectList(
                new LambdaQueryWrapper<CreationStageArtifact>()
                        .eq(CreationStageArtifact::getTaskId, runId)
                        .orderByAsc(CreationStageArtifact::getId));
        java.util.List<java.util.Map<String, Object>> summary = new java.util.ArrayList<>();
        for (CreationStageArtifact record : records) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("stage", record.getStage());
            item.put("round", record.getRound());
            item.put("tokens", record.getTokens());
            item.put("createdAt", record.getCreatedAt());
            summary.add(item);
        }
        return summary;
    }

    /**
     * 入口校验：参数完整性 + 前置防线（M4-E1：约束可满足性 + 注入模式，拒绝必给可行替代）。
     * P6 不可让渡：前置防线在两个入口（同步/异步）均强制执行，无开关。
     */
    private void validate(AiChatRequest request) {
        ThrowsUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(request.getBookId() == null || request.getBookId() <= 0,
                ErrorCode.PARAMS_ERROR, "bookId 不能为空");
        ThrowsUtils.throwIf(StringUtils.isBlank(request.getUserInput()),
                ErrorCode.PARAMS_ERROR, "userInput 不能为空");

        PrecheckVerdict verdict = inputGuard.precheck(request);
        if (verdict.blocked()) {
            // P1 拒绝：留审计 + 拒绝信息附可行替代（拒绝必须给替代，不允许只说不行）
            safetyAuditService.record(request.getBookId(), "PRE", verdict.category(), "REJECT",
                    "reason=" + verdict.reason());
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    verdict.reason() + "。可行替代：" + verdict.suggestion());
        }
        if (verdict.flagged()) {
            // P2 疑似：放行 + 标记（拦截率/误杀率双指标的观测数据源）
            safetyAuditService.record(request.getBookId(), "PRE", verdict.category(), "FLAG",
                    "reason=" + verdict.reason());
        }
    }
}
