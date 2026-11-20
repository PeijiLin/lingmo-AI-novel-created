package com.linpj.novel.create.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.KnowledgeBaseDto;
import com.linpj.novel.create.pojo.dto.KnowledgeBasePageRequest;
import com.linpj.novel.create.pojo.entity.KnowledgeBase;
import com.linpj.novel.create.pojo.vo.KnowledgeBaseVo;
import com.linpj.novel.create.service.KnowledgeBaseService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @author HL
 */
@RestController
@RequestMapping("/knowledgeBase")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Autowired
    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 添加知识库
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addKnowledgeBase(@RequestBody KnowledgeBaseDto knowledgeBaseDto) {
        ThrowsUtils.throwIf(knowledgeBaseDto == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(knowledgeBaseService.addKnowledgeBase(knowledgeBaseDto));
    }

    @PostMapping("/page")
    public BaseResponse<Page<KnowledgeBaseVo>> pageKnowledgeBase(@RequestBody KnowledgeBasePageRequest pageRequest) {
        return ResultUtils.success(knowledgeBaseService.pageKnowledgeBase(pageRequest));
    }

    /**
     * 删除知识库（前端以 POST 传 body {id}）
     */
    @PostMapping("/remove")
    public BaseResponse removeKnowledgeBase(@RequestBody Map<String, Object> payload) {
        Long id = payload == null ? null : toLong(payload.get("id"));
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = knowledgeBaseService.removeById(id);
        ThrowsUtils.throwIf(!b, ErrorCode.SYSTEM_ERROR);
        return ResultUtils.success(null, "删除成功");
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
