package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelCanonProposalMapper;
import com.linpj.novel.create.pojo.entity.NovelCanonProposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * 正典提案暂存服务（自主性改造 R2a：proposal.emit 只暂存不写入）
 *
 * 语义：
 * - <b>内容寻址幂等</b>：UNIQUE(run, node, proposalHash)——同提案重复 emit 跳过
 *   （工具层重放由回执账兜底，本层防同 run 内模型重复提交同一提案）
 * - <b>暂存非写入</b>：本服务不触碰 novel_state_fact 等正典表——提交门（R3）在
 *   节点 SUCCESS 后统一裁定 ADOPTED/REJECTED 并落正典；任何 Agent 无直写通道
 * - <b>落账静默容错</b>：提案账是审计面，落账失败不阻断工具执行（回执仍留调用事实）
 *
 * @author HL
 */
@Service
public class CanonProposalService extends ServiceImpl<NovelCanonProposalMapper, NovelCanonProposal> {

    private static final Logger log = LoggerFactory.getLogger(CanonProposalService.class);

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ADOPTED = "ADOPTED";
    public static final String STATUS_REJECTED = "REJECTED";

    /**
     * 提案暂存（幂等）。
     *
     * @return 暂存行 id；无效参数/重复/故障返回 null（不阻断工具执行）
     */
    public Long record(Long runId, String nodeId, Integer stepNo, Long receiptId,
                       String proposalJson, String proposalHash) {
        if (runId == null || nodeId == null || nodeId.isBlank()
                || proposalJson == null || proposalJson.isBlank()) {
            return null;
        }
        String hash = proposalHash == null || proposalHash.isBlank() ? sha256(proposalJson) : proposalHash;
        try {
            if (existsByHash(runId, nodeId, hash)) {
                return null; // 幂等：同提案重复 emit 跳过
            }
            NovelCanonProposal record = new NovelCanonProposal();
            record.setRunId(runId);
            record.setNodeId(nodeId);
            record.setStepNo(stepNo);
            record.setReceiptId(receiptId);
            record.setProposal(proposalJson);
            record.setProposalHash(hash);
            record.setStatus(STATUS_PENDING);
            this.save(record);
            return record.getId();
        } catch (Exception e) {
            log.warn("提案暂存失败（静默容错——工具回执仍留调用事实）: run={}, node={}, error={}",
                    runId, nodeId, e.getMessage());
            return null;
        }
    }

    /** run/node 的全部暂存提案（提交门消费入口，R3 接；当前供审计/查询） */
    public List<NovelCanonProposal> proposalsOf(Long runId, String nodeId) {
        if (runId == null || nodeId == null) {
            return List.of();
        }
        try {
            List<NovelCanonProposal> records = this.list(new LambdaQueryWrapper<NovelCanonProposal>()
                    .eq(NovelCanonProposal::getRunId, runId)
                    .eq(NovelCanonProposal::getNodeId, nodeId)
                    .orderByAsc(NovelCanonProposal::getId));
            return records == null ? List.of() : records;
        } catch (Exception e) {
            log.warn("提案查询失败（返回空）: run={}, node={}, error={}", runId, nodeId, e.getMessage());
            return List.of();
        }
    }

    private boolean existsByHash(Long runId, String nodeId, String hash) {
        return this.count(new LambdaQueryWrapper<NovelCanonProposal>()
                .eq(NovelCanonProposal::getRunId, runId)
                .eq(NovelCanonProposal::getNodeId, nodeId)
                .eq(NovelCanonProposal::getProposalHash, hash)) > 0;
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest((text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "fallback:" + (text == null ? "" : text).hashCode();
        }
    }
}
