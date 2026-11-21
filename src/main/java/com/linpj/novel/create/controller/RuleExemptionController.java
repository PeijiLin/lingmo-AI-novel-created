package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.entity.NovelRuleExemption;
import com.linpj.novel.create.rule.RuleExemptionService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.List;

/**
 * 规则误报豁免接口（v2.0 M3-D6 误报闭环：作者标记"这是误报"）
 *
 * POST   /api/book/rule-exemption           标记误报（豁免 + 回归集入档）
 * GET    /api/book/rule-exemption/{bookId}  本书豁免清单（含误报理由）
 * DELETE /api/book/rule-exemption/{bookId}/{ruleId}  撤销豁免（规则修正后恢复检查）
 *
 * @author HL
 */
@RestController
@RequestMapping("/api/book/rule-exemption")
public class RuleExemptionController {

    private final RuleExemptionService ruleExemptionService;

    public RuleExemptionController(RuleExemptionService ruleExemptionService) {
        this.ruleExemptionService = ruleExemptionService;
    }

    /** 误报标记请求体 */
    @Data
    public static class ExemptRequest implements Serializable {
        private Long bookId;
        private String ruleId;
        private String reason;
    }

    /**
     * 标记误报：该书该规则即刻静默（下次生成不再检查），
     * 豁免记录进入回归集——规则词表/阈值调整时必须对豁免样本重跑
     */
    @PostMapping
    public BaseResponse<Boolean> exempt(@RequestBody ExemptRequest request) {
        ThrowsUtils.throwIf(request == null || request.getBookId() == null || request.getBookId() <= 0,
                ErrorCode.PARAMS_ERROR, "bookId 不能为空");
        ThrowsUtils.throwIf(request.getRuleId() == null || request.getRuleId().isBlank(),
                ErrorCode.PARAMS_ERROR, "ruleId 不能为空");
        boolean ok = ruleExemptionService.exempt(request.getBookId(), request.getRuleId().trim(),
                request.getReason());
        return ok ? ResultUtils.success(Boolean.TRUE) : ResultUtils.error(ErrorCode.SYSTEM_ERROR, "豁免失败，请重试");
    }

    /** 本书豁免清单（作者核对 + 回归集导出数据源） */
    @GetMapping("/{bookId}")
    public BaseResponse<List<NovelRuleExemption>> list(@PathVariable("bookId") Long bookId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(ruleExemptionService.listExemptions(bookId));
    }

    /** 撤销豁免：规则修正后恢复检查 */
    @DeleteMapping("/{bookId}/{ruleId}")
    public BaseResponse<Boolean> revoke(@PathVariable("bookId") Long bookId,
                                        @PathVariable("ruleId") String ruleId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(ruleId == null || ruleId.isBlank(), ErrorCode.PARAMS_ERROR);
        boolean ok = ruleExemptionService.revoke(bookId, ruleId);
        return ok ? ResultUtils.success(Boolean.TRUE) : ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "豁免记录不存在");
    }
}
