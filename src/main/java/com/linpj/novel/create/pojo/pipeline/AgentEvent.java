package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * SSE agent 事件 data 载荷（自主性改造 R0：AgentRuntime 进度协议，向后兼容）
 *
 * <p>协议设计（修正案 §11.3）：原方案七个独立事件名收敛为<b>单一 agent 通道 +
 * kind 判别字段</b>——协议简单、前端 switch 一处；老事件（stage/content）零改动。
 *
 * <p>三级下钻：traceId=runId、span=nodeId、stepId=step；成本按步入账
 * （agent_step.tokens 步级明细账）。
 *
 * <p>kind 七类：
 * <ul>
 *   <li>STEP          步状态变更（RUNNING→DONE/FAILED）</li>
 *   <li>TOOL_CALL     模型发起工具调用（含 reason）</li>
 *   <li>TOOL_RESULT   工具回执落盘（receiptId + ok）</li>
 *   <li>HYPOTHESIS    Critic 质疑假设入队/出队</li>
 *   <li>CITATION_CHECK 引文校验结果（R1b 起）</li>
 *   <li>ASK_HUMAN     内层人工卡点（整个 run 挂 WAITING_HUMAN）</li>
 *   <li>FINISH_REPORT 收口交付报告（含 unresolved）</li>
 * </ul>
 *
 * @author HL
 */
@Data
public class AgentEvent implements Serializable {

    public static final String KIND_STEP = "STEP";
    public static final String KIND_TOOL_CALL = "TOOL_CALL";
    public static final String KIND_TOOL_RESULT = "TOOL_RESULT";
    public static final String KIND_HYPOTHESIS = "HYPOTHESIS";
    public static final String KIND_CITATION_CHECK = "CITATION_CHECK";
    public static final String KIND_ASK_HUMAN = "ASK_HUMAN";
    public static final String KIND_FINISH_REPORT = "FINISH_REPORT";

    /** 事件类别（七类之一） */
    private String kind;

    /** run 级追踪 ID（creation_task.id） */
    private Long runId;

    /** 节点标识（阶段:轮次，span） */
    private String nodeId;

    /** 步序号（stepId，从 1 起） */
    private Integer step;

    /** 关联工具回执 ID（TOOL_CALL/TOOL_RESULT/CITATION_CHECK 携带） */
    private Long receiptId;

    /** 工具名（TOOL_CALL、TOOL_RESULT 与 CITATION_CHECK 类事件携带） */
    private String tool;

    /** 结局标志（TOOL_RESULT/CITATION_CHECK 携带） */
    private Boolean ok;

    /** 载荷（决策原文/回执摘要/问题与答复——结构由 kind 决定，前端按 kind 解析） */
    private Object payload;

    /** 步状态事件（RUNNING→DONE/FAILED 状态变更） */
    public static AgentEvent step(Long runId, String nodeId, int step, String status, Object payload) {
        AgentEvent event = new AgentEvent();
        event.setKind(KIND_STEP);
        event.setRunId(runId);
        event.setNodeId(nodeId);
        event.setStep(step);
        event.setPayload(payload == null ? status : payload);
        return event;
    }

    /** 工具调用事件（模型发起；payload 含 args 与 reason） */
    public static AgentEvent toolCall(Long runId, String nodeId, int step, String tool, Object payload) {
        AgentEvent event = new AgentEvent();
        event.setKind(KIND_TOOL_CALL);
        event.setRunId(runId);
        event.setNodeId(nodeId);
        event.setStep(step);
        event.setTool(tool);
        event.setPayload(payload);
        return event;
    }

    /** 工具回执事件（落盘后发射；ok=false 失败留痕不留成功） */
    public static AgentEvent toolResult(Long runId, String nodeId, int step, Long receiptId,
                                        String tool, boolean ok, Object payload) {
        AgentEvent event = new AgentEvent();
        event.setKind(KIND_TOOL_RESULT);
        event.setRunId(runId);
        event.setNodeId(nodeId);
        event.setStep(step);
        event.setReceiptId(receiptId);
        event.setTool(tool);
        event.setOk(ok);
        event.setPayload(payload);
        return event;
    }

    /** 质疑假设事件（Critic L2 专用：假设队列进出） */
    public static AgentEvent hypothesis(Long runId, String nodeId, int step, Object payload) {
        AgentEvent event = new AgentEvent();
        event.setKind(KIND_HYPOTHESIS);
        event.setRunId(runId);
        event.setNodeId(nodeId);
        event.setStep(step);
        event.setPayload(payload);
        return event;
    }

    /** 引文校验事件（R1b 引文协议：receipt 绑定结果） */
    public static AgentEvent citationCheck(Long runId, String nodeId, int step, Long receiptId,
                                           boolean ok, Object payload) {
        AgentEvent event = new AgentEvent();
        event.setKind(KIND_CITATION_CHECK);
        event.setRunId(runId);
        event.setNodeId(nodeId);
        event.setStep(step);
        event.setReceiptId(receiptId);
        event.setOk(ok);
        event.setPayload(payload);
        return event;
    }

    /** 人工卡点事件（内层 ask_human：整个 run 挂 WAITING_HUMAN，不占超时） */
    public static AgentEvent askHuman(Long runId, String nodeId, int step, Object payload) {
        AgentEvent event = new AgentEvent();
        event.setKind(KIND_ASK_HUMAN);
        event.setRunId(runId);
        event.setNodeId(nodeId);
        event.setStep(step);
        event.setPayload(payload);
        return event;
    }

    /** 收口交付事件（finish：交付物摘要 + unresolved 清单） */
    public static AgentEvent finishReport(Long runId, String nodeId, int step, Object payload) {
        AgentEvent event = new AgentEvent();
        event.setKind(KIND_FINISH_REPORT);
        event.setRunId(runId);
        event.setNodeId(nodeId);
        event.setStep(step);
        event.setPayload(payload);
        return event;
    }
}
