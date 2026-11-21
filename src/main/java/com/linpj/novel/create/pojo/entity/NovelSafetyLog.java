package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 安全审计账本（v2.0 设计 7.1/7.4：三层防线全量留证）
 *
 * 双指标纪律（v2.0 §7.4）：拦截率与误杀率必须同时报告，只报拦截率视为论证不完整——
 * FLAG 记录即误杀观测线（P2 放行样本），REJECT 记录即拦截线（P1 拒绝样本）。
 *
 * @TableName novel_safety_log
 *
 * @author HL
 */
@TableName("novel_safety_log")
@Data
public class NovelSafetyLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    /** PRE / STREAM / POST（前置/中置/后置） */
    private String layer;

    /** PROMPT_INJECTION / SUSPECTED_INJECTION / CONSTRAINT_VIOLATION / ... */
    private String category;

    /** REJECT / FLAG / REWRITE / PASS */
    private String action;

    /** 转义留证（控制符剥离 + 截断，防日志二次注入——红线：日志系统不是新攻击面） */
    private String detail;

    private Date createdAt;
}
