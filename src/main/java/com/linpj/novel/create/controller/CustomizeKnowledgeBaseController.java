package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.service.CustomizeKnowledgeBaseService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @author HL
 */
@RestController
@RequestMapping("/customizeKnowledgeBase")
public class CustomizeKnowledgeBaseController {
    private final CustomizeKnowledgeBaseService customizeKnowledgeBaseService;

    public CustomizeKnowledgeBaseController(CustomizeKnowledgeBaseService customizeKnowledgeBaseService) {
        this.customizeKnowledgeBaseService = customizeKnowledgeBaseService;
    }

    /**
     * 上传文件到指定知识库
     * @param multipartFile
     * @return
     */
    @PostMapping("/upload/file")
    public BaseResponse<Void> uploadFile(MultipartFile multipartFile, Long knowledgeBaseId) throws IOException {
        ThrowsUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(knowledgeBaseId == null, ErrorCode.PARAMS_ERROR);
        customizeKnowledgeBaseService.uploadFile(multipartFile, knowledgeBaseId);
        return ResultUtils.success();
    }


    /**
     * 上传内容的到指定知识库
     * @param content
     * @param knowledgeBaseId
     * @return
     */
    @PostMapping("/upload/content")
    public BaseResponse<Void> uploadContent(String content, Long knowledgeBaseId) {
        ThrowsUtils.throwIf(content == null, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(knowledgeBaseId == null, ErrorCode.PARAMS_ERROR);
        customizeKnowledgeBaseService.uploadContent(content, knowledgeBaseId);
        return ResultUtils.success();
    }

}
