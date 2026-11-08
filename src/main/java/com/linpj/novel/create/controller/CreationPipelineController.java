package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.AiChatRequest;
import com.linpj.novel.create.pojo.entity.CreationTask;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pipeline.CreationPipelineService;
import com.linpj.novel.create.service.CreationTaskService;
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

    public CreationPipelineController(CreationPipelineService creationPipelineService,
                                        CreationTaskService creationTaskService) {
        this.creationPipelineService = creationPipelineService;
        this.creationTaskService = creationTaskService;
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

    private void validate(AiChatRequest request) {
        ThrowsUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(request.getBookId() == null || request.getBookId() <= 0,
                ErrorCode.PARAMS_ERROR, "bookId 不能为空");
        ThrowsUtils.throwIf(StringUtils.isBlank(request.getUserInput()),
                ErrorCode.PARAMS_ERROR, "userInput 不能为空");
    }
}
