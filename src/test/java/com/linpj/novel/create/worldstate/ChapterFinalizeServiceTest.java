package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.agent.ExtractionAgent;
import com.linpj.novel.create.config.PipelineExtractionProperties;
import com.linpj.novel.create.config.PipelineFeatures;
import com.linpj.novel.create.pojo.pipeline.AliveStatus;
import com.linpj.novel.create.pojo.pipeline.ChapterExtraction;
import com.linpj.novel.create.pojo.pipeline.CharacterStateSnapshot;
import com.linpj.novel.create.pojo.pipeline.EventExtraction;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CharacterStateService;
import com.linpj.novel.create.service.PlotThreadService;
import com.linpj.novel.create.service.StoryDigestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 章节定稿结算服务单元测试（幂等防重 + 全失败不影响章节保存）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChapterFinalizeServiceTest {

    @Mock private ExtractionAgent extractionAgent;
    @Mock private StoryDigestService storyDigestService;
    @Mock private CharacterStateService characterStateService;
    @Mock private PlotThreadService plotThreadService;
    @Mock private CharacterCardsService characterCardsService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private WikiConflictService wikiConflictService;
    @Mock private StateHistoryService stateHistoryService;
    @Mock private PendingAssertionService pendingAssertionService;
    @Mock private EventTimelineService eventTimelineService;
    @Mock private SummaryTreeService summaryTreeService;

    private ChapterFinalizeService service;
    private PipelineExtractionProperties extractionProperties;
    private final PipelineFeatures pipelineFeatures = new PipelineFeatures();

    @BeforeEach
    void setUp() {
        extractionProperties = new PipelineExtractionProperties();
        extractionProperties.setConfidenceThreshold(0.6);
        service = new ChapterFinalizeService(extractionAgent, storyDigestService,
                characterStateService, plotThreadService, characterCardsService, redisTemplate,
                extractionProperties, pipelineFeatures, wikiConflictService, stateHistoryService,
                pendingAssertionService, eventTimelineService, summaryTreeService);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private CharacterStateSnapshot snapshot(Long characterId) {
        return snapshot(characterId, 0.9);
    }

    private CharacterStateSnapshot snapshot(Long characterId, Double confidence) {
        CharacterStateSnapshot snapshot = new CharacterStateSnapshot();
        snapshot.setCharacterId(characterId);
        snapshot.setLocation("城墙");
        snapshot.setMindset("紧绷");
        snapshot.setConfidence(confidence);
        return snapshot;
    }

    private ChapterExtraction extraction(List<CharacterStateSnapshot> snapshots) {
        ChapterExtraction extraction = new ChapterExtraction();
        extraction.setSnapshots(snapshots);
        extraction.setEvents(List.of());
        return extraction;
    }

    private ExtractionAgent.ExtractionResult extractionResult(List<CharacterStateSnapshot> snapshots) {
        return new ExtractionAgent.ExtractionResult(extraction(snapshots), 80L);
    }

    private void stubExtraction(List<CharacterStateSnapshot> snapshots) {
        when(plotThreadService.extractInlinePlotMarks(anyString())).thenReturn(List.of());
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("摘要");
        when(extractionAgent.extractChapter(any(), any(), any())).thenReturn(extractionResult(snapshots));
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());
    }

    @Test
    void lowConfidenceSnapshotIsQuarantined() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(plotThreadService.extractInlinePlotMarks(anyString())).thenReturn(List.of());
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("摘要");
        when(extractionAgent.extractChapter(any(), any(), any())).thenReturn(
                extractionResult(List.of(snapshot(1L, 0.3), snapshot(2L, 0.9), snapshot(3L, null))));
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());

        service.finalizeChapter(1L, 10L, "第一章", "章节正文");

        // 低置信度不入权威层；达标与未声明均照常入账（迁移期宽松语义）
        verify(characterStateService, never()).updateState(eq(1L), eq(1L), anyLong(), any());
        verify(characterStateService).updateState(eq(1L), eq(2L), eq(10L), any());
        verify(characterStateService).updateState(eq(1L), eq(3L), eq(10L), any());
    }

    @Test
    void confidenceThresholdIsConfigurable() {
        extractionProperties.setConfidenceThreshold(0.95);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(plotThreadService.extractInlinePlotMarks(anyString())).thenReturn(List.of());
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("摘要");
        when(extractionAgent.extractChapter(any(), any(), any())).thenReturn(
                extractionResult(List.of(snapshot(1L, 0.7))));
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());

        service.finalizeChapter(1L, 10L, "第一章", "章节正文");

        // 阈值调高后 0.7 被隔离：阈值待金标准校准（设计 2.5），门禁必须随之可调
        verify(characterStateService, never()).updateState(anyLong(), anyLong(), anyLong(), any());
    }

    // ------------------------------------------------------------------
    // 正典冲突仲裁（v2.0 M1-B4：R1 自动生效留痕 / R3 死亡回退待审）
    // ------------------------------------------------------------------

    @Test
    void ruleRoutingOnNaturalEvolutionAutoAppliesAndRecords() {
        pipelineFeatures.setRuleRouting(true);
        when(valueOperations.get(anyString())).thenReturn(null);
        CharacterStateSnapshot dying = snapshot(1L);
        dying.setAliveStatus(AliveStatus.DEAD);
        stubExtraction(List.of(dying));
        when(characterStateService.findAliveStatuses(eq(1L), any()))
                .thenReturn(Map.of(1L, AliveStatus.ALIVE));

        service.finalizeChapter(1L, 10L, "第一章", "章节正文");

        // R1：自然演化自动生效 + 双向留痕
        verify(characterStateService).updateState(eq(1L), eq(1L), eq(10L), any());
        verify(wikiConflictService).record(eq(1L), eq(10L), eq(1L),
                eq("STATE_EVOLVE"), eq("ALIVE"), eq("DEAD"), eq("AUTO_APPLIED"), anyString());
    }

    @Test
    void ruleRoutingOnResurrectionIsHeldForReview() {
        pipelineFeatures.setRuleRouting(true);
        when(valueOperations.get(anyString())).thenReturn(null);
        CharacterStateSnapshot aliveAgain = snapshot(1L);
        aliveAgain.setAliveStatus(AliveStatus.ALIVE);
        stubExtraction(List.of(aliveAgain));
        when(characterStateService.findAliveStatuses(eq(1L), any()))
                .thenReturn(Map.of(1L, AliveStatus.DEAD));

        service.finalizeChapter(1L, 10L, "第一章", "章节正文");

        // R3：死而复生不入账（保留旧 DEAD），待人审仲裁
        verify(characterStateService, never()).updateState(anyLong(), anyLong(), anyLong(), any());
        verify(wikiConflictService).record(eq(1L), eq(10L), eq(1L),
                eq("RESURRECTION_REVIEW"), eq("DEAD"), eq("ALIVE"), eq("PENDING_REVIEW"), anyString());
    }

    @Test
    void ruleRoutingOffResurrectionKeepsLegacyOverwriteBehavior() {
        when(valueOperations.get(anyString())).thenReturn(null);
        CharacterStateSnapshot aliveAgain = snapshot(1L);
        aliveAgain.setAliveStatus(AliveStatus.ALIVE);
        stubExtraction(List.of(aliveAgain));

        service.finalizeChapter(1L, 10L, "第一章", "章节正文");

        // 关开关（默认）：直接覆盖，不仲裁不记账——行为与主线一致
        verify(characterStateService).updateState(eq(1L), eq(1L), eq(10L), any());
        verify(wikiConflictService, never()).record(anyLong(), anyLong(), anyLong(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unknownPreviousStateAppliesWithoutConflictRecord() {
        pipelineFeatures.setRuleRouting(true);
        when(valueOperations.get(anyString())).thenReturn(null);
        CharacterStateSnapshot fresh = snapshot(1L);
        fresh.setAliveStatus(AliveStatus.ALIVE);
        stubExtraction(List.of(fresh));
        when(characterStateService.findAliveStatuses(eq(1L), any()))
                .thenReturn(Map.of(1L, AliveStatus.UNKNOWN));

        service.finalizeChapter(1L, 10L, "第一章", "章节正文");

        // 无先前状态（首章/旧数据）：UNKNOWN 不触发死亡回退，直接入账不记冲突（审计噪音控制）
        verify(characterStateService).updateState(eq(1L), eq(1L), eq(10L), any());
        verify(wikiConflictService, never()).record(anyLong(), anyLong(), anyLong(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void conflictRecordFailureDoesNotBlockStateUpdate() {
        pipelineFeatures.setRuleRouting(true);
        when(valueOperations.get(anyString())).thenReturn(null);
        CharacterStateSnapshot dying = snapshot(1L);
        dying.setAliveStatus(AliveStatus.DEAD);
        stubExtraction(List.of(dying));
        when(characterStateService.findAliveStatuses(eq(1L), any()))
                .thenReturn(Map.of(1L, AliveStatus.ALIVE));
        doThrow(new RuntimeException("账本库不可用"))
                .when(wikiConflictService).record(anyLong(), anyLong(), anyLong(), anyString(),
                        anyString(), anyString(), anyString(), anyString());

        // 冲突记账是审计能力：故障不反阻塞状态入账
        assertDoesNotThrow(() -> service.finalizeChapter(1L, 10L, "第一章", "章节正文"));
        verify(characterStateService).updateState(eq(1L), eq(1L), eq(10L), any());
    }

    // ------------------------------------------------------------------
    // 断言可信度分级与事件入账（v2.0 M2-C6 / C2 承接）
    // ------------------------------------------------------------------

    @Test
    void credibilityOn非FACT断言隔离不进权威层() {
        pipelineFeatures.setCredibility(true);
        when(valueOperations.get(anyString())).thenReturn(null);
        CharacterStateSnapshot hearsay = snapshot(1L);
        hearsay.setAssertionType("HEARSAY");
        CharacterStateSnapshot fact = snapshot(2L);
        fact.setAssertionType("FACT");
        stubExtraction(List.of(hearsay, fact));

        service.finalizeChapter(1L, 10L, "第一章", "章节正文");

        // HEARSAY 隔离待证实区；FACT 照常入账
        verify(characterStateService, never()).updateState(eq(1L), eq(1L), anyLong(), any());
        verify(pendingAssertionService).record(eq(1L), eq(10L), eq(1L), eq("HEARSAY"), any());
        verify(characterStateService).updateState(eq(1L), eq(2L), eq(10L), any());
        // credibility 开启时执行 30 章过期清扫
        verify(pendingAssertionService).expireOutdated(1L, 10L);
    }

    @Test
    void credibilityOff非FACT标签照常入账为现状行为() {
        when(valueOperations.get(anyString())).thenReturn(null);
        CharacterStateSnapshot hearsay = snapshot(1L);
        hearsay.setAssertionType("HEARSAY");
        stubExtraction(List.of(hearsay));

        service.finalizeChapter(1L, 10L, "第一章", "章节正文");

        // 关开关（默认）：标签被忽略，全部按现状入账；不产生隔离与清扫
        verify(characterStateService).updateState(eq(1L), eq(1L), eq(10L), any());
        verify(pendingAssertionService, never()).record(anyLong(), anyLong(), anyLong(), anyString(), any());
        verify(pendingAssertionService, never()).expireOutdated(anyLong(), anyLong());
    }

    @Test
    void 事件入账调用时间线并做偏移映射() {
        pipelineFeatures.setCredibility(true);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(plotThreadService.extractInlinePlotMarks(anyString())).thenReturn(List.of());
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("摘要");
        EventExtraction relative = new EventExtraction();
        relative.setEventDesc("林砚抵达王都");
        relative.setLocation("王都");
        relative.setParticipants(List.of(1L));
        relative.setNarrativeMode("NORMAL");
        relative.setTimeKind("DAY_OFFSET");
        relative.setDays(3);
        relative.setCausedByEventId(3L);
        EventExtraction noTime = new EventExtraction();
        noTime.setEventDesc("顾昭拜会旧友");
        noTime.setTimeKind(null);
        ChapterExtraction extraction = new ChapterExtraction();
        extraction.setSnapshots(List.of());
        extraction.setEvents(java.util.Arrays.asList(relative, noTime));
        when(extractionAgent.extractChapter(any(), any(), any()))
                .thenReturn(new ExtractionAgent.ExtractionResult(extraction, 80L));
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());

        service.finalizeChapter(1L, 10L, "第一章", "章节正文");

        // DAY_OFFSET 映射为结构化偏移（时间运算在 TimeNormalization/时间线侧完成）；因果引用透传（B1-2）
        verify(eventTimelineService).recordEvent(eq(1L), eq(10L), org.mockito.ArgumentMatchers.isNull(),
                eq("NORMAL"), eq(TimeNormalization.Offset.dayOffset(3)),
                org.mockito.ArgumentMatchers.isNull(), eq(10L), eq("林砚抵达王都"), eq("王都"), eq(List.of(1L)),
                eq(3L));
        // 无时间信息：偏移 null（UNKNOWN 语义），事件仍入账供偏序校验
        verify(eventTimelineService).recordEvent(eq(1L), eq(10L), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), eq(10L), eq("顾昭拜会旧友"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void finalizeChapterUpdatesDigestAndCharacterStates() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(plotThreadService.extractInlinePlotMarks(anyString())).thenReturn(List.of("新伏笔"));
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("滚动摘要");
        when(extractionAgent.extractChapter(any(), any(), any()))
                .thenReturn(extractionResult(List.of(snapshot(1L), snapshot(2L))));
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());

        service.finalizeChapter(1L, 10L, "第一章", "章节正文");

        // 1. 内联伏笔补账
        verify(plotThreadService).recordInlinePlotMarks(1L, 10L, List.of("新伏笔"));
        // 2. 摘要滚动更新
        verify(storyDigestService).saveDigest(1L, 10L, "滚动摘要");
        // 3. 人物状态增量维护
        verify(characterStateService).updateState(eq(1L), eq(1L), eq(10L), any());
        verify(characterStateService).updateState(eq(1L), eq(2L), eq(10L), any());
        // 4. 防重标记写入
        verify(valueOperations).set(contains("10"), anyString(), any(Duration.class));
    }

    @Test
    void duplicateContentSkipsSettlement() throws Exception {
        String content = "章节正文";
        String hash = sha256(content);
        when(valueOperations.get(anyString())).thenReturn(hash);

        service.finalizeChapter(1L, 10L, "第一章", content);

        // 内容 hash 未变化：跳过全部结算
        verify(extractionAgent, never()).rollUpDigest(any(), any(), any());
        verify(extractionAgent, never()).extractChapter(any(), any(), any());
        verify(storyDigestService, never()).saveDigest(anyLong(), anyLong(), anyString());
        verify(characterStateService, never()).updateState(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void redisFailureDoesNotBlockSettlement() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis 不可用"));
        when(plotThreadService.extractInlinePlotMarks(anyString())).thenReturn(List.of());
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("滚动摘要");
        when(extractionAgent.extractChapter(any(), any(), any())).thenReturn(extractionResult(List.of()));
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.finalizeChapter(1L, 10L, "第一章", "章节正文"));

        verify(storyDigestService).saveDigest(eq(1L), eq(10L), eq("滚动摘要"));
    }

    @Test
    void extractionFailureDoesNotPropagate() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(plotThreadService.extractInlinePlotMarks(anyString())).thenReturn(List.of());
        when(extractionAgent.rollUpDigest(any(), any(), any()))
                .thenThrow(new RuntimeException("LLM 超时"));

        // 定稿结算全部失败不影响章节保存本身
        assertDoesNotThrow(() -> service.finalizeChapter(1L, 10L, "第一章", "章节正文"));
        verify(characterStateService, never()).updateState(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void blankOrNullContentIsIgnored() {
        service.finalizeChapter(1L, 10L, "第一章", null);
        service.finalizeChapter(1L, 10L, "第一章", "  ");
        service.finalizeChapter(null, 10L, "第一章", "正文");

        verify(extractionAgent, never()).rollUpDigest(any(), any(), any());
        verify(valueOperations, times(0)).set(anyString(), any(), any(Duration.class));
    }

    private static String sha256(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    // ------------------------------------------------------------------
    // summaryTree 开关分流（M5-B4：开=三层树+派生写回；关=滚动合并现状行为）
    // ------------------------------------------------------------------

    private void stubTreeBasics(String chapterDigest) {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(plotThreadService.extractInlinePlotMarks(anyString())).thenReturn(List.of());
        // 透传桩：cleanContent 为主线语义（stripInlinePlotMarks 的标记剥离逻辑在 PlotThreadServiceImplTest 覆盖）
        when(plotThreadService.stripInlinePlotMarks(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(extractionAgent.summarizeChapter(anyString(), anyString())).thenReturn(chapterDigest);
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("滚动摘要不该被调用");
        when(extractionAgent.extractChapter(any(), any(), any()))
                .thenReturn(extractionResult(List.of()));
        when(characterCardsService.getCharacterCardsByProjectId(1L)).thenReturn(List.of());
    }

    @Test
    void summaryTree开_章节详摘入账并派生滚动摘要() {
        pipelineFeatures.setSummaryTree(true);
        stubTreeBasics("第十章详摘");
        when(summaryTreeService.recordChapterDigest(1L, 10L, "第十章详摘")).thenReturn(10);
        when(summaryTreeService.loadChapterDigestsOfSegment(1L, 0)).thenReturn(List.of("第十章详摘"));
        when(extractionAgent.compressDigests(any())).thenReturn("段零中摘");
        // 章 10 非 10 的倍数余 0 边界？10%10==0 触发书重算——本用例覆盖段边界全链
        when(summaryTreeService.nodeExists(1L, "BOOK", 0)).thenReturn(false);
        when(summaryTreeService.loadSegmentDigests(1L)).thenReturn(List.of("段零中摘"));
        when(summaryTreeService.renderTreeDigest(1L, 10L)).thenReturn("【全书概览】段零中摘……");

        service.finalizeChapter(1L, 10L, "第十章", "章节正文");

        // 树链路：章详摘 -> 段重算 -> （段边界）书重算 -> 派生写回
        verify(extractionAgent).summarizeChapter("第十章", "章节正文");
        verify(summaryTreeService).recordChapterDigest(1L, 10L, "第十章详摘");
        verify(summaryTreeService).upsertNode(1L, "SEGMENT", 0, "段零中摘");
        verify(summaryTreeService).upsertNode(1L, "BOOK", 0, "段零中摘");
        verify(storyDigestService).saveDigest(1L, 10L, "【全书概览】段零中摘……");
        // 滚动链路退役：rollUpDigest 零调用（等成本换结构——省下的钱换成了树节点）
        verify(extractionAgent, never()).rollUpDigest(any(), any(), any());
    }

    @Test
    void summaryTree开_非段边界不重算全书短摘() {
        pipelineFeatures.setSummaryTree(true);
        stubTreeBasics("第十一章详摘");
        when(summaryTreeService.recordChapterDigest(1L, 10L, "第十一章详摘")).thenReturn(11);
        when(summaryTreeService.loadChapterDigestsOfSegment(1L, 1)).thenReturn(List.of("第十一章详摘"));
        when(extractionAgent.compressDigests(any())).thenReturn("段一中摘");
        when(summaryTreeService.nodeExists(1L, "BOOK", 0)).thenReturn(true); // 已有书短摘
        when(summaryTreeService.renderTreeDigest(1L, 10L)).thenReturn("派生摘要");

        service.finalizeChapter(1L, 10L, "第十一章", "章节正文");

        // 章 11 非边界且书短摘已存在：不触发书重算（每 10 章一次的成本约束）
        verify(summaryTreeService, never()).loadSegmentDigests(1L);
        verify(summaryTreeService, never()).upsertNode(any(), eq("BOOK"), any(), any());
    }

    @Test
    void summaryTree开_章号不可定位回退滚动路径() {
        pipelineFeatures.setSummaryTree(true);
        stubTreeBasics("详摘");
        when(summaryTreeService.recordChapterDigest(1L, 10L, "详摘")).thenReturn(null);
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("滚动摘要");

        service.finalizeChapter(1L, 10L, "第十章", "章节正文");

        verify(extractionAgent).rollUpDigest(any(), any(), any());
        verify(storyDigestService).saveDigest(1L, 10L, "滚动摘要");
    }

    @Test
    void summaryTree开_树维护异常回退滚动路径() {
        pipelineFeatures.setSummaryTree(true);
        stubTreeBasics("详摘");
        when(summaryTreeService.recordChapterDigest(1L, 10L, "详摘"))
                .thenThrow(new RuntimeException("树库不可用"));
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("滚动摘要");

        service.finalizeChapter(1L, 10L, "第十章", "章节正文");

        // 树是能力不是闸门：树路径故障回退滚动摘要，记忆能力优先
        verify(extractionAgent).rollUpDigest(any(), any(), any());
        verify(storyDigestService).saveDigest(1L, 10L, "滚动摘要");
    }

    @Test
    void summaryTree关_零树交互现状行为不变() {
        // 开关关（默认）：树服务零交互，滚动合并逐字节现状
        stubTreeBasics("不该被用到");
        when(extractionAgent.rollUpDigest(any(), any(), any())).thenReturn("滚动摘要");
        when(storyDigestService.getDigest(1L)).thenReturn("旧摘要");

        service.finalizeChapter(1L, 10L, "第十章", "章节正文");

        org.mockito.Mockito.verifyNoInteractions(summaryTreeService);
        verify(extractionAgent, never()).summarizeChapter(any(), any());
        verify(extractionAgent).rollUpDigest("旧摘要", "第十章", "章节正文");
    }
}
