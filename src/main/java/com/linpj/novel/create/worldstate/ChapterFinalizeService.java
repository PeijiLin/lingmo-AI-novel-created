package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.agent.ExtractionAgent;
import com.linpj.novel.create.config.PipelineExtractionProperties;
import com.linpj.novel.create.config.PipelineFeatures;
import com.linpj.novel.create.pojo.pipeline.AssertionType;
import com.linpj.novel.create.pojo.pipeline.AliveStatus;
import com.linpj.novel.create.pojo.pipeline.CharacterStateSnapshot;
import com.linpj.novel.create.pojo.pipeline.EventExtraction;
import com.linpj.novel.create.utils.JsonUtil;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CharacterStateService;
import com.linpj.novel.create.service.PlotThreadService;
import com.linpj.novel.create.service.StoryDigestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 章节定稿结算服务（World State 账本维护入口）
 *
 * 时机（设计 5.3）：章节内容定稿（用户确认保存章节）后执行：
 *   1. 内联伏笔标记补账（幂等）
 *   2. LLM 章节摘要 -> 与旧摘要合并滚动更新 story_digest
 *   3. LLM 抽取出场人物动态状态 -> 增量维护 character_state
 *
 * 阶段3 同步实现（不阻塞用户保存主流程的异常兜底）；阶段4 挪入 novel.chapter.finalized
 * Kafka 消费者异步执行，本服务保持业务逻辑不变。
 *
 * 防重：章节内容 hash 未变化则跳过（连续保存 / 消费重复投递均幂等）。
 *
 * @author HL
 */
@Service
public class ChapterFinalizeService {

    private static final Logger log = LoggerFactory.getLogger(ChapterFinalizeService.class);

    private static final String FINALIZE_HASH_KEY = "novel:finalize:hash:";

    private final ExtractionAgent extractionAgent;
    private final StoryDigestService storyDigestService;
    private final CharacterStateService characterStateService;
    private final PlotThreadService plotThreadService;
    private final CharacterCardsService characterCardsService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PipelineExtractionProperties extractionProperties;
    private final PipelineFeatures pipelineFeatures;
    private final WikiConflictService wikiConflictService;
    private final StateHistoryService stateHistoryService;
    private final PendingAssertionService pendingAssertionService;
    private final EventTimelineService eventTimelineService;

    public ChapterFinalizeService(ExtractionAgent extractionAgent,
                                   StoryDigestService storyDigestService,
                                   CharacterStateService characterStateService,
                                   PlotThreadService plotThreadService,
                                   CharacterCardsService characterCardsService,
                                   RedisTemplate<String, Object> redisTemplate,
                                   PipelineExtractionProperties extractionProperties,
                                   PipelineFeatures pipelineFeatures,
                                   WikiConflictService wikiConflictService,
                                   StateHistoryService stateHistoryService,
                                   PendingAssertionService pendingAssertionService,
                                   EventTimelineService eventTimelineService) {
        this.extractionAgent = extractionAgent;
        this.storyDigestService = storyDigestService;
        this.characterStateService = characterStateService;
        this.plotThreadService = plotThreadService;
        this.characterCardsService = characterCardsService;
        this.redisTemplate = redisTemplate;
        this.extractionProperties = extractionProperties;
        this.pipelineFeatures = pipelineFeatures;
        this.wikiConflictService = wikiConflictService;
        this.stateHistoryService = stateHistoryService;
        this.pendingAssertionService = pendingAssertionService;
        this.eventTimelineService = eventTimelineService;
    }

    /**
     * 章节定稿结算（全部失败不影响章节保存本身，仅记录日志）
     *
     * @param bookId    书籍 ID
     * @param chapterId 章节记录 ID
     * @param title     章节标题（可空）
     * @param content   章节正文（定稿内容）
     */
    public void finalizeChapter(Long bookId, Long chapterId, String title, String content) {
        if (bookId == null || content == null || content.isBlank()) {
            return;
        }
        if (isDuplicate(chapterId, content)) {
            log.info("章节内容未变化，跳过定稿结算: bookId={}, chapterId={}", bookId, chapterId);
            return;
        }
        try {
            // 0. 内联伏笔标记补账（幂等：同描述已存在则跳过）
            List<String> marks = plotThreadService.extractInlinePlotMarks(content);
            if (!marks.isEmpty()) {
                plotThreadService.recordInlinePlotMarks(bookId, chapterId, marks);
            }
            String cleanContent = plotThreadService.stripInlinePlotMarks(content);

            // 1. 摘要滚动更新 story_digest
            String existingDigest = storyDigestService.getDigest(bookId);
            String digest = extractionAgent.rollUpDigest(existingDigest, title, cleanContent);
            if (digest != null && !digest.isBlank()) {
                storyDigestService.saveDigest(bookId, chapterId, digest.trim());
            }

            // 2. 统一抽取（人物快照 + 事件时间线，同一次调用）-> 分级入账
            ExtractionAgent.ExtractionResult result =
                    extractionAgent.extractChapter(cleanContent, renderCharacterList(bookId));
            List<CharacterStateSnapshot> snapshots =
                    result.extraction() == null || result.extraction().getSnapshots() == null
                            ? List.of() : result.extraction().getSnapshots();
            for (CharacterStateSnapshot snapshot : snapshots) {
                if (snapshot != null && snapshot.getCharacterId() != null) {
                    if (isLowConfidence(snapshot)) {
                        // 不入账（不进权威层）：快照内容进日志留审计，待人审队列（M1-B4/M3）接管为待审条目
                        log.warn("低置信度抽取不入账（待人审）: bookId={}, chapterId={}, characterId={}, confidence={}, snapshot={}",
                                bookId, chapterId, snapshot.getCharacterId(), snapshot.getConfidence(),
                                JsonUtil.toJson(snapshot));
                        continue;
                    }
                    if (isNonFactQuarantined(snapshot)) {
                        // 非 FACT（推测/传闻/梦境）：隔离进待证实区，不进正典权威层（v2.0 设计 2.3）
                        AssertionType type = AssertionType.parse(snapshot.getAssertionType());
                        pendingAssertionService.record(bookId, chapterId, snapshot.getCharacterId(),
                                type.name(), snapshot);
                        log.info("非 FACT 断言隔离待证实: bookId={}, chapterId={}, characterId={}, type={}",
                                bookId, chapterId, snapshot.getCharacterId(), type);
                        continue;
                    }
                    applyStateWithArbitration(bookId, chapterId, snapshot);
                }
            }

            // 3. 事件入账时间线（含归一化：LLM 出偏移，代码做算术）
            recordEvents(result.extraction() == null ? List.of() : result.extraction().getEvents(),
                    bookId, chapterId);

            // 4. 待证实断言过期清扫（30 章未证实自动归档）
            if (pipelineFeatures.isCredibility()) {
                pendingAssertionService.expireOutdated(bookId, chapterId);
            }

            markProcessed(chapterId, content);
            log.info("章节定稿结算完成: bookId={}, chapterId={}, 摘要={}字, 人物状态={}个",
                    bookId, chapterId, digest == null ? 0 : digest.length(), snapshots.size());
        } catch (Exception e) {
            log.error("章节定稿结算失败（不影响章节保存）: bookId={}, chapterId={}", bookId, chapterId, e);
        }
    }

    // ------------------------------------------------------------------
    // 断言可信度门禁（v2.0 设计 2.3 / M2-C6）与事件入账（C2 承接）
    // ------------------------------------------------------------------

    /**
     * 非 FACT 判定（credibility 部署开关门控）：关闭时全部按 FACT 走现状路径。
     * null/非法标签归 FACT——快照主体字段的错误入账风险低于整章账本跳过，
     * 漂移率由 S6 探针度量后再收紧。
     */
    private boolean isNonFactQuarantined(CharacterStateSnapshot snapshot) {
        if (!pipelineFeatures.isCredibility()) {
            return false;
        }
        return AssertionType.parse(snapshot.getAssertionType()) != AssertionType.FACT;
    }

    /** 事件入账：LLM 结构化偏移映射为 TimeNormalization.Offset（代码做日历运算） */
    private void recordEvents(List<EventExtraction> events, Long bookId, Long chapterId) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (EventExtraction event : events) {
            if (event == null || event.getEventDesc() == null || event.getEventDesc().isBlank()) {
                continue;
            }
            try {
                eventTimelineService.recordEvent(bookId, chapterId, null,
                        event.getNarrativeMode(),
                        toOffset(event),
                        event.getTimeLabel(),
                        chapterId,
                        event.getEventDesc(),
                        event.getLocation(),
                        event.getParticipants());
            } catch (Exception e) {
                // 单事件失败不拖累整章结算（时间线是校验数据源，缺失时可退化偏序校验）
                log.warn("事件入账失败（跳过该事件）: bookId={}, event={}", bookId, event.getEventDesc());
            }
        }
    }

    /** 偏移映射：DAY_OFFSET/FUZZY/ABSOLUTE_LABEL -> Offset；其余（含缺 days）为 null=UNKNOWN 不猜值 */
    private static TimeNormalization.Offset toOffset(EventExtraction event) {
        String kind = event.getTimeKind() == null ? "" : event.getTimeKind().trim().toUpperCase();
        return switch (kind) {
            case "DAY_OFFSET" -> event.getDays() == null ? null
                    : TimeNormalization.Offset.dayOffset(event.getDays());
            case "FUZZY" -> event.getDays() == null ? null
                    : TimeNormalization.Offset.fuzzy(event.getDays());
            case "ABSOLUTE_LABEL" -> TimeNormalization.Offset.absoluteLabel(event.getTimeLabel());
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // 正典冲突仲裁（v2.0 设计 6.4 / M1-B4：冲突不静默覆盖；ruleRouting 部署开关门控）
    // ------------------------------------------------------------------

    /**
     * 快照入账前仲裁（首期落在生死硬字段）：
     * - ALIVE->DEAD 自然演化（R1）：自动生效 + 冲突账本 AUTO_APPLIED 留痕
     * - DEAD->ALIVE 死亡回退（R3）：旧状态保留、新快照不入账，PENDING_REVIEW 待作者仲裁——
     *   复活流/写作错误二选一由人定夺，系统不擅改
     * - ruleRouting 关闭（默认）：直接覆盖，行为与主线一致（现状语义）
     * 其余字段变化（位置/心态等软信息）直接入账不记冲突，避免审计噪音。
     */
    private void applyStateWithArbitration(Long bookId, Long chapterId, CharacterStateSnapshot snapshot) {
        Long characterId = snapshot.getCharacterId();
        if (!pipelineFeatures.isRuleRouting()) {
            characterStateService.updateState(bookId, characterId, chapterId, snapshot);
            if (pipelineFeatures.isTriTimeline()) {
                stateHistoryService.appendFact(bookId, characterId, chapterId, snapshot);
            }
            return;
        }
        AliveStatus previous = characterStateService
                .findAliveStatuses(bookId, List.of(characterId))
                .getOrDefault(characterId, AliveStatus.UNKNOWN);
        AliveStatus incoming = snapshot.getAliveStatus() == null ? AliveStatus.UNKNOWN : snapshot.getAliveStatus();

        if (previous == AliveStatus.DEAD && incoming == AliveStatus.ALIVE) {
            // R3：死亡回退需作者授权——保留旧状态，新快照不入账（宁可阻塞入账也不把“复活”静默写进正典）
            log.warn("死亡回退待审（不入账）: bookId={}, chapterId={}, characterId={}", bookId, chapterId, characterId);
            wikiConflictService.record(bookId, chapterId, characterId,
                    "RESURRECTION_REVIEW", previous.name(), incoming.name(), "PENDING_REVIEW",
                    "抽取快照声明死亡角色存活，待作者仲裁：改文(REJECTED)或确认复活后人工生效(RESOLVED)");
            return;
        }

        characterStateService.updateState(bookId, characterId, chapterId, snapshot);
        if (pipelineFeatures.isTriTimeline()) {
            // C3 三时间轴：投影入账同步追记事实账本（软失效旧区间，可回溯求解）
            stateHistoryService.appendFact(bookId, characterId, chapterId, snapshot);
        }
        if (previous == AliveStatus.ALIVE && incoming == AliveStatus.DEAD) {
            // R1：自然演化自动生效，双向留痕可追溯
            wikiConflictService.record(bookId, chapterId, characterId,
                    "STATE_EVOLVE", previous.name(), incoming.name(), "AUTO_APPLIED",
                    "剧情内自然演化，旧事实置失效自动生效");
        }
    }

    // ------------------------------------------------------------------
    // 抽取置信度门禁（v2.0 设计 2.5）
    // ------------------------------------------------------------------

    /**
     * 低置信度判定：显式低于阈值 → 不入权威层；null（模型未声明）→ 迁移期宽松照常入账。
     * 与全链路哲学一致：宁可少记不可错记，但未声明不能阻断既有账本维护。
     */
    private boolean isLowConfidence(CharacterStateSnapshot snapshot) {
        return snapshot.getConfidence() != null
                && snapshot.getConfidence() < extractionProperties.getConfidenceThreshold();
    }

    // ------------------------------------------------------------------
    // 防重（内容 hash）
    // ------------------------------------------------------------------

    private boolean isDuplicate(Long chapterId, String content) {
        try {
            Object prev = redisTemplate.opsForValue().get(FINALIZE_HASH_KEY + chapterId);
            return sha256(content).equals(prev);
        } catch (Exception e) {
            log.warn("定稿防重检查失败（Redis 异常，按未处理执行）: {}", e.getMessage());
            return false;
        }
    }

    private void markProcessed(Long chapterId, String content) {
        try {
            redisTemplate.opsForValue().set(FINALIZE_HASH_KEY + chapterId, sha256(content), Duration.ofDays(30));
        } catch (Exception e) {
            log.warn("定稿防重标记失败: {}", e.getMessage());
        }
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // 退化 hash：不完美但保持流程可用
            return String.valueOf(content.hashCode());
        }
    }

    // ------------------------------------------------------------------
    // 角色清单渲染（供抽取 Agent 对齐 characterId）
    // ------------------------------------------------------------------

    private String renderCharacterList(Long bookId) {
        try {
            List<CharacterCardsVo> characters = characterCardsService.getCharacterCardsByProjectId(bookId);
            if (characters == null || characters.isEmpty()) {
                return null;
            }
            return characters.stream()
                    .map(c -> "角色ID: " + c.getId() + ", 角色名: " + c.getName())
                    .reduce("", (a, b) -> a + b + "\n");
        } catch (Exception e) {
            log.warn("角色清单加载失败: {}", e.getMessage());
            return null;
        }
    }
}
