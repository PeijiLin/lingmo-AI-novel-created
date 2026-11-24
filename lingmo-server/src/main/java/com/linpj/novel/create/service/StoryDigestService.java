package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.entity.NovelStoryDigest;

/**
 * 滚动前情摘要服务
 *
 * 更新时机（设计 5.3）：章节内容定稿（用户确认保存）后，LLM 生成章节摘要并与旧摘要
 * 合并滚动更新（阶段3先同步实现，阶段4挪入 Kafka 消费者异步执行）。
 *
 * @author HL
 */
public interface StoryDigestService extends IService<NovelStoryDigest> {

    /** 读取滚动摘要（无记录返回 null） */
    String getDigest(Long bookId);

    /** 覆盖式写入滚动摘要（供阶段3摘要滚动更新 / 阶段4消费者调用） */
    void saveDigest(Long bookId, Long lastChapterId, String summary);
}
