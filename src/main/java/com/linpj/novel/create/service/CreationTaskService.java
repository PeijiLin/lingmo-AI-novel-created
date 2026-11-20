package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.entity.CreationTask;

/**
 * 创作任务服务：任务创建、状态推进、token 埋点
 *
 * @author HL
 */
public interface CreationTaskService extends IService<CreationTask> {

    /** 创建 RUNNING 任务记录（同步 SSE 模式），携带管道入参快照（mode/轮数/机制开关），返回任务 ID */
    Long createRunningTask(Long bookId, Long chapterId, String mode, Object params);

    /** 创建 PENDING 任务记录（异步 Kafka 模式，幂等栅栏初值），携带完整入参 */
    Long createPendingTask(Long bookId, Long chapterId, String mode, Object params);

    /** PENDING -> RUNNING 原子推进（幂等栅栏：仅首次消费成功，重复投递返回 false） */
    boolean markRunningIfPending(Long taskId);

    /** 任务完成（含降级标记与用量统计） */
    void completeTask(Long taskId, boolean degraded, Integer totalTokens, Long elapsedMs);

    /**
     * 任务失败（不改动 current_stage：保留最后推进到的阶段，供断点续跑定位）
     */
    void failTask(Long taskId, String error);

    /** 推进当前阶段（断点续跑预留） */
    void advanceStage(Long taskId, String currentStage);
}
