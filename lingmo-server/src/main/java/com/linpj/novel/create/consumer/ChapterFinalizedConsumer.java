package com.linpj.novel.create.consumer;

import com.linpj.novel.create.constant.CreationTopics;
import com.linpj.novel.create.pojo.entity.CreateBook;
import com.linpj.novel.create.pojo.pipeline.ChapterFinalizedMessage;
import com.linpj.novel.create.service.CreateBookService;
import com.linpj.novel.create.service.ingest.EmbeddingIndexService;
import com.linpj.novel.create.utils.JsonUtil;
import com.linpj.novel.create.worldstate.ChapterFinalizeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 章节定稿消费者（novel.chapter.finalized，设计第七节）
 *
 * 职责：章节内容定稿（用户确认保存章节）后异步执行——
 *   1. LLM 章节摘要 -> 滚动更新 story_digest
 *   2. LLM 抽取出场人物动态状态 -> 增量维护 character_state
 *   3. 内联伏笔标记补账
 *   4. M2：章节终稿切块回灌向量库（CHAPTER 块，按 bookId+chapterNum 幂等——本书历史片段可检索）
 * 幂等：章节内容 hash（Redis）防重复投递与连续保存；RAG 回灌按章先删后插。
 *
 * @author HL
 */
@Component
public class ChapterFinalizedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChapterFinalizedConsumer.class);

    private final ChapterFinalizeService chapterFinalizeService;
    private final EmbeddingIndexService embeddingIndexService;
    private final CreateBookService createBookService;

    public ChapterFinalizedConsumer(ChapterFinalizeService chapterFinalizeService,
                                     EmbeddingIndexService embeddingIndexService,
                                     CreateBookService createBookService) {
        this.chapterFinalizeService = chapterFinalizeService;
        this.embeddingIndexService = embeddingIndexService;
        this.createBookService = createBookService;
    }

    @KafkaListener(topics = {CreationTopics.CHAPTER_FINALIZED}, groupId = "novel-chapter", concurrency = "3")
    public void onChapterFinalized(List<String> messages, Acknowledgment ack) {
        for (String message : messages) {
            try {
                ChapterFinalizedMessage event = JsonUtil.fromJson(message, ChapterFinalizedMessage.class);
                if (event != null && event.getBookId() != null && event.getContent() != null) {
                    chapterFinalizeService.finalizeChapter(
                            event.getBookId(), event.getChapterId(), event.getTitle(), event.getContent());
                    backfillChapterChunks(event);
                }
            } catch (Exception e) {
                log.error("章节定稿事件消费失败: {}", message, e);
            }
        }
        ack.acknowledge();
    }

    /** M2：终稿切块回灌（失败不阻塞账本主流程——RAG 是投影，缺章可重发补） */
    private void backfillChapterChunks(ChapterFinalizedMessage event) {
        try {
            Integer chapterNum = resolveChapterNum(event.getChapterId());
            if (chapterNum == null) {
                log.warn("章节 RAG 回灌跳过（章号不可得）: bookId={}, chapterId={}",
                        event.getBookId(), event.getChapterId());
                return;
            }
            int chunks = embeddingIndexService.indexChapter(
                    event.getBookId(), chapterNum, event.getContent());
            log.info("章节 RAG 回灌: bookId={}, chapterNum={}, chunks={}",
                    event.getBookId(), chapterNum, chunks);
        } catch (Exception e) {
            log.error("章节 RAG 回灌失败: bookId={}, chapterId={}",
                    event.getBookId(), event.getChapterId(), e);
        }
    }

    /** 章号解析：定稿事件的 chapterId 是 create_book 草稿 id，章号取其 number 字段 */
    private Integer resolveChapterNum(Long chapterId) {
        if (chapterId == null) {
            return null;
        }
        try {
            CreateBook draft = createBookService.getById(chapterId);
            return draft == null ? null : draft.getNumber();
        } catch (Exception e) {
            return null;
        }
    }
}
