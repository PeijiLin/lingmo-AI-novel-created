package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.service.CustomizeKnowledgeBaseService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

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
     * 前端以 FormData 传 knowledgeId；兼容历史 knowledgeBaseId 命名。
     * @param multipartFile
     * @return
     */
    @PostMapping("/upload/file")
    public BaseResponse<Void> uploadFile(MultipartFile multipartFile,
                                         @RequestParam(value = "knowledgeId", required = false) Long knowledgeId,
                                         @RequestParam(value = "knowledgeBaseId", required = false) Long knowledgeBaseId) throws IOException {
        ThrowsUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR);
        Long kbId = knowledgeId != null ? knowledgeId : knowledgeBaseId;
        ThrowsUtils.throwIf(kbId == null, ErrorCode.PARAMS_ERROR, "知识库 id 不能为空");
        customizeKnowledgeBaseService.uploadFile(multipartFile, kbId);
        return ResultUtils.success();
    }


    /**
     * 上传内容的到指定知识库
     * 前端以 JSON body 传 {knowledgeId, content}；兼容 knowledgeBaseId 命名。
     * @param body
     * @return
     */
    @PostMapping("/upload/content")
    public BaseResponse<Void> uploadContent(@RequestBody Map<String, Object> body) {
        ThrowsUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR);
        Object content = body.get("content");
        Long kbId = toLong(body.get("knowledgeId"));
        if (kbId == null) {
            kbId = toLong(body.get("knowledgeBaseId"));
        }
        ThrowsUtils.throwIf(content == null, ErrorCode.PARAMS_ERROR, "内容不能为空");
        ThrowsUtils.throwIf(kbId == null, ErrorCode.PARAMS_ERROR, "知识库 id 不能为空");
        customizeKnowledgeBaseService.uploadContent(String.valueOf(content), kbId);
        return ResultUtils.success();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

}
