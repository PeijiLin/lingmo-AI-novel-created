package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 人物动态状态抽取结果（章节定稿后由抽取节点从正文提取）
 *
 * B5 硬字段契约（v2.0 设计 2.5 白名单抽取）：aliveStatus / itemNames 为结构化可判定字段，
 * 规则引擎直接消费；其余字段为自由文本，供创作上下文参考。
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

    /** 持有物品变化（自由文本，供 Writer 参考） */
    private String items;

    /** 受伤/能力变化 */
    private String conditionChanges;

    /**
     * 生死状态（供 RC-01 确定性判定）：ALIVE / DEAD / UNKNOWN。
     * 正文未提及或无法判定必须填 UNKNOWN，禁止猜值（红线 6 的规则侧贯彻）。
     */
    private AliveStatus aliveStatus;

    /**
     * 持有物名清单（供 RC-06 确定性比对）：
     * 章节结束时该角色持有的显著道具名（如"青铜剑"）；无显著道具返回空数组。
     */
    private List<String> itemNames;

    /**
     * 抽取置信度（0.0–1.0，v2.0 设计 2.5 抽取质量护栏）：
     * 基于正文表述明确程度的自评；低于 confidenceThreshold 的快照不进权威层（不入账，留审计日志）。
     * null = 模型未声明（迁移期宽松处理：照常入账，日志提示）
     */
    private Double confidence;
}
