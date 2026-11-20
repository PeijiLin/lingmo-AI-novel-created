package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 人物动态状态抽取结果（章节定稿后由抽取节点从正文提取）
 *
 * @author HL
 */
@Data
public class CharacterStateSnapshot implements Serializable {

    /** 角色卡 ID */
    private Long characterId;

    /** 当前位置 */
    private String location;

    /** 当前心态 */
    private String mindset;

    /** 本章新获知信息 */
    private String newKnowledge;

    /** 持有物品变化 */
    private String items;

    /** 受伤/能力变化 */
    private String conditionChanges;

    /**
     * 抽取置信度（0.0–1.0，v2.0 设计 2.5 抽取质量护栏）：
     * 基于正文表述明确程度的自评；低于 confidenceThreshold 的快照不进权威层（不入账，留审计日志）。
     * null = 模型未声明（迁移期宽松处理：照常入账，日志提示）
     */
    private Double confidence;
}
