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
import java.util.Date;
import java.util.List;

/**
 * 规则误报豁免 / 叙诡保护区接口（v2.0 M3-D6 误报闭环 + M4-E5 保护区声明）
 *
 * POST   /api/book/rule-exemption                        标记误报或声明保护区（带章节区间即为保护区）
 * GET    /api/book/rule-exemption/{bookId}               本书豁免/保护区清单（含理由与复核时间）
 * GET    /api/book/rule-exemption/{bookId}/due           到期复核队列（过期项提醒续期或撤回）
 * PUT    /api/book/rule-exemption/{bookId}/review?ruleId=x  调整复核时间（规则 ID 含特殊字符时用此入口）
 * PUT    /api/book/rule-exemption/{bookId}/{ruleId}/review  调整复核时间（兼容简单规则 ID，body 为空 = 长期有效）
 * DELETE /api/book/rule-exemption/{bookId}?ruleId=x        撤销豁免（规则 ID 含特殊字符时用此入口）
 * DELETE /api/book/rule-exemption/{bookId}/{ruleId}        撤销豁免（兼容简单规则 ID）
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

    /** 误报标记 / 保护区声明请求体 */
    @Data
    public static class ExemptRequest implements Serializable {
        private Long bookId;
        private String ruleId;
        private String reason;
        /** 保护区起始章（与 chapterTo 任一非空即按区间保护处理） */
        private Long chapterFrom;
        /** 保护区结束章（闭区间） */
        private Long chapterTo;
        /** 到期复核时间（空 = 长期有效） */
        private Date reviewAt;
    }

    /** 复核时间请求体（reviewAt 为空 = 取消到期） */
    @Data
    public static class ReviewRequest implements Serializable {
        private Date reviewAt;
    }

    /**
     * 标记误报或声明保护区：
     * 不带章节区间 = 整本豁免（该书该规则即刻静默，下次生成不再检查）；
     * 带章节区间 = 保护区（区间内只告警不阻塞，区间外照常拦截）。
     * 两类记录都进回归集——规则词表/阈值调整时必须对豁免样本重跑。
     */
    @PostMapping
    public BaseResponse<Boolean> exempt(@RequestBody ExemptRequest request) {
        ThrowsUtils.throwIf(request == null || request.getBookId() == null || request.getBookId() <= 0,
                ErrorCode.PARAMS_ERROR, "bookId 不能为空");
        ThrowsUtils.throwIf(request.getRuleId() == null || request.getRuleId().isBlank(),
                ErrorCode.PARAMS_ERROR, "ruleId 不能为空");
        boolean hasRange = request.getChapterFrom() != null || request.getChapterTo() != null;
        ThrowsUtils.throwIf(request.getChapterFrom() != null && request.getChapterTo() != null
                        && request.getChapterFrom() > request.getChapterTo(),
                ErrorCode.PARAMS_ERROR, "保护区区间非法：起始章大于结束章");
        boolean ok = hasRange
                ? ruleExemptionService.declareZone(request.getBookId(), request.getRuleId().trim(),
                        request.getChapterFrom(), request.getChapterTo(), request.getReviewAt(), request.getReason())
                : ruleExemptionService.exempt(request.getBookId(), request.getRuleId().trim(), request.getReason());
        return ok ? ResultUtils.success(Boolean.TRUE) : ResultUtils.error(ErrorCode.SYSTEM_ERROR, "豁免失败，请重试");
    }

    /** 本书豁免/保护区清单（作者核对 + 回归集导出数据源） */
    @GetMapping("/{bookId}")
    public BaseResponse<List<NovelRuleExemption>> list(@PathVariable("bookId") Long bookId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(ruleExemptionService.listExemptions(bookId));
    }

    /** 到期复核队列：过期保护区已失效（规则恢复拦截），等作者续期或撤回 */
    @GetMapping("/{bookId}/due")
    public BaseResponse<List<NovelRuleExemption>> dueForReview(@PathVariable("bookId") Long bookId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(ruleExemptionService.listDueForReview(bookId));
    }

    /**
     * 调整复核时间（query 传 ruleId）：规则 ID 可含 / ? & 等字符，
     * 走路径变量会被容器解码成路径分隔符并返回 400，故单独提供此入口。
     */
    @PutMapping("/{bookId}/review")
    public BaseResponse<Boolean> reviewByParam(@PathVariable("bookId") Long bookId,
                                               @RequestParam("ruleId") String ruleId,
                                               @RequestBody(required = false) ReviewRequest request) {
        return doReview(bookId, ruleId, request);
    }

    /** 调整复核时间：续期（新到期时间）或改为长期有效（reviewAt 为空），兼容简单规则 ID */
    @PutMapping("/{bookId}/{ruleId}/review")
    public BaseResponse<Boolean> review(@PathVariable("bookId") Long bookId,
                                        @PathVariable("ruleId") String ruleId,
                                        @RequestBody(required = false) ReviewRequest request) {
        return doReview(bookId, ruleId, request);
    }

    private BaseResponse<Boolean> doReview(Long bookId, String ruleId, ReviewRequest request) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(ruleId == null || ruleId.isBlank(), ErrorCode.PARAMS_ERROR);
        Date reviewAt = request == null ? null : request.getReviewAt();
        boolean ok = ruleExemptionService.renew(bookId, ruleId.trim(), reviewAt);
        return ok ? ResultUtils.success(Boolean.TRUE) : ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "豁免记录不存在");
    }

    /** 撤销豁免（query 传 ruleId，支持任意字符的规则 ID） */
    @DeleteMapping("/{bookId}")
    public BaseResponse<Boolean> revokeByParam(@PathVariable("bookId") Long bookId,
                                               @RequestParam("ruleId") String ruleId) {
        return doRevoke(bookId, ruleId);
    }

    /** 撤销豁免：规则修正后恢复检查，兼容简单规则 ID */
    @DeleteMapping("/{bookId}/{ruleId}")
    public BaseResponse<Boolean> revoke(@PathVariable("bookId") Long bookId,
                                        @PathVariable("ruleId") String ruleId) {
        return doRevoke(bookId, ruleId);
    }

    private BaseResponse<Boolean> doRevoke(Long bookId, String ruleId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(ruleId == null || ruleId.isBlank(), ErrorCode.PARAMS_ERROR);
        boolean ok = ruleExemptionService.revoke(bookId, ruleId);
        return ok ? ResultUtils.success(Boolean.TRUE) : ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "豁免记录不存在");
    }
}
