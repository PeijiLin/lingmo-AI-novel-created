package com.linpj.novel.create.consumer;

import com.linpj.novel.create.constant.CreationTopics;
import com.linpj.novel.create.pojo.entity.CreationTask;
import com.linpj.novel.create.pojo.pipeline.AsyncCreationTaskMessage;
import com.linpj.novel.create.producer.CreationEventProducer;
import com.linpj.novel.create.service.CreationTaskService;
import com.linpj.novel.create.pipeline.CreationPipelineService;
import com.linpj.novel.create.utils.JsonUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 异步创作任务消费者（novel.creation.task，设计第七节）
 *
 * 幂等：Redisson 分布式锁 + creation_task.status 作幂等栅栏（PENDING -> RUNNING CAS），
 * 重复投递直接 ack 丢弃；进度不走 SSE，前端轮询 GET /pipeline/task/{id}。
 *
 * 断点续跑（单实例语义）：应用重启时，仍处于 RUNNING 的任务视为崩溃遗留，
 * 依据任务表内保存的入参重新投递执行（阶段产物已落库，current_stage 已记录）。
 *
 * @author HL
 */
@Component
public class CreationTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(CreationTaskConsumer.class);

    private static final String LOCK_PREFIX = "novel:creation-task:lock:";

    private final CreationPipelineService creationPipelineService;
    private final CreationTaskService creationTaskService;
    private final CreationEventProducer creationEventProducer;
    private final RedissonClient redissonClient;

    public CreationTaskConsumer(CreationPipelineService creationPipelineService,
                                 CreationTaskService creationTaskService,
                                 CreationEventProducer creationEventProducer,
                                 RedissonClient redissonClient) {
        this.creationPipelineService = creationPipelineService;
        this.creationTaskService = creationTaskService;
        this.creationEventProducer = creationEventProducer;
        this.redissonClient = redissonClient;
    }

    @KafkaListener(topics = {CreationTopics.CREATION_TASK}, groupId = "novel-creation", concurrency = "3")
    public void onCreationTask(List<String> messages, Acknowledgment ack) {
        for (String message : messages) {
            try {
                process(JsonUtil.fromJson(message, AsyncCreationTaskMessage.class));
            } catch (Exception e) {
                // 单条消息处理失败不阻塞批次：任务状态机保持 FAILED 语义由管道自身落库
                log.error("异步创作任务消费失败: {}", message, e);
            }
        }
        ack.acknowledge();
    }

    private void process(AsyncCreationTaskMessage message) {
        if (message == null || message.getTaskId() == null) {
            return;
        }
        Long taskId = message.getTaskId();
        RLock lock = redissonClient.getLock(LOCK_PREFIX + taskId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 60, TimeUnit.SECONDS);
            if (!acquired) {
                log.info("任务 {} 正在被其他消费者处理，重复投递丢弃", taskId);
                return;
            }
            // 幂等栅栏：仅 PENDING 能推进为 RUNNING（CAS），重复投递直接跳过
            if (!creationTaskService.markRunningIfPending(taskId)) {
                log.info("任务 {} 非 PENDING 状态，重复投递丢弃", taskId);
                return;
            }
            log.info("开始执行异步创作任务: taskId={}, mode={}", taskId, message.getMode());
            creationPipelineService.runAsync(taskId, message.toRequest());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }

    /**
     * 断点续跑（单实例语义）：应用重启时，仍为 RUNNING 的任务视为崩溃遗留，重新投递。
     * 重新投递的任务不重置状态（幂等栅栏将放行 CAS）：置回 PENDING 后由消费者接管。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void requeueCrashedTasksOnStartup() {
        try {
            List<CreationTask> crashed = creationTaskService.lambdaQuery()
                    .eq(CreationTask::getStatus, "RUNNING")
                    .list();
            if (crashed == null || crashed.isEmpty()) {
                return;
            }
            for (CreationTask task : crashed) {
                // 依据任务表保存的入参重建消息（params 已在提交时持久化）
                AsyncCreationTaskMessage message = JsonUtil.fromJson(
                        JsonUtil.toJson(task.getParams()), AsyncCreationTaskMessage.class);
                message.setTaskId(task.getId());
                // 置回 PENDING，让幂等栅栏放行重新执行
                creationTaskService.lambdaUpdate()
                        .eq(CreationTask::getId, task.getId())
                        .eq(CreationTask::getStatus, "RUNNING")
                        .set(CreationTask::getStatus, "PENDING")
                        .update();
                creationEventProducer.sendCreationTask(message);
                log.warn("崩溃任务重新入队: taskId={}, mode={}", task.getId(), task.getMode());
            }
        } catch (Exception e) {
            log.error("崩溃任务重启入队失败（不影响应用启动）", e);
        }
    }
}
