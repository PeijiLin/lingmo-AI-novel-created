package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 节点 checkpoint（v2.0 设计 6.2 / M5-F2：节点级断点续跑账本）
 *
 * 只写 SUCCEEDED：失败/中断节点无 checkpoint，续跑时自然重做（重试语义而非跳过）。
 * 产物本体不在此表重复存储——由 (run_id, stage, round) 定位 creation_stage_artifact。
 *
 * @author HL
 */
@TableName(value = "novel_node_checkpoint")
@Data
public class NovelNodeCheckpoint implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** run = 创作任务（creation_task.id；入参/开关快照在其 params 列） */
    private Long runId;

    /** 节点标识：阶段:轮次（PLANNING / WRITING:1 / CRITIC:1 / POLISHING:1） */
    private String nodeId;

    /** SUCCEEDED（保留列：未来 BLOCKED_HUMAN 卡点状态扩展位） */
    private String status;

    /** 节点核心输入的内容哈希（同输入续跑才可复用；输入变更 = 新节点意图，重做） */
    private String inputHash;

    /** 该节点 token 用量（重复计费审计：续跑跳过节点 = 0 计费的凭证） */
    private Integer tokens;

    /** 备注（如 resumed-from / precise-A / budget-L1） */
    private String note;

    private Date createdAt;
}
