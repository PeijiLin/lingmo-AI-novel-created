package com.linpj.novel.create.agent.tool;

import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import com.linpj.novel.create.utils.JsonUtil;
import com.linpj.novel.create.worldstate.ToolReceiptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具注册表与唯一执行通道（自主性改造 R1a：§12.8 Registry 红线）
 *
 * <p>红线四条：
 * <ol>
 *   <li><b>唯一执行通道</b>：AgentRuntime 只经本类 execute 执行工具；
 *       ChatClient 注册的 ToolCallback 只承担描述（callback.call 永不触发——
 *       internalToolExecutionEnabled(false)），测试以 executionCount 断言；</li>
 *   <li><b>白名单物理裁剪</b>：不存在/未白名单工具不执行，但记拒绝回执（留痕）
 *       + 拒因 ToolResponse 喂回（模型可纠错）；</li>
 *   <li><b>幂等重放</b>：同 (run, node, tool, argsHash, attempt) 已有成功回执直接复用
 *       （kill-resume 不重复执行/计费）；最新 attempt 失败才允许 attempt+1
 *       （工具契约"重试≤1"由账本推导，R0 设计）；</li>
 *   <li><b>失败留痕</b>：执行异常落 ok=false 回执（失败不推进状态 ≠ 失败不留痕）。</li>
 * </ol>
 *
 * <p>回执 result 一律为不可信数据区标记下的事实材料：只进 ToolResponse 文本与回执表，
 * 任何工具均无正典写通道（读范围≠写授权）。
 *
 * @author HL
 */
@Service
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    /** 工具执行硬超时（毫秒）——正典查询应远低于此值，超时视为失败回执 */
    private static final long EXECUTION_TIMEOUT_MS = 10_000L;

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();
    /** 真实执行计数（幂等重放/拒绝不计）——红线测试断言依据 */
    private final Map<String, AtomicInteger> executionCounts = new ConcurrentHashMap<>();

    /** Spring 装配：所有 AgentTool bean（@Component 工具）自动注册 */
    @org.springframework.beans.factory.annotation.Autowired
    public ToolRegistry(List<AgentTool> registeredTools) {
        if (registeredTools != null) {
            registeredTools.forEach(this::register);
        }
    }

    /** 手工装配（测试用） */
    public ToolRegistry() {
    }

    public void register(AgentTool tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            return;
        }
        tools.put(tool.name(), tool);
    }

    public boolean has(String name) {
        return name != null && tools.containsKey(name);
    }

    /**
     * 为白名单生成 ChatClient 描述注册件（占位函数体永不被调——执行权归本类）。
     * 未注册名悄然忽略（模型不可见 = 物理白名单）。
     */
    public List<ToolCallback> callbacksFor(Set<String> whitelist) {
        List<ToolCallback> callbacks = new ArrayList<>();
        if (whitelist == null) {
            return callbacks;
        }
        for (String name : whitelist) {
            AgentTool tool = tools.get(name);
            if (tool == null) {
                continue;
            }
            // 占位函数体永不被调（执行权归本类）；inputType 必填是 Spring AI 构建器约束，
            // String.class + 自定义 inputSchema：schema 生效、类型仅占位
            callbacks.add(FunctionToolCallback.builder(tool.name(), (String args) -> "unused: execution-via-registry")
                    .description(tool.description())
                    .inputType(String.class)
                    .inputSchema(tool.inputSchema())
                    .build());
        }
        return callbacks;
    }

    /**
     * 唯一执行入口。
     *
     * @return 执行结果（含回执 id/成功性/结果文本/是否本真实执行；拒绝/超时/异常均带 ok=false）
     */
    public ToolExecution execute(String toolName, String argumentsJson,
                                 AgentToolContext context, ToolReceiptService receiptService) {
        AgentTool.AgentContext agentContext = context.toAgentContext();
        String argsHash = sha256(argumentsJson == null ? "" : argumentsJson);

        // ---- 白名单拒绝（未注册工具：拒绝留痕 + 拒因回喂，不执行） ----
        if (!has(toolName)) {
            NovelToolReceipt receipt = null;
            try {
                receipt = receiptService.record(context.runId(), context.nodeId(),
                        "rejected:" + toolName, argsHash, 0,
                        "工具不在白名单：" + toolName, false, 0, 0);
            } catch (Exception e) {
                log.warn("拒绝回执落账失败（拒因直接回喂）: tool={}, error={}", toolName, e.getMessage());
            }
            log.info("拒绝未授权工具调用: run={}, node={}, tool={}",
                    context.runId(), context.nodeId(), toolName);
            return new ToolExecution(receipt == null ? null : receipt.getId(), false,
                    "拒绝执行：工具 " + toolName + " 不在白名单", false);
        }

        // ---- 幂等重放：任意成功回执直接复用（不执行不计费） ----
        try {
            if (receiptService.hasSucceeded(context.runId(), context.nodeId(), toolName, argsHash)) {
                NovelToolReceipt latest = receiptService.latest(
                        context.runId(), context.nodeId(), toolName, argsHash);
                log.debug("工具回执幂等重放: run={}, tool={}, receipt={}",
                        context.runId(), toolName, latest == null ? null : latest.getId());
                return new ToolExecution(latest == null ? null : latest.getId(), true,
                        String.valueOf(latest.getResult()), false);
            }
        } catch (Exception e) {
            log.warn("幂等预检故障（按未执行处理，走真实执行）: tool={}, error={}", toolName, e.getMessage());
        }

        // ---- attempt 推导：最新 attempt 失败才允许 +1（依赖 R0 回执账本） ----
        int attempt = 0;
        try {
            NovelToolReceipt latest = receiptService.latest(
                    context.runId(), context.nodeId(), toolName, argsHash);
            if (latest != null && !Boolean.TRUE.equals(latest.getOk())) {
                attempt = (latest.getAttempt() == null ? 0 : latest.getAttempt()) + 1;
                if (attempt > 1) {
                    // 重试预算（≤1）已耗尽：直接失败回执，不再执行
                    NovelToolReceipt exhausted = receiptService.record(context.runId(), context.nodeId(),
                            toolName, argsHash, latest.getAttempt() == null ? 1 : latest.getAttempt(),
                            "重试预算已耗尽（上次执行失败）", false, 0, 0);
                    return new ToolExecution(exhausted == null ? null : exhausted.getId(), false,
                            "工具执行失败（重试预算已耗尽）", false);
                }
            }
        } catch (Exception e) {
            // 预检故障按 attempt=0 执行（宁可重做不误拒绝——record 的唯一键幂等兜底竞态）
            attempt = 0;
        }

        // ---- 真实执行（超时硬上限；异常/超时落失败回执） ----
        AgentTool tool = tools.get(toolName);
        long startAt = System.currentTimeMillis();
        String resultText;
        boolean ok = true;
        try {
            resultText = timedExecute(tool, agentContext, argumentsJson);
            if (resultText == null) {
                resultText = "（空结果）";
            }
        } catch (Exception e) {
            ok = false;
            resultText = "工具执行失败：" + e.getMessage();
            log.warn("工具执行失败（落失败回执）: run={}, tool={}, error={}",
                    context.runId(), toolName, e.getMessage());
        }
        long costMs = System.currentTimeMillis() - startAt;
        Long receiptId = null;
        try {
            NovelToolReceipt receipt = receiptService.record(context.runId(), context.nodeId(),
                    toolName, argsHash, attempt, resultText, ok, 0, costMs);
            receiptId = receipt == null ? null : receipt.getId();
        } catch (Exception e) {
            log.warn("回执落账失败（结果仍回喂模型，审计缺失由步账兜底）: tool={}, error={}",
                    toolName, e.getMessage());
        }
        executionCounts.computeIfAbsent(toolName, k -> new AtomicInteger()).incrementAndGet();
        return new ToolExecution(receiptId, ok, resultText, true);
    }

    /** 执行并施加墙钟硬上限（守护线程实现：正典只读工具不应超时，超过即失败回执） */
    private String timedExecute(AgentTool tool, AgentTool.AgentContext context, String argumentsJson)
            throws Exception {
        var future = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return tool.execute(new AgentToolContext(context.runId(), context.nodeId(),
                                context.data()), argumentsJson);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        try {
            return future.get(EXECUTION_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("工具执行超时（>" + EXECUTION_TIMEOUT_MS + "ms）");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException(cause.getMessage() == null ? cause.toString() : cause.getMessage(), cause);
        }
    }

    /** 真实执行计数（含失败执行；幂等重放/白名单拒绝不计）——测试红线断言依据 */
    public int executionCount(String toolName) {
        AtomicInteger counter = executionCounts.get(toolName);
        return counter == null ? 0 : counter.get();
    }

    /** 参数内容哈希（SHA-256；回执内容寻址键） */
    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            // 退化为稳定字符串键（哈希故障不应阻断工具执行）
            return "fallback:" + (text == null ? "" : text).hashCode();
        }
    }

    /** 工具执行结果（receiptId：落账成功才有） */
    public record ToolExecution(Long receiptId, boolean ok, String resultText, boolean executedNow) {
    }

    /** 工具注册启动日志辅助（Spring 装配后打印可用面） */
    public String describeTools() {
        return JsonUtil.toJson(Map.of("tools", tools.keySet()));
    }
}
