package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelAgentStepMapper;
import com.linpj.novel.create.pojo.entity.NovelAgentStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * AgentRuntime 步骤账本服务（自主性改造 R0：步骤级 Checkpoint 落盘与读取）
 *
 * 语义（修正案 §11.3）：
 * - <b>写通道 upsert 幂等</b>：同 (run, node, step) 重复落账覆盖更新——
 *   失败/崩溃步重做 = 覆盖旧行，DONE 步不重做（回执重放路径不调本方法）
 * - <b>写异常上抛</b>：步骤账本同时是恢复依据与审计账本，写入失败是运行时
 *   （R1a AgentRuntime）的决策点（挂起/降级），服务层不静默吞掉
 * - <b>读故障宁可重做</b>：读不到已完成步 = 重做该步（重试语义，与
 *   NodeCheckpointService.isResumable 的"宁可重做不可误跳"同一取舍；
 *   代价是多花模型调用，收益是永不误跳过关键步骤）
 *
 * @author HL
 */
@Service
public class AgentStepService extends ServiceImpl<NovelAgentStepMapper, NovelAgentStep> {

    private static final Logger log = LoggerFactory.getLogger(AgentStepService.class);

    /** 步骤状态：落盘即占位（崩溃残留，续跑重做覆盖） */
    public static final String STATUS_RUNNING = "RUNNING";

    /** 步骤状态：完成（kill-resume 可从回执重放，不重复调用） */
    public static final String STATUS_DONE = "DONE";

    /** 步骤状态：失败（重做覆盖；留痕不推进状态） */
    public static final String STATUS_FAILED = "FAILED";

    /** 步骤状态：跳过（如降级 L1 后未走的内循环步骤，占位保序） */
    public static final String STATUS_SKIPPED = "SKIPPED";

    /**
     * 步骤落账（upsert 幂等）。
     *
     * @param decision 模型结构化决策原文（可空）
     * @param receiptId 工具回执 ID（TOOL_CALL 步携带，可空）
     * @param status    步骤状态（RUNNING/DONE/FAILED/SKIPPED）
     * @return 落账行 ID；无效参数或读故障（预检查失败）返回 null；写故障上抛
     */
    public Long recordStep(Long runId, String nodeId, int stepNo, String kind, Object decision,
                           Long receiptId, String status, int tokens, String note) {
        if (runId == null || nodeId == null || nodeId.isBlank() || stepNo < 1
                || kind == null || kind.isBlank()) {
            return null;
        }
        NovelAgentStep existing;
        try {
            existing = findByKey(runId, nodeId, stepNo);
        } catch (Exception e) {
            // 读故障：不盲写（写也可能同样的故障），返回 null 由调用方决策重试
            log.warn("步骤账本预检查失败（不落盘，调用方可重试）: runId={}, node={}, step={}, error={}",
                    runId, nodeId, stepNo, e.getMessage());
            return null;
        }
        if (existing != null) {
            existing.setKind(kind);
            existing.setDecision(decision);
            existing.setReceiptId(receiptId);
            existing.setStatus(status);
            existing.setTokens(tokens);
            existing.setNote(note);
            this.updateById(existing);
            return existing.getId();
        }
        NovelAgentStep record = new NovelAgentStep();
        record.setRunId(runId);
        record.setNodeId(nodeId);
        record.setStepNo(stepNo);
        record.setKind(kind);
        record.setDecision(decision);
        record.setReceiptId(receiptId);
        record.setStatus(status);
        record.setTokens(tokens);
        record.setNote(note);
        this.save(record);
        return record.getId();
    }

    /** 该步是否存在（任意状态） */
    public boolean hasStep(Long runId, String nodeId, int stepNo) {
        try {
            return findByKey(runId, nodeId, stepNo) != null;
        } catch (Exception e) {
            log.warn("步骤存在性查询失败（按不存在处理）: runId={}, node={}, step={}, error={}",
                    runId, nodeId, stepNo, e.getMessage());
            return false;
        }
    }

    /**
     * 该步是否完成（kill-resume 可重放判据）。
     * RUNNING/FAILED/SKIPPED 均不算——只有 DONE 才能跳过模型调用从回执重放。
     */
    public boolean hasDoneStep(Long runId, String nodeId, int stepNo) {
        try {
            NovelAgentStep step = findByKey(runId, nodeId, stepNo);
            return step != null && STATUS_DONE.equals(step.getStatus());
        } catch (Exception e) {
            log.warn("步骤完成判定失败（按未完成处理——重做优于误跳）: runId={}, node={}, step={}, error={}",
                    runId, nodeId, stepNo, e.getMessage());
            return false;
        }
    }

    /** 节点内全部步骤（stepNo 升序；重放消息序列的顺序依据）；读故障返回空列表 */
    public List<NovelAgentStep> stepsOf(Long runId, String nodeId) {
        if (runId == null || nodeId == null) {
            return List.of();
        }
        try {
            List<NovelAgentStep> records = this.list(new LambdaQueryWrapper<NovelAgentStep>()
                    .eq(NovelAgentStep::getRunId, runId)
                    .eq(NovelAgentStep::getNodeId, nodeId)
                    .orderByAsc(NovelAgentStep::getStepNo));
            // Java 侧双保险排序（与 NodeCheckpointService.succeededNodes 同防 SQL 条件失效）；
            // stream 派生新列表而非原地 sort——回放/桩返回不可变列表时同样安全；
            // 收集为可变 ArrayList——调用方（AgentRuntime.runLoop）会在 history 上追加新完成步
            if (records == null) {
                return List.of();
            }
            return records.stream()
                    .filter(r -> r != null && r.getStepNo() != null)
                    .sorted(Comparator.comparingInt(NovelAgentStep::getStepNo))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        } catch (Exception e) {
            log.warn("步骤列表查询失败（返回空，续跑按零已完步重做）: runId={}, node={}, error={}",
                    runId, nodeId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 节点内已消耗 token（步骤明细账求和；fence_state 推迟 R3 的替代——
     * 围栏剩余额度 = (已用步数, tokensUsed, run 起始时间戳) 的纯函数推导）。
     * RUNNING 行 tokens=0 不影响求和；读故障返回 0（保守视为未消耗，围栏稍宽不误杀）。
     */
    public long tokensUsed(Long runId, String nodeId) {
        if (runId == null || nodeId == null) {
            return 0;
        }
        try {
            return stepsOf(runId, nodeId).stream()
                    .filter(r -> r != null && r.getTokens() != null)
                    .mapToLong(NovelAgentStep::getTokens)
                    .sum();
        } catch (Exception e) {
            log.warn("步骤 token 汇总失败（按 0 处理）: runId={}, node={}, error={}",
                    runId, nodeId, e.getMessage());
            return 0;
        }
    }

    /** 按唯一键查步骤行（不存在返回 null） */
    private NovelAgentStep findByKey(Long runId, String nodeId, int stepNo) {
        List<NovelAgentStep> records = this.list(new LambdaQueryWrapper<NovelAgentStep>()
                .eq(NovelAgentStep::getRunId, runId)
                .eq(NovelAgentStep::getNodeId, nodeId)
                .eq(NovelAgentStep::getStepNo, stepNo));
        if (records == null || records.isEmpty()) {
            return null;
        }
        return records.get(0);
    }
}
