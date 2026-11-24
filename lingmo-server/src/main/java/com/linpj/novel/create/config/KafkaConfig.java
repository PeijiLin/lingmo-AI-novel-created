package com.linpj.novel.create.config;

import com.linpj.novel.create.constant.CreationTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka 主题定义（阶段4：多智能体流水线首个业务化 Kafka 接入）
 *
 * @author HL
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic documentUploadTopic() {
        return new NewTopic("document-upload", 10, (short) 1);
    }

    /** 异步创作任务 */
    @Bean
    public NewTopic creationTaskTopic() {
        return new NewTopic(CreationTopics.CREATION_TASK, 10, (short) 1);
    }

    /** 章节定稿（摘要滚动 + 人物状态抽取） */
    @Bean
    public NewTopic chapterFinalizedTopic() {
        return new NewTopic(CreationTopics.CHAPTER_FINALIZED, 10, (short) 1);
    }
}
