package com.linpj.novel.create.pipeline;

import com.linpj.novel.create.pojo.pipeline.StageEvent;
import com.linpj.novel.create.utils.JsonUtil;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * SSE 进度发射器：封装 Sinks.Many，向客户端推送 stage / content 混合事件流（设计 4.3 协议）
 *
 * 事件协议：
 *   event: stage    data: {"code":"WRITING","status":"STARTED","index":2,"total":4}
 *   event: content  data: {"text":"夜色渐沉"}
 *   event: stage    data: {"code":"COMPLETED","degraded":false,"usage":{...}}
 *
 * @author HL
 */
public class ProgressEmitter {

    private final Sinks.Many<ServerSentEvent<String>> sink;

    /** 异步模式用：丢弃式发射器（不产生 SSE 流，仅驱动状态机） */
    private static final ProgressEmitter DISCARDING = new ProgressEmitter(null);

    private ProgressEmitter(Sinks.Many<ServerSentEvent<String>> sink) {
        this.sink = sink;
    }

    public static ProgressEmitter create() {
        return new ProgressEmitter(Sinks.many().unicast().onBackpressureBuffer());
    }

    /** 异步执行专用：所有事件丢弃（任务进度靠 creation_task 状态机查询） */
    public static ProgressEmitter discarding() {
        return DISCARDING;
    }

    /** 推送阶段事件（STARTED / REVISION / COMPLETED / FAILED） */
    public void emitStage(StageEvent event) {
        emit("stage", JsonUtil.toJson(event));
    }

    /** 推送正文增量（Writer/Polisher 的 token 流） */
    public void emitContent(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        emit("content", JsonUtil.toJson(Map.of("text", text)));
    }

    private void emit(String eventName, String data) {
        if (sink == null) {
            return; // 丢弃模式：异步任务不走 SSE，进度靠任务状态机
        }
        ServerSentEvent<String> sse = ServerSentEvent.<String>builder()
                .event(eventName)
                .data(data)
                .build();
        // tryEmitNext：客户端断连后静默丢弃，不中断管道（产物仍会落库）
        Sinks.EmitResult result = sink.tryEmitNext(sse);
        if (result.isFailure()) {
            org.slf4j.LoggerFactory.getLogger(ProgressEmitter.class)
                    .debug("SSE 事件推送被丢弃（客户端可能已断连）：{}", result);
        }
    }

    /** 结束事件流 */
    public void complete() {
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    /** 异常终止事件流 */
    public void error(Throwable t) {
        if (sink != null) {
            sink.tryEmitError(t);
        }
    }

    public Flux<ServerSentEvent<String>> asFlux() {
        return sink.asFlux();
    }
}
