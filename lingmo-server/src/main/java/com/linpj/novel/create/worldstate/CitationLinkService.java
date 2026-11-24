package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelCitationLinkMapper;
import com.linpj.novel.create.pojo.entity.NovelCitationLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 引文绑定账本服务（自主性改造 R1b：§11.1-M1 引文协议——校验即落账）
 *
 * 语义：
 * - <b>落账静默容错</b>：引文账本是审计/回放面，落账失败不阻断收口流程
 *   （收口判定以内存校验结果为准；步账+回执账已保底恢复语义）
 * - <b>ok 与否都留痕</b>：校验通过的绑定（引文有效）与失败的绑定（模型幻觉/越界）
 *   同表落账——E5 引文有效率指标的数据源
 * - 幂等：UNIQUE(run,node,step,evidence_id)——同收口步同证据地址重复落账跳过
 *   （修复步重 finish 时已有绑定不重复入账）
 *
 * @author HL
 */
@Service
public class CitationLinkService extends ServiceImpl<NovelCitationLinkMapper, NovelCitationLink> {

    private static final Logger log = LoggerFactory.getLogger(CitationLinkService.class);

    /**
     * 引文绑定落账（静默容错；幂等跳过同键）。
     *
     * @return 落账行 id；无效参数/重复/故障返回 null（不阻断收口）
     */
    public Long record(Long runId, String nodeId, int stepNo, String claimText,
                       String evidenceId, long receiptId, int itemIndex, boolean ok, String violations) {
        if (runId == null || nodeId == null || nodeId.isBlank()
                || evidenceId == null || evidenceId.isBlank() || receiptId <= 0 || itemIndex < 1) {
            return null;
        }
        try {
            if (exists(runId, nodeId, stepNo, evidenceId)) {
                return null; // 幂等：修复步重 finish 的既有绑定不重复入账
            }
            NovelCitationLink link = new NovelCitationLink();
            link.setRunId(runId);
            link.setNodeId(nodeId);
            link.setStepNo(stepNo);
            link.setClaimText(truncate(claimText, 2000));
            link.setEvidenceId(evidenceId);
            link.setReceiptId(receiptId);
            link.setItemIndex(itemIndex);
            link.setOk(ok);
            link.setViolations(truncate(violations, 200));
            this.save(link);
            return link.getId();
        } catch (Exception e) {
            log.warn("引文绑定落账失败（静默容错——收口判定不受影响）: run={}, node={}, evidence={}, error={}",
                    runId, nodeId, evidenceId, e.getMessage());
            return null;
        }
    }

    /** 引文有效率统计（E5 指标数据源）：通过绑定数 / 全部绑定数 */
    public double validRate(Long runId, String nodeId) {
        try {
            List<NovelCitationLink> links = this.list(new LambdaQueryWrapper<NovelCitationLink>()
                    .eq(NovelCitationLink::getRunId, runId)
                    .eq(NovelCitationLink::getNodeId, nodeId));
            if (links == null || links.isEmpty()) {
                return 0.0;
            }
            long valid = links.stream().filter(l -> l != null && Boolean.TRUE.equals(l.getOk())).count();
            return (double) valid / links.size();
        } catch (Exception e) {
            log.warn("引文有效率统计失败（返回 0）: run={}, node={}, error={}",
                    runId, nodeId, e.getMessage());
            return 0.0;
        }
    }

    private boolean exists(Long runId, String nodeId, int stepNo, String evidenceId) {
        return this.count(new LambdaQueryWrapper<NovelCitationLink>()
                .eq(NovelCitationLink::getRunId, runId)
                .eq(NovelCitationLink::getNodeId, nodeId)
                .eq(NovelCitationLink::getStepNo, stepNo)
                .eq(NovelCitationLink::getEvidenceId, evidenceId)) > 0;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
