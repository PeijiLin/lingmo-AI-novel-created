package com.linpj.novel.create.pipeline;

/**
 * 章级预算耗尽（L4 止损 / 书级硬上限）：停止后续生成节点，任务失败转人工。
 *
 * <p>已完成产物不丢弃——各阶段 artifact 实时落库，failTask.error 携带预算报告，
 * 人工可基于已产出内容决策续写或放弃（"输出已完成部分 + 未完成报告"，设计 5.4 L4）。
 *
 * @author HL
 */
public class BudgetExhaustedException extends RuntimeException {

    public BudgetExhaustedException(String message) {
        super(message);
    }
}
