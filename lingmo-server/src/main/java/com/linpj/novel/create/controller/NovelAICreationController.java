package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.AiChatRequest;
import com.linpj.novel.create.service.impl.NovelAICreationService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * @author HL
 */
@RestController
@RequestMapping("/api/book/create/ai")
public class NovelAICreationController {

    private final NovelAICreationService aiCreationService;

    public NovelAICreationController(NovelAICreationService aiCreationService) {
        this.aiCreationService = aiCreationService;
    }


    @PostMapping(value = "/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> polishContent(@RequestBody AiChatRequest aiChatRequest) {
        ThrowsUtils.throwIf(aiChatRequest == null, ErrorCode.PARAMS_ERROR);
        if (StringUtils.isBlank(aiChatRequest.getCreationType())) {
            aiChatRequest.setCreationType("润色");
        }
        return aiCreationService.createContent(aiChatRequest);
    }

    @PostMapping(value = "/activate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> activateInspiration(@RequestBody AiChatRequest aiChatRequest) {
        ThrowsUtils.throwIf(aiChatRequest == null, ErrorCode.PARAMS_ERROR);
        if (StringUtils.isBlank(aiChatRequest.getCreationType())) {
            aiChatRequest.setCreationType("灵感");
        }
        return aiCreationService.createContent(aiChatRequest);
    }

    /**
     * 通用 AI 创作接口
     */
    @PostMapping(value = "/create", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> createContent(@RequestBody AiChatRequest aiChatRequest) {
        // 调用服务层
        ThrowsUtils.throwIf(aiChatRequest == null, ErrorCode.PARAMS_ERROR);
        aiChatRequest.setCreationType("续写");
        return aiCreationService.createContent(aiChatRequest);
    }

    /**
     * 快速续写接口
     */
    @PostMapping(value = "/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> continueWriting(@RequestBody AiChatRequest aiChatRequest) {
        ThrowsUtils.throwIf(aiChatRequest == null, ErrorCode.PARAMS_ERROR);
        aiChatRequest.setCreationType("续写");
        return aiCreationService.createContent(aiChatRequest);
    }

    /**
     * 生成对话接口
     */
    @PostMapping(value = "/dialogue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateDialogue(@RequestBody AiChatRequest aiChatRequest) {
        ThrowsUtils.throwIf(aiChatRequest == null, ErrorCode.PARAMS_ERROR);
        aiChatRequest.setCreationType("对话");
        return aiCreationService.createContent(aiChatRequest);
    }
}