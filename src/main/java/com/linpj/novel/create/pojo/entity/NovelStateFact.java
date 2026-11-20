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
 * 人物状态事实（v2.0 设计 6.3：失效章区间软失效——事实带生效区间，旧事实不删除）
 *
 * - T_narr 生效区间：[validFromChapter, validToChapter)；null validTo = 当前有效
 * - 倒叙/回溯求解：solveState(atChapter) 返回区间覆盖该章的最新事实——
 *   第 20 章回忆第 5 章时取第 5 章事实而非最新（v2.0 R-03 修复）
 * - 本表是权威账本；novel_character_state 当前态是性能投影（可从本表重建）
 *
 * @TableName novel_state_fact
 *
 * @author HL
 */
@TableName(value = "novel_state_fact", autoResultMap = true)
@Data
public class NovelStateFact implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long characterId;

    /** 快照来源章（T_narr 叙述顺序） */
    private Long sourceChapter;

    /** 生效起始章（含） */
    private Long validFromChapter;

    /** 生效截止章（不含）；null = 当前有效 */
    private Long validToChapter;

    /** 状态快照（JSONB） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object state;

    private Date createdAt;
}
