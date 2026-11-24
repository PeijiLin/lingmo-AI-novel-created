package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linpj.novel.create.utils.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 人物动态状态（角色卡是静态设定，这张是随剧情推进的当前态；book+character 唯一）
 * state JSON 结构：{位置, 心态, 新获知信息, 持有物品, 受伤/能力变化}
 * @TableName novel_character_state
 *
 * @author HL
 */
@TableName(value = "novel_character_state", autoResultMap = true)
@Data
public class NovelCharacterState implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long characterId;

    /** 状态快照至哪章 */
    private Long chapterId;

    /** 动态状态（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object state;

    private Date updatedAt;
}
