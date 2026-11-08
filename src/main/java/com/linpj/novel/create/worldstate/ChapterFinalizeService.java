package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.agent.ExtractionAgent;
import com.linpj.novel.create.pojo.pipeline.CharacterStateSnapshot;
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

    public ChapterFinalizeService(ExtractionAgent extractionAgent,
                                   StoryDigestService storyDigestService,
                                   CharacterStateService characterStateService,
                                   PlotThreadService plotThreadService,
                                   CharacterCardsService characterCardsService,
                                   RedisTemplate<String, Object> redisTemplate) {
        this.extractionAgent = extractionAgent;
        this.storyDigestService = storyDigestService;
        this.characterStateService = characterStateService;
        this.plotThreadService = plotThreadService;
        this.characterCardsService = characterCardsService;
        this.redisTemplate = redisTemplate;
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

            // 2. 人物动态状态抽取 -> 增量维护 character_state
            ExtractionAgent.ExtractionResult result =
                    extractionAgent.extractStates(cleanContent, renderCharacterList(bookId));
            if (result.snapshots() != null) {
                for (CharacterStateSnapshot snapshot : result.snapshots()) {
                    if (snapshot != null && snapshot.getCharacterId() != null) {
                        characterStateService.updateState(bookId, snapshot.getCharacterId(), chapterId, snapshot);
                    }
                }
            }
            markProcessed(chapterId, content);
            log.info("章节定稿结算完成: bookId={}, chapterId={}, 摘要={}字, 人物状态={}个",
                    bookId, chapterId, digest == null ? 0 : digest.length(),
                    result.snapshots() == null ? 0 : result.snapshots().size());
        } catch (Exception e) {
            log.error("章节定稿结算失败（不影响章节保存）: bookId={}, chapterId={}", bookId, chapterId, e);
        }
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
