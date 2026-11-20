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
 * 待证实断言（v2.0 设计 2.3 断言可信度分级的隔离区）
 *
 * - 非 FACT 断言（HYPOTHETICAL/HEARSAY/DREAM/LIE）不进正典权威层，落本表
 * - 升格：后续章节 >=2 个独立锚点证实，或作者确认（status=CONFIRMED 后经正规写入通道采纳）
 * - 过期：30 章未证实自动归档（status=EXPIRED，不物理删除——审计可追溯）
 *
 * @TableName novel_pending_assertion
 *
 * @author HL
 */
@TableName(value = "novel_pending_assertion", autoResultMap = true)
@Data
public class NovelPendingAssertion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long chapterId;

    private Long characterId;

    /** HYPOTHETICAL / HEARSAY / DREAM / LIE（FACT 不入本表） */
    private String assertionType;

    /** 抽取快照原文（JSONB；升格与人工复核的依据） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object payload;

    /** PENDING / CONFIRMED / EXPIRED / REJECTED */
    private String status;

    /** 独立锚点证实计数（>=2 满足升格条件） */
    private Integer confirmations;

    /** 过期边界章（chapterId + 30）：当前章超过此值仍未证实即 EXPIRED */
    private Long expiresChapter;

    private Date createdAt;

    private Date resolvedAt;
}
