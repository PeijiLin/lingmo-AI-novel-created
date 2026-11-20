package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 正典冲突仲裁账本（v2.0 设计 6.4：冲突不静默覆盖）
 *
 * 冲突语义（首期落在生死硬字段上）：
 * - STATE_EVOLVE 自然演化（R1）：旧事实置失效、新事实自动生效，本表留痕 AUTO_APPLIED
 * - RESURRECTION_REVIEW 死亡回退（R3 视同人审）：旧状态保留、新快照不入账，
 *   本表 PENDING_REVIEW 等作者仲裁（改文=REJECTED / 确认复活=RESOLVED 后手工或工具生效）
 *
 * @TableName novel_wiki_conflict
 *
 * @author HL
 */
@TableName("novel_wiki_conflict")
@Data
public class NovelWikiConflict implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long chapterId;

    private Long characterId;

    /** STATE_EVOLVE / RESURRECTION_REVIEW */
    private String conflictKind;

    /** 冲突前权威值（如 DEAD） */
    private String oldValue;

    /** 冲突申报值（如 ALIVE） */
    private String newValue;

    /** AUTO_APPLIED / PENDING_REVIEW / RESOLVED / REJECTED */
    private String status;

    /** 备注（来源与证据描述） */
    private String note;

    private Date createdAt;

    private Date resolvedAt;
}
