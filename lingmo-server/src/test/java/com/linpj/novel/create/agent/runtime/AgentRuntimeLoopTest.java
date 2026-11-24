package com.linpj.novel.create.agent.runtime;

import com.linpj.novel.create.agent.tool.AgentToolContext;
import com.linpj.novel.create.agent.tool.ToolRegistry;
import com.linpj.novel.create.pojo.entity.NovelAgentStep;
import com.linpj.novel.create.worldstate.AgentStepService;
import com.linpj.novel.create.worldstate.ToolReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentRuntime 步骤循环测试（自主性改造 R1a：§12.4 判定表 / §12.6 降级分叉）
 *
 * 脚本化 ChatClient：按调用序返回预设响应，验证循环状态机各分支；
 * 步账桩收集落账供 kill-resume 断言。
 *
 * @author HL
 */
class AgentRuntimeLoopTest {

    private AgentStepService stepService;
    private ToolReceiptService receiptService;
    private ToolRegistry registry;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec spec;
    private ChatClient.CallResponseSpec callSpec;

    private final List<NovelAgentStep> ledger = new ArrayList<>();
    /** 脚本：模型调用序号 → 响应（耗尽后重复最后一个/抛异常可注入） */
    private final List<ChatResponse> script = new ArrayList<>();

    @BeforeEach
    void setUp() {
        stepService = mock(AgentStepService.class);
        receiptService = mock(ToolReceiptService.class);
        registry = new ToolRegistry();
        registry.register(new EchoTool());
        chatClient = mock(ChatClient.class);
        spec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(spec);
        when(spec.messages(any(List.class))).thenReturn(spec);
        when(spec.toolCallbacks(any(ToolCallback[].class))).thenReturn(spec);
        when(spec.toolCallbacks(org.mockito.ArgumentMatchers.<List<ToolCallback>>any())).thenReturn(spec);
        when(spec.options(any())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);

        // 步账桩：stepsOf 返回 ledger 可变副本（Runtime 会往 history 追加新完成步）；
        // recordStep 收集行并回显 id
        when(stepService.stepsOf(anyLong(), anyString())).thenAnswer(inv -> new ArrayList<>(ledger));
        when(stepService.tokensUsed(anyLong(), anyString())).thenAnswer(inv ->
                ledger.stream().filter(s -> s.getTokens() != null).mapToLong(NovelAgentStep::getTokens).sum());
        when(stepService.recordStep(anyLong(), anyString(), anyInt(), anyString(),
                any(), any(), anyString(), anyInt(), any())).thenAnswer(inv -> {
            NovelAgentStep step = new NovelAgentStep();
            step.setId((long) (ledger.size() + 1));
            step.setRunId(inv.getArgument(0));
            step.setNodeId(inv.getArgument(1));
            step.setStepNo(inv.getArgument(2));
            step.setKind(inv.getArgument(3));
            step.setDecision(inv.getArgument(4));
            step.setReceiptId(inv.getArgument(5));
            step.setStatus(inv.getArgument(6));
            step.setTokens(inv.getArgument(7));
            ledger.add(step);
            return step.getId();
        });

        // 回执桩：幂等预检恒未成功（走真实执行路径），回执落账返回自增 id
        when(receiptService.hasSucceeded(anyLong(), anyString(), anyString(), anyString())).thenReturn(false);
        when(receiptService.latest(anyLong(), anyString(), anyString(), anyString())).thenReturn(null);
        when(receiptService.record(anyLong(), anyString(), anyString(), anyString(), anyInt(),
                any(), anyBoolean(), anyInt(), anyLong())).thenAnswer(inv -> {
            com.linpj.novel.create.pojo.entity.NovelToolReceipt receipt =
                    new com.linpj.novel.create.pojo.entity.NovelToolReceipt();
            receipt.setId(System.nanoTime());
            receipt.setResult(inv.getArgument(5));
            receipt.setOk(inv.getArgument(6));
            return receipt;
        });
    }

    // ------------------------------------------------------------------
    // 模型脚本辅助
    // ------------------------------------------------------------------

    private void script(ChatResponse... responses) {
        script.clear();
        script.addAll(List.of(responses));
        final int[] cursor = {0};
        // doAnswer 姿势：重新配置 stub 不触发旧 answer（when() 求值会真实调用 mock 命中旧 stub）
        org.mockito.Mockito.doAnswer(inv -> {
            if (cursor[0] >= script.size()) {
                throw new IllegalStateException("脚本耗尽（测试未按预期步数推进）");
            }
            return script.get(cursor[0]++);
        }).when(callSpec).chatResponse();
    }

    private static ChatResponse toolCall(String callId, String name, String args) {
        AssistantMessage message = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(callId, "function", name, args)))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static ChatResponse plainText(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private AgentTask task(AgentFence fence, DeliverableContract contract, L1Fallback fallback) {
        return new AgentTask(7L, "CRITIC:1", fence, "system", "材料",
                chatClient, contract, fallback, null, null);
    }

    private static AgentFence fence(int maxSteps) {
        return AgentFence.of(maxSteps, 10_000, 60_000, Set.of("echo.tool"));
    }

    private static DeliverableContract okContract() {
        return new DeliverableContract() {
            @Override
            public String finishSchema() {
                return "{\"type\":\"object\"}";
            }

            @Override
            public String expectations() {
                return "测试";
            }

            @Override
            public ContractCheck validate(String deliverableJson) {
                return ContractCheck.passed();
            }
        };
    }

    /** 测试桩工具：回声 */
    static class EchoTool implements com.linpj.novel.create.agent.tool.AgentTool {
        @Override
        public String name() {
            return "echo.tool";
        }

        @Override
        public String description() {
            return "回声";
        }

        @Override
        public String inputSchema() {
            return "{\"type\":\"object\"}";
        }

        @Override
        public String execute(com.linpj.novel.create.agent.tool.AgentToolContext context, String argumentsJson) {
            return "echo:" + (argumentsJson == null ? "" : argumentsJson);
        }
    }

    // ------------------------------------------------------------------
    // 循环分支
    // ------------------------------------------------------------------

    @Test
    void 工具步后收口成功() {
        script(toolCall("c1", "echo.tool", "{\"text\":\"hi\"}"),
                toolCall("c2", AgentProtocol.FINISH_TOOL, "{\"deliverable\":{\"approved\":true}}"));

        AgentResult result = new AgentRuntime(stepService, receiptService, registry)
                .run(task(fence(8), okContract(), null));

        assertInstanceOf(AgentResult.Success.class, result);
        assertTrue(((AgentResult.Success) result).deliverableJson().contains("approved"));
        assertEquals(2, ledger.size(), "步1=工具步，步2=finish 步");
        assertEquals("TOOL_CALL", ledger.get(0).getKind());
        assertEquals("DONE", ledger.get(0).getStatus());
        assertEquals("FINISH", ledger.get(1).getKind());
        assertEquals(1, registry.executionCount("echo.tool"));
    }

    @Test
    void 修复步一次后仍败契约型降级() {
        DeliverableContract neverPass = new DeliverableContract() {
            @Override
            public String finishSchema() {
                return "{\"type\":\"object\"}";
            }

            @Override
            public String expectations() {
                return "永不通过";
            }

            @Override
            public ContractCheck validate(String deliverableJson) {
                return ContractCheck.fail("测试性拒绝");
            }
        };
        script(toolCall("f1", AgentProtocol.FINISH_TOOL, "{\"deliverable\":{}}"),
                toolCall("f2", AgentProtocol.FINISH_TOOL, "{\"deliverable\":{}}"));

        L1Fallback spyFallback = mock(L1Fallback.class);
        AgentResult result = new AgentRuntime(stepService, receiptService, registry)
                .run(task(fence(8), neverPass, spyFallback));

        assertInstanceOf(AgentResult.Degraded.class, result, "契约型失败直接 DEGRADED（不烧 L1）");
        assertTrue(((AgentResult.Degraded) result).reason().contains("contract-failed"));
        verify(spyFallback, times(0)).callOnce(); // 11.1-M1b：契约型跳过 L1
        assertEquals(2, ledger.size());
        assertEquals("FAILED", ledger.get(1).getStatus(), "第一次 finish 落 FAILED（修复步历史）");
    }

    @Test
    void 步数耗尽预算型降级走L1() {
        // 模型永远调工具不 finish → 步数烧尽（脚本给足两条：两步工具后步数耗尽）
        ChatResponse endless = toolCall("c1", "echo.tool", "{}");
        script(endless, endless);
        L1Fallback fallback = () -> "L1-RESULT";

        AgentResult result = new AgentRuntime(stepService, receiptService, registry)
                .run(task(fence(2), okContract(), fallback));

        assertInstanceOf(AgentResult.FallbackL1.class, result);
        assertEquals("L1-RESULT", ((AgentResult.FallbackL1) result).l1Result());
        assertTrue(((AgentResult.FallbackL1) result).reason().startsWith("steps-exhausted"));
        assertEquals(2, ledger.size(), "maxSteps=2 → 两步工具后预算耗尽");
    }

    @Test
    void 纯文本响应占位计步且连败熔断() {
        // 连续两个纯文本 → 连败 2 熔断（默认 maxConsecutiveFailures=2）
        script(plainText("我认为没问题"), plainText("真的没问题"));
        L1Fallback fallback = () -> "L1-RESULT";

        AgentResult result = new AgentRuntime(stepService, receiptService, registry)
                .run(task(fence(8), okContract(), fallback));

        assertInstanceOf(AgentResult.FallbackL1.class, result);
        assertTrue(((AgentResult.FallbackL1) result).reason().startsWith("consecutive-failures"));
        assertEquals(2, ledger.size(), "纯文本步占位落账（序列完整性）");
        ledger.forEach(step -> assertEquals("MALFORMED", step.getStatus()));
    }

    @Test
    void 无回调预算耗尽落DEGRADED() {
        script(toolCall("c1", "echo.tool", "{}"));

        AgentResult result = new AgentRuntime(stepService, receiptService, registry)
                .run(task(fence(1), okContract(), null));

        assertInstanceOf(AgentResult.Degraded.class, result, "无 L1 回调：预算型也落 Degraded");
        assertTrue(((AgentResult.Degraded) result).reason().contains("budget-exhausted"));
    }

    @Test
    void 相同决策签名连续复读触发熔断() {
        // 同参数工具调用连续 4 次：第 4 步后 repeat=3 → 环形熔断（maxRepeatSignature=3）
        ChatResponse same = toolCall("c1", "echo.tool", "{\"same\":\"args\"}");
        script(same, same, same, same);
        L1Fallback fallback = () -> "L1";

        AgentResult result = new AgentRuntime(stepService, receiptService, registry)
                .run(task(fence(8), okContract(), fallback));

        assertInstanceOf(AgentResult.FallbackL1.class, result);
        assertTrue(((AgentResult.FallbackL1) result).reason().startsWith("repeat-signature"));
        assertEquals(4, ledger.size(), "maxRepeatSignature=3 → 第 4 步同签名后熔断");
    }

    @Test
    void 混合调用finish顺延并逐调用应答() {
        // 同步携带工具+finish：finish 顺延（回执告知再确认），本步骤按工具步落账
        AssistantMessage mixed = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("m1", "function", "echo.tool", "{\"text\":\"1\"}"),
                new AssistantMessage.ToolCall("m2", "function", AgentProtocol.FINISH_TOOL, "{\"deliverable\":{\"x\":1}}")))
                .build();
        script(new ChatResponse(List.of(new Generation(mixed))),
                toolCall("f2", AgentProtocol.FINISH_TOOL, "{\"deliverable\":\"ok\"}"));

        AgentResult result = new AgentRuntime(stepService, receiptService, registry)
                .run(task(fence(8), new DeliverableContract() {
                    @Override
                    public String finishSchema() {
                        return "{\"type\":\"object\"}";
                    }

                    @Override
                    public String expectations() {
                        return "测试";
                    }

                    @Override
                    public ContractCheck validate(String json) {
                        // extractDeliverable 返回 JSON 序列化文本：字符串值是带引号的 "\"ok\""
                        return json != null && json.contains("ok") && !json.contains("fail")
                                ? ContractCheck.passed() : ContractCheck.fail("非 ok");
                    }
                }, null));

        assertInstanceOf(AgentResult.Success.class, result, "顺延一步后单独 finish 收口成功");
        assertEquals(2, ledger.size());
        assertEquals(1, registry.executionCount("echo.tool"), "混合步的工具照常执行");
    }

    @Test
    void kill后同task续跑从账本接续不重调工具() {
        // 第一段：一步工具后"崩溃"（脚本只剩一个响应——第二次调用抛错），
        // run 吞异常转 DEGRADED；ledger 已有 1 行 DONE 工具步
        script(toolCall("c1", "echo.tool", "{\"text\":\"saved\"}"));
        AgentRuntime runtime = new AgentRuntime(stepService, receiptService, registry);
        AgentResult first = runtime.run(task(fence(8), okContract(), null));
        // 说明：脚本耗尽异常在第二次调用抛出——首个 run 走 DEGRADED（runtime-error）
        assertInstanceOf(AgentResult.Degraded.class, first);
        assertEquals(1, ledger.size());
        int firstExecutions = registry.executionCount("echo.tool");

        // 第二段：续跑（同 run/node），脚本首响应 = 工具步重放不调模型（首脚本即 finish）
        script(toolCall("c9", AgentProtocol.FINISH_TOOL, "{\"deliverable\":{\"approved\":true}}"));
        AgentResult resumed = runtime.run(task(fence(8), okContract(), null));

        assertInstanceOf(AgentResult.Success.class, resumed);
        assertEquals("FINISH", ledger.get(ledger.size() - 1).getKind(), "续跑接续步号收口");
        assertEquals(2, ledger.size());
        assertEquals(firstExecutions, registry.executionCount("echo.tool"),
                "重放已完成工具步：零真实执行（kill-resume 不变式 2）");
    }

    @Test
    void 步进提示从账本与围栏纯函数推导() {
        script(toolCall("c1", "echo.tool", "{}"),
                toolCall("c2", AgentProtocol.FINISH_TOOL, "{\"deliverable\":{\"approved\":true}}"));

        new AgentRuntime(stepService, receiptService, registry)
                .run(task(fence(8), okContract(), null));

        ArgumentCaptor<List> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(spec, times(2)).messages(messagesCaptor.capture());
        List<?> secondMessages = messagesCaptor.getAllValues().get(1);
        org.springframework.ai.chat.messages.Message lastUser =
                (org.springframework.ai.chat.messages.Message) secondMessages.get(secondMessages.size() - 1);
        assertTrue(lastUser.getText().contains("第 2 步") && lastUser.getText().contains("剩余步数 7"),
                "重放的步提示由 stepNo 纯函数推导（纪律 3）");
    }
}
