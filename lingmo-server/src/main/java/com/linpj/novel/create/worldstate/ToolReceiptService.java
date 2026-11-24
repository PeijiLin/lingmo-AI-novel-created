package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelToolReceiptMapper;
import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工具回执账本服务（自主性改造 R0：真实回执驱动）
 *
 * 语义（修正案 §11.3 / 纪律 4）：
 * - <b>同键幂等重放</b>：UNIQUE(run, node, tool, args_hash, attempt)——
 *   kill-resume 后同 attempt 再执行，返回既有回执（不重复执行/计费）
 * - <b>重试预算可推导</b>：attempt 入唯一键；工具契约"重试≤1"的判定依据是
 *   latest() 行——最新 attempt 为 ok=false 才允许 attempt+1（纯内容寻址
 *   会把重试卡死在失败回执上）
 * - <b>失败回照样落盘</b>（ok=false, result 为空）：失败不推进状态 ≠ 失败不留痕
 * - <b>仅 run 内幂等，跨 run 不缓存</b>：正典漂移防线
 * - <b>读故障宁可重做</b>：读不到回执 = 重新执行工具（重复调用代价可控，
 *   绝不把"读不到"当"已成功"消费——防假成功的另一面）
 *
 * @author HL
 */
@Service
public class ToolReceiptService extends ServiceImpl<NovelToolReceiptMapper, NovelToolReceipt> {

    private static final Logger log = LoggerFactory.getLogger(ToolReceiptService.class);

    /**
     * 回执落账（同 attempt 幂等）。
     *
     * @param result  工具结果原文（失败回执传 null 或错误摘要）
     * @param ok      工具执行是否成功（失败照样落盘）
     * @return 落账或已存在的回执行；无效参数或读故障返回 null；非重复键写故障上抛
     */
    public NovelToolReceipt record(Long runId, String nodeId, String tool, String argsHash,
                                   int attempt, Object result, boolean ok, int tokens, long costMs) {
        if (runId == null || nodeId == null || nodeId.isBlank()
                || tool == null || tool.isBlank() || argsHash == null || argsHash.isBlank() || attempt < 0) {
            return null;
        }
        NovelToolReceipt existing;
        try {
            existing = findByKey(runId, nodeId, tool, argsHash, attempt);
        } catch (Exception e) {
            log.warn("回执预检查失败（不落盘，调用方可重试）: runId={}, node={}, tool={}, error={}",
                    runId, nodeId, tool, e.getMessage());
            return null;
        }
        if (existing != null) {
            // 同 attempt 幂等重放：返回既有回执，不重复执行/计费
            return existing;
        }
        NovelToolReceipt record = new NovelToolReceipt();
        record.setRunId(runId);
        record.setNodeId(nodeId);
        record.setTool(tool);
        record.setArgsHash(argsHash);
        record.setAttempt(attempt);
        record.setResult(result);
        record.setOk(ok);
        record.setTokens(tokens);
        record.setCostMs(costMs);
        try {
            this.save(record);
            return record;
        } catch (DuplicateKeyException e) {
            // 竞态（他实例同键先写入）：回读既有回执——幂等语义兜底
            return findByKeyOrNull(runId, nodeId, tool, argsHash, attempt);
        }
    }

    /**
     * 最新回执（最大 attempt 行；重试预算推导依据）。
     * 最新 attempt 为 ok=false 才允许 attempt+1：工具契约"重试≤1"落在这条判定上。
     *
     * @return 最新回执；无记录/读故障返回 null（调用方按"从未执行"重新调用工具）
     */
    public NovelToolReceipt latest(Long runId, String nodeId, String tool, String argsHash) {
        if (runId == null || nodeId == null || tool == null || argsHash == null) {
            return null;
        }
        try {
            List<NovelToolReceipt> records = findByToolKey(runId, nodeId, tool, argsHash);
            return records.stream()
                    .filter(r -> r != null && r.getAttempt() != null)
                    .max(java.util.Comparator.comparingInt(NovelToolReceipt::getAttempt))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("最新回执查询失败（按无回执处理——重新执行工具）: runId={}, node={}, tool={}, error={}",
                    runId, nodeId, tool, e.getMessage());
            return null;
        }
    }

    /**
     * 是否存在成功回执（幂等重放短路判据：任一 attempt ok=true 即复用）。
     * 读故障返回 false（宁可重做，防把"读不到"当"已成功"）。
     */
    public boolean hasSucceeded(Long runId, String nodeId, String tool, String argsHash) {
        if (runId == null || nodeId == null || tool == null || argsHash == null) {
            return false;
        }
        try {
            return findByToolKey(runId, nodeId, tool, argsHash).stream()
                    .anyMatch(r -> r != null && Boolean.TRUE.equals(r.getOk()));
        } catch (Exception e) {
            log.warn("成功回执查询失败（按不存在处理——重新执行工具）: runId={}, node={}, tool={}, error={}",
                    runId, nodeId, tool, e.getMessage());
            return false;
        }
    }

    private List<NovelToolReceipt> findByToolKey(Long runId, String nodeId, String tool, String argsHash) {
        List<NovelToolReceipt> records = this.list(new LambdaQueryWrapper<NovelToolReceipt>()
                .eq(NovelToolReceipt::getRunId, runId)
                .eq(NovelToolReceipt::getNodeId, nodeId)
                .eq(NovelToolReceipt::getTool, tool)
                .eq(NovelToolReceipt::getArgsHash, argsHash));
        return records == null ? List.of() : records;
    }

    private NovelToolReceipt findByKey(Long runId, String nodeId, String tool, String argsHash, int attempt) {
        List<NovelToolReceipt> records = findByToolKey(runId, nodeId, tool, argsHash);
        return records.stream()
                .filter(r -> r != null && r.getAttempt() != null && r.getAttempt() == attempt)
                .findFirst()
                .orElse(null);
    }

    private NovelToolReceipt findByKeyOrNull(Long runId, String nodeId, String tool, String argsHash, int attempt) {
        try {
            return findByKey(runId, nodeId, tool, argsHash, attempt);
        } catch (Exception e) {
            log.warn("竞态回读失败（返回 null，调用方决策重试）: runId={}, node={}, tool={}, error={}",
                    runId, nodeId, tool, e.getMessage());
            return null;
        }
    }
}
