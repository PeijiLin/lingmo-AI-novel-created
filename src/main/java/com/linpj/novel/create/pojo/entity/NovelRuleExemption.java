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

    private Date createdAt;
}
