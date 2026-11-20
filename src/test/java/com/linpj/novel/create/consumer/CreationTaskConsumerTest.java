package com.linpj.novel.create.consumer;

import com.linpj.novel.create.pipeline.CreationPipelineService;
import com.linpj.novel.create.pojo.pipeline.AsyncCreationTaskMessage;
import com.linpj.novel.create.pojo.pipeline.CreationMode;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.producer.CreationEventProducer;
import com.linpj.novel.create.service.CreationTaskService;
import com.linpj.novel.create.utils.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异步创作任务消费者单元测试（设计第七节：幂等栅栏 + 分布式锁）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
class CreationTaskConsumerTest {

    @Mock private CreationPipelineService pipelineService;
    @Mock private CreationTaskService taskService;
    @Mock private CreationEventProducer eventProducer;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;
    @Mock private Acknowledgment ack;

    private CreationTaskConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CreationTaskConsumer(pipelineService, taskService, eventProducer, redissonClient);
    }

    private String messageOf(Long taskId) {
        PipelineRequest request = new PipelineRequest();
        request.setBookId(1L);
        request.setChapterId(10L);
        request.setUserInput("写一段");
        request.setMode(CreationMode.STANDARD);
        return JsonUtil.toJson(AsyncCreationTaskMessage.of(taskId, request));
    }

    @Test
    void executesTaskWhenLockAndIdempotentFencePass() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(eq(0L), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(taskService.markRunningIfPending(5L)).thenReturn(true);

        consumer.onCreationTask(List.of(messageOf(5L)), ack);

        verify(pipelineService).runAsync(eq(5L), any());
        verify(ack).acknowledge();
    }

    @Test
    void duplicateDeliverySkippedByFence() throws Exception {
        // PENDING -> RUNNING CAS 失败（重复投递 / 已在跑）：直接丢弃
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(eq(0L), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(taskService.markRunningIfPending(5L)).thenReturn(false);

        consumer.onCreationTask(List.of(messageOf(5L)), ack);

        verify(pipelineService, never()).runAsync(any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void skipsWhenLockHeldByOtherConsumer() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(eq(0L), anyLong(), any(TimeUnit.class))).thenReturn(false);

        consumer.onCreationTask(List.of(messageOf(5L)), ack);

        verify(taskService, never()).markRunningIfPending(any());
        verify(pipelineService, never()).runAsync(any(), any());
    }

    @Test
    void lockInterruptedRestoresFlagWithoutFailing() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(eq(0L), anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());

        consumer.onCreationTask(List.of(messageOf(5L)), ack);

        assertTrue(Thread.currentThread().isInterrupted(), "中断标志应被恢复");
        verify(pipelineService, never()).runAsync(any(), any());
        verify(ack).acknowledge();
        Thread.interrupted(); // 清理标志位，避免影响后续测试
    }

    @Test
    void badMessageDoesNotBlockBatch() {
        consumer.onCreationTask(List.of("不是JSON{{{", messageOf(6L)), ack);

        // 毒消息只记日志，批次内其余消息继续处理，ack 一次性确认
        verify(ack).acknowledge();
    }

    @Test
    void nullTaskIdMessagesAreIgnored() {
        consumer.onCreationTask(List.of("{\"taskId\":null}", "{}"), ack);

        verify(redissonClient, never()).getLock(anyString());
        verify(ack).acknowledge();
    }
}
