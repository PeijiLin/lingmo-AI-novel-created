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
 * AgentRuntime 步骤账本（自主性改造 R0：步骤级 Checkpoint）
 *
 * 每步模型决策落盘 = 步骤级断点：恢复时已完成步骤从 novel_tool_receipt 回执重放，
 * 不重新调模型/工具（不重复计费）；未完成/失败步重做（同 stepNo 覆盖更新）。
 *
 * - 与 novel_node_checkpoint 的分工：node_checkpoint 记"节点级成功事实"（复用凭证），
 *   本表记"节点内每一步的决策与计费明细"（重放数据源 + E5 步级成本明细账）
 * - 1 步 = 1 次模型调用；stepNo 从 1 起，UNIQUE(run_id, node_id, step_no) 幂等
 * - kind 为决策出口类型（R1a 起工具化为三出口）：TOOL_CALL / ASK_HUMAN / FINISH
 *
 * @author HL
 */
@TableName(value = "novel_agent_step", autoResultMap = true)
@Data
public class NovelAgentStep implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** run = 创作任务（creation_task.id） */
    private Long runId;

    /** 节点标识：阶段:轮次（与 novel_node_checkpoint 同命名空间） */
    private String nodeId;

    /** 步序号（从 1 起；1 步 = 1 次模型调用） */
    private Integer stepNo;

    /** 步骤类型：TOOL_CALL / ASK_HUMAN / FINISH（决策三出口） */
    private String kind;

    /** 模型结构化决策原文（kill-resume 重放消息序列的数据源） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object decision;

    /** 关联工具回执（novel_tool_receipt.id；TOOL_CALL 步携带，失败/被拒工具不推进状态） */
    private Long receiptId;

    /**
     * 步骤状态：RUNNING（落盘即占位）→ DONE / FAILED / SKIPPED。
     * 崩溃残留 RUNNING，续跑重做该步（更新覆盖）；DONE 才可从回执重放。
     */
    private String status;

    /** 本步模型调用 token（B5 节点总账之下的步级明细账） */
    private Integer tokens;

    /** 备注（如 contract-failed / resumed） */
    private String note;

    private Date createdAt;
}
