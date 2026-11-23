package com.linpj.novel.create.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.exception.BusinessException;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 人审队列接口测试（M6-6 人机协同卡点最小闭环：四类卡点成队 + 分发处置写回对应服务）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewQueueControllerTest {

    @Mock private WikiConflictService wikiConflictService;
    @Mock private PendingAssertionService pendingAssertionService;
    @Mock private SafetyAuditService safetyAuditService;
    @Mock private CreationTaskService creationTaskService;
    @Mock private CreationStageArtifactMapper artifactMapper;

    private ReviewQueueController controller;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelSafetyLog.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                CreationStageArtifact.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                CreationTask.class);
    }

    @BeforeEach
    void setUp() {
        controller = new ReviewQueueController(wikiConflictService, pendingAssertionService,
                safetyAuditService, creationTaskService, artifactMapper);
        lenient().when(wikiConflictService.listPendingReview(1L)).thenReturn(List.of());
        lenient().when(pendingAssertionService.listPending(1L)).thenReturn(List.of());
        lenient().when(safetyAuditService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of());
        lenient().when(creationTaskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of());
    }

    private ReviewQueueController.ReviewDecision decision(boolean accept, String note) {
        ReviewQueueController.ReviewDecision decision = new ReviewQueueController.ReviewDecision();
        decision.setAccept(accept);
        decision.setNote(note);
        return decision;
    }

    // ------------------------------------------------------------------
    // 总览
    // ------------------------------------------------------------------

    @Test
    void 总览含四类计数与数据() {
        when(wikiConflictService.listPendingReview(1L)).thenReturn(List.of(conflict(7L)));
        when(pendingAssertionService.listPending(1L)).thenReturn(List.of(assertion(9L)));

        BaseResponse<Map<String, Object>> response = controller.overview(1L);

        Map<?, ?> counts = (Map<?, ?>) response.getData().get("counts");
        assertEquals(1, counts.get("conflict"));
        assertEquals(1, counts.get("assertion"));
        assertEquals(0, counts.get("safety"));
        assertEquals(0, counts.get("unmet"));
    }

    @Test
    void 分项清单路由() {
        when(wikiConflictService.listPendingReview(1L)).thenReturn(List.of(conflict(7L)));
        assertEquals(1, controller.listByType(1L, "conflict").getData().size());
        assertEquals(1, controller.listByType(1L, "CONFLICT").getData().size(), "大小写不敏感");
        assertThrows(BusinessException.class, () -> controller.listByType(1L, "unknown"));
    }

    // ------------------------------------------------------------------
    // 处置：各走单一写入者通道
    // ------------------------------------------------------------------

    @Test
    void 冲突仲裁确认走冲突服务() {
        controller.resolveConflict(1L, 7L, decision(true, "确认复活"));
        verify(wikiConflictService).resolve(7L, true, "确认复活");
    }

    @Test
    void 断言裁定驳回走断言服务() {
        controller.resolveAssertion(1L, 9L, decision(false, null));
        verify(pendingAssertionService).resolve(9L, false);
    }

    @Test
    void 安全裁定补记审计不修改原行() {
        NovelSafetyLog log = safetyLog(5L, "SEXUAL");
        when(safetyAuditService.getById(5L)).thenReturn(log);

        controller.resolveSafety(1L, 5L, decision(false, "确实违规"));

        // 审计行不可变：只补记一条 AUTHOR_REJECTED，不 update 原行
        verify(safetyAuditService, never()).updateById(any(NovelSafetyLog.class));
        verify(safetyAuditService).record(eq(1L), eq("POST"), eq("SEXUAL"),
                eq("AUTHOR_REJECTED"), eq("logId=5;note=确实违规"));
    }

    @Test
    void 安全审计记录不存在抛404() {
        when(safetyAuditService.getById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> controller.resolveSafety(1L, 404L, decision(true, null)));
    }

    @Test
    void 未达标章采纳落REVIEW轮次artifact() {
        CreationTask task = new CreationTask();
        task.setId(33L);
        task.setBookId(1L);
        task.setStatus("COMPLETED");
        when(creationTaskService.getById(33L)).thenReturn(task);

        controller.acceptUnmet(1L, 33L, decision(true, "接受这一稿"));

        ArgumentCaptor<CreationStageArtifact> captor = ArgumentCaptor.forClass(CreationStageArtifact.class);
        verify(artifactMapper).insert(captor.capture());
        assertEquals("REVIEW", captor.getValue().getStage());
        assertEquals(97, captor.getValue().getRound(), "采纳留痕轮次（出队幂等标记）");
        assertEquals(0, captor.getValue().getTokens());
    }

    @Test
    void 未达标章任务不存在抛404() {
        when(creationTaskService.getById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> controller.acceptUnmet(1L, 404L, decision(true, null)));
    }

    // ------------------------------------------------------------------
    // 未达标队列装载（unmet=true 且未采纳）
    // ------------------------------------------------------------------

    @Test
    void 未达标队列_采纳后的任务出队() {
        CreationTask unmetTask = completedTask(33L, true);
        CreationTask acceptedTask = completedTask(44L, true);
        CreationTask cleanTask = completedTask(55L, false);
        when(creationTaskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(unmetTask, acceptedTask, cleanTask));
        // 两次查询按调用顺序：先行 adopted 查询再 accepted 查询（mock 无视 wrapper 条件，需顺序 stub）
        when(artifactMapper.selectList(any()))
                .thenReturn(List.of(adoptedRow(33L, true), adoptedRow(44L, true), adoptedRow(55L, false)))
                .thenReturn(List.of(acceptedRow(44L)));

        BaseResponse<List<?>> response = controller.listByType(1L, "unmet");

        assertEquals(1, response.getData().size(), "只有 33：44 已采纳出队、55 本就达标");
        assertEquals(33L, ((CreationTask) response.getData().get(0)).getId());
    }

    @Test
    void 决策体accept必填() {
        ReviewQueueController.ReviewDecision invalid = new ReviewQueueController.ReviewDecision();
        assertThrows(BusinessException.class, () -> controller.resolveConflict(1L, 7L, invalid));
        assertThrows(BusinessException.class, () -> controller.resolveConflict(1L, 7L, null));
    }

    // ------------------------------------------------------------------
    // 安全队列装载：已补记决策的 MANUAL_REVIEW 不再出现
    // ------------------------------------------------------------------

    @Test
    void 安全队列_已裁定记录出队() {
        NovelSafetyLog pending = safetyLog(5L, "SEXUAL");
        pending.setAction("MANUAL_REVIEW");
        NovelSafetyLog resolved = safetyLog(6L, "VIOLENCE");
        resolved.setAction("MANUAL_REVIEW");
        NovelSafetyLog decisionRow = safetyLog(99L, "VIOLENCE");
        decisionRow.setAction("AUTHOR_REJECTED");
        decisionRow.setDetail("logId=6;note=xxx");
        // 第一次调用（决策行查询）与第二次调用（队列查询）分别返回
        when(safetyAuditService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(decisionRow))
                .thenReturn(List.of(pending, resolved));

        BaseResponse<List<?>> response = controller.listByType(1L, "safety");

        assertEquals(1, response.getData().size(), "logId=6 已补记决策出队，仅剩 5 待审");
        assertEquals(5L, ((NovelSafetyLog) response.getData().get(0)).getId());
    }

    // ------------------------------------------------------------------
    // 数据构造
    // ------------------------------------------------------------------

    private NovelWikiConflict conflict(Long id) {
        NovelWikiConflict conflict = new NovelWikiConflict();
        conflict.setId(id);
        conflict.setBookId(1L);
        conflict.setConflictKind("RESURRECTION_REVIEW");
        conflict.setStatus("PENDING_REVIEW");
        return conflict;
    }

    private NovelPendingAssertion assertion(Long id) {
        NovelPendingAssertion assertion = new NovelPendingAssertion();
        assertion.setId(id);
        assertion.setBookId(1L);
        assertion.setAssertionType("HEARSAY");
        assertion.setStatus("PENDING");
        return assertion;
    }

    private NovelSafetyLog safetyLog(Long id, String category) {
        NovelSafetyLog log = new NovelSafetyLog();
        log.setId(id);
        log.setBookId(1L);
        log.setLayer("POST");
        log.setCategory(category);
        return log;
    }

    private CreationTask completedTask(Long id, boolean unmet) {
        CreationTask task = new CreationTask();
        task.setId(id);
        task.setBookId(1L);
        task.setStatus("COMPLETED");
        return task;
    }

    private CreationStageArtifact adoptedRow(Long taskId, boolean unmet) {
        CreationStageArtifact row = new CreationStageArtifact();
        row.setTaskId(taskId);
        row.setStage("CRITIC");
        row.setRound(96);
        row.setArtifact(unmet ? Map.of("unmet", true) : Map.of("unmet", false));
        return row;
    }

    private CreationStageArtifact acceptedRow(Long taskId) {
        CreationStageArtifact row = new CreationStageArtifact();
        row.setTaskId(taskId);
        row.setStage("REVIEW");
        row.setRound(97);
        row.setArtifact(Map.of("accepted", true));
        return row;
    }
}
