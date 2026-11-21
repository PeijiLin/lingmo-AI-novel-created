package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 规则误报豁免（v2.0 M3-D6 误报闭环）
 *
 * 作者标记"这是误报"后该规则对本书静默（豁免白名单）；
 * 豁免清单同时充当回归集——规则词表/阈值每次调整必须对豁免样本重跑（实验纪律）。
 *
 * @TableName novel_rule_exemption
 *
 * @author HL
 */
@TableName("novel_rule_exemption")
@Data
public class NovelRuleExemption implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    /** 规则 ID（如 RC-12） */
    private String ruleId;

    /** 误报理由（豁免即回归样本，理由供规则调整时定位根因） */
    private String reason;

    /** 保护区起始章（v2.0 M4-E5；为空且 chapterTo 为空 = 整本豁免，沿用 D6 语义） */
    private Long chapterFrom;

    /** 保护区结束章（闭区间；仅 chapterFrom 有值表示"该章起往后"） */
    private Long chapterTo;

    /** 到期复核时间（为空 = 长期有效；过期后保护区失效、规则恢复拦截，由人审队列提醒续期） */
    private Date reviewAt;

    private Date createdAt;
}
