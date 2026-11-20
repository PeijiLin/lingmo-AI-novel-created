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
