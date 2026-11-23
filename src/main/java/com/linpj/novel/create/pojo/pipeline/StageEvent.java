package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * SSE stage 事件 data 载荷（设计 4.3 协议）
 *
 * 示例：
 *   event: stage  data: {"code":"WRITING","status":"STARTED","index":2,"total":4}
 *   event: stage  data: {"code":"WRITING","status":"REVISION","round":1}
 *   event: stage  data: {"code":"COMPLETED","degraded":false,"usage":{...}}
 *   event: stage  data: {"code":"FAILED","error":"..."}
 *
 * @author HL
 */
@Data
public class StageEvent implements Serializable {

    /** 阶段编码：PLANNING / WRITING / CRITIC / POLISHING / COMPLETED / FAILED */
    private String code;

    /** 阶段状态：STARTED / REVISION / COMPLETED / FAILED */
    private String status;

    /** 阶段序号（从 1 开始） */
    private Integer index;

    /** 阶段总数 */
    private Integer total;

    /** 重写轮次（仅 REVISION 状态携带） */
    private Integer round;

    /** 是否发生降级（仅 COMPLETED 携带） */
    private Boolean degraded;

    /** 用量统计（仅 COMPLETED 携带） */
    private StageUsage usage;

    /** 失败原因（仅 FAILED 携带） */
    private String error;

    /**
     * 草稿态标记（仅 STARTED 携带；M5-F3 / B6-2）：
     * true = 本阶段内容以草稿实时下发，终稿将以 REVISION 信号整体替换客户端缓冲
     * （Polisher 润色流：作者实时看到润色过程，最终以复核+gate 后的版本为准）
     */
    private Boolean draft;

    public static StageEvent started(StageCode code, int index, int total) {
        StageEvent event = new StageEvent();
        event.setCode(code.name());
        event.setStatus("STARTED");
        event.setIndex(index);
        event.setTotal(total);
        return event;
    }

    /** 草稿态阶段开始（F3：内容实时下发 + 终稿以 REVISION 替换） */
    public static StageEvent draft(StageCode code, int index, int total) {
        StageEvent event = started(code, index, total);
        event.setDraft(Boolean.TRUE);
        return event;
    }

    public static StageEvent revision(StageCode code, int round) {
        StageEvent event = new StageEvent();
        event.setCode(code.name());
        event.setStatus("REVISION");
        event.setRound(round);
        return event;
    }

    public static StageEvent completed(boolean degraded, StageUsage usage) {
        StageEvent event = new StageEvent();
        event.setCode(StageCode.COMPLETED.name());
        event.setStatus("COMPLETED");
        event.setDegraded(degraded);
        event.setUsage(usage);
        return event;
    }

    public static StageEvent failed(String error) {
        StageEvent event = new StageEvent();
        event.setCode(StageCode.FAILED.name());
        event.setStatus("FAILED");
        event.setError(error);
        return event;
    }
}
