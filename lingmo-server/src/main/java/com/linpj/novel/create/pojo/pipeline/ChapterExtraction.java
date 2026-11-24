package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 章节定稿抽取统一产物（v2.0 设计 2.2 + 2.3：人物快照与事件时间线同一次调用产出，
 * 避免逐产物单独调用 LLM——成本与漂移双控）
 *
 * @author HL
 */
@Data
public class ChapterExtraction implements Serializable {

    /** 出场人物状态快照（低置信度/非 FACT 的分级处理在结算侧执行） */
    private List<CharacterStateSnapshot> snapshots;

    /** 关键事件（含结构化时间偏移——由结算侧映射为 TimeNormalization.Offset 入时间线） */
    private List<EventExtraction> events;
}
