package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelNodeCheckpointMapper;
import com.linpj.novel.create.pojo.entity.NovelNodeCheckpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 节点 checkpoint 服务（M5-F2：断点续跑"不重放已完成节点副作用"的判定依据）
 *
 * - 写入：编排器节点成功后旁路落账（故障静默——checkpoint 是续跑能力，不是管道依赖，
 *   写失败只损失续跑资格，不能反过来阻断创作）
 * - 读取：续跑入口按 runId 查成功节点集；按 (run, node, inputHash) 判定可复用性
 * - 幂等：UNIQUE(run_id, node_id) 兜底，同节点重复落账跳过（续跑后二次成功不再新增行）
 *
 * @author HL
 */
@Service
public class NodeCheckpointService extends ServiceImpl<NovelNodeCheckpointMapper, NovelNodeCheckpoint> {

    private static final Logger log = LoggerFactory.getLogger(NodeCheckpointService.class);

    private static final String STATUS_SUCCEEDED = "SUCCEEDED";

    /**
     * 节点成功落账（旁路、静默容错）。
     *
     * @param runId     任务 ID
     * @param nodeId    节点标识（阶段:轮次）
     * @param inputHash 节点核心输入哈希（复用判据；可空 = 无复用语义仅记录事实）
     * @param tokens    该节点 token 用量
     * @param note      备注（可空）
     */
    public void markSucceeded(Long runId, String nodeId, String inputHash, Integer tokens, String note) {
        if (runId == null || nodeId == null || nodeId.isBlank()) {
            return;
        }
        try {
            if (hasSucceeded(runId, nodeId)) {
                return; // 幂等：续跑后节点再次成功不重复落账
            }
            NovelNodeCheckpoint record = new NovelNodeCheckpoint();
            record.setRunId(runId);
            record.setNodeId(nodeId);
            record.setStatus(STATUS_SUCCEEDED);
            record.setInputHash(inputHash);
            record.setTokens(tokens);
            record.setNote(note);
            this.save(record);
        } catch (Exception e) {
            // 静默容错：checkpoint 故障只损失续跑资格，绝不阻断创作主链路
            log.warn("checkpoint 落账失败（续跑资格损失，管道不受影响）: runId={}, node={}, error={}",
                    runId, nodeId, e.getMessage());
        }
    }

    /** 该节点是否已成功（含唯一键存在性；查询故障按"未成功"处理——宁可重做不可误跳） */
    public boolean hasSucceeded(Long runId, String nodeId) {
        if (runId == null || nodeId == null) {
            return false;
        }
        try {
            return this.count(new LambdaQueryWrapper<NovelNodeCheckpoint>()
                    .eq(NovelNodeCheckpoint::getRunId, runId)
                    .eq(NovelNodeCheckpoint::getNodeId, nodeId)
                    .eq(NovelNodeCheckpoint::getStatus, STATUS_SUCCEEDED)) > 0;
        } catch (Exception e) {
            log.warn("checkpoint 查询失败（按未成功处理——重做优于误跳）: runId={}, node={}, error={}",
                    runId, nodeId, e.getMessage());
            return false;
        }
    }

    /**
     * 节点可复用判定（F2 核心）：已成功 且 inputHash 一致。
     * hash 不一致 = 输入已变更（作者重写指令/新约束）= 新意图，必须重做。
     */
    public boolean isResumable(Long runId, String nodeId, String inputHash) {
        if (inputHash == null) {
            return false; // 无输入指纹 = 无复用语义
        }
        try {
            return this.count(new LambdaQueryWrapper<NovelNodeCheckpoint>()
                    .eq(NovelNodeCheckpoint::getRunId, runId)
                    .eq(NovelNodeCheckpoint::getNodeId, nodeId)
                    .eq(NovelNodeCheckpoint::getStatus, STATUS_SUCCEEDED)
                    .eq(NovelNodeCheckpoint::getInputHash, inputHash)) > 0;
        } catch (Exception e) {
            log.warn("checkpoint 复用判定失败（按不可复用处理）: runId={}, node={}, error={}",
                    runId, nodeId, e.getMessage());
            return false;
        }
    }

    /** run 的全部成功节点 ID 集（续跑入口与 run 汇总用） */
    public Set<String> succeededNodes(Long runId) {
        Set<String> nodes = new LinkedHashSet<>();
        if (runId == null) {
            return nodes;
        }
        try {
            List<NovelNodeCheckpoint> records = this.list(new LambdaQueryWrapper<NovelNodeCheckpoint>()
                    .eq(NovelNodeCheckpoint::getRunId, runId)
                    .eq(NovelNodeCheckpoint::getStatus, STATUS_SUCCEEDED)
                    .orderByAsc(NovelNodeCheckpoint::getId));
            // Java 侧双保险过滤：SQL 条件失效/脏数据时语义仍稳（只聚成功节点）
            records.stream()
                    .filter(record -> record != null && STATUS_SUCCEEDED.equals(record.getStatus()))
                    .forEach(record -> nodes.add(record.getNodeId()));
        } catch (Exception e) {
            log.warn("checkpoint 节点集查询失败（返回空集）: runId={}, error={}", runId, e.getMessage());
        }
        return nodes;
    }
}
