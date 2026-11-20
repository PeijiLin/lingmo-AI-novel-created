package com.linpj.novel.create.pojo.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
    PRECISE;

    /**
     * 大小写兼容反序列化："fast" / "Fast" / "FAST" 均可（Jackson 默认枚举绑定大小写敏感，
     * 前端传小写会直接 400）；空串视为未传（走默认 FAST），未知值显式抛错而非静默降级。
     */
    @JsonCreator
    public static CreationMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
