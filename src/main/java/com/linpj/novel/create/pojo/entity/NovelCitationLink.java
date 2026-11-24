package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 引文绑定账本（自主性改造 R1b：§11.1-M1 引文协议）
 *
 * Critic L2 收口硬校验逐条落账——校验通过与否都留痕（审计/回放/评测面）。
 * evidenceId=ev:{receipt_id}#{序号} 回指 novel_tool_receipt.result 第 index 条（可回验原文）。
 *
 * @author HL
 */
@TableName(value = "novel_citation_link")
@Data
public class NovelCitationLink implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** run = 创作任务（creation_task.id） */
    private Long runId;

    /** 节点标识（阶段:轮次，与步账同命名空间） */
    private String nodeId;

    /** 收口步号（FINISH 步） */
    private Integer stepNo;

    /** 引文文本（issue.evidence / claim.text） */
    private String claimText;

    /** 证据地址（ev:{receiptId}#{序号}，代码生成） */
    private String evidenceId;

    /** 回指 novel_tool_receipt.id */
    private Long receiptId;

    /** 回执内条目序号（1 起） */
    private Integer itemIndex;

    /** 该条绑定是否通过硬校验（存在性+界内+文本一致） */
    private Boolean ok;

    /** 具体失败原因（格式非法/回执不存在/越界/文本不一致） */
    private String violations;

    private Date createdAt;
}
