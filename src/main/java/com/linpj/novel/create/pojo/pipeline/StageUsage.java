package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 管道全程用量统计（完成事件透出，前端可显示"本章消耗 xx tokens"）
 *
 * @author HL
 */
@Data
public class StageUsage implements Serializable {

    /** 实际执行的阶段数（含重写轮次） */
    private int stages;

    /** 全程 LLM token 总用量 */
    private long totalTokens;

    /** 全程耗时（毫秒） */
    private long elapsedMs;

    public void addTokens(long tokens) {
        this.totalTokens += Math.max(0, tokens);
    }
}
