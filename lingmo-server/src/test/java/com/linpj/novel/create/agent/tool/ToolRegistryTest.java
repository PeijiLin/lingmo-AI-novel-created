package com.linpj.novel.create.agent.tool;

import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import com.linpj.novel.create.worldstate.ToolReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ToolRegistry 红线测试（自主性改造 R1a：§12.8——"未经 Registry 的工具调用零执行"）
 *
 * @author HL
 */
class ToolRegistryTest {

    private ToolRegistry registry;
    private ToolReceiptService receipts;

    private final AgentTool echoTool = new AgentTool() {
        @Override
        public String name() {
            return "echo.tool";
        }

        @Override
        public String description() {
            return "回声工具（测试桩）";
        }

        @Override
        public String inputSchema() {
            return "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}";
        }

        @Override
        public String execute(AgentToolContext context, String argumentsJson) {
            return "echo:" + (argumentsJson == null ? "" : argumentsJson);
        }
    };

    private final AgentTool boomTool = new AgentTool() {
        @Override
        public String name() {
            return "boom.tool";
        }

        @Override
        public String description() {
            return "必炸工具（失败留痕测试桩）";
        }

        @Override
        public String inputSchema() {
            return "{\"type\":\"object\"}";
        }

        @Override
        public String execute(AgentToolContext context, String argumentsJson) {
            throw new IllegalStateException("模拟执行失败");
        }
    };

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.register(echoTool);
        registry.register(boomTool);
        receipts = mock(ToolReceiptService.class);
    }

    private NovelToolReceipt receipt(long id, int attempt, boolean ok) {
        NovelToolReceipt receipt = new NovelToolReceipt();
        receipt.setId(id);
        receipt.setAttempt(attempt);
        receipt.setOk(ok);
        receipt.setResult("stored-result");
        return receipt;
    }

    @Test
    void 未注册工具拒绝执行且拒因留痕() {
        ToolRegistry.ToolExecution execution = registry.execute(
                "evil.tool", "{}", AgentToolContext.of(7L, "CRITIC:1", null), receipts);

        assertFalse(execution.ok());
        assertTrue(execution.resultText().contains("不在白名单"));
        assertEquals(0, registry.executionCount("evil.tool"), "拒绝不进执行计数");
    }

    @Test
    void 白名单回调物理裁剪未注册名() {
        List<ToolCallback> callbacks = registry.callbacksFor(Set.of("echo.tool", "not.registered"));

        assertEquals(1, callbacks.size(), "未注册名不生成描述回调——模型不可见（物理白名单）");
        assertEquals("echo.tool", callbacks.get(0).getToolDefinition().name());
    }

    @Test
    void 幂等重放零真实执行() {
        when(receipts.hasSucceeded(anyLong(), anyString(), anyString(), anyString())).thenReturn(true);
        when(receipts.latest(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(receipt(55L, 0, true));

        ToolRegistry.ToolExecution execution = registry.execute(
                "echo.tool", "{\"text\":\"hi\"}", AgentToolContext.of(7L, "CRITIC:1", null), receipts);

        assertTrue(execution.ok());
        assertFalse(execution.executedNow(), "已有成功回执 → 重放，不执行");
        assertEquals("stored-result", execution.resultText());
        assertEquals(0, registry.executionCount("echo.tool"), "杀-恢复不重复执行（kill-resume 不变式 2）");
    }

    @Test
    void 执行成功落回执并计数() {
        when(receipts.hasSucceeded(anyLong(), anyString(), anyString(), anyString())).thenReturn(false);
        when(receipts.record(anyLong(), anyString(), anyString(), anyString(), anyInt(),
                any(), anyBoolean(), anyInt(), anyLong())).thenReturn(receipt(1L, 0, true));

        ToolRegistry.ToolExecution execution = registry.execute(
                "echo.tool", "{\"text\":\"hi\"}", AgentToolContext.of(7L, "CRITIC:1", null), receipts);

        assertTrue(execution.ok());
        assertTrue(execution.executedNow());
        assertEquals(1L, execution.receiptId(), "回执 id 回传（步账 receiptId 落账依据）");
        assertTrue(execution.resultText().startsWith("echo:"));
        assertEquals(1, registry.executionCount("echo.tool"));
    }

    @Test
    void 执行异常失败回执留痕() {
        when(receipts.hasSucceeded(anyLong(), anyString(), anyString(), anyString())).thenReturn(false);
        when(receipts.record(anyLong(), anyString(), anyString(), anyString(), anyInt(),
                any(), anyBoolean(), anyInt(), anyLong())).thenReturn(receipt(2L, 0, false));

        ToolRegistry.ToolExecution execution = registry.execute(
                "boom.tool", "{}", AgentToolContext.of(7L, "CRITIC:1", null), receipts);

        assertFalse(execution.ok(), "异常落 ok=false 失败回执（失败不推进状态≠失败不留痕）");
        assertTrue(execution.resultText().contains("执行失败"));
        assertEquals(1, registry.executionCount("boom.tool"), "失败执行同样计数（真实执行=审计口径）");
    }

    @Test
    void 重试预算耗尽直接拒绝不再执行() {
        when(receipts.hasSucceeded(anyLong(), anyString(), anyString(), anyString())).thenReturn(false);
        when(receipts.latest(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(receipt(3L, 1, false)); // attempt=1 已失败 = 重试（≤1）已花完

        ToolRegistry.ToolExecution execution = registry.execute(
                "echo.tool", "{}", AgentToolContext.of(7L, "CRITIC:1", null), receipts);

        assertFalse(execution.ok());
        assertTrue(execution.resultText().contains("重试预算已耗尽"));
        assertEquals(0, registry.executionCount("echo.tool"), "预算耗尽不执行（防失败重试无限烧）");
    }

    @Test
    void 最新失败允许重试一次进位() {
        when(receipts.hasSucceeded(anyLong(), anyString(), anyString(), anyString())).thenReturn(false);
        when(receipts.latest(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(receipt(4L, 0, false)); // attempt=0 失败 → 允许 attempt=1
        when(receipts.record(anyLong(), anyString(), anyString(), anyString(), eq(1),
                any(), anyBoolean(), anyInt(), anyLong())).thenReturn(receipt(5L, 1, true));

        ToolRegistry.ToolExecution execution = registry.execute(
                "echo.tool", "{}", AgentToolContext.of(7L, "CRITIC:1", null), receipts);

        assertTrue(execution.ok());
        assertTrue(execution.executedNow(), "attempt 0→1 是合法重试（真实执行）");
        assertEquals(1, registry.executionCount("echo.tool"));
    }
}
