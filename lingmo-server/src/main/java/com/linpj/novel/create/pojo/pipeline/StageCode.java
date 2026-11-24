package com.linpj.novel.create.pojo.pipeline;

/**
 * 管道阶段编码（SSE stage 事件与 creation_task.current_stage 共用）
 *
 * @author HL
 */
public enum StageCode {
    PLANNING,
    WRITING,
    CRITIC,
    POLISHING,
    COMPLETED,
    FAILED
}
