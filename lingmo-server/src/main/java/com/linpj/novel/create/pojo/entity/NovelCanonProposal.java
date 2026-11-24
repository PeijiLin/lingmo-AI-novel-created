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
 * 正典提案暂存账本（自主性改造 R2a：§2 proposal.emit 协议——只暂存不写入）
 *
 * Writer L2 通过 proposal.emit 工具暂存正典提案（状态变更事实等）；
 * 提交门在节点 SUCCESS 后统一提交（消费端 R3 接入；PENDING→ADOPTED/REJECTED 裁定）。
 * 读范围≠写授权：任何 Agent 无正典直写工具，只有提案通道。
 *
 * @author HL
 */
@TableName(value = "novel_canon_proposal", autoResultMap = true)
@Data
public class NovelCanonProposal implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** run = 创作任务（creation_task.id） */
    private Long runId;

    /** 节点标识（阶段:轮次，与步账同命名空间） */
    private String nodeId;

    /** 提案发起步号（可空——工具调用步） */
    private Integer stepNo;

    /** 关联工具回执（proposal.emit 调用的审计链） */
    private Long receiptId;

    /** 提案内容（{type, characterId, state, note}——对应 novel_state_fact 结构） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object proposal;

    /** 提案内容哈希（内容寻址幂等键） */
    private String proposalHash;

    /** PENDING（暂存）→ ADOPTED / REJECTED（R3 提交门裁定） */
    private String status;

    private Date createdAt;
}
