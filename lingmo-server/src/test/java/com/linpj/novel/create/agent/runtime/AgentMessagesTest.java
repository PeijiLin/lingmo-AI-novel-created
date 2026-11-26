package com.linpj.novel.create.agent.runtime;

import com.linpj.novel.create.pojo.entity.NovelAgentStep;
import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 消息序列纯函数与账本重放测试（自主性改造 R1a：§12.5 kill-resume 确定性核心）
 *
 * 不变式锁定：相同（任务静态文本 + 账本行 + 回执）必产相同序列；
 * 账行类型 → 消息段映射规则与 AgentRuntime 落账约定一一对应。
 *
 * @author HL
 */
class AgentMessagesTest {

    private static final Function<Long, NovelToolReceipt> NO_RECEIPT = id -> null;

    private AgentTask task(int maxSteps) {
        DeliverableContract contract = new DeliverableContract() {
            @Override
            public String finishSchema() {
                return "{\"type\":\"object\"}";
            }

            @Override
            public String expectations() {
                return "测试契约";
            }

            @Override
            public ContractCheck validate(String deliverableJson) {
                return ContractCheck.passed();
            }
        };
        return AgentTask.of(7L, "CRITIC:1", AgentFence.of(maxSteps, 1000, 60_000, java.util.Set.of("canon.query")),
                null, "系统提示", "任务材料", contract);
    }

    private NovelAgentStep step(int stepNo, String kind, String status, Object decision, Long receiptId, int tokens) {
        NovelAgentStep step = new NovelAgentStep();
        step.setRunId(7L);
        step.setNodeId("CRITIC:1");
        step.setStepNo(stepNo);
        step.setKind(kind);
        step.setStatus(status);
        step.setDecision(decision);
        step.setReceiptId(receiptId);
        step.setTokens(tokens);
        return step;
    }

    @Test
    void 空历史基线序列() {
        List<Message> messages = AgentMessages.build(task(8), List.of(), NO_RECEIPT, 1);

        assertEquals(3, messages.size());
        assertEquals("任务材料" + AgentProtocol.protocolBlock(task(8)), messages.get(1).getText(),
                "首条 user = 任务材料 + 协议块（静态内容）");
        assertTrue(messages.get(2).getText().contains("第 1 步"));
        assertTrue(messages.get(2).getText().contains("剩余步数 8"),
                "剩余步数 = maxSteps - k + 1（纯函数推导）");
        assertInstanceOf(org.springframework.ai.chat.messages.SystemMessage.class, messages.get(0));
    }

    @Test
    void 同输入必产同输出纯函数确定性() {
        List<NovelAgentStep> history = List.of(
                step(1, "TOOL_CALL", "DONE",
                        Map.of("calls", List.of(Map.of("id", "c1", "name", "canon.query", "args", "{\"type\":\"TIMELINE\"}")),
                                "receipts", List.of(Map.of("callId", "c1", "name", "canon.query", "receiptId", 55L))),
                        55L, 100));

        List<Message> first = AgentMessages.build(task(8), history, NO_RECEIPT, 2);
        List<Message> second = AgentMessages.build(task(8), history, NO_RECEIPT, 2);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getText(), second.get(i).getText(), "第 " + i + " 条消息逐字节一致");
        }
    }

    @Test
    void 工具步重放由回执表重建() {
        NovelToolReceipt receipt = new NovelToolReceipt();
        receipt.setId(55L);
        receipt.setResult("{\"events\":[]}");

        List<NovelAgentStep> history = List.of(step(1, "TOOL_CALL", "DONE",
                Map.of("calls", List.of(Map.of("id", "c1", "name", "canon.query", "args", "{\"type\":\"TIMELINE\"}")),
                        "receipts", List.of(Map.of("callId", "c1", "name", "canon.query", "receiptId", 55L))),
                55L, 100));

        List<Message> messages = AgentMessages.build(task(8), history, id -> receipt, 2);

        assertEquals(5, messages.size(), "system+任务user协议 | assistant(toolCalls)+tool回执 | user(stepHint)");
        AssistantMessage assistant = assertInstanceOf(AssistantMessage.class, messages.get(2));
        assertEquals(1, assistant.getToolCalls().size());
        assertEquals("canon.query", assistant.getToolCalls().get(0).name());

        ToolResponseMessage toolMessage = assertInstanceOf(ToolResponseMessage.class, messages.get(3));
        assertEquals(1, toolMessage.getResponses().size());
        assertEquals("[UNTRUSTED-DATA] {\"events\":[]}", toolMessage.getResponses().get(0).responseData(),
                "回执 result 从回执表重放（不重调工具；R3 起带不可信数据标记）");
    }

    @Test
    void 纯文本步与修复步的附加消息由账行推导() {
        List<NovelAgentStep> history = List.of(
                step(1, "TEXT", "MALFORMED", Map.of("text", "我认为没有问题"), null, 50),
                step(2, "FINISH", "FAILED",
                        Map.of("args", "{\"deliverable\":{}}", "callId", "cf", "violations", "issues 缺失"),
                        null, 60));

        List<Message> messages = AgentMessages.build(task(8), history, NO_RECEIPT, 3);

        // system + user协议 | assistant(text) + user(retry) | assistant(finish) + user(repair) | user(stepHint)
        assertEquals(7, messages.size());
        assertEquals("我认为没有问题", messages.get(2).getText());
        assertEquals(AgentProtocol.retryHint(), messages.get(3).getText(), "重试指令固定模板（推导）");
        AssistantMessage finishCall = assertInstanceOf(AssistantMessage.class, messages.get(4));
        assertEquals(AgentProtocol.FINISH_TOOL, finishCall.getToolCalls().get(0).name());
        assertTrue(messages.get(5).getText().contains("issues 缺失"), "修复指令含 violations（从账行推导）");
        assertTrue(messages.get(6).getText().contains("第 3 步"));
    }

    @Test
    void 内联结果回退与回执缺失占位() {
        List<NovelAgentStep> history = List.of(
                step(1, "TOOL_CALL", "DONE",
                        Map.of(
                                "calls", List.of(
                                        Map.of("id", "c1", "name", "rejected:foo", "args", "{}"),
                                        Map.of("id", "c2", "name", "canon.query", "args", "{}")),
                                "receipts", List.of(
                                        Map.of("callId", "c1", "name", "rejected:foo", "result", "拒绝执行：工具 foo 不在白名单"),
                                        Map.of("callId", "c2", "name", "canon.query", "receiptId", 99L))),
                        null, 100));

        List<Message> messages = AgentMessages.build(task(8), history, id -> null, 2);
        ToolResponseMessage toolMessage = assertInstanceOf(ToolResponseMessage.class, messages.get(3));
        assertEquals("拒绝执行：工具 foo 不在白名单", toolMessage.getResponses().get(0).responseData(),
                "无 receiptId（拒绝/deferred）用内联 result 重建——协议逐调用应答不缺");
        assertTrue(toolMessage.getResponses().get(1).responseData().contains("回执缺失"),
                "回执表读不到时缺省占位（不中断重放）");
    }
}
