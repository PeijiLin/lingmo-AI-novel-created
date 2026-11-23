package com.linpj.novel.create.pipeline;

import com.linpj.novel.create.pojo.pipeline.StageCode;
import com.linpj.novel.create.pojo.pipeline.StageEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE 进度发射器测试：事件命名、丢弃模式、断连容错
 *
 * @author HL
 */
class ProgressEmitterTest {

    @Test
    void emitsStageAndContentEventsWithCorrectNames() throws Exception {
        ProgressEmitter emitter = ProgressEmitter.create();
        CopyOnWriteArrayList<ServerSentEvent<String>> received = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        emitter.asFlux()
                .doOnNext(received::add)
                .doOnComplete(done::countDown)
                .subscribe();

        emitter.emitStage(StageEvent.started(StageCode.WRITING, 1, 1));
        emitter.emitContent("夜色渐沉");
        emitter.complete();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(2, received.size());
        assertEquals("stage", received.get(0).event());
        assertTrue(received.get(0).data().contains("\"code\":\"WRITING\""));
        assertEquals("content", received.get(1).event());
        assertTrue(received.get(1).data().contains("夜色渐沉"));
    }

    @Test
    void discardingModeSwallowsAllEvents() {
        ProgressEmitter emitter = ProgressEmitter.discarding();

        // 异步模式不产生 SSE：所有发射方法应为无副作用的 no-op
        assertDoesNotThrow(() -> {
            emitter.emitStage(StageEvent.completed(false, null));
            emitter.emitContent("text");
            emitter.complete();
            emitter.error(new RuntimeException("should be swallowed"));
        });
    }

    @Test
    void errorSignalPropagatesToSubscriber() throws Exception {
        ProgressEmitter emitter = ProgressEmitter.create();
        CountDownLatch done = new CountDownLatch(1);
        CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();
        emitter.asFlux().doOnError(t -> {
            errors.add(t);
            done.countDown();
        }).subscribe();

        emitter.error(new IllegalStateException("管道异常"));
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(1, errors.size());
    }

    @Test
    void agentEventUsesSingleChannelWithKindDiscriminator() throws Exception {
        // 自主性改造 R0：七类 agent 进度收敛为单一 "agent" 事件通道 + kind 判别字段；
        // 老事件（stage/content）零改动——向后兼容
        ProgressEmitter emitter = ProgressEmitter.create();
        CopyOnWriteArrayList<ServerSentEvent<String>> received = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        emitter.asFlux()
                .doOnNext(received::add)
                .doOnComplete(done::countDown)
                .subscribe();

        emitter.emitContent("夜色渐沉");
        emitter.emitAgent(com.linpj.novel.create.pojo.pipeline.AgentEvent.toolCall(
                9L, "CRITIC:1", 1, "canon.query",
                java.util.Map.of("reason", "核对角色左臂伤势")));
        emitter.emitAgent(com.linpj.novel.create.pojo.pipeline.AgentEvent.toolResult(
                9L, "CRITIC:1", 1, 55L, "canon.query", true, null));
        emitter.emitAgent(null); // null 事件直接吞掉
        emitter.complete();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(3, received.size());
        assertEquals("agent", received.get(1).event());
        assertTrue(received.get(1).data().contains("\"kind\":\"TOOL_CALL\""));
        assertTrue(received.get(1).data().contains("canon.query"));
        assertTrue(received.get(1).data().contains("\"nodeId\":\"CRITIC:1\""));
        assertEquals("agent", received.get(2).event());
        assertTrue(received.get(2).data().contains("\"kind\":\"TOOL_RESULT\""));
        assertTrue(received.get(2).data().contains("\"receiptId\":55"));
        assertTrue(received.get(2).data().contains("\"ok\":true"));
    }

    @Test
    void discardingModeSwallowsAgentEvents() {
        // 异步任务不带 SSE：agent 事件同样走丢弃模式
        ProgressEmitter emitter = ProgressEmitter.discarding();
        assertDoesNotThrow(() -> {
            emitter.emitAgent(com.linpj.novel.create.pojo.pipeline.AgentEvent.step(
                    9L, "CRITIC:1", 1, "DONE", null));
            emitter.emitAgent(com.linpj.novel.create.pojo.pipeline.AgentEvent.askHuman(
                    9L, "CRITIC:1", 2, java.util.Map.of("question", "主角是否知道密道存在？")));
            emitter.emitAgent(null);
        });
    }

    @Test
    void unsubscribedEmitterDoesNotBlockPipeline() {
        // 客户端断连后再 emit：tryEmitNext 静默丢弃，不抛异常（管道继续跑，产物照常落库）
        ProgressEmitter emitter = ProgressEmitter.create();
        var subscription = emitter.asFlux().subscribe();
        subscription.dispose();

        assertDoesNotThrow(() -> {
            emitter.emitContent("no one is listening");
            emitter.emitStage(StageEvent.completed(false, null));
            emitter.complete();
        });
    }

    @Test
    void sharedDiscardingInstanceIsSafeForConcurrentTasks() throws Exception {
        // discarding() 返回共享单例：并发任务同时发射不应相互干扰或抛错
        ProgressEmitter a = ProgressEmitter.discarding();
        ProgressEmitter b = ProgressEmitter.discarding();
        assertEquals(a, b);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                a.emitContent("x" + i);
                b.emitContent("y" + i);
            }
        };
        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);
    }
}
