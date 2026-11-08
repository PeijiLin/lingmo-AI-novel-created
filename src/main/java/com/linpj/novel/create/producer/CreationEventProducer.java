package com.linpj.novel.create.producer;

import com.linpj.novel.create.constant.CreationTopics;
import com.linpj.novel.create.pojo.pipeline.AsyncCreationTaskMessage;
import com.linpj.novel.create.pojo.pipeline.ChapterFinalizedMessage;
import com.linpj.novel.create.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 创作流水线事件生产者（风格对齐现有 EventProducer：JSON 载荷 + 业务键分区）
 *
 * @author HL
 */
@Component
public class CreationEventProducer {

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    /** 投递异步创作任务（key=taskId 保证同任务顺序性） */
    public void sendCreationTask(AsyncCreationTaskMessage message) {
        kafkaTemplate.send(CreationTopics.CREATION_TASK,
                String.valueOf(message.getTaskId()), JsonUtil.toJson(message));
    }

    /** 投递章节定稿事件（key=bookId 保证同书章节顺序性） */
    public void sendChapterFinalized(ChapterFinalizedMessage message) {
        kafkaTemplate.send(CreationTopics.CHAPTER_FINALIZED,
                String.valueOf(message.getBookId()), JsonUtil.toJson(message));
    }
}
