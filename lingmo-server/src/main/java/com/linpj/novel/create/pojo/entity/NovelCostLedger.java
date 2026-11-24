package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 成本账本行（v2.0 设计 6.2 CostLedger / M5-F4：每个 run 每个节点一行，轮次折叠）
 *
 * 溯源：由 creation_stage_artifact 派生（stage → Agent → model 映射确定），
 * 任务终态（成功/失败均含——烧掉的 token 不消失）由 CostLedgerService 单点入账。
 *
 * @author HL
 */
@TableName(value = "novel_cost_ledger")
@Data
public class NovelCostLedger implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** run = 创作任务（creation_task.id） */
    private Long runId;

    private Long bookId;

    private Long chapterId;

    /** 任务模式（FAST/STANDARD/REFINED/PRECISE） */
    private String mode;

    /** 执行节点（stage 域：PLANNING/WRITING/CRITIC/POLISHING——轮次折叠） */
    private String node;

    /** 模型名（价目键；CRITIC 节点在异源配置下为 Critic 专用模型） */
    private String model;

    /** 该节点 token 合计（含全部轮次/变体） */
    private Long totalTokens;

    /** 输入 token（usage 元数据回传时细化；当前为 null） */
    private Long inputTokens;

    /** 输出 token（同上） */
    private Long outputTokens;

    /** 成本（total_tokens/1000 × 价目；未配价目记 0，导出报告标注未计价部分） */
    private BigDecimal cost;

    private Date createdAt;
}
