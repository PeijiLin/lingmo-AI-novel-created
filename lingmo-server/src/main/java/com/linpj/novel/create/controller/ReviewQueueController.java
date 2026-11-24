package com.linpj.novel.create.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.mapper.novel.CreationStageArtifactMapper;
import com.linpj.novel.create.pojo.entity.CreationStageArtifact;
import com.linpj.novel.create.pojo.entity.CreationTask;
import com.linpj.novel.create.pojo.entity.NovelPendingAssertion;
import com.linpj.novel.create.pojo.entity.NovelSafetyLog;
import com.linpj.novel.create.pojo.entity.NovelWikiConflict;
import com.linpj.novel.create.security.SafetyAuditService;
import com.linpj.novel.create.service.CreationTaskService;
import com.linpj.novel.create.worldstate.PendingAssertionService;
import com.linpj.novel.create.worldstate.WikiConflictService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人审队列接口（v2.0 设计 5.6 人机协同卡点 / M6-6：最小闭环——数据源已由 M1–M4 落账，此处统一成队）
 *
 * 四类卡点（对应设计 5.6 表）：
 * - CONFLICT   正典冲突仲裁（wiki_conflict PENDING_REVIEW，M1-B4 死亡回退等）→ 改文/改设定/驳回
 * - ASSERTION  待证实断言（pending_assertion PENDING，M2-C6 非 FACT 隔离）→ 确认/驳回
 * - SAFETY     安全人审（safety_log action=MANUAL_REVIEW，M4-E3 后置 gate 兜底）→ 放行/拒绝
 * - UNMET      未达标章（artifact ADOPTED_ROUND unmet=true，M3-D6 轮次留账）→ 接受/重写
 *
 * 处置动作分三类写回通道：
 * - 状态推进：conflict.resolve / assertion.resolve / safetyLog 覆盖 action（统一走各自服务的单一写入者通道）
 * - UNMET 接受：打标记（终稿已存，无需改任务状态）；UNMET 重写：建议重写通道（重开任务由既有创作入口承担，
 *   本接口不代跑管道——人审队列是决策入口不是执行器）
 *
 * GET  /api/book/review-queue/{bookId}          总览（四类各取待办，count 汇总）
 * GET  /api/book/review-queue/{bookId}/{type}    分页式清单（type = conflict/assertion/safety/unmet）
 * PUT  /api/book/review-queue/{bookId}/conflict/{id}    仲裁（accept=true 确认新事实/改设定）
 * PUT  /api/book/review-queue/{bookId}/assertion/{id}   裁定（accept=true 确认）
 * PUT  /api/book/review-queue/{bookId}/safety/{id}      裁定（approve=false 记拒绝留痕；true 记放行——
 *                                                       拦截语义不可撤，仅补作者决定）
 * PUT  /api/book/review-queue/{bookId}/unmet/{taskId}   采纳（接受未达标稿：终稿不动，决策留痕）
 *
 * @author HL
 */
@RestController
@RequestMapping("/api/book/review-queue")
public class ReviewQueueController {

    /** 未达标采纳留痕轮次（与 M3-D6 ADOPTED_ROUND=96 同域的自定义轮次：出队幂等标记） */
    public static final int UNMET_ACCEPT_ROUND = 97;

    /** M3-D6 轮次留账的固定轮次（与 CreationPipelineService.ADOPTED_ROUND 同值；跨类常量引用避免魔法数） */
    public static final int ADOPTED_ROUND = 96;

    private final WikiConflictService wikiConflictService;
    private final PendingAssertionService pendingAssertionService;
    private final SafetyAuditService safetyAuditService;
    private final CreationTaskService creationTaskService;
    private final CreationStageArtifactMapper artifactMapper;

    public ReviewQueueController(WikiConflictService wikiConflictService,
                                 PendingAssertionService pendingAssertionService,
                                 SafetyAuditService safetyAuditService,
                                 CreationTaskService creationTaskService,
                                 CreationStageArtifactMapper artifactMapper) {
        this.wikiConflictService = wikiConflictService;
        this.pendingAssertionService = pendingAssertionService;
        this.safetyAuditService = safetyAuditService;
        this.creationTaskService = creationTaskService;
        this.artifactMapper = artifactMapper;
    }

    /** 仲裁请求体（decision 必填；action: 改文/改设定/驳回等描述性留痕） */
    @Data
    public static class ReviewDecision implements Serializable {
        /** true=确认/放行/采纳；false=驳回/拒绝 */
        private Boolean accept;
        /** 处置说明（改文还是改设定等；未填按 accept 默认文案） */
        private String note;
    }

    // ------------------------------------------------------------------
    // 队列总览 / 分项清单
    // ------------------------------------------------------------------

    /** 总览：四类待办数量与首屏数据（人审工作台的入口 payload） */
    @GetMapping("/{bookId}")
    public BaseResponse<Map<String, Object>> overview(@PathVariable("bookId") Long bookId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        Map<String, Object> overview = new LinkedHashMap<>();
        List<NovelWikiConflict> conflicts = wikiConflictService.listPendingReview(bookId);
        List<NovelPendingAssertion> assertions = pendingAssertionService.listPending(bookId);
        List<NovelSafetyLog> safeties = listSafetyReviews(bookId);
        List<CreationTask> unmets = listUnmetTasks(bookId);
        overview.put("counts", Map.of(
                "conflict", conflicts.size(),
                "assertion", assertions.size(),
                "safety", safeties.size(),
                "unmet", unmets.size()));
        overview.put("conflict", conflicts);
        overview.put("assertion", assertions);
        overview.put("safety", safeties);
        overview.put("unmet", unmets);
        return ResultUtils.success(overview);
    }

    /** 分项清单（前端分页拉取用；type 大小写不敏感） */
    @GetMapping("/{bookId}/{type}")
    public BaseResponse<List<?>> listByType(@PathVariable("bookId") Long bookId,
                                            @PathVariable("type") String type) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(type == null || type.isBlank(), ErrorCode.PARAMS_ERROR);
        return switch (type.toLowerCase()) {
            case "conflict" -> ResultUtils.success(wikiConflictService.listPendingReview(bookId));
            case "assertion" -> ResultUtils.success(pendingAssertionService.listPending(bookId));
            case "safety" -> ResultUtils.success(listSafetyReviews(bookId));
            case "unmet" -> ResultUtils.success(listUnmetTasks(bookId));
            default -> throw new com.linpj.novel.create.exception.BusinessException(
                    ErrorCode.PARAMS_ERROR, "未知队列类型: " + type + "（可选 conflict/assertion/safety/unmet）");
        };
    }

    // ------------------------------------------------------------------
    // 处置（四类卡点的决策写回）
    // ------------------------------------------------------------------

    /** 冲突仲裁：accept=true 确认新事实（改设定升版本由正规通道）/ false 驳回（维持旧事实=改文） */
    @PutMapping("/{bookId}/conflict/{conflictId}")
    public BaseResponse<Boolean> resolveConflict(@PathVariable("bookId") Long bookId,
                                                 @PathVariable("conflictId") Long conflictId,
                                                 @RequestBody ReviewDecision decision) {
        validateDecision(decision);
        wikiConflictService.resolve(conflictId, Boolean.TRUE.equals(decision.getAccept()),
                decision.getNote());
        return ResultUtils.success(Boolean.TRUE);
    }

    /** 断言裁定：accept=true 确认（CONFIRMED）/ false 驳回（REJECTED） */
    @PutMapping("/{bookId}/assertion/{assertionId}")
    public BaseResponse<Boolean> resolveAssertion(@PathVariable("bookId") Long bookId,
                                                   @PathVariable("assertionId") Long assertionId,
                                                   @RequestBody ReviewDecision decision) {
        validateDecision(decision);
        pendingAssertionService.resolve(assertionId, Boolean.TRUE.equals(decision.getAccept()));
        return ResultUtils.success(Boolean.TRUE);
    }

    /**
     * 安全裁定：拦截语义不可撤（词表命中即已生效）且审计行不可改（日志不可变原则）——
     * 本端点只补记一条作者决策审计行（AUTHOR_APPROVED/AUTHOR_REJECTED），原 MANUAL_REVIEW 行保留；
     * 队列出队语义由补记行承载（listSafetyReviews 排除已补记者）。
     */
    @PutMapping("/{bookId}/safety/{logId}")
    public BaseResponse<Boolean> resolveSafety(@PathVariable("bookId") Long bookId,
                                               @PathVariable("logId") Long logId,
                                               @RequestBody ReviewDecision decision) {
        validateDecision(decision);
        NovelSafetyLog log = safetyAuditService.getById(logId);
        ThrowsUtils.throwIf(log == null, ErrorCode.NOT_FOUND_ERROR, "安全审计记录不存在");
        boolean approve = Boolean.TRUE.equals(decision.getAccept());
        safetyAuditService.record(bookId, "POST", log.getCategory(),
                approve ? "AUTHOR_APPROVED" : "AUTHOR_REJECTED",
                "logId=" + logId + (decision.getNote() == null ? "" : ";note=" + decision.getNote()));
        return ResultUtils.success(Boolean.TRUE);
    }

    /** 未达标章采纳：终稿已存不动，决策留痕（重走生成由既有创作入口承担——队列是决策入口非执行器） */
    @PutMapping("/{bookId}/unmet/{taskId}")
    public BaseResponse<Boolean> acceptUnmet(@PathVariable("bookId") Long bookId,
                                             @PathVariable("taskId") Long taskId,
                                             @RequestBody(required = false) ReviewDecision decision) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        CreationTask task = creationTaskService.getById(taskId);
        ThrowsUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        // 未达标采纳留痕（同 taskId 幂等：同轮次二次插入仍保留首条语义，队列侧以存在性出队）
        CreationStageArtifact adoption = new CreationStageArtifact();
        adoption.setTaskId(taskId);
        adoption.setStage("REVIEW");
        adoption.setRound(UNMET_ACCEPT_ROUND);
        adoption.setArtifact(Map.of("accepted", true,
                "note", decision == null || decision.getNote() == null ? "作者采纳未达标稿" : decision.getNote()));
        adoption.setTokens(0);
        artifactMapper.insert(adoption);
        return ResultUtils.success(Boolean.TRUE);
    }

    // ------------------------------------------------------------------
    // 数据源查询
    // ------------------------------------------------------------------

    /** 安全人审队列：POST 层 MANUAL_REVIEW（后置 gate 兜底转人审）且未补记作者决策者 */
    private List<NovelSafetyLog> listSafetyReviews(Long bookId) {
        java.util.Set<Long> resolvedIds = new java.util.HashSet<>();
        safetyAuditService.list(new LambdaQueryWrapper<NovelSafetyLog>()
                .eq(NovelSafetyLog::getBookId, bookId)
                .in(NovelSafetyLog::getAction, java.util.List.of("AUTHOR_APPROVED", "AUTHOR_REJECTED")))
                .forEach(row -> {
                    // detail 形如 "logId=123;note=.."：解析原记录 ID
                    if (row != null && row.getDetail() != null && row.getDetail().startsWith("logId=")) {
                        try {
                            String tail = row.getDetail().substring(6);
                            int end = tail.indexOf(';');
                            resolvedIds.add(Long.parseLong(end < 0 ? tail : tail.substring(0, end)));
                        } catch (NumberFormatException ignored) {
                            // 格式异常不阻断队列展示
                        }
                    }
                });
        return safetyAuditService.list(new LambdaQueryWrapper<NovelSafetyLog>()
                .eq(NovelSafetyLog::getBookId, bookId)
                .eq(NovelSafetyLog::getAction, "MANUAL_REVIEW")
                .orderByDesc(NovelSafetyLog::getId))
                .stream()
                .filter(row -> row != null && !resolvedIds.contains(row.getId()))
                .toList();
    }

    /** 未达标章队列：COMPLETED 任务 + ADOPTED_ROUND artifact unmet=true（M3-D6 轮次留账）。
     * 幂等排除：REVIEW/97 采纳行已存在的任务不再出现（决策闭环后出队）。
     */
    private List<CreationTask> listUnmetTasks(Long bookId) {
        List<CreationTask> completed = creationTaskService.list(new LambdaQueryWrapper<CreationTask>()
                .eq(CreationTask::getBookId, bookId)
                .eq(CreationTask::getStatus, "COMPLETED")
                .orderByDesc(CreationTask::getId)
                .last("LIMIT 200"));
        if (completed.isEmpty()) {
            return completed;
        }
        List<Long> taskIds = completed.stream().map(CreationTask::getId).toList();
        List<CreationStageArtifact> adopted = artifactMapper.selectList(
                new LambdaQueryWrapper<CreationStageArtifact>()
                        .in(CreationStageArtifact::getTaskId, taskIds)
                        .eq(CreationStageArtifact::getStage, "CRITIC")
                        .eq(CreationStageArtifact::getRound, ADOPTED_ROUND));
        List<CreationStageArtifact> accepted = artifactMapper.selectList(
                new LambdaQueryWrapper<CreationStageArtifact>()
                        .in(CreationStageArtifact::getTaskId, taskIds)
                        .eq(CreationStageArtifact::getStage, "REVIEW")
                        .eq(CreationStageArtifact::getRound, UNMET_ACCEPT_ROUND));
        java.util.Set<Long> acceptedIds = new java.util.HashSet<>();
        accepted.forEach(row -> acceptedIds.add(row.getTaskId()));
        List<CreationTask> unmets = new ArrayList<>();
        for (CreationStageArtifact row : adopted) {
            if (acceptedIds.contains(row.getTaskId())) {
                continue;
            }
            Object unmet = row.getArtifact() instanceof Map<?, ?> map ? map.get("unmet") : null;
            if (Boolean.TRUE.equals(unmet)) {
                completed.stream()
                        .filter(task -> task != null && task.getId() != null
                                && task.getId().equals(row.getTaskId()))
                        .findFirst()
                        .ifPresent(unmets::add);
            }
        }
        return unmets;
    }

    private static void validateDecision(ReviewDecision decision) {
        ThrowsUtils.throwIf(decision == null || decision.getAccept() == null,
                ErrorCode.PARAMS_ERROR, "accept 不能为空（true=确认/放行，false=驳回/拒绝）");
    }
}
