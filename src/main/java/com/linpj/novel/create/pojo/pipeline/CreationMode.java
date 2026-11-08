package com.linpj.novel.create.pojo.pipeline;

/**
 * 创作模式（成本分级）
 *
 * FAST      Writer 单链路（现状，1x 成本，灵感速记）
 * STANDARD  Writer + Critic 闭环（+重写 2-6 次，2-4x，日常创作默认档）
 * REFINED   Planner + Writer + Critic + Polisher（5-10 次，5-8x，精修/定稿）
 * PRECISE   REFINED + 并行双稿择优（8-14 次，8-12x，实验档，见设计 11.2）
 *
 * @author HL
 */
public enum CreationMode {
    FAST,
    STANDARD,
    REFINED,
    PRECISE
}
