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
}
