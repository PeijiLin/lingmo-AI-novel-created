package com.linpj.novel.create.constant;

/**
 * 多智能体创作流水线 Kafka 主题（阶段4：Kafka 首个业务化接入）
 *
 * @author HL
 */
public final class CreationTopics {

    /** 整章/批量生成异步任务（消费者调用编排器异步跑管道，task 状态机推进） */
    public static final String CREATION_TASK = "novel.creation.task";

    /** 章节内容定稿（消费者生成章节摘要滚动更新 story_digest + 抽取人物状态补账） */
    public static final String CHAPTER_FINALIZED = "novel.chapter.finalized";

    private CreationTopics() {
    }
}
