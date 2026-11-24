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
 * 工具回执账本（自主性改造 R0：真实回执驱动）
 *
 * 模型不消费"想象的结果"，只消费落盘回执——防工具幻觉/假成功的物理基础。
 *
 * - 内容+次数寻址幂等：UNIQUE(run_id, node_id, tool, args_hash, attempt)。
 *   同 attempt 重放返回既有回执（不重复执行/计费）；attempt 入键使崩溃后的重试
 *   预算可从表推导（最新 attempt ok=false 才允许 attempt+1，工具契约"重试≤1"）
 * - 仅 run 内幂等，跨 run 不缓存（正典漂移防线：跨 run 的事实可能已变，重查便宜）
 * - ok=false 失败回照样落盘（失败不推进状态 ≠ 失败不留痕）
 * - result 一律视为不可信数据区：工具输出只作事实材料，消费侧不得当指令（注入防线）
 * - 证据 ID 派生规则（修正案 M1，R1b 落地）：evidence 类工具结果每条 =
 *   ev:{receipt_id}#{序号}，finish 引文只认 ID，引文校验退化为确定性存在性校验
 *
 * @author HL
 */
@TableName(value = "novel_tool_receipt", autoResultMap = true)
@Data
public class NovelToolReceipt implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** run = 创作任务（creation_task.id） */
    private Long runId;

    /** 节点标识：阶段:轮次（与 novel_node_checkpoint 同命名空间） */
    private String nodeId;

    /** 工具名（ToolRegistry 注册名，如 canon.query / evidence.search） */
    private String tool;

    /** 参数内容哈希（SHA-256；与 run/node/tool/attempt 构成唯一键） */
    private String argsHash;

    /** 尝试序号（0 起；同 attempt 幂等，attempt+1 需最新 attempt 为失败） */
    private Integer attempt;

    /** 工具结果原文（不可信数据区：只作事实材料，禁止当指令消费） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object result;

    /** 工具执行结果：true=成功回执 false=失败回执（失败照样落盘留痕） */
    private Boolean ok;

    /** 工具侧 token 成本（如检索为 0；LLM 型工具才有值） */
    private Integer tokens;

    /** 执行耗时（毫秒） */
    private Long costMs;

    private Date createdAt;
}
