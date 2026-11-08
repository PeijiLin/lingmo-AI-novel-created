package com.linpj.novel.create.consumer;

import com.linpj.novel.create.constant.CreationTopics;
import com.linpj.novel.create.pojo.pipeline.ChapterFinalizedMessage;
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
 * 幂等：章节内容 hash（Redis）防重复投递与连续保存。
 *
 * @author HL
 */
@Component
public class ChapterFinalizedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChapterFinalizedConsumer.class);

    private final ChapterFinalizeService chapterFinalizeService;

    public ChapterFinalizedConsumer(ChapterFinalizeService chapterFinalizeService) {
        this.chapterFinalizeService = chapterFinalizeService;
    }

    @KafkaListener(topics = {CreationTopics.CHAPTER_FINALIZED}, groupId = "novel-chapter", concurrency = "3")
    public void onChapterFinalized(List<String> messages, Acknowledgment ack) {
        for (String message : messages) {
            try {
                ChapterFinalizedMessage event = JsonUtil.fromJson(message, ChapterFinalizedMessage.class);
                if (event != null && event.getBookId() != null && event.getContent() != null) {
                    chapterFinalizeService.finalizeChapter(
                            event.getBookId(), event.getChapterId(), event.getTitle(), event.getContent());
                }
            } catch (Exception e) {
                log.error("章节定稿事件消费失败: {}", message, e);
            }
        }
        ack.acknowledge();
    }
}
