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
 * Wiki 正典页（WIKI_RAG_UNIFIED_PLAN §3.1：一类实体一页）
 *
 * <p>权威分层（ADR-2）：正典页 > 派生摘要 > 向量召回。角色/地点/势力/物品/世界规则
 * 的静态设定以页卡为权威；动态状态走 novel_state_fact / event_timeline 账本。
 *
 * <p>entity_key 归一化键：char:{characterId} / loc:{名} / rule:{名}——书内唯一。
 *
 * @author HL
 */
@TableName(value = "novel_wiki_page", autoResultMap = true)
@Data
public class NovelWikiPage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    /** CHARACTER / LOCATION / FACTION / ITEM / WORLD_RULE / STYLE(预留) */
    private String entityType;

    /** 对应实体表 id（如 character_cards.id）；纯设定页可空 */
    private Long entityId;

    /** 库内唯一归一化键（char:12 / loc:青云山 / rule:灵力体系） */
    private String entityKey;

    private String title;

    /** 页卡正文（注入时可截断） */
    private String canonical;

    /** 硬事实白名单（键受控，见设计 §3.4） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object attrs;

    /** DRAFT / CANON / DEPRECATED */
    private String status;

    /** 版本号（每次编辑 +1，冲突仲裁可回溯旧版） */
    private Integer version;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
